package pro.sketchware.activities.main.fragments.templates;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** A schema-validated entry supplied by template.sketchlib.in. Missing metrics stay unavailable. */
public final class TemplateCatalogItem {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern HASH = Pattern.compile("[A-Fa-f0-9]{64}");

    public final String id;
    public final String title;
    public final String developer;
    public final String version;
    public final String category;
    public final String description;
    public final String previewImageUrl;
    public final String downloadUrl;
    public final String sha256;
    public final String updatedAt;
    public final String minHostVersion;
    public final String documentationUrl;
    public final String sourceCodeUrl;
    public final boolean featured;
    public final int screenCount;
    public final long downloads;
    public final List<String> tags;
    public final List<String> features;
    public final List<String> screenshots;

    private TemplateCatalogItem(String id, String title, String developer, String version,
                                String category, String description, String previewImageUrl,
                                String downloadUrl, String sha256, String updatedAt,
                                String minHostVersion, String documentationUrl, String sourceCodeUrl,
                                boolean featured, int screenCount, long downloads,
                                List<String> tags, List<String> features, List<String> screenshots) {
        this.id = id;
        this.title = title;
        this.developer = developer;
        this.version = version;
        this.category = category;
        this.description = description;
        this.previewImageUrl = previewImageUrl;
        this.downloadUrl = downloadUrl;
        this.sha256 = sha256;
        this.updatedAt = updatedAt;
        this.minHostVersion = minHostVersion;
        this.documentationUrl = documentationUrl;
        this.sourceCodeUrl = sourceCodeUrl;
        this.featured = featured;
        this.screenCount = screenCount;
        this.downloads = downloads;
        this.tags = immutable(tags);
        this.features = immutable(features);
        this.screenshots = immutable(screenshots);
    }

    public static TemplateCatalogItem fromJson(JSONObject object) throws Exception {
        String id = required(object, "id", 128);
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid template ID.");
        String title = required(object, "title", 160);
        String downloadUrl = required(object, "downloadUrl", 2048);
        String sha256 = required(object, "sha256", 64).toLowerCase(Locale.ROOT);
        if (!HASH.matcher(sha256).matches()) throw new IllegalArgumentException("Template SHA-256 must contain 64 hexadecimal characters.");
        requireHttps(downloadUrl, "downloadUrl");
        String preview = optional(object, "previewImageUrl", 2048);
        if (!preview.isEmpty()) requireHttps(preview, "previewImageUrl");
        String documentationUrl = optional(object, "documentationUrl", 2048);
        String sourceCodeUrl = optional(object, "sourceCodeUrl", 2048);
        if (!documentationUrl.isEmpty()) requireHttps(documentationUrl, "documentationUrl");
        if (!sourceCodeUrl.isEmpty()) requireHttps(sourceCodeUrl, "sourceCodeUrl");
        List<String> screenshots = stringArray(object.optJSONArray("screenshots"), 12);
        for (String screenshot : screenshots) requireHttps(screenshot, "screenshots");
        String updatedAt = optional(object, "updatedAt", 80);
        if (!updatedAt.isEmpty() && parseDateMillis(updatedAt) < 0) throw new IllegalArgumentException("Template updatedAt must be an ISO-8601 date or timestamp.");
        String minHostVersion = optional(object, "minHostVersion", 80);
        String version = optional(object, "version", 80);
        if (!minHostVersion.isEmpty() && !validVersion(minHostVersion)) throw new IllegalArgumentException("Invalid template minHostVersion.");
        if (!version.isEmpty() && !validVersion(version)) throw new IllegalArgumentException("Invalid template version.");
        int screens = optionalInt(object, "screenCount", -1, 0, 50);
        long downloads = optionalLong(object, "downloads", -1, 0, Long.MAX_VALUE);
        Object featuredRaw = object.opt("featured");
        if (featuredRaw != null && featuredRaw != JSONObject.NULL && !(featuredRaw instanceof Boolean)) {
            throw new IllegalArgumentException("Template featured must be boolean.");
        }
        return new TemplateCatalogItem(id, title, optional(object, "developer", 160), version,
                defaultText(optional(object, "category", 80), "Other"), optional(object, "description", 12000),
                preview, downloadUrl, sha256, updatedAt, minHostVersion,
                documentationUrl, sourceCodeUrl, Boolean.TRUE.equals(featuredRaw), screens, downloads,
                stringArray(object.optJSONArray("tags"), 40), stringArray(object.optJSONArray("features"), 100), screenshots);
    }

    public JSONObject toJson() throws org.json.JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("title", title);
        object.put("developer", developer);
        object.put("version", version);
        object.put("category", category);
        object.put("description", description);
        object.put("previewImageUrl", previewImageUrl);
        object.put("downloadUrl", downloadUrl);
        object.put("sha256", sha256);
        object.put("updatedAt", updatedAt);
        object.put("minHostVersion", minHostVersion);
        object.put("documentationUrl", documentationUrl);
        object.put("sourceCodeUrl", sourceCodeUrl);
        object.put("featured", featured);
        if (screenCount >= 0) object.put("screenCount", screenCount);
        if (downloads >= 0) object.put("downloads", downloads);
        object.put("tags", new JSONArray(tags));
        object.put("features", new JSONArray(features));
        object.put("screenshots", new JSONArray(screenshots));
        return object;
    }

    private static String required(JSONObject object, String key, int max) {
        String value = optional(object, key, max);
        if (value.isEmpty()) throw new IllegalArgumentException("Template entry is missing '" + key + "'.");
        return value;
    }

    private static String optional(JSONObject object, String key, int max) {
        Object raw = object.opt(key);
        if (raw == null || raw == JSONObject.NULL) return "";
        if (!(raw instanceof String)) throw new IllegalArgumentException("Template field '" + key + "' must be a string.");
        String value = ((String) raw).trim();
        if (value.length() > max) throw new IllegalArgumentException("Template field '" + key + "' is too long.");
        return value;
    }

    private static int optionalInt(JSONObject object, String key, int fallback, int min, int max) {
        Object raw = object.opt(key);
        if (raw == null || raw == JSONObject.NULL) return fallback;
        if (!(raw instanceof Number)) throw new IllegalArgumentException("Template field '" + key + "' must be numeric.");
        double value = ((Number) raw).doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max) throw new IllegalArgumentException("Invalid template field '" + key + "'.");
        return (int) value;
    }

    private static long optionalLong(JSONObject object, String key, long fallback, long min, long max) {
        Object raw = object.opt(key);
        if (raw == null || raw == JSONObject.NULL) return fallback;
        if (!(raw instanceof Number)) throw new IllegalArgumentException("Template field '" + key + "' must be numeric.");
        double value = ((Number) raw).doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max) throw new IllegalArgumentException("Invalid template field '" + key + "'.");
        return (long) value;
    }

    private static List<String> stringArray(JSONArray array, int maxItems) {
        if (array == null) return Collections.emptyList();
        if (array.length() > maxItems) throw new IllegalArgumentException("Template metadata array is too large.");
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (!(raw instanceof String)) throw new IllegalArgumentException("Template metadata contains a non-string value.");
            String value = ((String) raw).trim();
            if (value.length() > 300) throw new IllegalArgumentException("Template metadata value is too long.");
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? Collections.emptyList() : values));
    }

    private static String defaultText(String value, String fallback) { return value.isEmpty() ? fallback : value; }

    private static boolean validVersion(String version) {
        return version.matches("[0-9]+(?:\\.[0-9]+){0,3}(?:[-+][A-Za-z0-9.-]+)?");
    }

    public long updatedAtMillis() { return parseDateMillis(updatedAt); }

    private static long parseDateMillis(String value) {
        if (value == null || value.isBlank()) return -1L;
        try { return java.time.Instant.parse(value).toEpochMilli(); } catch (Exception ignored) { }
        try { return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli(); } catch (Exception ignored) { }
        try { return java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(); } catch (Exception ignored) { }
        return -1L;
    }

    private static void requireHttps(String value, String field) {
        try {
            java.net.URI uri = new java.net.URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Template " + field + " must be a valid HTTPS URL without embedded credentials.");
            }
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid template " + field + ".", exception);
        }
    }
}
