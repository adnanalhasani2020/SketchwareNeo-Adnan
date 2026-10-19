package mod.sketchlibx.terminal;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.activity.managers.cpp.InbuiltNdkManager;

public final class TerminalCommandDispatcher {

    private static final long POLL_MILLIS = 250;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "terminal-command");
        thread.setDaemon(true);
        return thread;
    });
    private static TerminalCommandServer server;

    private TerminalCommandDispatcher() {
    }

    public static File getIpcDirectory(Context context) {
        return new File(new File(context.getApplicationContext().getFilesDir(), "terminal"), "ipc");
    }

    public static synchronized void ensureStarted(Context context) {
        if (server != null && server.isRunning()) return;
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        TerminalCommandHandler handler = new TerminalCommandHandler(
                Environment.getExternalStorageDirectory(),
                TerminalSessionManager::hasSessions,
                WORKER,
                mainHandler::post,
                (url, callback) -> InbuiltNdkManager.installNdkAndCmake(appContext, url, new InbuiltNdkManager.InstallCallback() {
                    @Override
                    public void onProgress(String message, int progress, boolean isIndeterminate) {
                        callback.onProgress(message, progress, isIndeterminate);
                    }

                    @Override
                    public void onSuccess() {
                        callback.onSuccess();
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                }));
        server = new TerminalCommandServer(getIpcDirectory(appContext), handler, POLL_MILLIS);
        server.start();
    }

    public static synchronized void stop() {
        if (server != null) server.stop();
        server = null;
    }
}
