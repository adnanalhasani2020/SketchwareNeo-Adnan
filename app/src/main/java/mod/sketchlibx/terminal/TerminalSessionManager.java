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

    public static synchronized boolean hasSessions(String scId) {
        List<Entry> sessions = PROJECT_SESSIONS.get(normalizeProjectId(scId));
        return sessions != null && !sessions.isEmpty();
    }

    /** Stops every session of this project and discards its output and generated helper script. */
    public static synchronized void releaseProject(Context context, String scId) {
        String projectId = normalizeProjectId(scId);
        killAllSessions(projectId);
        deleteInitScript(context.getApplicationContext(), projectId);
    }

    /** Releases sessions that belong to any project other than {@code activeScId}. */
    public static synchronized void releaseAllExcept(Context context, String activeScId) {
        String activeId = normalizeProjectId(activeScId);
        for (String projectId : new ArrayList<>(PROJECT_SESSIONS.keySet())) {
            if (!projectId.equals(activeId)) releaseProject(context, projectId);
        }
    }

    private static void deleteInitScript(Context appContext, String projectId) {
        File initFile = new File(new File(appContext.getFilesDir(), "terminal"), "session-init-" + projectId + ".sh");
        if (initFile.isFile() && !initFile.delete()) {
            android.util.Log.w("NeoTerminal", "Could not delete terminal helper script " + initFile.getName());
        }
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
        String[] env = buildEnvironment(appContext, scId, homeDir, binDirs, initScript);
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

    private static String[] buildEnvironment(Context appContext, String scId, String homeDir, List<String> binDirs, File initScript) {
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
        env.add("SW_PROJECT_ID=" + scId);
        env.add("SW_IPC=" + TerminalCommandReceiver.getIpcDirectory(appContext).getAbsolutePath());
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
     * Installs the same helper set in every shell session. Every helper is either a thin wrapper
     * around an installed tool or a request to the app (see {@link TerminalCommandReceiver}); none
     * of them evaluates user input as shell code.
     */
    static String buildInitScript(String filesDir, String packageName, String projectId) {
        StringBuilder help = new StringBuilder();
        for (String line : TerminalCommands.HELP_LINES) {
            help.append("  printf '%s\\n' ").append(shellQuote(line)).append("\n");
        }
        return SCRIPT_TEMPLATE
                .replace("@NDK_DIR@", shellQuote(filesDir + "/ndk"))
                .replace("@CMAKE_DIR@", shellQuote(filesDir + "/cmake"))
                .replace("@PACKAGE@", shellQuote(packageName))
                .replace("@ACTION@", TerminalCommands.ACTION_COMMAND)
                .replace("@PROJECT_ID@", projectId)
                .replace("@HELP_LINES@", help.toString())
                .replace("@BUILD@", TerminalCommands.COMMAND_BUILD)
                .replace("@CANCEL_BUILD@", TerminalCommands.COMMAND_CANCEL_BUILD)
                .replace("@CLEAN_CACHE@", TerminalCommands.COMMAND_CLEAN_CACHE)
                .replace("@CLEAN_GRADLE@", TerminalCommands.COMMAND_CLEAN_GRADLE_BACKUPS);
    }

    private static File writeInitScript(Context appContext, String projectId) {
        String script = buildInitScript(appContext.getFilesDir().getAbsolutePath(), appContext.getPackageName(), projectId);

        File scriptsDir = new File(appContext.getFilesDir(), "terminal");
        File initFile = new File(scriptsDir, "session-init-" + projectId + ".sh");
        File ipcDir = TerminalCommandReceiver.getIpcDirectory(appContext);
        try {
            if (!scriptsDir.isDirectory() && !scriptsDir.mkdirs() && !scriptsDir.isDirectory()) {
                throw new IOException("Could not create terminal helper directory");
            }
            if (!ipcDir.isDirectory() && !ipcDir.mkdirs() && !ipcDir.isDirectory()) {
                throw new IOException("Could not create terminal request directory");
            }
            try (FileOutputStream output = new FileOutputStream(initFile, false)) {
                output.write(script.getBytes(StandardCharsets.UTF_8));
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


    private static final String SCRIPT_TEMPLATE = """
NDK_DIR=@NDK_DIR@
CMAKE_DIR=@CMAKE_DIR@
SW_PACKAGE=@PACKAGE@
clear
printf '\\033[1;32m'; echo 'Sketchware Neo Terminal'; printf '\\033[0m'
echo 'Project ID: @PROJECT_ID@'
echo 'Working directory:'; pwd
sw_refresh_path() {
  PATH=${SW_BASE_PATH:-$PATH}; unset ANDROID_NDK_HOME ANDROID_NDK_ROOT NDK_HOME CMAKE_HOME
  cmake_bin="$CMAKE_DIR/3.22.1/bin"; if [ -x "$cmake_bin/cmake" ]; then PATH="$cmake_bin:$PATH"; CMAKE_HOME="$CMAKE_DIR/3.22.1"; fi
  for d in "$NDK_DIR"/*/; do [ -f "$d/build/cmake/android.toolchain.cmake" ] || continue; ndk_root=${d%/}; for host in linux-aarch64 linux-arm64 android-arm64 android-aarch64; do bin="$ndk_root/toolchains/llvm/prebuilt/$host/bin"; if [ -x "$bin/clang" ] || [ -x "$bin/clang++" ]; then PATH="$bin:$PATH"; ANDROID_NDK_HOME="$ndk_root"; ANDROID_NDK_ROOT="$ndk_root"; NDK_HOME="$ndk_root"; break; fi; done; [ -n "${ANDROID_NDK_HOME:-}" ] && break; done
  export PATH ANDROID_NDK_HOME ANDROID_NDK_ROOT NDK_HOME CMAKE_HOME
}
sw_refresh_path
ndk_status() {
  sw_refresh_path
  if [ -x "$CMAKE_DIR/3.22.1/bin/cmake" ]; then "$CMAKE_DIR/3.22.1/bin/cmake" --version 2>/dev/null | head -1; else echo 'CMake 3.22.1: not installed'; fi
  found=0; for d in "$NDK_DIR"/*/; do [ -f "$d/build/cmake/android.toolchain.cmake" ] || continue; echo "NDK: $(basename "$d")"; found=1; done; [ "$found" -eq 1 ] || echo 'NDK: not installed'
  command -v ninja >/dev/null 2>&1 && echo "Ninja: $(ninja --version 2>/dev/null)" || echo 'Ninja: not installed'
}
ndk_install() {
  if [ -z "$1" ]; then echo 'Usage: ndk_install <https://ndk-archive.zip>'; return 2; fi
  case "$1" in https://*|http://*) ;; *) echo 'Use an http:// or https:// archive URL.'; return 2 ;; esac
  command -v am >/dev/null 2>&1 || { echo 'Android Activity Manager (am) is unavailable in PATH.'; return 127; }
  result=$(am broadcast -a mod.sketchlibx.terminal.ACTION_INSTALL_NDK -n "$SW_PACKAGE/mod.sketchlibx.terminal.NdkInstallReceiver" --es ndk_url "$1" 2>&1); rc=$?; printf '%s\\n' "$result"
  [ "$rc" -eq 0 ] || { echo 'Could not send the install request.'; return "$rc"; }
  echo 'Request sent. Installation runs in the background; use ndk_status to check the result.'
}
sw_call() {
  sw_c_cmd=$1; sw_c_max=$2; shift 2
  command -v am >/dev/null 2>&1 || { echo 'Android Activity Manager (am) is unavailable in PATH.'; return 127; }
  [ -d "$SW_IPC" ] || { echo 'The terminal request directory is unavailable.'; return 1; }
  SW_SEQ=$((${SW_SEQ:-0}+1))
  sw_c_id="$$-$(date +%s)-$SW_SEQ"
  sw_c_out="$SW_IPC/$sw_c_id.out"; sw_c_rc="$SW_IPC/$sw_c_id.rc"
  rm -f "$sw_c_out" "$sw_c_rc"
  sw_c_res=$(am broadcast -a @ACTION@ -n "$SW_PACKAGE/mod.sketchlibx.terminal.TerminalCommandReceiver" --es command "$sw_c_cmd" --es request_id "$sw_c_id" --es sc_id "$SW_PROJECT_ID" "$@" 2>&1); sw_c_status=$?
  if [ "$sw_c_status" -ne 0 ]; then printf '%s\\n' "$sw_c_res"; echo 'Could not send the request to Sketchware Neo.'; return "$sw_c_status"; fi
  sw_c_shown=0; sw_c_tick=0; sw_c_stop=0; sw_c_seen=0; sw_c_cancelsent=0
  trap 'sw_c_stop=1' INT
  while :; do
    if [ -f "$sw_c_out" ] || [ -f "$sw_c_rc" ]; then sw_c_seen=1; fi
    if [ -f "$sw_c_out" ]; then
      sw_c_lines=$(wc -l < "$sw_c_out" 2>/dev/null); sw_c_lines=$((${sw_c_lines:-0}+0))
      if [ "$sw_c_lines" -gt "$sw_c_shown" ]; then sed -n "$((sw_c_shown+1)),${sw_c_lines}p" "$sw_c_out"; sw_c_shown=$sw_c_lines; fi
    fi
    [ -f "$sw_c_rc" ] && break
    if [ "$sw_c_stop" -eq 1 ]; then
      if [ "$sw_c_cmd" = "@BUILD@" ] && [ "$sw_c_cancelsent" -eq 0 ]; then
        sw_c_cancelsent=1; sw_c_stop=0; echo 'Cancel requested. Waiting for the build to stop (Ctrl+C again to stop waiting)...'
        am broadcast -a @ACTION@ -n "$SW_PACKAGE/mod.sketchlibx.terminal.TerminalCommandReceiver" --es command @CANCEL_BUILD@ --es request_id "cancel-$sw_c_id" --es sc_id "$SW_PROJECT_ID" >/dev/null 2>&1
      else
        trap - INT; echo 'Stopped waiting. The operation may still be running in Sketchware Neo.'; return 130
      fi
    fi
    if [ "$sw_c_seen" -eq 0 ] && [ "$sw_c_tick" -ge 15 ]; then trap - INT; echo 'No response from Sketchware Neo. The request was not handled.'; return 1; fi
    if [ "$sw_c_tick" -ge "$sw_c_max" ]; then trap - INT; echo "Stopped waiting after $sw_c_max seconds. The operation may still be running in Sketchware Neo."; return 1; fi
    sleep 1; sw_c_tick=$((sw_c_tick+1))
  done
  sw_c_lines=$(wc -l < "$sw_c_out" 2>/dev/null); sw_c_lines=$((${sw_c_lines:-0}+0))
  if [ "$sw_c_lines" -gt "$sw_c_shown" ]; then sed -n "$((sw_c_shown+1)),${sw_c_lines}p" "$sw_c_out"; fi
  sw_c_status=$(cat "$sw_c_rc" 2>/dev/null)
  case "$sw_c_status" in ''|*[!0-9]*) sw_c_status=1 ;; esac
  rm -f "$sw_c_out" "$sw_c_rc"; trap - INT
  return "$sw_c_status"
}
sw_confirm() {
  printf '%s\\nType yes to continue: ' "$1"; IFS= read -r sw_answer
  [ "$sw_answer" = yes ]
}
sw_build() { sw_call @BUILD@ 3600; }
sw_clean_cache() {
  sw_call @CLEAN_CACHE@ 120 --ez dry_run true; sw_rc=$?
  [ "$sw_rc" -eq 10 ] || return "$sw_rc"
  sw_confirm 'The build cache shown above will be deleted. Projects and sources are not touched.' || { echo 'Cancelled.'; return 1; }
  sw_call @CLEAN_CACHE@ 900 --ez confirmed true
}
sw_clean_gradle_backups() {
  sw_call @CLEAN_GRADLE@ 120 --ez dry_run true; sw_rc=$?
  [ "$sw_rc" -eq 10 ] || return "$sw_rc"
  sw_confirm 'The Gradle backups listed above will be deleted. The current Gradle files are not touched.' || { echo 'Cancelled.'; return 1; }
  sw_call @CLEAN_GRADLE@ 120 --ez confirmed true
}
sw_help() {
@HELP_LINES@}
help() { sw_help; }
sw_project() { echo 'Project directory:'; printf '%s\\n' "$SW_PROJECT_DIR"; echo 'Files:'; ls -la; }
gradlew() {
  if [ -f ./gradlew ]; then sh ./gradlew "$@"; return $?; fi
  if command -v gradle >/dev/null 2>&1; then gradle "$@"; return $?; fi
  echo 'No Gradle wrapper was found in this directory, and system Gradle is not installed.'
  echo "Current directory: $PWD"
  case "$PWD" in */.sketchware/data/*) echo 'This is a Sketchware app-project data directory, not a Gradle repository. Build the app with sw_build or the Run button.' ;; *) echo 'Change to a Gradle repository root containing gradlew and gradle/wrapper, then retry.' ;; esac
  return 127
}
swdoctor() {
  echo '--- Sketchware Neo Terminal diagnostics ---'; echo "Directory: $PWD"; echo "UID: $(id 2>/dev/null)"; echo "Android: $(getprop ro.build.version.release 2>/dev/null)"; echo
  for c in sh ls cat grep find sed awk git gradle npm node pkg cmake ninja clang clang++ am; do command -v "$c" >/dev/null 2>&1 && printf '  [OK]   %s: %s\\n' "$c" "$(command -v "$c")" || printf '  [MISS] %s\\n' "$c"; done
  if [ -f ./gradlew ]; then echo 'Gradle wrapper: present in current directory'; else echo 'Gradle wrapper: not present in current directory'; fi
  ndk_status
}
alias ll='ls -la'
echo 'Type help to list the available commands.'
ndk_status
echo
""";
}
