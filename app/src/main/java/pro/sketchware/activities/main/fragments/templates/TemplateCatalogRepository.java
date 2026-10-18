package pro.sketchware.activities.main.fragments.templates;

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
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

/** Bounded, HTTPS-only catalog/download client for template.sketchlib.in. */
public final class TemplateCatalogRepository {
    public static final String DEFAULT_CATALOG_URL = "https://template.sketchlib.in/api/v1/catalog.json";
    private static final String PREFS = "sketchlib_template_catalog";
    private static final String CACHE_JSON = "catalog_json";
    private static final String CACHE_TIME = "catalog_time";
    private static final long CACHE_TTL_MS = 5L * 60L * 1000L;
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024;

    private TemplateCatalogRepository() { }

    public static final class CatalogResult {
        public final List<TemplateCatalogItem> items;
        public final boolean staleCache;
        public final String refreshError;
        CatalogResult(List<TemplateCatalogItem> items, boolean staleCache, String refreshError) {
            this.items = java.util.Collections.unmodifiableList(new ArrayList<>(items));
            this.staleCache = staleCache;
            this.refreshError = refreshError == null ? "" : refreshError;
        }
    }

    public static List<TemplateCatalogItem> fetchCatalog(Context context) throws Exception {
        return fetchCatalogResult(context).items;
    }

    public static CatalogResult fetchCatalogResult(Context context) throws Exception {
        if (context == null) throw new IllegalArgumentException("A valid app context is required.");
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cached = preferences.getString(CACHE_JSON, "");
        long cachedAt = preferences.getLong(CACHE_TIME, 0L);
        long age = System.currentTimeMillis() - cachedAt;
        if (!cached.isBlank() && age >= 0 && age < CACHE_TTL_MS) {
            try { return new CatalogResult(parseCatalog(cached), false, ""); } catch (Exception ignored) { /* discard invalid cache */ }
        }
        try {
            byte[] body = readHttps(DEFAULT_CATALOG_URL, MAX_CATALOG_BYTES, "application/json");
            String json = new String(body, StandardCharsets.UTF_8);
            List<TemplateCatalogItem> items = parseCatalog(json);
            preferences.edit().putString(CACHE_JSON, json).putLong(CACHE_TIME, System.currentTimeMillis()).apply();
            return new CatalogResult(items, false, "");
        } catch (Exception networkError) {
            if (!cached.isBlank()) {
                try { return new CatalogResult(parseCatalog(cached), true, safeMessage(networkError)); }
                catch (Exception ignored) { /* fail below with the useful network error */ }
            }
            throw networkError;
        }
    }

    private static List<TemplateCatalogItem> parseCatalog(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        Object rawSchema = root.opt("schemaVersion");
        if (!(rawSchema instanceof Number) || ((Number) rawSchema).doubleValue() != 1.0d) {
            throw new IllegalArgumentException("Unsupported template catalog schema. Expected schemaVersion 1.");
        }
        JSONArray items = root.optJSONArray("templates");
        if (items == null || items.length() > 1000) throw new IllegalArgumentException("Template catalog is missing its templates array or is too large.");
        ArrayList<TemplateCatalogItem> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject object = items.optJSONObject(i);
            if (object == null) throw new IllegalArgumentException("Invalid template record at index " + i + ".");
            TemplateCatalogItem item = TemplateCatalogItem.fromJson(object);
            if (!ids.add(item.id.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate template ID in catalog.");
            result.add(item);
        }
        return result;
    }

    public static File downloadAndVerify(Context context, TemplateCatalogItem item) throws Exception {
        if (context == null || item == null) throw new IllegalArgumentException("Select a template to download.");
        File directory = new File(context.getCacheDir(), "sketchlib-templates");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) throw new java.io.IOException("Could not create the template cache directory.");
        File part = new File(directory, item.id + ".part");
        File completed = new File(directory, item.id + "-" + safeVersion(item.version) + ".swnscreen");
        if (part.exists() && !part.delete()) throw new java.io.IOException("Could not clear an earlier partial download.");
        try {
            downloadToFile(item.downloadUrl, part);
            String actual = sha256(part);
            if (!actual.equalsIgnoreCase(item.sha256)) throw new SecurityException("Template integrity verification failed. The package was not opened.");
            if (completed.exists() && !completed.delete()) throw new java.io.IOException("Could not replace the cached template file.");
            if (!part.renameTo(completed)) throw new java.io.IOException("Could not finalize the verified template package.");
            return completed;
        } catch (Exception error) {
            if (part.exists()) part.delete();
            throw error;
        }
    }

    private static String safeVersion(String version) {
        if (version == null || version.isBlank()) return "latest";
        return version.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static byte[] readHttps(String address, long maxBytes, String accept) throws Exception {
        URL current = new URL(address);
        requireHttps(current);
        for (int redirects = 0; redirects <= 5; redirects++) {
            SketchlibHttpClient.beforeRequest(current);
            HttpsURLConnection connection = (HttpsURLConnection) current.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", accept);
                connection.setRequestProperty("User-Agent", "SketchwareNeo-TemplateManager/1");
                int status = connection.getResponseCode();
                String retryAfter = connection.getHeaderField("Retry-After");
                SketchlibHttpClient.recordResponse(current, status, retryAfter);
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new java.io.IOException("The template server returned a redirect without a destination.");
                    current = new URL(current, location);
                    requireHttps(current);
                    continue;
                }
                if (status < 200 || status >= 300) throw SketchlibHttpClient.httpError(current, status, retryAfter);
                if (connection.getContentLengthLong() > maxBytes) throw new java.io.IOException("The template catalog exceeds the response size limit.");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if ((long) output.size() + read > maxBytes) throw new java.io.IOException("The template catalog exceeds the response size limit.");
                        output.write(buffer, 0, read);
                    }
                    return output.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        throw new java.io.IOException("Too many template catalog redirects.");
    }

    private static void downloadToFile(String address, File destination) throws Exception {
        URL current = new URL(address);
        requireHttps(current);
        for (int redirects = 0; redirects <= 5; redirects++) {
            SketchlibHttpClient.beforeRequest(current);
            HttpsURLConnection connection = (HttpsURLConnection) current.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/vnd.sketchware-neo.screen+zip, application/zip, application/octet-stream");
                connection.setRequestProperty("User-Agent", "SketchwareNeo-TemplateManager/1");
                int status = connection.getResponseCode();
                String retryAfter = connection.getHeaderField("Retry-After");
                SketchlibHttpClient.recordResponse(current, status, retryAfter);
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new java.io.IOException("The template server returned a redirect without a destination.");
                    current = new URL(current, location);
                    requireHttps(current);
                    continue;
                }
                if (status < 200 || status >= 300) throw SketchlibHttpClient.httpError(current, status, retryAfter);
                if (connection.getContentLengthLong() > MAX_DOWNLOAD_BYTES) throw new java.io.IOException("Template package exceeds 64 MB.");
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[16 * 1024];
                    long total = 0L;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_DOWNLOAD_BYTES) throw new java.io.IOException("Template package exceeds 64 MB.");
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                    output.getFD().sync();
                    return;
                }
            } finally { connection.disconnect(); }
        }
        throw new java.io.IOException("Too many template download redirects.");
    }

    private static void requireHttps(URL url) {
        if (url == null || !"https".equalsIgnoreCase(url.getProtocol()) || url.getHost() == null || url.getUserInfo() != null) {
            throw new IllegalArgumentException("Template catalog and downloads must use HTTPS URLs without embedded credentials.");
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? "Catalog server unavailable" : message;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }
}
