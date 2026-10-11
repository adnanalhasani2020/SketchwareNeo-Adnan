package mod.pranav.dependency.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GradleModuleMetadata {

    private static final String CATEGORY = "org.gradle.category";
    private static final String USAGE = "org.gradle.usage";
    private static final String LIBRARY_ELEMENTS = "org.gradle.libraryelements";
    private static final String JVM_ENVIRONMENT = "org.gradle.jvm.environment";
    private static final String KOTLIN_PLATFORM = "org.jetbrains.kotlin.platform.type";
    private static final String BUILD_TYPE = "com.android.build.api.attributes.BuildTypeAttr";

    private GradleModuleMetadata() {
    }

    public static final class Selection {
        public final String variantName;
        @Nullable
        public final String redirectGroup;
        @Nullable
        public final String redirectModule;
        @Nullable
        public final String redirectVersion;
        @Nullable
        public final String fileName;
        public final boolean platformOnly;
        public final List<String> considered;

        Selection(String variantName, @Nullable String redirectGroup, @Nullable String redirectModule,
                  @Nullable String redirectVersion, @Nullable String fileName, boolean platformOnly, List<String> considered) {
            this.variantName = variantName;
            this.redirectGroup = redirectGroup;
            this.redirectModule = redirectModule;
            this.redirectVersion = redirectVersion;
            this.fileName = fileName;
            this.platformOnly = platformOnly;
            this.considered = considered;
        }

        public boolean isRedirect() {
            return redirectGroup != null && redirectModule != null && redirectVersion != null;
        }
    }

    @Nullable
    public static Selection select(@NonNull String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray variants = root.optJSONArray("variants");
        if (variants == null || variants.length() == 0) return null;

        List<String> considered = new ArrayList<>();
        Selection best = null;
        int bestScore = Integer.MIN_VALUE;
        boolean sawPlatform = false;
        String platformName = null;

        for (int i = 0; i < variants.length(); i++) {
            JSONObject variant = variants.optJSONObject(i);
            if (variant == null) continue;
            String name = variant.optString("name", "variant" + i);
            JSONObject attributes = variant.optJSONObject("attributes");
            String category = attribute(attributes, CATEGORY);
            if ("documentation".equals(category)) {
                considered.add(name + " (documentation, ignored)");
                continue;
            }
            if ("platform".equals(category) || "enforced-platform".equals(category)) {
                sawPlatform = true;
                platformName = name;
                considered.add(name + " (platform)");
                continue;
            }

            String platform = attribute(attributes, KOTLIN_PLATFORM);
            if (platform != null && !platform.equals("androidJvm") && !platform.equals("jvm")) {
                considered.add(name + " (" + platform + ", not usable on Android)");
                continue;
            }
            String usage = attribute(attributes, USAGE);
            if (usage != null && !usage.equals("java-api") && !usage.equals("java-runtime")) {
                considered.add(name + " (usage " + usage + ", ignored)");
                continue;
            }

            String fileName = null;
            JSONArray files = variant.optJSONArray("files");
            if (files != null) {
                fileName = pickFile(files);
            }
            JSONObject availableAt = variant.optJSONObject("available-at");
            String redirectGroup = null;
            String redirectModule = null;
            String redirectVersion = null;
            if (availableAt != null) {
                redirectGroup = nonEmpty(availableAt.optString("group", ""));
                redirectModule = nonEmpty(availableAt.optString("module", ""));
                redirectVersion = nonEmpty(availableAt.optString("version", ""));
            }
            boolean redirect = redirectGroup != null && redirectModule != null && redirectVersion != null;
            if (!redirect && fileName == null) {
                considered.add(name + " (no downloadable file)");
                continue;
            }

            int score = 0;
            if ("androidJvm".equals(platform)) score += 10;
            else if ("jvm".equals(platform)) score += 6;
            else score += 5;
            if ("java-runtime".equals(usage)) score += 4;
            else if ("java-api".equals(usage)) score += 3;
            else score += 1;
            String environment = attribute(attributes, JVM_ENVIRONMENT);
            if ("android".equals(environment)) score += 3;
            else if ("standard-jvm".equals(environment)) score += 1;
            String elements = attribute(attributes, LIBRARY_ELEMENTS);
            if ("aar".equals(elements) || "jar".equals(elements)) score += 2;
            else if (elements != null) score -= 2;
            String buildType = attribute(attributes, BUILD_TYPE);
            if ("release".equals(buildType)) score += 1;
            else if (buildType != null) score -= 1;

            considered.add(name + " (score " + score + (redirect ? ", available-at " + redirectModule : ", file " + fileName) + ")");
            if (score > bestScore) {
                bestScore = score;
                best = new Selection(name, redirectGroup, redirectModule, redirectVersion, redirect ? null : fileName, false, considered);
            }
        }

        if (best != null) return best;
        if (sawPlatform) return new Selection(platformName, null, null, null, null, true, considered);
        return new Selection("none", null, null, null, null, false, considered);
    }

    @Nullable
    private static String pickFile(JSONArray files) {
        String jar = null;
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.optJSONObject(i);
            if (file == null) continue;
            String url = file.optString("url", file.optString("name", ""));
            String lower = url.toLowerCase();
            if (lower.contains("-sources") || lower.contains("-javadoc")) continue;
            if (lower.endsWith(".aar")) return url;
            if (lower.endsWith(".jar") && jar == null) jar = url;
        }
        return jar;
    }

    @Nullable
    private static String attribute(@Nullable JSONObject attributes, String key) {
        if (attributes == null || !attributes.has(key)) return null;
        String value = attributes.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    @Nullable
    private static String nonEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
