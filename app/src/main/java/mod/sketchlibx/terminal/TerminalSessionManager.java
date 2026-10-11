package mod.sketchlibx.terminal;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import mod.hey.studios.activity.managers.cpp.InbuiltNdkManager;

/**
 * Owns real PTY {@link TerminalSession}s, multiple independent ones per project (sc_id) — each
 * "New Session" creates a genuinely separate subprocess with its own PTY, cwd and scrollback.
 * Sessions outlive any single UI surface: switching tabs, placements, or app recreation reattaches
 * to whichever sessions are still running rather than losing them.
 */
public final class TerminalSessionManager {

    public static final class Entry {
        public final String sessionId;
        public final TerminalSession session;
        public volatile String label;

        private Entry(String sessionId, String label, TerminalSession session) {
            this.sessionId = sessionId;
            this.label = label;
            this.session = session;
        }
    }

    private static final Map<String, List<Entry>> PROJECT_SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> PROJECT_COUNTERS = new ConcurrentHashMap<>();

    private TerminalSessionManager() {
    }

    private static String normalizeProjectId(String scId) {
        if (scId == null || scId.trim().isEmpty()) return "unknown";
        String value = scId.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        if (value.length() > 64) value = value.substring(0, 64);
        return value.isEmpty() ? "unknown" : value;
    }

    public static synchronized List<Entry> getSessions(String scId) {
        String projectId = normalizeProjectId(scId);
        return PROJECT_SESSIONS.computeIfAbsent(projectId, k -> new CopyOnWriteArrayList<>());
    }

    /** Returns the first still-running session for this project, or creates one if none exist. */
    public static synchronized Entry getOrCreateFirst(Context context, String scId, TerminalSessionClient client) {
        String projectId = normalizeProjectId(scId);
        List<Entry> sessions = getSessions(projectId);
        for (Entry e : sessions) {
            if (e.session.isRunning()) return e;
        }
        return createSession(context, projectId, client);
    }

    /** Always spawns a genuinely new, independent PTY — this is what "+" / "New Session" calls. */
    public static synchronized Entry createSession(Context context, String scId, TerminalSessionClient client) {
        String projectId = normalizeProjectId(scId);
        Context appContext = context.getApplicationContext();
        List<Entry> sessions = getSessions(projectId);
        int number = PROJECT_COUNTERS.computeIfAbsent(projectId, k -> new AtomicInteger(0)).incrementAndGet();
        String sessionId = projectId + "#" + number;

        // Prepare the shell startup file before the PTY starts. Sending the source command
        // immediately after constructing TerminalSession races shell startup on some devices;
        // that race left sw_help/swdoctor undefined even though the script existed.
        File initScript = writeInitScript(appContext, projectId);
        TerminalSession session = spawnSession(appContext, projectId, client, initScript);
        Entry entry = new Entry(sessionId, "Session " + number, session);
        sessions.add(entry);
        return entry;
    }

    /** Kills exactly one session; the project's other sessions are unaffected. */
    public static synchronized void killSession(String scId, String sessionId) {
        List<Entry> sessions = PROJECT_SESSIONS.get(normalizeProjectId(scId));
        if (sessions == null) return;
        for (Entry e : sessions) {
            if (e.sessionId.equals(sessionId)) {
                e.session.finishIfRunning();
                sessions.remove(e);
                break;
            }
        }
    }

    public static synchronized void killAllSessions(String scId) {
        String projectId = normalizeProjectId(scId);
        List<Entry> sessions = PROJECT_SESSIONS.remove(projectId);
        if (sessions != null) {
            for (Entry e : sessions) e.session.finishIfRunning();
        }
        PROJECT_COUNTERS.remove(projectId);
    }

    private static TerminalSession spawnSession(Context appContext, String scId, TerminalSessionClient client, File initScript) {
        String homeDir = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.sketchware/data/" + scId + "/";
        File home = new File(homeDir);
        if (!home.exists()) home.mkdirs();

        List<String> binDirs = collectToolchainBinDirs(appContext);
        String shellPath = resolveShell(binDirs);
        String[] env = buildEnvironment(appContext, homeDir, binDirs, initScript);
        String[] args = {shellPath, "-i"};

        return new TerminalSession(shellPath, homeDir, args, env, 4000, client);
    }

    /** A real bash from an installed toolchain is used if present; otherwise the real system shell. */
    private static String resolveShell(List<String> binDirs) {
        for (String dir : binDirs) {
            File bash = new File(dir, "bash");
            if (bash.isFile() && bash.canExecute()) return bash.getAbsolutePath();
        }
        return "/system/bin/sh";
    }

    private static List<String> collectToolchainBinDirs(Context appContext) {
        Set<String> dirs = new LinkedHashSet<>();
        File filesDir = appContext.getFilesDir();
        File cmakeRoot = new File(filesDir, "cmake");
        for (String binPath : findVersionedBinDirs(cmakeRoot)) {
            File bin = new File(binPath);
            if (isExecutable(new File(bin, "cmake")) || isExecutable(new File(bin, "ninja"))) {
                dirs.add(bin.getAbsolutePath());
            }
        }

        File ndkRoot = new File(filesDir, "ndk");
        File[] ndkVersions = ndkRoot.listFiles(File::isDirectory);
        if (ndkVersions != null) {
            for (File ndkVersion : ndkVersions) {
                if (!new File(ndkVersion, "build/cmake/android.toolchain.cmake").isFile()) continue;
                File prebuiltRoot = new File(ndkVersion, "toolchains/llvm/prebuilt");
                File[] hostDirs = prebuiltRoot.listFiles(File::isDirectory);
                if (hostDirs == null) continue;
                for (File hostDir : hostDirs) {
                    String host = hostDir.getName().toLowerCase(Locale.ROOT);
                    // The installed AndroidIDE NDK uses linux-aarch64. Never expose desktop
                    // x86_64 tool binaries to Android's shell PATH.
                    if (!(host.equals("linux-aarch64") || host.equals("linux-arm64")
                            || host.equals("android-arm64") || host.equals("android-aarch64"))) {
                        continue;
                    }
                    File bin = new File(hostDir, "bin");
                    if (bin.isDirectory() && (isExecutable(new File(bin, "clang"))
                            || isExecutable(new File(bin, "clang++")))) {
                        dirs.add(bin.getAbsolutePath());
                    }
                }
            }
        }
        return new ArrayList<>(dirs);
    }

    private static boolean isExecutable(File file) {
        return file.isFile() && file.canExecute();
    }

    private static String[] buildEnvironment(Context appContext, String homeDir, List<String> binDirs, File initScript) {
        List<String> env = new ArrayList<>();
        Set<String> basePathEntries = new LinkedHashSet<>();
        String systemPath = System.getenv("PATH");
        if (systemPath != null && !systemPath.trim().isEmpty()) {
            for (String entry : systemPath.split(":")) {
                if (!entry.trim().isEmpty()) basePathEntries.add(entry.trim());
            }
        }
        // Include Android platform locations even if the app process was launched with a sparse PATH.
        basePathEntries.add("/system/bin");
        basePathEntries.add("/system/xbin");
        basePathEntries.add("/vendor/bin");
        basePathEntries.add("/vendor/xbin");
        basePathEntries.add("/product/bin");
        basePathEntries.add("/apex/com.android.runtime/bin");
        basePathEntries.add("/sbin");
        basePathEntries.add("/system/sbin");

        Set<String> allPathEntries = new LinkedHashSet<>(binDirs);
        allPathEntries.addAll(basePathEntries);
        env.add("PATH=" + joinPath(allPathEntries));
        env.add("SW_BASE_PATH=" + joinPath(basePathEntries));
        env.add("HOME=" + homeDir);
        env.add("PWD=" + homeDir);
        env.add("SW_PROJECT_DIR=" + homeDir);
        env.add("TMPDIR=" + appContext.getCacheDir().getAbsolutePath());
        env.add("TERM=xterm-256color");
        env.add("COLORTERM=truecolor");
        env.add("LANG=en_US.UTF-8");
        env.add("PS1=\\W $ ");
        // mksh sources ENV for interactive PTY shells, so helper functions are defined before
        // the user can type. This avoids a startup race from writing a command into a new PTY.
        if (initScript != null && initScript.isFile()) env.add("ENV=" + initScript.getAbsolutePath());
        copyIfSet(env, "ANDROID_ROOT", "/system");
        copyIfSet(env, "ANDROID_DATA", "/data");
        copyIfSet(env, "ANDROID_ASSETS", "/system/app");

        File installedNdk = InbuiltNdkManager.getInstalledNdkDir(appContext);
        if (installedNdk != null && new File(installedNdk, "build/cmake/android.toolchain.cmake").isFile()) {
            env.add("ANDROID_NDK_HOME=" + installedNdk.getAbsolutePath());
            env.add("ANDROID_NDK_ROOT=" + installedNdk.getAbsolutePath());
            env.add("NDK_HOME=" + installedNdk.getAbsolutePath());
        }
        File cmake = new File(appContext.getFilesDir(), "cmake/3.22.1");
        if (isExecutable(new File(cmake, "bin/cmake"))) env.add("CMAKE_HOME=" + cmake.getAbsolutePath());
        return env.toArray(new String[0]);
    }

    private static String joinPath(Set<String> entries) {
        StringBuilder path = new StringBuilder();
        for (String entry : entries) {
            if (path.length() > 0) path.append(':');
            path.append(entry);
        }
        return path.toString();
    }

    private static void copyIfSet(List<String> env, String name, String fallback) {
        String value = System.getenv(name);
        env.add(name + "=" + (value != null && !value.isEmpty() ? value : fallback));
    }

    private static List<String> findVersionedBinDirs(File toolRoot) {
        List<String> result = new ArrayList<>();
        if (!toolRoot.isDirectory()) return result;
        File[] versionDirs = toolRoot.listFiles(File::isDirectory);
        if (versionDirs == null) return result;
        for (File versionDir : versionDirs) {
            File bin = new File(versionDir, "bin");
            if (bin.isDirectory()) result.add(bin.getAbsolutePath());
        }
        return result;
    }

    /**
     * Installs the same helper set in every shell session. Commands use the Android shell and
     * installed toolchain binaries only; this does not pretend that Termux/pkg/npm are installed.
     */
    private static File writeInitScript(Context appContext, String projectId) {
        String filesDir = appContext.getFilesDir().getAbsolutePath();
        String ndkDir = filesDir + "/ndk";
        String cmakeDir = filesDir + "/cmake";
        String pkg = appContext.getPackageName();
        String quotedNdk = shellQuote(ndkDir);
        String quotedCmake = shellQuote(cmakeDir);
        String quotedPackage = shellQuote(pkg);

        StringBuilder script = new StringBuilder(3000);
        script.append("NDK_DIR=").append(quotedNdk).append("\n")
                .append("CMAKE_DIR=").append(quotedCmake).append("\n")
                .append("SW_PACKAGE=").append(quotedPackage).append("\n")
                .append("clear\n")
                .append("printf '\033[1;32m'; echo 'Sketchware Neo Terminal'; printf '\033[0m'\n")
                .append("echo 'Project ID: ").append(projectId).append("'\n")
                .append("echo 'Working directory:'; pwd\n")
                .append("sw_refresh_path() {\n")
                .append("  PATH=${SW_BASE_PATH:-$PATH}; unset ANDROID_NDK_HOME ANDROID_NDK_ROOT NDK_HOME CMAKE_HOME\n")
                .append("  cmake_bin=\"$CMAKE_DIR/3.22.1/bin\"; if [ -x \"$cmake_bin/cmake\" ]; then PATH=\"$cmake_bin:$PATH\"; CMAKE_HOME=\"$CMAKE_DIR/3.22.1\"; fi\n")
                .append("  for d in \"$NDK_DIR\"/*/; do [ -f \"$d/build/cmake/android.toolchain.cmake\" ] || continue; ndk_root=${d%/}; for host in linux-aarch64 linux-arm64 android-arm64 android-aarch64; do bin=\"$ndk_root/toolchains/llvm/prebuilt/$host/bin\"; if [ -x \"$bin/clang\" ] || [ -x \"$bin/clang++\" ]; then PATH=\"$bin:$PATH\"; ANDROID_NDK_HOME=\"$ndk_root\"; ANDROID_NDK_ROOT=\"$ndk_root\"; NDK_HOME=\"$ndk_root\"; break; fi; done; [ -n \"${ANDROID_NDK_HOME:-}\" ] && break; done\n")
                .append("  export PATH ANDROID_NDK_HOME ANDROID_NDK_ROOT NDK_HOME CMAKE_HOME\n")
                .append("}\n")
                .append("sw_refresh_path\n")
                .append("ndk_status() {\n")
                .append("  sw_refresh_path\n")
                .append("  if [ -x \"$CMAKE_DIR/3.22.1/bin/cmake\" ]; then \"$CMAKE_DIR/3.22.1/bin/cmake\" --version 2>/dev/null | head -1; else echo 'CMake 3.22.1: not installed'; fi\n")
                .append("  found=0; for d in \"$NDK_DIR\"/*/; do [ -f \"$d/build/cmake/android.toolchain.cmake\" ] || continue; echo \"NDK: $(basename \"$d\")\"; found=1; done; [ \"$found\" -eq 1 ] || echo 'NDK: not installed'\n")
                .append("  command -v ninja >/dev/null 2>&1 && echo \"Ninja: $(ninja --version 2>/dev/null)\" || echo 'Ninja: not installed'\n")
                .append("}\n")
                .append("ndk_install() {\n")
                .append("  if [ -z \"$1\" ]; then echo 'Usage: ndk_install <https://ndk-archive.zip>'; return 2; fi\n")
                .append("  case \"$1\" in https://*|http://*) ;; *) echo 'Use an http:// or https:// archive URL.'; return 2 ;; esac\n")
                .append("  command -v am >/dev/null 2>&1 || { echo 'Android Activity Manager (am) is unavailable in PATH.'; return 127; }\n")
                .append("  result=$(am broadcast -a mod.sketchlibx.terminal.ACTION_INSTALL_NDK -n \"$SW_PACKAGE/mod.sketchlibx.terminal.NdkInstallReceiver\" --es ndk_url \"$1\" 2>&1); rc=$?; printf '%s\\n' \"$result\"\n")
                .append("  [ \"$rc\" -eq 0 ] || { echo 'Could not send the install request.'; return \"$rc\"; }\n")
                .append("  echo 'Request sent. Installation runs in the background; use ndk_status to check the result.'\n")
                .append("}\n")
                .append("ndk_uninstall() {\n")
                .append("  echo 'This removes the installed NDK and CMake directories used by Sketchware Neo.'\n")
                .append("  printf 'Type yes to continue: '; IFS= read -r answer\n")
                .append("  [ \"$answer\" = yes ] || { echo 'Cancelled.'; return 1; }\n")
                .append("  rm -rf -- \"$NDK_DIR\" \"$CMAKE_DIR\" || return $?\n")
                .append("  sw_refresh_path\n")
                .append("  echo 'NDK/CMake files removed.'\n")
                .append("}\n")
                .append("sw_help() {\n")
                .append("  echo 'Android shell: pwd, ls, cd, mkdir, cp, mv, rm, cat, grep, find, sed, awk, head, tail, wc, chmod, env, getprop, am'\n")
                .append("  echo 'Neo helpers: sw_help, swdoctor, sw_project, gradlew <tasks>, ndk_status, ndk_install <url>, ndk_uninstall'\n")
                .append("  echo 'npm, pkg, git, cmake, ninja and clang work only when their executable is installed and accessible.'\n")
                .append("  echo 'Use gradlew <tasks> without ./ for a helpful wrapper check; ./gradlew requires an actual file in this directory.'\n")
                .append("  echo 'Sketchware app projects in .sketchware/data/<id> are not Gradle source repositories. Use Sketchware Build/Run for those projects.'\n")
                .append("}\n")
                .append("sw_project() { echo 'Project directory:'; printf '%s\\n' \"$SW_PROJECT_DIR\"; echo 'Files:'; ls -la; }\n")
                .append("gradlew() {\n")
                .append("  if [ -f ./gradlew ]; then sh ./gradlew \"$@\"; return $?; fi\n")
                .append("  if command -v gradle >/dev/null 2>&1; then gradle \"$@\"; return $?; fi\n")
                .append("  echo 'No Gradle wrapper was found in this directory, and system Gradle is not installed.'\n")
                .append("  echo \"Current directory: $PWD\"\n")
                .append("  case \"$PWD\" in */.sketchware/data/*) echo 'This is a Sketchware app-project data directory, not the Sketchware Neo Android source repository. Build the app with Sketchware Build/Run; build Neo itself from its source repository.' ;; *) echo 'Change to a Gradle repository root containing gradlew and gradle/wrapper, then retry.' ;; esac\n")
                .append("  return 127\n")
                .append("}\n")
                .append("swdoctor() {\n")
                .append("  echo '--- Sketchware Neo Terminal diagnostics ---'; echo \"Directory: $PWD\"; echo \"UID: $(id 2>/dev/null)\"; echo \"Android: $(getprop ro.build.version.release 2>/dev/null)\"; echo\n")
                .append("  for c in sh ls cat grep find sed awk git gradle npm node pkg cmake ninja clang clang++ am; do command -v \"$c\" >/dev/null 2>&1 && printf '  [OK]   %s: %s\\n' \"$c\" \"$(command -v \"$c\")\" || printf '  [MISS] %s\\n' \"$c\"; done\n")
                .append("  echo '  [OK]   Neo helper: gradlew (run without ./)'\n")
                .append("  if [ -f ./gradlew ]; then echo 'Gradle wrapper: present in current directory'; else echo 'Gradle wrapper: not present in current directory'; fi\n")
                .append("  ndk_status\n")
                .append("}\n")
                .append("alias ll='ls -la'\n")
                .append("echo 'Helpers: sw_help | swdoctor | sw_project | ndk_status'\n")
                .append("ndk_status\n")
                .append("echo 'Build a Sketchware project with Build/Run; this shell starts in the project data directory.'\n")
                .append("echo\n");
        File scriptsDir = new File(appContext.getFilesDir(), "terminal");
        File initFile = new File(scriptsDir, "session-init-" + projectId + ".sh");
        try {
            if (!scriptsDir.isDirectory() && !scriptsDir.mkdirs() && !scriptsDir.isDirectory()) {
                throw new IOException("Could not create terminal helper directory");
            }
            try (FileOutputStream output = new FileOutputStream(initFile, false)) {
                output.write(script.toString().getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            return initFile;
        } catch (IOException e) {
            android.util.Log.e("NeoTerminal", "Could not prepare terminal helper script", e);
            return null;
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

}
