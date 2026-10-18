package neo.sketchware.plugin.marketplace;

import android.content.Context;
import android.content.SharedPreferences;

import neo.sketchware.marketplace.SketchlibHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

/** Secure HTTPS catalog reader/downloader. The app does not ship a fake/default marketplace endpoint. */
public final class PluginMarketplaceRepository {
    private static final String PREFS = "plugin_marketplace_settings";
    private static final String KEY_CATALOG_URL = "catalog_url";
    public static final String DEFAULT_CATALOG_URL = "https://plugin.sketchlib.in/api/v1/catalog.json";
    private static final String KEY_CATALOG_CACHE_PREFIX = "catalog_cache.";
    private static final String KEY_CATALOG_CACHE_TIME_PREFIX = "catalog_cache_time.";
    private static final long CATALOG_CACHE_TTL_MS = 5L * 60L * 1000L;
    private static final String KEY_INSTALLED_VERSION_PREFIX = "installed_version.";
    private static final java.util.regex.Pattern PLUGIN_ID_PATTERN = java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 100L * 1024L * 1024L;

    private PluginMarketplaceRepository() {}

    public static String getCatalogUrl(Context context) {
        return prefs(context).getString(KEY_CATALOG_URL, DEFAULT_CATALOG_URL).trim();
    }

    public static void setCatalogUrl(Context context, String url) {
        String normalized = url == null ? "" : url.trim();
        if (!normalized.isEmpty()) requireHttps(normalized, "catalog URL");
        prefs(context).edit().putString(KEY_CATALOG_URL, normalized).apply();
    }

    /** Returns the last catalog version successfully installed when a legacy manifest has no version field. */
    public static String getInstalledVersion(Context context, String pluginId) {
        String key = installedVersionKey(pluginId);
        return key == null ? "" : prefs(context).getString(key, "").trim();
    }

    public static void recordInstalledVersion(Context context, String pluginId, String version) {
        String key = installedVersionKey(pluginId);
        if (key == null || version == null || !PluginVersionUtils.isValidVersion(version)) return;
        prefs(context).edit().putString(key, version.trim()).apply();
    }

    public static void clearInstalledVersion(Context context, String pluginId) {
        String key = installedVersionKey(pluginId);
        if (key != null && context != null) prefs(context).edit().remove(key).apply();
    }

    private static String installedVersionKey(String pluginId) {
        return pluginId != null && PLUGIN_ID_PATTERN.matcher(pluginId).matches()
                ? KEY_INSTALLED_VERSION_PREFIX + pluginId : null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<PluginCatalogItem> fetchCatalog(String catalogUrl) throws Exception {
        requireHttps(catalogUrl, "catalog URL");
        byte[] bytes = readHttps(catalogUrl, MAX_CATALOG_BYTES, "application/json");
        return parseCatalogJson(new String(bytes, StandardCharsets.UTF_8));
    }

    private static List<PluginCatalogItem> parseCatalogJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        Object rawSchema = root.opt("schemaVersion");
        if (!(rawSchema instanceof Number)) throw new IllegalArgumentException("Catalog schemaVersion must be the integer 1.");
        double schemaNumber = ((Number) rawSchema).doubleValue();
        if (!Double.isFinite(schemaNumber) || schemaNumber != 1.0d) throw new IllegalArgumentException("Unsupported catalog schemaVersion. This build supports version 1.");
        JSONArray array = root.optJSONArray("plugins");
        if (array == null) throw new IllegalArgumentException("Catalog is missing its 'plugins' array");
        if (array.length() > 2000) throw new IllegalArgumentException("Catalog contains too many plugin entries");
        List<PluginCatalogItem> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject raw = array.optJSONObject(i);
            if (raw == null) throw new IllegalArgumentException("Invalid catalog entry at index " + i);
            PluginCatalogItem item = PluginCatalogItem.fromJson(raw);
            if (!ids.add(item.pluginId())) throw new IllegalArgumentException("Duplicate pluginId in catalog: " + item.pluginId());
            items.add(item);
        }
        return items;
    }

    /** Uses a short-lived local cache to avoid bursts against rate-limited public APIs. */
    public static List<PluginCatalogItem> fetchCatalog(Context context, String catalogUrl) throws Exception {
        if (context == null) return fetchCatalog(catalogUrl);
        requireHttps(catalogUrl, "catalog URL");
        String cacheId = Integer.toHexString(catalogUrl.hashCode());
        SharedPreferences preferences = prefs(context);
        String cacheKey = KEY_CATALOG_CACHE_PREFIX + cacheId;
        String timestampKey = KEY_CATALOG_CACHE_TIME_PREFIX + cacheId;
        long cachedAt = preferences.getLong(timestampKey, 0L);
        String cachedJson = preferences.getString(cacheKey, "");
        long age = System.currentTimeMillis() - cachedAt;
        if (!cachedJson.isBlank() && age >= 0 && age < CATALOG_CACHE_TTL_MS) {
            try { return parseCatalogJson(cachedJson); } catch (Exception ignored) { /* discard invalid cache */ }
        }
        List<PluginCatalogItem> loaded = fetchCatalog(catalogUrl);
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", 1);
            JSONArray array = new JSONArray();
            for (PluginCatalogItem item : loaded) array.put(item.toJson());
            root.put("plugins", array);
            preferences.edit().putString(cacheKey, root.toString())
                    .putLong(timestampKey, System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
        return loaded;
    }

    public static File downloadAndVerify(Context context, PluginCatalogItem item) throws Exception {
        if (item == null) throw new IllegalArgumentException("No plugin selected");
        if (item.pluginId() == null || !PLUGIN_ID_PATTERN.matcher(item.pluginId()).matches()) {
            throw new IllegalArgumentException("Invalid plugin ID for download");
        }
        requireHttps(item.downloadUrl(), "plugin download URL");
        File directory = new File(context.getCacheDir(), "plugin-marketplace");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Cannot create temporary download directory");
        }
        File part = new File(directory, item.pluginId() + ".part");
        if (part.exists() && !part.delete()) throw new IllegalStateException("Cannot clear previous partial download");
        try {
            downloadHttpsToFile(item.downloadUrl(), part, MAX_ARCHIVE_BYTES);
            String digest = sha256(part);
            if (!digest.equalsIgnoreCase(item.sha256())) throw new SecurityException("SHA-256 verification failed. The plugin was not installed.");
            boolean apk = isApkArchive(part) || archivePathEndsWithApk(item.downloadUrl());
            File output = new File(directory, item.pluginId() + (apk ? ".apk" : ".jar"));
            if (output.exists() && !output.delete()) throw new IllegalStateException("Cannot replace cached plugin archive");
            if (!part.renameTo(output)) throw new IllegalStateException("Cannot finalize downloaded plugin file");
            return output;
        } catch (Exception e) {
            if (part.exists()) part.delete();
            throw e;
        }
    }

    private static byte[] readHttps(String address, long maxBytes, String acceptHeader) throws Exception {
        URL current = new URL(address);
        requireHttps(current.toString(), "URL");
        for (int redirects = 0; redirects <= 5; redirects++) {
            SketchlibHttpClient.beforeRequest(current);
            HttpsURLConnection connection = (HttpsURLConnection) current.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(25000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", acceptHeader);
                connection.setRequestProperty("User-Agent", "SketchwareNeo-PluginManager/1");
                int status = connection.getResponseCode();
                String retryAfter = connection.getHeaderField("Retry-After");
                SketchlibHttpClient.recordResponse(current, status, retryAfter);
                if (status >= 300 && status <= 399) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new java.io.IOException("HTTPS redirect has no destination");
                    current = new URL(current, location);
                    requireHttps(current.toString(), "redirect URL");
                    continue;
                }
                if (status < 200 || status >= 300) throw SketchlibHttpClient.httpError(current, status, retryAfter);
                long contentLength = connection.getContentLengthLong();
                if (contentLength > maxBytes) throw new java.io.IOException("Download exceeds the configured size limit");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if ((long) output.size() + read > maxBytes) throw new java.io.IOException("Response exceeds the configured size limit");
                        output.write(buffer, 0, read);
                    }
                    return output.toByteArray();
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new java.io.IOException("Too many HTTPS redirects");
    }



    private static boolean archivePathEndsWithApk(String address) {
        try {
            String path = new java.net.URI(address).getPath();
            return path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".apk");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isApkArchive(File archive) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive)) {
            java.util.zip.ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            return entry != null && !entry.isDirectory();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void downloadHttpsToFile(String address, File destination, long maxBytes) throws Exception {
        URL current = new URL(address);
        requireHttps(current.toString(), "plugin download URL");
        for (int redirects = 0; redirects <= 5; redirects++) {
            SketchlibHttpClient.beforeRequest(current);
            HttpsURLConnection connection = (HttpsURLConnection) current.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/java-archive, application/octet-stream, application/vnd.android.package-archive");
                connection.setRequestProperty("User-Agent", "SketchwareNeo-PluginManager/1");
                int status = connection.getResponseCode();
                String retryAfter = connection.getHeaderField("Retry-After");
                SketchlibHttpClient.recordResponse(current, status, retryAfter);
                if (status >= 300 && status <= 399) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new java.io.IOException("HTTPS redirect has no destination");
                    current = new URL(current, location);
                    requireHttps(current.toString(), "redirect URL");
                    continue;
                }
                if (status < 200 || status >= 300) throw SketchlibHttpClient.httpError(current, status, retryAfter);
                long length = connection.getContentLengthLong();
                if (length > maxBytes) throw new java.io.IOException("Plugin archive exceeds the 100 MB safety limit");
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[32 * 1024];
                    long total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > maxBytes) throw new java.io.IOException("Plugin archive exceeds the 100 MB safety limit");
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                    output.getFD().sync();
                    return;
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new java.io.IOException("Too many HTTPS redirects");
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private static void requireHttps(String address, String label) {
        try {
            java.net.URI uri = new java.net.URI(address);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException(label + " must be an HTTPS URL without embedded credentials");
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Invalid " + label, e);
        }
    }
}
