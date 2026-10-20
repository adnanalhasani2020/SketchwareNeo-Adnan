package com.besome.sketch.lib.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Persistent storage for the color picker collection.
 *
 * The collection is intentionally stored outside an individual project so that the
 * same custom colors are available to the color picker in every project:
 * <external-storage>/.sketchware/resources/colors/colors.xml
 *
 * The file uses the familiar Android values XML shape, but is a Sketchware-owned
 * collection file (not the app's generated res/values/colors.xml).
 */
public final class CustomColorStore {
    private static final String TAG = "CustomColorStore";
    private static final String RELATIVE_PATH = ".sketchware/resources/colors/colors.xml";
    private static final String LEGACY_BACKUP_SUFFIX = ".bak";

    private CustomColorStore() {
        // Utility class.
    }

    /** Returns the shared color-collection file. */
    public static File getFile(Context context) {
        return new File(Environment.getExternalStorageDirectory(), RELATIVE_PATH);
    }

    /**
     * Imports legacy comma-separated colors from SharedPreferences into XML.
     * The caller should clear the old preference key only when this returns true.
     * Existing XML colors win in ordering, and legacy colors are merged without
     * duplicates so migration does not discard either collection.
     */
    public static synchronized boolean migrateLegacyColors(Context context, String legacyCsv) {
        File file = getFile(context);
        List<String> existing = readColors(context);
        if (existing == null) {
            Log.e(TAG, "Cannot migrate custom colors because the XML file could not be read safely: " + file);
            return false;
        }

        List<String> legacy = parseLegacyColors(legacyCsv);
        boolean needsWrite = !file.exists() || !legacy.isEmpty();
        if (!needsWrite) {
            return true;
        }

        for (String color : legacy) {
            if (!existing.contains(color)) {
                existing.add(color);
            }
        }
        return writeColors(file, existing);
    }

    /**
     * Returns normalized ARGB colors. A null return means the existing XML could
     * not be read; callers must not treat that as an empty collection and overwrite it.
     */
    public static synchronized List<String> readColors(Context context) {
        File file = getFile(context);
        recoverBackupIfNeeded(file);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (FileInputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            List<String> colors = new ArrayList<>();
            boolean rootSeen = false;

            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (!rootSeen) {
                        if (!"resources".equals(tag)) {
                            throw new XmlPullParserException("Expected <resources> root element");
                        }
                        rootSeen = true;
                    } else if ("color".equals(tag)) {
                        String value = parser.nextText();
                        String normalized = normalizeColor(value);
                        if (normalized != null && !colors.contains(normalized)) {
                            colors.add(normalized);
                        }
                    }
                }
                event = parser.next();
            }

            if (!rootSeen) {
                throw new XmlPullParserException("Missing <resources> root element");
            }
            return colors;
        } catch (IOException | XmlPullParserException | RuntimeException e) {
            Log.e(TAG, "Failed to read custom color collection; original file will be preserved", e);
            return null;
        }
    }

    /** Adds a hex color to the XML collection. Returns false on an I/O/parse failure. */
    public static synchronized boolean addColor(Context context, String color) {
        String normalized = normalizeColor(color);
        if (normalized == null) {
            return false;
        }
        List<String> colors = readColors(context);
        if (colors == null) {
            return false;
        }
        if (colors.contains(normalized)) {
            return true;
        }
        colors.add(0, normalized);
        return writeColors(getFile(context), colors);
    }

    /** Removes exactly one matching color (rather than doing substring replacement). */
    public static synchronized boolean removeColor(Context context, String color) {
        String normalized = normalizeColor(color);
        if (normalized == null) {
            return false;
        }
        List<String> colors = readColors(context);
        if (colors == null) {
            return false;
        }
        boolean removed = colors.remove(normalized);
        return !removed || writeColors(getFile(context), colors);
    }

    /** Clears the collection while leaving other picker preferences (such as custom attrs) untouched. */
    public static synchronized boolean clear(Context context) {
        List<String> colors = readColors(context);
        if (colors == null) {
            return false;
        }
        return writeColors(getFile(context), new ArrayList<>());
    }

    /** Normalizes supported hex strings to #AARRGGBB; invalid strings return null. */
    public static String normalizeColor(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (!candidate.matches("(?i)^#(?:[0-9a-f]{3}|[0-9a-f]{4}|[0-9a-f]{6}|[0-9a-f]{8})$")) {
            return null;
        }
        try {
            return String.format(Locale.US, "#%08X", Color.parseColor(candidate));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> parseLegacyColors(String legacyCsv) {
        List<String> colors = new ArrayList<>();
        if (legacyCsv == null || legacyCsv.trim().isEmpty()) {
            return colors;
        }
        String[] values = legacyCsv.split(",");
        for (String value : values) {
            String normalized = normalizeColor(value);
            if (normalized != null && !colors.contains(normalized)) {
                colors.add(normalized);
            }
        }
        return colors;
    }

    private static boolean writeColors(File target, List<String> colors) {
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory())) {
            Log.e(TAG, "Cannot create color collection directory: " + parent);
            return false;
        }

        File temporary = new File(parent, target.getName() + ".tmp");
        File backup = new File(parent, target.getName() + LEGACY_BACKUP_SUFFIX);
        temporary.delete();

        try (FileOutputStream output = new FileOutputStream(temporary)) {
            XmlSerializer serializer = Xml.newSerializer();
            Writer writer = new OutputStreamWriter(output, "UTF-8");
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startTag(null, "resources");

            int index = 1;
            for (String color : colors) {
                String normalized = normalizeColor(color);
                if (normalized == null) {
                    continue;
                }
                serializer.startTag(null, "color");
                serializer.attribute(null, "name", "custom_color_" + index++);
                serializer.text(normalized);
                serializer.endTag(null, "color");
            }

            serializer.endTag(null, "resources");
            serializer.endDocument();
            serializer.flush();
            writer.flush();
            output.getFD().sync();
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            Log.e(TAG, "Failed to write temporary custom color collection", e);
            temporary.delete();
            return false;
        }

        boolean hadOriginal = target.exists();
        if (hadOriginal) {
            if (backup.exists() && !backup.delete()) {
                Log.e(TAG, "Cannot replace stale color collection backup: " + backup);
                temporary.delete();
                return false;
            }
            if (!target.renameTo(backup)) {
                Log.e(TAG, "Cannot back up existing color collection: " + target);
                temporary.delete();
                return false;
            }
        }

        if (!temporary.renameTo(target)) {
            Log.e(TAG, "Cannot commit custom color collection: " + target);
            temporary.delete();
            if (hadOriginal && backup.exists() && !backup.renameTo(target)) {
                Log.e(TAG, "CRITICAL: color collection backup could not be restored: " + backup);
            }
            return false;
        }

        if (hadOriginal && backup.exists() && !backup.delete()) {
            Log.w(TAG, "New color collection is saved, but backup could not be deleted: " + backup);
        }
        return true;
    }

    private static void recoverBackupIfNeeded(File target) {
        if (target.exists()) {
            return;
        }
        File backup = new File(target.getParentFile(), target.getName() + LEGACY_BACKUP_SUFFIX);
        if (backup.exists() && !backup.renameTo(target)) {
            Log.w(TAG, "Could not restore color collection backup: " + backup);
        }
    }
}
