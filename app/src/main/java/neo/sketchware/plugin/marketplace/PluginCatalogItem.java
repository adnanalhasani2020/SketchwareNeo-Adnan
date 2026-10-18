package neo.sketchware.plugin.marketplace;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Metadata for a single catalog entry. Ratings/download counts remain absent when not supplied. */
public record PluginCatalogItem(
        String pluginId,
        String name,
        String developer,
        String version,
        int apiVersion,
        String minHostVersion,
        String category,
        String description,
        String downloadUrl,
        String sha256,
        String homepageUrl,
        String documentationUrl,
        String changelogUrl,
        String sourceCodeUrl,
        String license,
        String iconUrl,
        String updatedAt,
        double rating,
        long downloads,
        List<String> screenshots,
        List<String> features,
        List<String> components,
        List<String> blocks,
        List<String> dependencies
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[A-Fa-f0-9]{64}");

    public PluginCatalogItem {
        screenshots = immutable(screenshots);
        features = immutable(features);
        components = immutable(components);
        blocks = immutable(blocks);
        dependencies = immutable(dependencies);
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static PluginCatalogItem fromJson(JSONObject object) throws JSONException {
        String id = requiredString(object, "pluginId", 128);
        if (!ID_PATTERN.matcher(id).matches()) throw new IllegalArgumentException("Invalid pluginId: " + id);
        String name = requiredString(object, "name", 160);
        String version = requiredString(object, "version", 80);
        if (!PluginVersionUtils.isValidVersion(version)) throw new IllegalArgumentException("Invalid version for " + id);
        String downloadUrl = requiredString(object, "downloadUrl", 2048);
        String hash = requiredString(object, "sha256", 64).toLowerCase(Locale.ROOT);
        if (!SHA256_PATTERN.matcher(hash).matches()) throw new IllegalArgumentException("Invalid SHA-256 for " + id);
        requireHttps(downloadUrl, "downloadUrl");
        int apiVersion = 1;
        if (object.has("apiVersion") && !object.isNull("apiVersion")) {
            Object rawApiVersion = object.opt("apiVersion");
            if (!(rawApiVersion instanceof Number)) throw new IllegalArgumentException("apiVersion must be numeric for " + id);
            double apiNumber = ((Number) rawApiVersion).doubleValue();
            if (!Double.isFinite(apiNumber) || apiNumber < 1 || apiNumber != Math.rint(apiNumber) || apiNumber > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid plugin API version for " + id);
            }
            apiVersion = (int) apiNumber;
        }

        String minHostVersion = optionalString(object, "minHostVersion", 80);
        if (!minHostVersion.isBlank() && !PluginVersionUtils.isValidVersion(minHostVersion)) {
            throw new IllegalArgumentException("Invalid minHostVersion for " + id);
        }
        String homepageUrl = optionalString(object, "homepageUrl", 2048);
        String documentationUrl = optionalString(object, "documentationUrl", 2048);
        String changelogUrl = optionalString(object, "changelogUrl", 2048);
        String sourceCodeUrl = optionalString(object, "sourceCodeUrl", 2048);
        String iconUrl = optionalString(object, "iconUrl", 2048);
        String updatedAt = optionalString(object, "updatedAt", 80);
        requireOptionalHttps(homepageUrl, "homepageUrl");
        requireOptionalHttps(documentationUrl, "documentationUrl");
        requireOptionalHttps(changelogUrl, "changelogUrl");
        requireOptionalHttps(sourceCodeUrl, "sourceCodeUrl");
        requireOptionalHttps(iconUrl, "iconUrl");
        double rating = -1;
        if (object.has("rating") && !object.isNull("rating")) {
            Object rawRating = object.opt("rating");
            if (!(rawRating instanceof Number)) throw new IllegalArgumentException("Rating must be numeric");
            rating = ((Number) rawRating).doubleValue();
            if (!Double.isFinite(rating) || rating < 0 || rating > 5) throw new IllegalArgumentException("Rating must be between 0 and 5");
        }
        long downloads = -1;
        if (object.has("downloads") && !object.isNull("downloads")) {
            Object rawDownloads = object.opt("downloads");
            if (!(rawDownloads instanceof Number)) throw new IllegalArgumentException("Downloads must be numeric");
            double count = ((Number) rawDownloads).doubleValue();
            if (!Double.isFinite(count) || count < 0 || count != Math.rint(count) || count > Long.MAX_VALUE) {
                throw new IllegalArgumentException("Downloads must be a non-negative integer");
            }
            downloads = (long) count;
        }

        List<String> dependencies = stringArrayField(object, "dependencies", 64);
        for (String dependency : dependencies) {
            if (!ID_PATTERN.matcher(dependency).matches() || dependency.equals(id)) {
                throw new IllegalArgumentException("Invalid dependency ID in catalog entry " + id);
            }
        }

        return new PluginCatalogItem(
                id, name, optionalString(object, "developer", 160), version, apiVersion,
                minHostVersion, defaultIfBlank(optionalString(object, "category", 80), "Other"),
                optionalString(object, "description", 12000), downloadUrl, hash,
                homepageUrl, documentationUrl, changelogUrl, sourceCodeUrl,
                optionalString(object, "license", 120), iconUrl, updatedAt, rating, downloads,
                httpsStringArrayField(object, "screenshots", 100), stringArrayField(object, "features", 100),
                stringArrayField(object, "components", 100), stringArrayField(object, "blocks", 100),
                dependencies
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("pluginId", pluginId);
        object.put("name", name);
        object.put("developer", developer);
        object.put("version", version);
        object.put("apiVersion", apiVersion);
        object.put("minHostVersion", minHostVersion);
        object.put("category", category);
        object.put("description", description);
        object.put("downloadUrl", downloadUrl);
        object.put("sha256", sha256);
        object.put("homepageUrl", homepageUrl);
        object.put("documentationUrl", documentationUrl);
        object.put("changelogUrl", changelogUrl);
        object.put("sourceCodeUrl", sourceCodeUrl);
        object.put("license", license);
        object.put("iconUrl", iconUrl);
        if (updatedAt != null && !updatedAt.isBlank()) object.put("updatedAt", updatedAt);
        if (rating >= 0) object.put("rating", rating);
        if (downloads >= 0) object.put("downloads", downloads);
        object.put("screenshots", toArray(screenshots));
        object.put("features", toArray(features));
        object.put("components", toArray(components));
        object.put("blocks", toArray(blocks));
        object.put("dependencies", toArray(dependencies));
        return object;
    }

    private static JSONArray toArray(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) for (String value : values) array.put(value);
        return array;
    }

    private static List<String> httpsStringArrayField(JSONObject object, String key, int maximum) {
        List<String> values = stringArrayField(object, key, maximum);
        for (String value : values) requireHttps(value, "screenshots URL");
        return values;
    }

    private static List<String> stringArrayField(JSONObject object, String key, int maximum) {
        JSONArray array = object.optJSONArray(key);
        if (object.has(key) && !object.isNull(key) && array == null) {
            throw new IllegalArgumentException("Catalog field '" + key + "' must be an array of strings");
        }
        if (array == null) return Collections.emptyList();
        if (array.length() > maximum) throw new IllegalArgumentException("Too many metadata values");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (!(raw instanceof String)) throw new IllegalArgumentException("Metadata values must be strings");
            String value = ((String) raw).trim();
            if (value.length() > 2048) throw new IllegalArgumentException("Metadata value is too long");
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    private static String requiredString(JSONObject object, String key, int maxLength) throws JSONException {
        String value = optionalString(object, key, maxLength);
        if (value.isBlank()) throw new IllegalArgumentException("Catalog entry is missing '" + key + "'");
        return value;
    }

    private static String optionalString(JSONObject object, String key, int maxLength) {
        if (!object.has(key) || object.isNull(key)) return "";
        Object raw = object.opt(key);
        if (!(raw instanceof String)) throw new IllegalArgumentException("Catalog field '" + key + "' must be a string");
        String value = ((String) raw).trim();
        if (value.length() > maxLength) throw new IllegalArgumentException("Catalog field '" + key + "' is too long");
        return value;
    }

    private static void requireOptionalHttps(String value, String field) {
        if (value != null && !value.isBlank()) requireHttps(value, field);
    }

    private static void requireHttps(String value, String field) {
        try {
            java.net.URI uri = new java.net.URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("'" + field + "' must be an HTTPS URL");
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL in '" + field + "'");
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
