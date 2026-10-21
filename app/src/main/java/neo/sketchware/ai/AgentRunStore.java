package neo.sketchware.ai;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** File-backed run history. Writes are atomic and use app-private storage (no broad storage permission). */
public final class AgentRunStore {
    private static final Gson GSON = new Gson();
    private static final Object LOCK = new Object();
    private static final Type RUN_TYPE = new TypeToken<AgentRun>() {}.getType();

    private AgentRunStore() {}

    private static File directory(Context context) {
        File directory = new File(context.getApplicationContext().getFilesDir(), "ai_agent_runs");
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Unable to create private AI run history directory");
        }
        return directory;
    }

    private static boolean validId(String id) {
        if (id == null || id.length() > 64) return false;
        try { UUID.fromString(id); return true; } catch (IllegalArgumentException e) { return false; }
    }

    private static File file(Context context, String id) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid agent run identifier");
        return new File(directory(context), id + ".json");
    }

    public static void save(Context context, AgentRun run) {
        if (context == null || run == null || !validId(run.id)) throw new IllegalArgumentException("Invalid agent run");
        synchronized (LOCK) {
            File target = file(context, run.id);
            File temp = new File(target.getParentFile(), run.id + ".json.tmp");
            File backup = new File(target.getParentFile(), run.id + ".json.bak");
            try {
                run.updatedAt = System.currentTimeMillis();
                try (FileOutputStream stream = new FileOutputStream(temp);
                     Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
                    GSON.toJson(run, RUN_TYPE, writer);
                    writer.flush();
                    stream.getFD().sync();
                }
                if (backup.exists() && !backup.delete()) throw new IllegalStateException("Unable to clear stale AI-run backup");
                if (target.exists() && !target.renameTo(backup)) throw new IllegalStateException("Unable to preserve previous AI-run record");
                if (!temp.renameTo(target)) {
                    if (backup.exists()) backup.renameTo(target);
                    throw new IllegalStateException("Unable to commit AI-run record");
                }
                if (backup.exists()) backup.delete();
            } catch (Exception e) {
                if (temp.exists()) temp.delete();
                if (!target.exists() && backup.exists()) backup.renameTo(target);
                throw new IllegalStateException("Unable to save AI run history", e);
            }
        }
    }

    public static AgentRun get(Context context, String id) {
        synchronized (LOCK) {
            File target = file(context, id);
            File backup = new File(target.getParentFile(), id + ".json.bak");
            if (!target.isFile() && backup.isFile()) backup.renameTo(target);
            if (!target.isFile()) return null;
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(target), StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, RUN_TYPE);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static List<AgentRun> list(Context context) {
        synchronized (LOCK) {
            List<AgentRun> result = new ArrayList<>();
            File[] files = directory(context).listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File source : files) {
                    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)) {
                        AgentRun run = GSON.fromJson(reader, RUN_TYPE);
                        if (run != null && validId(run.id)) result.add(run);
                    } catch (Exception ignored) {
                        // Ignore one damaged history entry; the rest of the workspace remains usable.
                    }
                }
            }
            Collections.sort(result, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
            return result;
        }
    }

    public static boolean delete(Context context, String id) {
        synchronized (LOCK) {
            File f = file(context, id);
            File b = new File(f.getParentFile(), id + ".json.bak");
            boolean ok = !f.exists() || f.delete();
            if (b.exists()) ok = b.delete() && ok;
            return ok;
        }
    }
}
