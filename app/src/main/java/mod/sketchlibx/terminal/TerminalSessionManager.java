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

    public static synchronized void releaseProject(Context context, String scId) {
        String projectId = normalizeProjectId(scId);
        killAllSessions(projectId);
        deleteInitScript(context.getApplicationContext(), projectId);
    }

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

    public static synchronized Entry getOrCreateFirst(Context context, String scId, TerminalSessionClient client) {
        String projectId = normalizeProjectId(scId);
        List<Entry> sessions = getSessions(projectId);
        for (Entry e : sessions) {
            if (e.session.isRunning()) return e;
        }
        return createSession(context, projectId, client);
    }


    public static synchronized Entry createSession(Context context, String scId, TerminalSessionClient client) {
        String projectId = normalizeProjectId(scId);
        Context appContext = context.getApplicationContext();
        List<Entry> sessions = getSessions(projectId);
        int number = PROJECT_COUNTERS.computeIfAbsent(projectId, k -> new AtomicInteger(0)).incrementAndGet();
        String sessionId = projectId + "#" + number;

        File initScript = writeInitScript(appContext, projectId);
        TerminalSession session = spawnSession(appContext, projectId, client, initScript);
        Entry entry = new Entry(sessionId, "Session " + number, session);
        sessions.add(entry);
        TerminalCommandDispatcher.ensureStarted(appContext);
        return entry;
    }

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
        stopCommandServerIfIdle();
    }

    private static boolean hasAnySession() {
        for (List<Entry> list : PROJECT_SESSIONS.values()) {
            if (!list.isEmpty()) return true;
        }
        return false;
    }

    private static void stopCommandServerIfIdle() {
        if (!hasAnySession()) TerminalCommandDispatcher.stop();
    }

    public static synchronized void killAllSessions(String scId) {
        String projectId = normalizeProjectId(scId);
        List<Entry> sessions = PROJECT_SESSIONS.remove(projectId);
        if (sessions != null) {
            for (Entry e : sessions) e.session.finishIfRunning();
        }
        PROJECT_COUNTERS.remove(projectId);
        stopCommandServerIfIdle();
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
        env.add("SW_IPC=" + TerminalCommandDispatcher.getIpcDirectory(appContext).getAbsolutePath());
        env.add("TMPDIR=" + appContext.getCacheDir().getAbsolutePath());
        env.add("TERM=xterm-256color");
        env.add("COLORTERM=truecolor");
        env.add("LANG=en_US.UTF-8");
        env.add("PS1=\\W $ ");

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


    static String buildInitScript(String filesDir, String projectId) {
        StringBuilder help = new StringBuilder();
        for (String line : TerminalCommands.HELP_LINES) {
            help.append("  printf '%s\\n' ").append(shellQuote(line)).append("\n");
        }
        return SCRIPT_TEMPLATE
                .replace("@NDK_DIR@", shellQuote(filesDir + "/ndk"))
                .replace("@CMAKE_DIR@", shellQuote(filesDir + "/cmake"))
                .replace("@PROJECT_ID@", projectId)
                .replace("@HELP_LINES@", help.toString())
                .replace("@BUILD@", TerminalCommands.COMMAND_BUILD)
                .replace("@CANCEL_BUILD@", TerminalCommands.COMMAND_CANCEL_BUILD)
                .replace("@CLEAN_CACHE@", TerminalCommands.COMMAND_CLEAN_CACHE)
                .replace("@CLEAN_GRADLE@", TerminalCommands.COMMAND_CLEAN_GRADLE_BACKUPS)
                .replace("@NDK_INSTALL@", TerminalCommands.COMMAND_NDK_INSTALL)
                .replace("@STATUS@", TerminalCommands.COMMAND_STATUS);
    }

    private static File writeInitScript(Context appContext, String projectId) {
        String script = buildInitScript(appContext.getFilesDir().getAbsolutePath(), projectId);

        File scriptsDir = new File(appContext.getFilesDir(), "terminal");
        File initFile = new File(scriptsDir, "session-init-" + projectId + ".sh");
        File ipcDir = TerminalCommandDispatcher.getIpcDirectory(appContext);
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
  case "$1" in *'
'*) echo 'The URL must be a single line.'; return 2 ;; esac
  sw_call @NDK_INSTALL@ 7200 "url=$1"
}
sw_send() {
  sw_s_file="$SW_IPC/$1.req"
  { printf 'command=%s\\n' "$2"; printf 'sc_id=%s\\n' "$SW_PROJECT_ID"; shift 2; for sw_s_arg in "$@"; do printf '%s\\n' "$sw_s_arg"; done; } > "$sw_s_file.tmp" 2>/dev/null && mv "$sw_s_file.tmp" "$sw_s_file" 2>/dev/null || { rm -f "$sw_s_file.tmp"; return 1; }
}
sw_call() {
  sw_c_cmd=$1; sw_c_max=$2; shift 2
  for sw_c_tool in mv rm sleep cat wc sed; do command -v "$sw_c_tool" >/dev/null 2>&1 || { echo "The required tool '$sw_c_tool' is missing from this shell."; return 127; }; done
  if [ ! -d "$SW_IPC" ] || [ ! -w "$SW_IPC" ]; then echo 'The terminal request directory is missing or not writable.'; return 1; fi
  SW_SEQ=$((${SW_SEQ:-0}+1))
  sw_c_id="$$-$SW_SEQ-${RANDOM:-0}"
  sw_c_out="$SW_IPC/$sw_c_id.out"; sw_c_rc="$SW_IPC/$sw_c_id.rc"; sw_c_req="$SW_IPC/$sw_c_id.req"
  rm -f "$sw_c_out" "$sw_c_rc" "$sw_c_req"
  sw_send "$sw_c_id" "$sw_c_cmd" "$@" || { echo 'Could not write the request file.'; return 1; }
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
        sw_send "cancel-$sw_c_id" @CANCEL_BUILD@
      else
        trap - INT; rm -f "$sw_c_req"; echo 'Stopped waiting. The operation may still be running in Sketchware Neo.'; return 130
      fi
    fi
    if [ "$sw_c_seen" -eq 0 ] && [ "$sw_c_tick" -ge 10 ]; then
      trap - INT
      if [ -f "$sw_c_req" ]; then rm -f "$sw_c_req"; echo 'Sketchware Neo did not pick up the request: the terminal service is not running. Reopen the Terminal tab and try again.'; else echo 'Sketchware Neo picked up the request but sent no answer.'; fi
      return 1
    fi
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
sw_status() { sw_call @STATUS@ 30; }
sw_build() { sw_call @BUILD@ 3600; }
sw_clean_cache() {
  sw_call @CLEAN_CACHE@ 120 dry_run=true; sw_rc=$?
  [ "$sw_rc" -eq 10 ] || return "$sw_rc"
  sw_confirm 'The build cache shown above will be deleted. Projects and sources are not touched.' || { echo 'Cancelled.'; return 1; }
  sw_call @CLEAN_CACHE@ 900 confirmed=true
}
sw_clean_gradle_backups() {
  sw_call @CLEAN_GRADLE@ 120 dry_run=true; sw_rc=$?
  [ "$sw_rc" -eq 10 ] || return "$sw_rc"
  sw_confirm 'The Gradle backups listed above will be deleted. The current Gradle files are not touched.' || { echo 'Cancelled.'; return 1; }
  sw_call @CLEAN_GRADLE@ 120 confirmed=true
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
  for c in sh ls cat grep find sed awk git gradle npm node pkg cmake ninja clang clang++; do command -v "$c" >/dev/null 2>&1 && printf '  [OK]   %s: %s\\n' "$c" "$(command -v "$c")" || printf '  [MISS] %s\\n' "$c"; done
  if [ -f ./gradlew ]; then echo 'Gradle wrapper: present in current directory'; else echo 'Gradle wrapper: not present in current directory'; fi
  ndk_status
}
alias ll='ls -la'
echo 'Type help to list the available commands.'
ndk_status
echo
""";
}
