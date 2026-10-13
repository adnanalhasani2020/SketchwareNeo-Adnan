package pro.sketchware.activities.editor.gradle;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import mod.jbk.build.BuiltInLibraries;
import mod.sketchlibx.importer.GradleDependency;
import mod.sketchlibx.importer.GradleParser;
import pro.sketchware.utility.FileUtil;

/**
 * Prepares the complete Unity Ads 4.20.1 Maven dependency graph for Sketchware's in-app builder.
 * The old bundled Unity 4.21.0 JAR/DEX is deliberately not used for generated projects.
 */
public final class UnityAdsDependencyManager {

    public static final String SDK_VERSION = "4.20.1";
    public static final String GROUP = "com.unity3d.ads";
    public static final String ARTIFACT = "unity-ads";
    private static final GradleDependency UNITY_DEPENDENCY =
            new GradleDependency("implementation", GROUP, ARTIFACT, SDK_VERSION);

    private UnityAdsDependencyManager() {
    }

    @NonNull
    public static GradleDependency getDependency() {
        return UNITY_DEPENDENCY;
    }

    @NonNull
    public static GradleSyncEngine.Row inspect(String scId) {
        List<GradleSyncEngine.Row> rows = GradleSyncEngine.inspect(
                scId, Collections.singletonList(UNITY_DEPENDENCY));
        return rows.get(0);
    }

    public static boolean isReady(String scId) {
        return inspect(scId).state == GradleSyncEngine.DepState.READY;
    }

    @NonNull
    public static GradleSyncEngine.Result failureResult(@NonNull String message, boolean cancelled) {
        return new GradleSyncEngine.Result(false, cancelled, false, message);
    }

    public static void requireReadyForBuild(String scId) {
        String message = getReadinessError(scId);
        if (message != null) throw new IllegalStateException(message);
    }

    @Nullable
    public static String getReadinessError(String scId) {
        if (scId == null || scId.trim().isEmpty()) {
            return "Unity Ads cannot build because the Sketchware project ID is missing.";
        }
        GradleSyncEngine.Row row = inspect(scId);
        if (row.state == GradleSyncEngine.DepState.READY) return null;
        StringBuilder message = new StringBuilder("Unity Ads SDK ").append(SDK_VERSION)
                .append(" dependencies are not ready (status: ").append(row.state).append("). ")
                .append("Open Ad Networks, enable Unity Ads, and Save while connected to the internet.");
        if (row.error != null && !row.error.trim().isEmpty()) {
            message.append("\n\n").append(row.error.trim());
        } else if (row.note != null && !row.note.trim().isEmpty()) {
            message.append("\n\n").append(row.note.trim());
        }
        return message.toString();
    }

    /**
     * Returns Neo built-ins that the resolver used to satisfy Unity's transitive dependencies.
     * Those libraries must be added to ProjectBuilder as well, otherwise the corresponding
     * built-in JAR/DEX or manifest entries can be absent from the target APK.
     */
    @NonNull
    public static List<String> getProvidedBuiltInLibraries(String scId) {
        GradleSyncEngine.Store store = GradleSyncEngine.loadStore(scId);
        List<String> recorded = store.builtIns.get(UNITY_DEPENDENCY.coordinate());
        if (recorded == null || recorded.isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String libraryName : recorded) {
            if (libraryName == null || libraryName.isEmpty()
                    || BuiltInLibraries.UNITY_ADS.equals(libraryName)) continue;
            result.addAll(BuiltInArtifacts.closure(libraryName));
        }
        result.remove(BuiltInLibraries.UNITY_ADS);
        return new ArrayList<>(result);
    }

    /**
     * Resolve Unity Ads and its complete transitive graph. Other custom Gradle declarations are
     * passed through for dependency conflict reconciliation, but only Unity's coordinate is
     * downloaded/required for this operation. The actual Gradle file is read-only here.
     */
    @NonNull
    public static GradleSyncEngine.Result sync(Context context, String scId,
                                                GradleSyncEngine.Listener listener,
                                                AtomicBoolean cancel) {
        if (scId == null || scId.trim().isEmpty()) {
            throw new IllegalArgumentException("Sketchware project ID is required to sync Unity Ads");
        }

        String gradleContent = readEnabledCustomGradle(scId);
        List<GradleDependency> dependencies = new ArrayList<>();
        boolean unityAdded = false;
        for (GradleDependency dependency : new GradleParser().parseDependencyList(gradleContent)) {
            if (isUnityDependency(dependency)) {
                if (!unityAdded) {
                    dependencies.add(UNITY_DEPENDENCY);
                    unityAdded = true;
                }
            } else {
                String incompatible = incompatibleOkHttpDependency(dependency);
                if (incompatible != null) {
                    return new GradleSyncEngine.Result(false, false, false, incompatible);
                }
                dependencies.add(dependency);
            }
        }
        if (!unityAdded) dependencies.add(UNITY_DEPENDENCY);

        GradleSyncEngine.setSkipSubDependencies(scId, UNITY_DEPENDENCY.key(), false);
        return GradleSyncEngine.sync(context, scId, dependencies,
                Collections.singleton(UNITY_DEPENDENCY.key()), gradleContent,
                listener, cancel, true);
    }

    /**
     * When Unity is switched off, remove the SDK closure previously synced by this helper unless
     * the user separately declared Unity Ads in enabled Custom Gradle. Shared dependency folders
     * referenced by remaining declared dependencies are retained by GradleSyncEngine.
     */
    public static void removeManagedDependencyIfDisabled(String scId) {
        if (scId == null || scId.trim().isEmpty()) return;
        String gradleContent = readEnabledCustomGradle(scId);
        List<GradleDependency> remaining = new ArrayList<>();
        for (GradleDependency dependency : new GradleParser().parseDependencyList(gradleContent)) {
            if (isUnityDependency(dependency)) {
                // A dependency explicitly present in Custom Gradle belongs to the user's build.
                return;
            }
            remaining.add(dependency);
        }
        GradleSyncEngine.removeFromProject(scId, UNITY_DEPENDENCY, remaining);
    }

    private static boolean isUnityDependency(GradleDependency dependency) {
        return GROUP.equals(dependency.group) && ARTIFACT.equals(dependency.artifact);
    }

    @NonNull
    private static String readEnabledCustomGradle(String scId) {
        if (!CustomGradleBuildManager.isEnabled(scId)) return "";
        File appGradle = new File(CustomGradleBuildManager.getDirectory(scId),
                CustomGradleBuildManager.FILE_APP_BUILD);
        return appGradle.isFile() ? FileUtil.readFile(appGradle.getAbsolutePath()) : "";
    }

    @Nullable
    private static String incompatibleOkHttpDependency(GradleDependency dependency) {
        if (dependency.version == null) return null;
        if ("com.squareup.okhttp3".equals(dependency.group) && "okhttp".equals(dependency.artifact)
                && !major(dependency.version).equals("3")) {
            return "Unity Ads " + SDK_VERSION + " requires OkHttp 3.x, but Custom Gradle also declares okhttp:"
                    + dependency.version + ". Remove that incompatible declaration or use a compatible version before enabling Unity Ads.";
        }
        if ("com.squareup.okio".equals(dependency.group) && "okio".equals(dependency.artifact)
                && !major(dependency.version).equals("1")) {
            return "Unity Ads " + SDK_VERSION + " requires the Okio 1.x line through OkHttp 3.x, but Custom Gradle also declares okio:"
                    + dependency.version + ". Resolve this major-version conflict before enabling Unity Ads.";
        }
        return null;
    }

    private static String major(String version) {
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) end++;
        return end == 0 ? "" : version.substring(0, end);
    }
}
