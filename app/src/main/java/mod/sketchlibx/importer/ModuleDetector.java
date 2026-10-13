package mod.sketchlibx.importer;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates the project root (folder with settings.gradle) and the
 * application module folder (folder with src/main/ and the
 * com.android.application plugin in its build.gradle).
 *
 * Rules (no guessing):
 *  1. Walk dirs depth-first to find settings.gradle / settings.gradle.kts.
 *  2. Parse the include list from settings.gradle.
 *  3. For each included module, verify it has com.android.application plugin.
 *  4. If multiple matches, pick the one explicitly named "app".
 *  5. If still ambiguous, throw — never silently pick the wrong module.
 */
public class ModuleDetector {

    private static final String TAG = "ModuleDetector";

    public static class DetectionResult {
        /** Folder containing settings.gradle */
        public final File projectRoot;
        /** Folder containing src/main/ and build.gradle with application plugin */
        public final File appModuleDir;

        public DetectionResult(File projectRoot, File appModuleDir) {
            this.projectRoot  = projectRoot;
            this.appModuleDir = appModuleDir;
        }
    }

    /**
     * @param extractedRoot  Root of the temp folder where the ZIP was extracted.
     * @return DetectionResult, or null if detection fails.
     */
    public DetectionResult detect(File extractedRoot) {

        // Step 1: find settings.gradle / settings.gradle.kts
        File settingsFile = findSettingsGradle(extractedRoot);
        if (settingsFile == null) {
            Log.w(TAG, "settings.gradle not found — falling back to src/main scan.");
            return fallbackDetect(extractedRoot);
        }

        File projectRoot = settingsFile.getParentFile();
        Log.d(TAG, "Project root: " + projectRoot.getAbsolutePath());

        // Step 2: parse included module names
        List<String> moduleNames = parseIncludes(settingsFile);
        Log.d(TAG, "Included modules: " + moduleNames);

        // Step 3: find the module with com.android.application plugin
        List<File> appModuleCandidates = new ArrayList<>();
        for (String moduleName : moduleNames) {
            // settings.gradle uses ':app' or 'app' — strip leading colon
            String cleanName = moduleName.startsWith(":") ? moduleName.substring(1) : moduleName;
            // Handle nested module paths like ':feature:login' → feature/login
            cleanName = cleanName.replace(":", File.separator);

            File moduleDir = new File(projectRoot, cleanName);
            if (!moduleDir.exists()) {
                Log.w(TAG, "Module directory not found: " + moduleDir.getAbsolutePath());
                continue;
            }

            if (isApplicationModule(moduleDir)) {
                appModuleCandidates.add(moduleDir);
                Log.d(TAG, "Application module candidate: " + moduleDir.getAbsolutePath());
            }
        }

        // Step 4: resolve candidate
        if (appModuleCandidates.isEmpty()) {
            // Some valid Android projects apply the application plugin to the root
            // project itself instead of declaring an :app submodule.
            if (new File(projectRoot, "src/main/AndroidManifest.xml").isFile()
                    && isApplicationModule(projectRoot)) {
                Log.d(TAG, "Root project is itself the Android application module.");
                return new DetectionResult(projectRoot, projectRoot);
            }
            Log.w(TAG, "No included com.android.application module found — fallback to src/main scan.");
            return fallbackDetect(extractedRoot);
        }

        if (appModuleCandidates.size() == 1) {
            return new DetectionResult(projectRoot, appModuleCandidates.get(0));
        }

        // Prefer the conventional root-level :app module, not an unrelated nested
        // module that happens to have a directory named app.
        File rootApp = new File(projectRoot, "app");
        for (File candidate : appModuleCandidates) {
            if (candidate.getAbsoluteFile().equals(rootApp.getAbsoluteFile())) {
                Log.d(TAG, "Multiple candidates — chose root ':app': " + candidate.getAbsolutePath());
                return new DetectionResult(projectRoot, candidate);
            }
        }

        // Never import the wrong module silently. A user can choose a module in a
        // future advanced picker; until then, fail closed when the archive is ambiguous.
        Log.e(TAG, "Multiple application modules found and none is named 'app'; refusing to guess: "
                + appModuleCandidates);
        return null;
    }

    // ── settings.gradle search ────────────────────────────────────────────────

    /** Finds the shallowest settings.gradle or settings.gradle.kts file first.
     * This avoids accidentally treating a nested included build as the root project. */
    private File findSettingsGradle(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        java.util.ArrayDeque<File> queue = new java.util.ArrayDeque<>();
        queue.add(dir);
        while (!queue.isEmpty()) {
            File current = queue.removeFirst();
            File groovy = new File(current, "settings.gradle");
            if (groovy.isFile()) return groovy;
            File kts = new File(current, "settings.gradle.kts");
            if (kts.isFile()) return kts;
            File[] children = current.listFiles();
            if (children == null) continue;
            java.util.Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File child : children) {
                if (!child.isDirectory()) continue;
                String name = child.getName();
                if (".git".equals(name) || ".gradle".equals(name) || ".idea".equals(name)
                        || "build".equals(name) || ".cxx".equals(name) || "node_modules".equals(name)
                        || "captures".equals(name) || "intermediates".equals(name)) continue;
                queue.addLast(child);
            }
        }
        return null;
    }

    // ── include() / include(':app') parser ────────────────────────────────────

    private static final Pattern INCLUDE_CALL = Pattern.compile(
            "(?m)\\binclude\\s*(?:\\(\\s*([^\\r\\n)]*)\\)|([^\\r\\n;]+))");
    private static final Pattern QUOTED_MODULE = Pattern.compile("['\"](:?[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)*)['\"]");

    private List<String> parseIncludes(File settingsFile) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        String content = readFile(settingsFile);
        if (content == null) return new ArrayList<>();
        // Remove comments before matching so examples in comments do not become modules.
        content = stripComments(content);
        Matcher calls = INCLUDE_CALL.matcher(content);
        while (calls.find()) {
            String args = calls.group(1) != null ? calls.group(1) : calls.group(2);
            if (args == null) continue;
            Matcher quoted = QUOTED_MODULE.matcher(args);
            while (quoted.find()) result.add(quoted.group(1));
        }
        // The root application convention is common even if a custom settings plugin
        // synthesizes module includes that this lightweight parser cannot evaluate.
        if (result.isEmpty()) result.add(":app");
        return new ArrayList<>(result);
    }

    // ── com.android.application check ────────────────────────────────────────

    private boolean isApplicationModule(File moduleDir) {
        // Must have src/main/
        if (!new File(moduleDir, "src/main").exists()) return false;

        File gradle    = new File(moduleDir, "build.gradle");
        File gradleKts = new File(moduleDir, "build.gradle.kts");

        String content = null;
        if (gradle.exists())    content = readFile(gradle);
        if (content == null && gradleKts.exists()) content = readFile(gradleKts);
        if (content == null)    return false;

        String uncommented = stripComments(content);
        return uncommented.contains("com.android.application")
                || uncommented.matches("(?s).*\\bid\\s*\\(?\\s*['\"]android['\"]\\s*\\)?.*")
                // Version-catalog aliases generated from pluginManagement aliases.
                || uncommented.matches("(?s).*alias\\s*\\(\\s*libs\\.plugins\\.[A-Za-z0-9_.-]*(?:android|application)[A-Za-z0-9_.-]*\\s*\\).*" );
    }

    // ── Fallback: just find first folder with src/main ────────────────────────

    /**
     * Used when settings.gradle is absent or unusable.
     * Finds the first directory that has src/main/AndroidManifest.xml.
     */
    private DetectionResult fallbackDetect(File root) {
        List<File> candidates = new ArrayList<>();
        collectApplicationModules(root, candidates);
        if (candidates.isEmpty()) {
            Log.e(TAG, "No application module found during fallback detection; refusing to guess a random src/main module.");
            return null;
        }
        if (candidates.size() == 1) {
            File app = candidates.get(0);
            File projectRoot = app.equals(root) ? root : app.getParentFile();
            return new DetectionResult(projectRoot, app);
        }
        // Without settings.gradle there is no reliable module mapping. Refuse an
        // ambiguous archive rather than choosing an arbitrary directory named app.
        Log.e(TAG, "Fallback detection found ambiguous application modules: " + candidates);
        return null;
    }

    private void collectApplicationModules(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        if (new File(dir, "src/main/AndroidManifest.xml").isFile() && isApplicationModule(dir)) out.add(dir);
        File[] children = dir.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String name = child.getName();
            if (".git".equals(name) || ".gradle".equals(name) || ".idea".equals(name)
                    || "build".equals(name) || ".cxx".equals(name) || "node_modules".equals(name)
                    || "captures".equals(name) || "intermediates".equals(name)) continue;
            collectApplicationModules(child, out);
        }
    }

    // ── Comment stripping (quote-aware so URLs containing // stay intact) ────

    private static String stripComments(String source) {
        if (source == null || source.isEmpty()) return source;
        StringBuilder out = new StringBuilder(source.length());
        char quote = 0;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : 0;
            if (lineComment) {
                if (c == '\n' || c == '\r') { lineComment = false; out.append(c); }
                else out.append(' ');
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') { out.append("  "); i++; blockComment = false; }
                else out.append(c == '\n' || c == '\r' ? c : ' ');
                continue;
            }
            if (quote != 0) {
                out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; out.append(c); continue; }
            if (c == '/' && next == '/') { out.append("  "); i++; lineComment = true; continue; }
            if (c == '/' && next == '*') { out.append("  "); i++; blockComment = true; continue; }
            out.append(c);
        }
        return out.toString();
    }

    // ── File read util (local, no FileUtil dependency here) ──────────────────

    private String readFile(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read: " + f.getAbsolutePath(), e);
            return null;
        }
        return sb.toString();
    }
}
