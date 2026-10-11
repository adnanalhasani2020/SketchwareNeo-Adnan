package mod.pranav.dependency.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ManagedVersionResolver {

    private static final int MAX_DEPTH = 8;

    public static final class Entry {
        public final String group;
        public final String artifact;
        public final String version;
        @Nullable
        public final String scope;

        public Entry(String group, String artifact, @Nullable String version, @Nullable String scope) {
            this.group = group;
            this.artifact = artifact;
            this.version = version == null ? "" : version;
            this.scope = scope;
        }

        public String key() {
            return group + ":" + artifact;
        }

        public String coordinate() {
            return group + ":" + artifact + ":" + version;
        }
    }

    public static final class PomView {
        public final String group;
        public final String artifact;
        public final String version;
        @Nullable
        public final String parentGroup;
        @Nullable
        public final String parentArtifact;
        @Nullable
        public final String parentVersion;
        public final Map<String, String> properties;
        public final List<Entry> managed;
        public final List<Entry> dependencies;

        public PomView(String group, String artifact, String version, @Nullable String parentGroup, @Nullable String parentArtifact,
                       @Nullable String parentVersion, Map<String, String> properties, List<Entry> managed, List<Entry> dependencies) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
            this.parentGroup = parentGroup;
            this.parentArtifact = parentArtifact;
            this.parentVersion = parentVersion;
            this.properties = properties;
            this.managed = managed;
            this.dependencies = dependencies;
        }
    }

    public interface PomLoader {
        @Nullable
        PomView load(@NonNull String group, @NonNull String artifact, @NonNull String version) throws Exception;
    }

    private final PomLoader loader;
    private final List<String> problems = new ArrayList<>();

    public ManagedVersionResolver(@NonNull PomLoader loader) {
        this.loader = loader;
    }

    @NonNull
    public List<String> getProblems() {
        return problems;
    }

    @NonNull
    public Map<String, Entry> managedVersions(@NonNull PomView pom) {
        Map<String, Entry> result = new LinkedHashMap<>();
        collect(pom, result, new HashSet<>(), 0);
        return result;
    }

    @NonNull
    public List<Entry> effectiveDependencies(@NonNull PomView pom) {
        Map<String, Entry> managed = managedVersions(pom);
        Map<String, String> properties = effectiveProperties(pom, 0, new HashSet<>());
        List<Entry> resolved = new ArrayList<>();
        for (Entry dependency : pom.dependencies) {
            String group = substitute(dependency.group, pom, properties);
            String version = substitute(dependency.version, pom, properties);
            if (version.isEmpty()) {
                Entry managedEntry = managed.get(group + ":" + dependency.artifact);
                if (managedEntry != null) version = managedEntry.version;
            }
            resolved.add(new Entry(group, dependency.artifact, version, dependency.scope));
        }
        return resolved;
    }

    private void collect(PomView pom, Map<String, Entry> out, Set<String> visited, int depth) {
        String id = pom.group + ":" + pom.artifact + ":" + pom.version;
        if (!visited.add(id)) return;
        if (depth > MAX_DEPTH) {
            problems.add("Stopped following imports at " + id + ": nesting is deeper than " + MAX_DEPTH);
            return;
        }
        Map<String, String> properties = effectiveProperties(pom, 0, new HashSet<>());

        for (Entry entry : pom.managed) {
            String group = substitute(entry.group, pom, properties);
            String version = substitute(entry.version, pom, properties);
            if ("import".equals(entry.scope)) {
                if (version.isEmpty()) {
                    problems.add("Imported BOM " + group + ":" + entry.artifact + " has no version in " + id);
                    continue;
                }
                PomView imported = loadSafely(group, entry.artifact, version);
                if (imported != null) collect(imported, out, visited, depth + 1);
                continue;
            }
            if (version.isEmpty() || version.contains("${")) {
                problems.add("Managed entry " + group + ":" + entry.artifact + " in " + id + " has an unresolved version '" + entry.version + "'");
                continue;
            }
            out.putIfAbsent(group + ":" + entry.artifact, new Entry(group, entry.artifact, version, entry.scope));
        }

        if (pom.parentGroup != null && pom.parentArtifact != null && pom.parentVersion != null) {
            PomView parent = loadSafely(pom.parentGroup, pom.parentArtifact, pom.parentVersion);
            if (parent != null) collect(parent, out, visited, depth + 1);
        }
    }

    @Nullable
    private PomView loadSafely(String group, String artifact, String version) {
        try {
            PomView loaded = loader.load(group, artifact, version);
            if (loaded == null) problems.add("Could not load POM " + group + ":" + artifact + ":" + version);
            return loaded;
        } catch (Exception e) {
            problems.add("Could not load POM " + group + ":" + artifact + ":" + version + ": " + FailureFormatter.describe(e));
            return null;
        }
    }

    @NonNull
    private Map<String, String> effectiveProperties(PomView pom, int depth, Set<String> seen) {
        Map<String, String> result = new HashMap<>();
        String id = pom.group + ":" + pom.artifact + ":" + pom.version;
        if (depth > MAX_DEPTH || !seen.add(id)) return result;
        if (pom.parentGroup != null && pom.parentArtifact != null && pom.parentVersion != null) {
            PomView parent = loadSafely(pom.parentGroup, pom.parentArtifact, pom.parentVersion);
            if (parent != null) result.putAll(effectiveProperties(parent, depth + 1, seen));
        }
        result.putAll(pom.properties);
        return result;
    }

    @NonNull
    public static String substitute(@Nullable String value, @NonNull PomView pom, @NonNull Map<String, String> properties) {
        if (value == null) return "";
        String current = value.trim();
        for (int round = 0; round < 6 && current.contains("${"); round++) {
            StringBuilder out = new StringBuilder();
            int index = 0;
            boolean changed = false;
            while (index < current.length()) {
                int start = current.indexOf("${", index);
                if (start < 0) {
                    out.append(current, index, current.length());
                    break;
                }
                int end = current.indexOf('}', start);
                if (end < 0) {
                    out.append(current, index, current.length());
                    break;
                }
                out.append(current, index, start);
                String key = current.substring(start + 2, end);
                String replacement = lookup(key, pom, properties);
                if (replacement != null) {
                    out.append(replacement);
                    changed = true;
                } else {
                    out.append(current, start, end + 1);
                }
                index = end + 1;
            }
            current = out.toString();
            if (!changed) break;
        }
        return current;
    }

    @Nullable
    private static String lookup(String key, PomView pom, Map<String, String> properties) {
        switch (key) {
            case "project.version":
            case "pom.version":
            case "version":
                return pom.version;
            case "project.groupId":
            case "pom.groupId":
            case "groupId":
                return pom.group;
            case "project.artifactId":
            case "pom.artifactId":
                return pom.artifact;
            case "project.parent.version":
                return pom.parentVersion;
            case "project.parent.groupId":
                return pom.parentGroup;
            default:
                return properties.get(key);
        }
    }
}
