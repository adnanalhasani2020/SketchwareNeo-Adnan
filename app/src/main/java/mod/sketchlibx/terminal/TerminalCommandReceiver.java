package mod.sketchlibx.terminal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Receives the fixed set of terminal helper requests sent by the shell functions in the session
 * init script. Nothing here executes shell input: a request is one of a few known command names plus
 * a validated project id. Results are written to files in the app's private terminal directory,
 * which the shell function polls, so the terminal only reports a result once it is really known.
 */
public class TerminalCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "TerminalCommand";
    private static final Pattern REQUEST_ID = Pattern.compile("[0-9A-Za-z_-]{1,64}");
    private static final long STALE_IPC_MILLIS = 60L * 60L * 1000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "terminal-command");
        thread.setDaemon(true);
        return thread;
    });

    public static File getIpcDirectory(Context context) {
        return new File(new File(context.getApplicationContext().getFilesDir(), "terminal"), "ipc");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TerminalCommands.ACTION_COMMAND.equals(intent.getAction())) return;

        String requestId = intent.getStringExtra(TerminalCommands.EXTRA_REQUEST_ID);
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            Log.w(TAG, "Ignoring request with an invalid id");
            return;
        }
        File ipcDirectory = getIpcDirectory(context);
        purgeStaleFiles(ipcDirectory);
        Reply reply = new Reply(ipcDirectory, requestId);

        String scId = intent.getStringExtra(TerminalCommands.EXTRA_SC_ID);
        if (!TerminalCleanup.isValidProjectId(scId) || !TerminalSessionManager.hasSessions(scId)) {
            reply.finish(TerminalCleanup.CODE_INVALID, "This terminal is not attached to an open project.");
            return;
        }

        String command = intent.getStringExtra(TerminalCommands.EXTRA_COMMAND);
        if (command == null) {
            reply.finish(TerminalCleanup.CODE_INVALID, "Missing command.");
            return;
        }
        switch (command) {
            case TerminalCommands.COMMAND_BUILD -> startBuild(reply, scId);
            case TerminalCommands.COMMAND_CANCEL_BUILD -> {
                TerminalBuildBridge.Host host = TerminalBuildBridge.get(scId);
                if (host != null && host.isBuildRunning()) host.cancelBuild();
                reply.finish(TerminalCleanup.CODE_OK, "Cancel requested.");
            }
            case TerminalCommands.COMMAND_CLEAN_CACHE, TerminalCommands.COMMAND_CLEAN_GRADLE_BACKUPS -> {
                boolean dryRun = intent.getBooleanExtra(TerminalCommands.EXTRA_DRY_RUN, false);
                boolean confirmed = intent.getBooleanExtra(TerminalCommands.EXTRA_CONFIRMED, false);
                runCleanup(goAsync(), reply, command, scId, dryRun, confirmed);
            }
            default -> reply.finish(TerminalCleanup.CODE_INVALID, "Unknown command: " + command);
        }
    }

    private void startBuild(Reply reply, String scId) {
        TerminalBuildBridge.Host host = TerminalBuildBridge.get(scId);
        if (host == null) {
            reply.finish(TerminalCommands.EXIT_NOT_AVAILABLE, "The project editor is not open, so a build cannot be started from here.");
            return;
        }
        if (host.isBuildRunning()) {
            reply.finish(TerminalCommands.EXIT_BUSY, "A build is already running for this project.");
            return;
        }
        reply.line("Starting APK build...");
        boolean started = host.startBuild(new TerminalBuildBridge.Listener() {
            private String lastProgress = "";

            @Override
            public void onProgress(String message) {
                if (message == null) return;
                String text = message.trim();
                if (text.isEmpty() || text.equals(lastProgress)) return;
                lastProgress = text;
                reply.line(text);
            }

            @Override
            public void onSuccess(String apkPath) {
                reply.line("Build succeeded.");
                if (apkPath != null) reply.line("APK: " + apkPath);
                reply.finish(TerminalCleanup.CODE_OK);
            }

            @Override
            public void onFailure(String message) {
                reply.line("Build failed.");
                reply.lines(firstLines(message, 12));
                reply.line("Open the compile log from the project editor for the full output.");
                reply.finish(TerminalCleanup.CODE_FAILED);
            }

            @Override
            public void onCanceled() {
                reply.finish(TerminalCommands.EXIT_CANCELED, "Build canceled.");
            }
        });
        if (!started) reply.finish(TerminalCommands.EXIT_BUSY, "The build could not be started.");
    }

    private void runCleanup(PendingResult pending, Reply reply, String command, String scId, boolean dryRun, boolean confirmed) {
        WORKER.execute(() -> {
            try {
                if (TerminalBuildBridge.isBuildRunning(scId)) {
                    reply.finish(TerminalCommands.EXIT_BUSY, "A build is running. Wait for it to finish or cancel it first.");
                    return;
                }
                File root = Environment.getExternalStorageDirectory();
                TerminalCleanup.Result result = TerminalCommands.COMMAND_CLEAN_CACHE.equals(command)
                        ? TerminalCleanup.clearMyscCache(root, scId, dryRun, confirmed)
                        : TerminalCleanup.deleteGradleBackups(root, scId, dryRun, confirmed);
                reply.lines(result.lines);
                reply.finish(result.code);
            } catch (Throwable t) {
                Log.e(TAG, "Cleanup failed", t);
                reply.finish(TerminalCleanup.CODE_FAILED, "Unexpected error: " + t.getMessage());
            } finally {
                pending.finish();
            }
        });
    }

    private static List<String> firstLines(String text, int max) {
        if (text == null || text.trim().isEmpty()) return List.of("No error details were provided.");
        String[] all = text.trim().split("\\R");
        return List.of(all).subList(0, Math.min(all.length, max));
    }

    private static void purgeStaleFiles(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        for (File file : files) {
            String name = file.getName();
            boolean ours = name.endsWith(".out") || name.endsWith(".rc") || name.endsWith(".rc.tmp");
            if (ours && file.isFile() && now - file.lastModified() > STALE_IPC_MILLIS) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    /** Output file ({@code <id>.out}) followed by a final status file ({@code <id>.rc}). */
    private static final class Reply {
        private final File out;
        private final File rc;
        private final File rcTemp;
        private boolean finished;

        Reply(File directory, String requestId) {
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            out = new File(directory, requestId + ".out");
            rc = new File(directory, requestId + ".rc");
            rcTemp = new File(directory, requestId + ".rc.tmp");
        }

        synchronized void line(String text) {
            if (finished) return;
            try (FileOutputStream stream = new FileOutputStream(out, true)) {
                stream.write((text + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "Could not write terminal reply", e);
            }
        }

        synchronized void lines(List<String> texts) {
            for (String text : texts) line(text);
        }

        synchronized void finish(int code, String finalLine) {
            line(finalLine);
            finish(code);
        }

        synchronized void finish(int code) {
            if (finished) return;
            try (FileOutputStream stream = new FileOutputStream(rcTemp, false)) {
                stream.write(String.valueOf(code).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "Could not write terminal status", e);
                return;
            }
            if (!rcTemp.renameTo(rc)) Log.w(TAG, "Could not publish terminal status");
            finished = true;
        }
    }
}
