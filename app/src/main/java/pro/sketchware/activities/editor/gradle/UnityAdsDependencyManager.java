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
 * Prepares Unity Ads 4.20.1's transitive dependencies for Neo's in-app builder.
 * The Unity SDK itself is always loaded from the bundled libs.zip entry; this
 * class never downloads the com.unity3d.ads:unity-ads artifact into project local_libs.
 */
public final class UnityAdsDependencyManager {
    public static final String SDK_VERSION = "4.20.1";
    public static final String GROUP = "com.unity3d.ads";
    public static final String ARTIFACT = "unity-ads";
    private static final GradleDependency UNITY_DEPENDENCY =
            new GradleDependency("implementation", GROUP, ARTIFACT, SDK_VERSION);

    /* Exact direct dependency versions from the Unity Ads 4.20.1 published POM. */
    private static final List<GradleDependency> TRANSITIVE_DEPENDENCIES = List.of(
            new GradleDependency("implementation", "androidx.activity", "activity-ktx", "1.7.1"),
            new GradleDependency("implementation", "androidx.core", "core-ktx", "1.9.0"),
            new GradleDependency("implementation", "androidx.lifecycle", "lifecycle-process", "2.6.1"),
            new GradleDependency("implementation", "androidx.lifecycle", "lifecycle-runtime-ktx", "2.6.1"),
            new GradleDependency("implementation", "androidx.startup", "startup-runtime", "1.1.1"),
            new GradleDependency("implementation", "androidx.webkit", "webkit", "1.6.1"),
            new GradleDependency("implementation", "androidx.datastore", "datastore", "1.0.0"),
            new GradleDependency("implementation", "org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.10.2"),
            new GradleDependency("implementation", "org.jetbrains.kotlin", "kotlin-stdlib-jdk8", "2.1.21"),
            new GradleDependency("implementation", "com.squareup.okhttp3", "okhttp", "3.12.13"),
            new GradleDependency("implementation", "com.google.protobuf", "protobuf-kotlin-lite", "3.25.6"),
            new GradleDependency("implementation", "androidx.work", "work-runtime-ktx", "2.7.0"),
            new GradleDependency("implementation", "com.google.android.gms", "play-services-cronet", "18.0.1"),
            new GradleDependency("implementation", "com.unity3d.coherence", "coherencelib", "0.1.0")
    );

    private UnityAdsDependencyManager() { }

    @NonNull
    public static GradleDependency getDependency() { return UNITY_DEPENDENCY; }

    @NonNull
    public static List<GradleDependency> getTransitiveDependencies() {
        return new ArrayList<>(TRANSITIVE_DEPENDENCIES);
    }

    @NonNull
    public static List<GradleSyncEngine.Row> inspect(String scId) {
        return GradleSyncEngine.inspect(scId, TRANSITIVE_DEPENDENCIES);
    }

    public static boolean isReady(String scId) {
        for (GradleSyncEngine.Row row : inspect(scId)) {
            if (row.state != GradleSyncEngine.DepState.READY) return false;
        }
        return true;
    }

    @Nullable
    public static String getReadinessError(String scId) {
        for (GradleSyncEngine.Row row : inspect(scId)) {
            if (row.state == GradleSyncEngine.DepState.READY) continue;
            StringBuilder message = new StringBuilder("Unity Ads ").append(SDK_VERSION)
                    .append(" dependency ").append(row.dep.coordinate())
                    .append(" is not ready (status: ").append(row.state).append(").");
            if (row.error != null && !row.error.trim().isEmpty()) message.append("\n\n").append(row.error.trim());
            else if (row.note != null && !row.note.trim().isEmpty()) message.append("\n\n").append(row.note.trim());
            return message.toString();
        }
        return null;
    }

    /** Resolve missing transitives only when the user actually builds a Unity-enabled project. */
    public static void prepareForBuild(Context context, String scId) {
        if (isReady(scId)) return;
        GradleSyncEngine.Listener silentListener = new GradleSyncEngine.Listener() {
            @Override public void onStatus(String message, int done, int total) { }
            @Override public void onDependencyState(String key, GradleSyncEngine.DepState state, @Nullable String error) { }
            @Override public void onLog(String line) { }
        };
        GradleSyncEngine.Result result = sync(context, scId, silentListener, new AtomicBoolean(false));
        if (!result.success || !isReady(scId)) {
            String detail = getReadinessError(scId);
            if (detail == null || detail.trim().isEmpty()) detail = result.message;
            throw new IllegalStateException("Unity Ads " + SDK_VERSION + " dependencies could not be prepared. "
                    + (detail == null ? "Check network access and retry the build." : detail));
        }
    }

    /** Resolve only the SDK's transitives at build time; the root SDK stays bundled. */
    @NonNull
    public static GradleSyncEngine.Result sync(Context context, String scId,
                                               GradleSyncEngine.Listener listener,
                                               AtomicBoolean cancel) {
        if (scId == null || scId.trim().isEmpty()) {
            throw new IllegalArgumentException("Sketchware project ID is required to prepare Unity Ads");
        }
        String gradleContent = readEnabledCustomGradle(scId);
        List<GradleDependency> declared = new ArrayList<>();
        boolean rootAdded = false;
        for (GradleDependency dependency : new GradleParser().parseDependencyList(gradleContent)) {
            if (isUnityDependency(dependency)) {
                if (!rootAdded) { declared.add(UNITY_DEPENDENCY); rootAdded = true; }
            } else {
                String incompatible = incompatibleOkHttpDependency(dependency);
                if (incompatible != null) return new GradleSyncEngine.Result(false, false, false, incompatible);
                declared.add(dependency);
            }
        }
        if (!rootAdded) declared.add(UNITY_DEPENDENCY);
        declared.addAll(TRANSITIVE_DEPENDENCIES);

        Set<String> targets = new LinkedHashSet<>();
        for (GradleDependency dependency : TRANSITIVE_DEPENDENCIES) {
            targets.add(dependency.key());
            GradleSyncEngine.setSkipSubDependencies(scId, dependency.key(), false);
        }
        return GradleSyncEngine.sync(context, scId, declared, targets, gradleContent,
                listener, cancel, true);
    }

    /**
     * Built-in artifacts used to satisfy transitive coordinates must be added to the
     * in-app DEX/resource build too. Non-built-in dependencies are enabled by the sync engine.
     */
    @NonNull
    public static List<String> getProvidedBuiltInLibraries(String scId) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (GradleDependency dependency : TRANSITIVE_DEPENDENCIES) {
            BuiltInArtifacts.Match match = BuiltInArtifacts.findSatisfying(
                    scId, dependency.group, dependency.artifact, dependency.version);
            if (match != null) result.addAll(BuiltInArtifacts.closure(match.libraryName));
            List<String> recorded = GradleSyncEngine.loadStore(scId).builtIns.get(dependency.coordinate());
            if (recorded != null) {
                for (String libraryName : recorded) result.addAll(BuiltInArtifacts.closure(libraryName));
            }
        }
        result.remove(BuiltInLibraries.UNITY_ADS);
        return new ArrayList<>(result);
    }

    /** Remove a stale root SDK copy created by earlier versions, without downloading anything. */
    public static void removeBundledSdkDuplicate(String scId) {
        if (scId == null || scId.trim().isEmpty()) return;
        String gradleContent = readEnabledCustomGradle(scId);
        List<GradleDependency> remaining = new ArrayList<>();
        for (GradleDependency dependency : new GradleParser().parseDependencyList(gradleContent)) {
            if (isUnityDependency(dependency)) return; // preserve an explicit user Custom Gradle declaration
            remaining.add(dependency);
        }
        remaining.addAll(TRANSITIVE_DEPENDENCIES);
        GradleSyncEngine.removeFromProject(scId, UNITY_DEPENDENCY, remaining);
    }

    private static boolean isUnityDependency(GradleDependency dependency) {
        return GROUP.equals(dependency.group) && ARTIFACT.equals(dependency.artifact);
    }

    @NonNull
    private static String readEnabledCustomGradle(String scId) {
        if (!CustomGradleBuildManager.isEnabled(scId)) return "";
        File appGradle = new File(CustomGradleBuildManager.getDirectory(scId), CustomGradleBuildManager.FILE_APP_BUILD);
        return appGradle.isFile() ? FileUtil.readFile(appGradle.getAbsolutePath()) : "";
    }

    @Nullable
    private static String incompatibleOkHttpDependency(GradleDependency dependency) {
        if (dependency.version == null) return null;
        if ("com.squareup.okhttp3".equals(dependency.group) && "okhttp".equals(dependency.artifact)
                && !major(dependency.version).equals("3")) {
            return "Unity Ads " + SDK_VERSION + " requires OkHttp 3.x, but Custom Gradle also declares okhttp:"
                    + dependency.version + ". Remove that incompatible declaration or resolve the conflict before building.";
        }
        if ("com.squareup.okio".equals(dependency.group)
                && ("okio".equals(dependency.artifact) || "okio-jvm".equals(dependency.artifact))
                && !major(dependency.version).equals("1")) {
            return "Unity Ads " + SDK_VERSION + " requires the Okio 1.x line through OkHttp 3.x, but Custom Gradle also declares okio:"
                    + dependency.version + ". Resolve this major-version conflict before building.";
        }
        return null;
    }

    private static String major(String version) {
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) end++;
        return end == 0 ? "" : version.substring(0, end);
    }
}
