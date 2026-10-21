package neo.sketchware.ai.local;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Manages imported GGUF files in app-private storage. It does not itself execute inference. */
public final class LocalModelManager {
    private LocalModelManager() {}

    public static File getDirectory(Context context) {
        File directory = new File(context.getApplicationContext().getFilesDir(), "local_models");
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Unable to create private local-model storage");
        }
        return directory;
    }

    /** Reserved for a future catalogue that supplies verified download URLs; no placeholder URL is exposed. */
    public static List<LocalModel> catalog() {
        return new ArrayList<>();
    }

    public static List<File> installed(Context context) {
        File[] files = getDirectory(context).listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".gguf"));
        List<File> result = new ArrayList<>();
        if (files != null) {
            // Keep malformed legacy imports visible so the user can remove/re-import them.
            for (File file : files) if (file.isFile()) result.add(file);
        }
        return result;
    }

    /** Validates imported/legacy files before presenting them as usable GGUF models. */
    public static boolean isValidGguf(File file) {
        return hasGgufHeader(file);
    }

    /**
     * Imports and validates a GGUF model atomically. Files remain private to Sketchware Neo.
     * Importing is not inference: a compatible local runtime/server is still required to execute it.
     */
    public static File importModel(Context context, Uri uri, String displayName) throws Exception {
        if (context == null || uri == null) throw new IllegalArgumentException("Choose a model file first");
        File directory = getDirectory(context);
        String safeName = sanitizeFileName(displayName);
        if (!safeName.toLowerCase(Locale.ROOT).endsWith(".gguf")) safeName += ".gguf";
        File target = uniqueTarget(directory, safeName);
        File part = new File(directory, target.getName() + ".part");
        if (part.exists() && !part.delete()) throw new IllegalStateException("Could not clear previous incomplete model import");
        boolean committed = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(part)) {
                if (input == null) throw new IllegalStateException("Android could not open the selected file");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
                output.getFD().sync();
            }
            if (!hasGgufHeader(part)) {
                throw new IllegalArgumentException("The selected file does not have a valid GGUF header. Choose a complete .gguf model file.");
            }
            if (!part.renameTo(target)) throw new IllegalStateException("Unable to finalize the model file in private storage");
            committed = true;
            return target;
        } finally {
            if (!committed && part.exists()) part.delete();
        }
    }

    private static boolean hasGgufHeader(File file) {
        // GGUF has a 4-byte magic, 32-bit little-endian version, and two 64-bit counts.
        // Requiring the full fixed header and a known format version rejects renamed/empty files.
        if (file == null || !file.isFile() || file.length() < 24L) return false;
        try (FileInputStream input = new FileInputStream(file)) {
            if (input.read() != 'G' || input.read() != 'G' || input.read() != 'U' || input.read() != 'F') return false;
            int b0 = input.read(), b1 = input.read(), b2 = input.read(), b3 = input.read();
            if ((b0 | b1 | b2 | b3) < 0) return false;
            long version = ((long) b0) | ((long) b1 << 8) | ((long) b2 << 16) | ((long) b3 << 24);
            return version >= 1 && version <= 3;
        } catch (Exception e) {
            return false;
        }
    }

    private static String sanitizeFileName(String input) {
        String name = input == null ? "model.gguf" : input.trim();
        name = name.replace('\\', '_').replace('/', '_').replaceAll("[^A-Za-z0-9._() -]", "_");
        name = name.replaceAll("\\.{2,}", ".").trim();
        if (name.isEmpty() || name.equals(".")) name = "model.gguf";
        if (name.length() > 100) name = name.substring(0, 100);
        return name;
    }

    private static File uniqueTarget(File directory, String name) {
        File result = new File(directory, name);
        if (!result.exists()) return result;
        String base = name.toLowerCase(Locale.ROOT).endsWith(".gguf") ? name.substring(0, name.length() - 5) : name;
        for (int i = 2; i < 10000; i++) {
            result = new File(directory, base + " (" + i + ").gguf");
            if (!result.exists()) return result;
        }
        throw new IllegalStateException("Too many model files with the same name");
    }

    public static boolean delete(Context context, String name) {
        if (context == null || name == null || name.contains("/") || name.contains("\\")) return false;
        File target = new File(getDirectory(context), name);
        return target.isFile() && target.delete();
    }
}
