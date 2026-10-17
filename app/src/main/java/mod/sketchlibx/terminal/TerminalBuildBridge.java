package mod.sketchlibx.terminal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets the terminal ask the open project editor to run its normal APK build without the terminal
 * depending on the editor classes.
 */
public final class TerminalBuildBridge {

    public interface Listener {
        void onProgress(String message);

        void onSuccess(String apkPath);

        void onFailure(String message);

        void onCanceled();
    }

    public interface Host {
        boolean isBuildRunning();

        boolean startBuild(Listener listener);

        void cancelBuild();
    }

    private static final Map<String, Host> HOSTS = new ConcurrentHashMap<>();

    private TerminalBuildBridge() {
    }

    public static void register(String scId, Host host) {
        if (scId != null && host != null) HOSTS.put(scId, host);
    }

    public static void unregister(String scId, Host host) {
        if (scId != null && host != null) HOSTS.remove(scId, host);
    }

    public static Host get(String scId) {
        return scId == null ? null : HOSTS.get(scId);
    }

    public static boolean isBuildRunning(String scId) {
        Host host = get(scId);
        return host != null && host.isBuildRunning();
    }
}
