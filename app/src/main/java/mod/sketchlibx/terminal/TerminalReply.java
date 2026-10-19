package mod.sketchlibx.terminal;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TerminalReply {

    private static final String TAG = "TerminalReply";

    private final File out;
    private final File rc;
    private final File rcTemp;
    private boolean finished;

    public TerminalReply(File directory, String requestId) {
        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();
        out = new File(directory, requestId + ".out");
        rc = new File(directory, requestId + ".rc");
        rcTemp = new File(directory, requestId + ".rc.tmp");
    }

    public synchronized void line(String text) {
        if (finished) return;
        try (FileOutputStream stream = new FileOutputStream(out, true)) {
            stream.write((text + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            warn("Could not write terminal reply", e);
        }
    }

    public synchronized void lines(List<String> texts) {
        for (String text : texts) line(text);
    }

    public synchronized void finish(int code, String finalLine) {
        line(finalLine);
        finish(code);
    }

    public synchronized void finish(int code) {
        if (finished) return;
        try (FileOutputStream stream = new FileOutputStream(rcTemp, false)) {
            stream.write(String.valueOf(code).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            warn("Could not write terminal status", e);
            return;
        }
        if (!rcTemp.renameTo(rc)) {
            warn("Could not publish terminal status", null);
            return;
        }
        finished = true;
    }

    private static void warn(String message, Throwable error) {
        try {
            Log.w(TAG, message, error);
        } catch (RuntimeException ignored) {

        }
    }
}
