package com.besome.sketch.editor.manage.view;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import pro.sketchware.utility.FilePathUtil;

/** Read/write implementation for the versioned, Sketchware Neo-only .swnscreen package. */
public final class ScreenPackageManager {
    public static final String FILE_EXTENSION = ".swnscreen";
    public static final String MIME_TYPE = "application/vnd.sketchware-neo.screen+zip";
    private static final String FORMAT_ID = "SketchwareNeoScreenPackage";
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_TOTAL_EXPANDED_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SCREENS = 50;
    private static final int MAX_JAVA_FILES = 200;
    private static final long MAX_JAVA_BYTES = 8L * 1024L * 1024L;
    private static final java.util.regex.Pattern SCREEN_NAME = java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final java.util.regex.Pattern JAVA_RELATIVE_PATH = java.util.regex.Pattern.compile("[A-Za-z0-9_.\\-/]{1,240}\\.java");
    private static final Set<String> ALL_DATA_FIELDS = Set.of("c", "d", "e", "f", "g", "h", "i", "j");
    private static final Set<String> VISUAL_DATA_FIELDS = Set.of("c", "h", "j");
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(Pair.class, new PairAdapter())
            .create();

    private ScreenPackageManager() { }

    public static final class Result {
        public final boolean success;
        public final String message;
        public final File outputFile;
        private Result(boolean success, String message, File outputFile) {
            this.success = success;
            this.message = message;
            this.outputFile = outputFile;
        }
    }

    public static final class ExportOptions {
        public final boolean includeLogic;
        public final boolean includeJavaSources;
        public ExportOptions(boolean includeLogic, boolean includeJavaSources) {
            this.includeLogic = includeLogic;
            this.includeJavaSources = includeJavaSources;
        }
    }

    public static final class ScreenDescriptor {
        public final String originalName;
        public final String typeLabel;
        public final String description;
        public final int index;
        ScreenDescriptor(String originalName, String typeLabel, String description, int index) {
            this.originalName = originalName;
            this.typeLabel = typeLabel;
            this.description = description;
            this.index = index;
        }
    }

    public static final class PackageContent {
        public final boolean includesLogic;
        public final boolean includesJavaSources;
        public final List<ScreenPayload> screens;
        public final List<JavaSource> javaSources;
        PackageContent(boolean includesLogic, boolean includesJavaSources, List<ScreenPayload> screens, List<JavaSource> javaSources) {
            this.includesLogic = includesLogic;
            this.includesJavaSources = includesJavaSources;
            this.screens = Collections.unmodifiableList(new ArrayList<>(screens));
            this.javaSources = Collections.unmodifiableList(new ArrayList<>(javaSources));
        }
        public List<ScreenDescriptor> getDescriptors() {
            ArrayList<ScreenDescriptor> result = new ArrayList<>();
            for (int i = 0; i < screens.size(); i++) {
                ScreenPayload payload = screens.get(i);
                result.add(new ScreenDescriptor(payload.screen.fileName, describeType(payload.screen), "", i));
            }
            return Collections.unmodifiableList(result);
        }
        public int getJavaSourceCount() { return javaSources.size(); }
        public int getScreenCount() { return screens.size(); }
    }

    private static final class ScreenPayload {
        final ProjectFileBean screen;
        final ProjectFileBean drawer;
        final JSONObject data;
        ScreenPayload(ProjectFileBean screen, ProjectFileBean drawer, JSONObject data) {
            this.screen = screen;
            this.drawer = drawer;
            this.data = data;
        }
    }

    private static final class JavaSource {
        final String relativePath;
        final byte[] contents;
        JavaSource(String relativePath, byte[] contents) {
            this.relativePath = relativePath;
            this.contents = contents;
        }
    }

    private static final class PendingMapEntry {
        final Map<Object, Object> target;
        final Object key;
        final Object value;
        final String fieldName;
        PendingMapEntry(Map<Object, Object> target, Object key, Object value, String fieldName) {
            this.target = target;
            this.key = key;
            this.value = value;
            this.fieldName = fieldName;
        }
    }

    private static final class PlannedScreen {
        final ScreenPayload payload;
        final ProjectFileBean target;
        final ProjectFileBean targetDrawer;
        PlannedScreen(ScreenPayload payload, ProjectFileBean target, ProjectFileBean targetDrawer) {
            this.payload = payload;
            this.target = target;
            this.targetDrawer = targetDrawer;
        }
    }

    private static final class JavaWrite {
        final File destination;
        final File staged;
        JavaWrite(File destination, File staged) {
            this.destination = destination;
            this.staged = staged;
        }
    }

    /** Export selected Activity-style screens to one native Sketchware Neo package. */
    public static File createExportFile(Context context, String projectId, List<String> selectedNames, ExportOptions options) throws Exception {
        if (context == null || isBlank(projectId) || selectedNames == null || selectedNames.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one screen to export.");
        }
        if (selectedNames.size() > MAX_SCREENS) throw new IllegalArgumentException("A package can contain at most " + MAX_SCREENS + " screens.");
        ExportOptions safeOptions = options == null ? new ExportOptions(true, false) : options;
        hC registry = jC.b(projectId);
        eC data = jC.a(projectId);
        if (registry == null || registry.b() == null || registry.c() == null || data == null) {
            throw new IOException("The project's screen registry or screen data could not be loaded.");
        }
        Map<String, ProjectFileBean> activityByName = new LinkedHashMap<>();
        for (ProjectFileBean bean : registry.b()) {
            if (bean != null && bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && !isBlank(bean.fileName)) activityByName.put(bean.fileName, bean);
        }
        LinkedHashSet<String> uniqueNames = new LinkedHashSet<>();
        for (String name : selectedNames) {
            if (isBlank(name) || !uniqueNames.add(name)) throw new IllegalArgumentException("The export selection contains an invalid or duplicate screen.");
            ProjectFileBean bean = activityByName.get(name);
            if (bean == null) throw new IOException("Screen '" + name + "' is no longer in this project. Refresh the list and try again.");
        }

        File outputDirectory = new File(context.getCacheDir(), "screen-package-export");
        ensureDirectory(outputDirectory);
        File output = new File(outputDirectory, "screen-export-" + System.currentTimeMillis() + FILE_EXTENSION);
        JSONObject manifest = new JSONObject();
        manifest.put("format", FORMAT_ID);
        manifest.put("schemaVersion", SCHEMA_VERSION);
        manifest.put("includesLogic", safeOptions.includeLogic);
        manifest.put("includesJavaSources", safeOptions.includeJavaSources);
        manifest.put("exportedAtEpochMs", System.currentTimeMillis());
        JSONArray screenDescriptors = new JSONArray();
        int index = 0;
        long expandedBytes = 0L;

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            for (String name : uniqueNames) {
                ProjectFileBean source = activityByName.get(name);
                ProjectFileBean drawer = null;
                if (source.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
                    drawer = findFile(registry.b(), registry.c(), source.getDrawerName(), ProjectFileBean.PROJECT_FILE_TYPE_DRAWER);
                    if (drawer == null) throw new IOException("Screen '" + name + "' uses a drawer, but its drawer data is missing.");
                }
                JSONObject screenJson = new JSONObject();
                screenJson.put("screen", beanToJson(source));
                if (drawer != null) screenJson.put("drawer", beanToJson(drawer));
                else screenJson.put("drawer", JSONObject.NULL);
                screenJson.put("data", collectScreenData(data, source, drawer, safeOptions.includeLogic));
                String entryName = "screens/screen_" + index + ".json";
                byte[] screenBytes = screenJson.toString().getBytes(StandardCharsets.UTF_8);
                expandedBytes += screenBytes.length;
                if (expandedBytes > MAX_TOTAL_EXPANDED_BYTES) throw new IOException("The selected screens exceed the 32 MB expanded package limit.");
                putZipEntry(zip, entryName, screenBytes);

                JSONObject descriptor = beanToJson(source);
                descriptor.put("index", index);
                descriptor.put("entry", entryName);
                screenDescriptors.put(descriptor);
                index++;
            }
            manifest.put("screens", screenDescriptors);

            ArrayList<String> javaEntryNames = new ArrayList<>();
            if (safeOptions.includeJavaSources) {
                List<JavaSource> sources = readJavaSources(projectId);
                for (JavaSource source : sources) {
                    String entryName = "java/" + source.relativePath;
                    expandedBytes += source.contents.length;
                    if (expandedBytes > MAX_TOTAL_EXPANDED_BYTES) throw new IOException("The selected screens and Java sources exceed the 32 MB expanded package limit.");
                    putZipEntry(zip, entryName, source.contents);
                    javaEntryNames.add(source.relativePath);
                }
            }
            JSONArray javaFiles = new JSONArray();
            for (String path : javaEntryNames) javaFiles.put(path);
            manifest.put("javaFiles", javaFiles);
            byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
            expandedBytes += manifestBytes.length;
            if (expandedBytes > MAX_TOTAL_EXPANDED_BYTES) throw new IOException("The selected screens exceed the package size limit.");
            putZipEntry(zip, "manifest.json", manifestBytes);
        } catch (Exception exception) {
            if (output.exists()) output.delete();
            throw exception;
        }
        if (!output.isFile() || output.length() <= 0 || output.length() > MAX_ARCHIVE_BYTES) {
            output.delete();
            throw new IOException("The screen package was empty or exceeded the 64 MB package limit.");
        }
        return output;
    }

    /** Inspect a package without extracting any archive paths to disk. */
    public static PackageContent readPackage(File file) throws Exception {
        if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_ARCHIVE_BYTES) {
            throw new IOException("Choose a valid .swnscreen file smaller than 64 MB.");
        }
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null || manifestEntry.isDirectory()) throw new IOException("This file is not a Sketchware Neo screen package.");
            byte[] manifestBytes = readBounded(zip.getInputStream(manifestEntry), 1024 * 1024);
            JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
            if (!FORMAT_ID.equals(manifest.optString("format"))) throw new IOException("Unsupported screen package format.");
            if (manifest.optInt("schemaVersion", -1) != SCHEMA_VERSION) throw new IOException("This screen package version is not supported by this Sketchware Neo build.");
            boolean includesLogic = manifest.optBoolean("includesLogic", false);
            boolean includesJava = manifest.optBoolean("includesJavaSources", false);
            JSONArray descriptors = manifest.optJSONArray("screens");
            if (descriptors == null || descriptors.length() == 0 || descriptors.length() > MAX_SCREENS) throw new IOException("The package must contain between 1 and " + MAX_SCREENS + " screens.");

            Set<String> allowedEntries = new HashSet<>();
            allowedEntries.add("manifest.json");
            ArrayList<ScreenPayload> screens = new ArrayList<>();
            long expandedBytes = manifestBytes.length;
            for (int i = 0; i < descriptors.length(); i++) {
                JSONObject descriptor = descriptors.optJSONObject(i);
                if (descriptor == null || descriptor.optInt("index", -1) != i) throw new IOException("Invalid screen descriptor at index " + i + ".");
                String expectedEntry = "screens/screen_" + i + ".json";
                if (!expectedEntry.equals(descriptor.optString("entry"))) throw new IOException("Invalid screen data path in package manifest.");
                ZipEntry screenEntry = zip.getEntry(expectedEntry);
                if (screenEntry == null || screenEntry.isDirectory()) throw new IOException("Screen data is missing for item " + (i + 1) + ".");
                allowedEntries.add(expectedEntry);
                byte[] bytes = readBounded(zip.getInputStream(screenEntry), MAX_ENTRY_BYTES);
                expandedBytes += bytes.length;
                if (expandedBytes > MAX_TOTAL_EXPANDED_BYTES) throw new IOException("The package expands beyond the 32 MB safety limit.");
                JSONObject screenJson = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                ProjectFileBean screen = beanFromJson(screenJson.optJSONObject("screen"));
                if (!screen.fileName.equals(descriptor.optString("fileName", ""))) {
                    throw new IOException("Screen metadata does not match its package manifest entry.");
                }
                if (screen.fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) throw new IOException("Only activity-style screens are supported in this package version.");
                ProjectFileBean drawer = screenJson.isNull("drawer") ? null : beanFromJson(screenJson.optJSONObject("drawer"));
                if (drawer != null && drawer.fileType != ProjectFileBean.PROJECT_FILE_TYPE_DRAWER) throw new IOException("Invalid drawer metadata in screen package.");
                JSONObject data = screenJson.optJSONObject("data");
                if (data == null) throw new IOException("Screen data is missing for '" + screen.fileName + "'.");
                validateDataObject(data, includesLogic);
                screens.add(new ScreenPayload(screen, drawer, data));
            }

            LinkedHashSet<String> javaNames = new LinkedHashSet<>();
            ArrayList<JavaSource> javaSources = new ArrayList<>();
            JSONArray javaFiles = manifest.optJSONArray("javaFiles");
            if (javaFiles == null) javaFiles = new JSONArray();
            if (javaFiles.length() > MAX_JAVA_FILES) throw new IOException("The package contains too many Java source files.");
            long javaBytes = 0L;
            for (int i = 0; i < javaFiles.length(); i++) {
                Object rawPath = javaFiles.opt(i);
                if (!(rawPath instanceof String)) throw new IOException("Invalid Java source path in package manifest.");
                String path = (String) rawPath;
                validateJavaRelativePath(path);
                if (!javaNames.add(path)) throw new IOException("Duplicate Java source path in package.");
                String entryName = "java/" + path;
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null || entry.isDirectory()) throw new IOException("A declared Java source file is missing: " + path);
                allowedEntries.add(entryName);
                byte[] bytes = readBounded(zip.getInputStream(entry), MAX_ENTRY_BYTES);
                javaBytes += bytes.length;
                expandedBytes += bytes.length;
                if (javaBytes > MAX_JAVA_BYTES || expandedBytes > MAX_TOTAL_EXPANDED_BYTES) throw new IOException("Java source files exceed the package size limit.");
                javaSources.add(new JavaSource(path, bytes));
            }
            if (!includesJava && !javaSources.isEmpty()) throw new IOException("The manifest omits Java sources but the package contains them.");

            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            Set<String> seenEntries = new HashSet<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!seenEntries.add(name)) throw new IOException("The package contains duplicate ZIP entries.");
                if (!allowedEntries.contains(name)) throw new IOException("Unexpected data entry in screen package: " + safeDisplayName(name));
                if (entry.getSize() > MAX_ENTRY_BYTES && !"manifest.json".equals(name)) throw new IOException("Package entry exceeds the size limit.");
            }
            Set<String> originalNames = new HashSet<>();
            for (ScreenPayload screen : screens) {
                if (!originalNames.add(screen.screen.fileName.toLowerCase(Locale.ROOT))) throw new IOException("The package contains duplicate screen names.");
            }
            return new PackageContent(includesLogic, includesJava, screens, javaSources);
        } catch (JsonParseException e) {
            throw new IOException("The screen package contains invalid JSON.", e);
        }
    }

    /** Import selected screens under user-specified names, preserving their typed native data. */
    public static Result importScreens(Context context, String targetProjectId, PackageContent content,
                                       Map<String, String> requestedNames, boolean includeLogic, boolean includeJavaSources) {
        if (context == null || isBlank(targetProjectId) || content == null || requestedNames == null || requestedNames.isEmpty()) {
            return failure("Choose a target project and at least one screen to import.");
        }
        if (requestedNames.size() > MAX_SCREENS) return failure("Too many screens were selected.");
        if (includeJavaSources && (!content.includesJavaSources || content.javaSources.isEmpty())) {
            includeJavaSources = false;
        }

        hC targetFiles = null;
        eC targetData = null;
        ArrayList<PlannedScreen> plans = new ArrayList<>();
        ArrayList<PendingMapEntry> pending = new ArrayList<>();
        ArrayList<ProjectFileBean> addedBeans = new ArrayList<>();
        ArrayList<File> installedJavaFiles = new ArrayList<>();
        ArrayList<JavaWrite> stagedJavaFiles = new ArrayList<>();
        boolean registryCommitted = false;
        boolean dataCommitted = false;
        File stageRoot = null;
        try {
            targetFiles = jC.b(targetProjectId);
            targetData = jC.a(targetProjectId);
            if (targetFiles == null || targetFiles.b() == null || targetFiles.c() == null || targetData == null) {
                return failure("The target project's screen registry could not be loaded.");
            }
            List<ProjectFileBean> existingActivities = new ArrayList<>(targetFiles.b());
            List<ProjectFileBean> existingOthers = new ArrayList<>(targetFiles.c());
            Map<String, ScreenPayload> payloadByName = new LinkedHashMap<>();
            for (ScreenPayload payload : content.screens) payloadByName.put(payload.screen.fileName, payload);

            Set<String> normalizedNames = new HashSet<>();
            Set<String> newActivityClassNames = new HashSet<>();
            for (Map.Entry<String, String> entry : requestedNames.entrySet()) {
                ScreenPayload payload = payloadByName.get(entry.getKey());
                String targetName = entry.getValue() == null ? "" : entry.getValue().trim();
                if (payload == null) return failure("A selected screen is missing from the package.");
                if (!SCREEN_NAME.matcher(targetName).matches()) return failure("Enter a valid name for '" + entry.getKey() + "' using letters, numbers and underscores.");
                if (!normalizedNames.add(targetName.toLowerCase(Locale.ROOT))) return failure("Two selected screens cannot have the same target name.");
                String className = ProjectFileBean.getActivityName(targetName).toLowerCase(Locale.ROOT);
                if (!newActivityClassNames.add(className)) return failure("The selected names would generate duplicate Java activity classes.");
                if (containsFile(existingActivities, targetName) || containsFile(existingOthers, targetName)) return failure("A project file named '" + targetName + "' already exists. Choose a different name.");
                for (ProjectFileBean other : existingActivities) {
                    if (other != null && other.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                            && ProjectFileBean.getActivityName(other.fileName).equalsIgnoreCase(ProjectFileBean.getActivityName(targetName))) {
                        return failure("The name '" + targetName + "' would generate a duplicate Java activity class.");
                    }
                }
                ProjectFileBean target = copyBean(payload.screen, targetName);
                ProjectFileBean targetDrawer = null;
                if (payload.drawer != null) {
                    String drawerName = ProjectFileBean.getDrawerName(targetName);
                    if (containsFile(existingActivities, drawerName) || containsFile(existingOthers, drawerName)) return failure("The drawer name for '" + targetName + "' is already in use.");
                    targetDrawer = copyBean(payload.drawer, drawerName);
                } else if (payload.screen.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
                    return failure("The package has a screen with a drawer enabled, but the drawer metadata is missing.");
                }
                if (!hasLayout(payload, includeLogic)) return failure("Layout data is missing for '" + payload.screen.fileName + "'. The package was not imported.");
                plans.add(new PlannedScreen(payload, target, targetDrawer));
            }
            if (plans.isEmpty()) return failure("No screens were selected.");

            Map<String, String> keyRenameMap = buildRenameMap(plans);
            String missingCustomView = findMissingCustomView(plans, existingOthers, includeLogic);
            if (missingCustomView != null) return failure("This package uses the custom view '" + missingCustomView + "', which is not in the target project. Import that custom view first. Nothing was changed.");

            // Convert all JSON values to their actual declared map value types before mutating project state.
            Set<String> destinationEntryKeys = new HashSet<>();
            for (PlannedScreen plan : plans) {
                JSONObject dataJson = plan.payload.data;
                JSONArray fieldNames = dataJson.names();
                if (fieldNames == null) return failure("The package has no screen data.");
                for (int f = 0; f < fieldNames.length(); f++) {
                    String fieldName = fieldNames.optString(f, "");
                    if (!ALL_DATA_FIELDS.contains(fieldName)) return failure("The package contains an unsupported screen data field.");
                    if (!includeLogic && !VISUAL_DATA_FIELDS.contains(fieldName)) continue;
                    JSONObject entries = dataJson.optJSONObject(fieldName);
                    if (entries == null) return failure("Invalid data map in package field '" + fieldName + "'.");
                    Field field = findField(targetData.getClass(), fieldName);
                    if (field == null) return failure("This Sketchware Neo build does not expose the required screen data map.");
                    field.setAccessible(true);
                    Object rawMap = field.get(targetData);
                    if (!(rawMap instanceof Map<?, ?>)) return failure("A required screen data map is unavailable.");
                    @SuppressWarnings("unchecked") Map<Object, Object> targetMap = (Map<Object, Object>) rawMap;
                    Type valueType = getMapValueType(field);
                    JSONArray entryNames = entries.names();
                    if (entryNames == null) continue;
                    for (int e = 0; e < entryNames.length(); e++) {
                        String oldKey = entryNames.optString(e, "");
                        String newKey = keyRenameMap.get(oldKey);
                        if (newKey == null) return failure("The package contains a screen data key that is not part of the selected screens.");
                        if (targetMap.containsKey(newKey)) return failure("A screen data entry named '" + newKey + "' already exists. Nothing was changed.");
                        String compositeKey = fieldName + "\u0000" + newKey;
                        if (!destinationEntryKeys.add(compositeKey)) return failure("Two screens in the package overlap in their stored data keys.");
                        JsonElement jsonValue = JsonParser.parseString(entries.get(oldKey).toString());
                        Object value = GSON.fromJson(jsonValue, valueType);
                        if (value == null) return failure("The package contains null screen data where a value is required.");
                        rewriteImportedReferences(value, keyRenameMap);
                        pending.add(new PendingMapEntry(targetMap, newKey, value, fieldName));
                    }
                }
            }

            if (includeJavaSources) {
                stageRoot = new File(context.getCacheDir(), "swnscreen-import-stage-" + System.currentTimeMillis());
                ensureDirectory(stageRoot);
                FilePathUtil paths = new FilePathUtil();
                File targetJavaRoot = new File(paths.getPathJava(targetProjectId)).getCanonicalFile();
                for (JavaSource source : content.javaSources) {
                    validateJavaRelativePath(source.relativePath);
                    File destination = new File(targetJavaRoot, source.relativePath).getCanonicalFile();
                    requireInside(targetJavaRoot, destination);
                    if (destination.exists()) {
                        if (!destination.isFile()) return failure("A Java source path is already used by a non-file in the target project.");
                        if (!constantTimeEquals(sha256(destination), sha256(source.contents))) {
                            return failure("A Java source file named '" + source.relativePath + "' already exists with different contents. Rename it or import without Java sources.");
                        }
                        continue;
                    }
                    File staged = new File(stageRoot, source.relativePath).getCanonicalFile();
                    requireInside(stageRoot, staged);
                    File parent = staged.getParentFile();
                    if (parent == null) return failure("Invalid Java source path.");
                    ensureDirectory(parent);
                    try (FileOutputStream stream = new FileOutputStream(staged)) { stream.write(source.contents); stream.getFD().sync(); }
                    stagedJavaFiles.add(new JavaWrite(destination, staged));
                }
            }

            // Apply only after all screen names, map keys, custom views, and Java paths pass validation.
            for (PendingMapEntry entry : pending) entry.target.put(entry.key, entry.value);
            for (PlannedScreen plan : plans) {
                targetFiles.a(plan.target);
                addedBeans.add(plan.target);
                if (plan.targetDrawer != null) {
                    targetFiles.a(plan.targetDrawer);
                    addedBeans.add(plan.targetDrawer);
                }
            }
            targetFiles.l();
            targetFiles.j();
            targetData.a(targetFiles);
            registryCommitted = true;
            saveScreenData(targetData);
            dataCommitted = true;

            for (JavaWrite javaWrite : stagedJavaFiles) {
                File parent = javaWrite.destination.getParentFile();
                if (parent != null) ensureDirectory(parent);
                if (javaWrite.destination.exists()) throw new IOException("Java source appeared during import; no file was overwritten.");
                try {
                    Files.move(javaWrite.staged.toPath(), javaWrite.destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(javaWrite.staged.toPath(), javaWrite.destination.toPath());
                }
                installedJavaFiles.add(javaWrite.destination);
            }

            return new Result(true, "Imported " + plans.size() + " screen" + (plans.size() == 1 ? "" : "s")
                    + (includeLogic ? " with layout and logic data" : " as visual layouts")
                    + (installedJavaFiles.isEmpty() ? ". You can now open and edit the screen in Sketchware Neo." : " and " + installedJavaFiles.size() + " Java source file(s). Review imported Java code before building."), null);
        } catch (Exception exception) {
            boolean rollbackOk = true;
            for (File installed : installedJavaFiles) {
                try { if (installed.exists() && !installed.delete()) rollbackOk = false; } catch (Exception ignored) { rollbackOk = false; }
            }
            for (PendingMapEntry entry : pending) {
                try { entry.target.remove(entry.key); } catch (Exception ignored) { rollbackOk = false; }
            }
            try {
                if (targetFiles != null && !addedBeans.isEmpty()) {
                    for (ProjectFileBean bean : addedBeans) targetFiles.b(bean.fileType, bean.fileName);
                    targetFiles.l();
                    targetFiles.j();
                    if (targetData != null) {
                        targetData.a(targetFiles);
                        saveScreenData(targetData);
                    }
                } else if (dataCommitted && targetData != null) {
                    saveScreenData(targetData);
                }
            } catch (Exception rollbackException) { rollbackOk = false; }
            if (!rollbackOk || registryCommitted && !dataCommitted) {
                return failure("Import failed and rollback could not be fully confirmed. Reopen the target project and inspect its screens before building. Cause: " + safeMessage(exception));
            }
            return failure("The screen package could not be imported safely: " + safeMessage(exception));
        } finally {
            if (stageRoot != null) deleteRecursivelyNoLinks(stageRoot);
        }
    }

    public static String validateTargetName(String name) {
        return name != null && SCREEN_NAME.matcher(name.trim()).matches() ? null
                : "Use 1–100 letters, numbers or underscores; the first character must be a letter.";
    }

    private static JSONObject collectScreenData(eC data, ProjectFileBean screen, ProjectFileBean drawer, boolean includeLogic) throws Exception {
        JSONObject result = new JSONObject();
        Map<String, String> keyMap = buildKeyMap(screen, screen, drawer, drawer);
        Set<String> includedFields = includeLogic ? ALL_DATA_FIELDS : VISUAL_DATA_FIELDS;
        for (Class<?> type = data.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !includedFields.contains(field.getName())) continue;
                field.setAccessible(true);
                Object value = field.get(data);
                if (!(value instanceof Map<?, ?>)) continue;
                Map<?, ?> sourceMap = (Map<?, ?>) value;
                JSONObject entries = new JSONObject();
                for (String key : keyMap.keySet()) {
                    if (!sourceMap.containsKey(key)) continue;
                    Object dataValue = sourceMap.get(key);
                    JsonElement jsonValue = GSON.toJsonTree(dataValue);
                    entries.put(key, new org.json.JSONTokener(jsonValue.toString()).nextValue());
                }
                if (entries.length() > 0) result.put(field.getName(), entries);
            }
        }
        JSONObject layouts = result.optJSONObject("c");
        if (layouts == null || !layouts.has(screen.getXmlName())) {
            throw new IOException("Layout data for '" + screen.fileName + "' is missing. Nothing was exported.");
        }
        if (drawer != null && !layouts.has(drawer.getXmlName())) {
            throw new IOException("Drawer layout data for '" + screen.fileName + "' is missing. Nothing was exported.");
        }
        return result;
    }

    private static Map<String, String> buildRenameMap(List<PlannedScreen> plans) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (PlannedScreen plan : plans) {
            ProjectFileBean source = plan.payload.screen;
            ProjectFileBean target = plan.target;
            addKey(result, source.fileName, target.fileName);
            addKey(result, source.getXmlName(), target.getXmlName());
            addKey(result, source.getJavaName(), target.getJavaName());
            addKey(result, source.getActivityName(), target.getActivityName());
            if (plan.payload.drawer != null && plan.targetDrawer != null) {
                addKey(result, plan.payload.drawer.fileName, plan.targetDrawer.fileName);
                addKey(result, plan.payload.drawer.getXmlName(), plan.targetDrawer.getXmlName());
                addKey(result, plan.payload.drawer.getDrawersJavaName(), plan.targetDrawer.getDrawersJavaName());
            }
        }
        return result;
    }

    private static JSONObject beanToJson(ProjectFileBean bean) throws Exception {
        JSONObject object = new JSONObject();
        object.put("fileName", bean.fileName);
        object.put("fileType", bean.fileType);
        object.put("orientation", bean.orientation);
        object.put("keyboardSetting", bean.keyboardSetting);
        object.put("options", bean.options);
        object.put("presetName", bean.presetName == null ? "" : bean.presetName);
        object.put("theme", bean.theme);
        return object;
    }

    private static ProjectFileBean beanFromJson(JSONObject object) throws Exception {
        if (object == null) throw new IOException("Screen metadata is missing.");
        String fileName = object.optString("fileName", "");
        int fileType = object.optInt("fileType", -1);
        if (fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && fileType != ProjectFileBean.PROJECT_FILE_TYPE_DRAWER) {
            throw new IOException("Unsupported screen type in package metadata.");
        }
        if (fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && !SCREEN_NAME.matcher(fileName).matches()) {
            throw new IOException("Invalid activity screen name in package metadata.");
        }
        if (fileType == ProjectFileBean.PROJECT_FILE_TYPE_DRAWER
                && (!fileName.startsWith("_drawer_") || !SCREEN_NAME.matcher(fileName.substring("_drawer_".length())).matches())) {
            throw new IOException("Invalid drawer name in package metadata.");
        }
        int orientation = object.optInt("orientation", ProjectFileBean.ORIENTATION_PORTRAIT);
        int keyboard = object.optInt("keyboardSetting", ProjectFileBean.KEYBOARD_STATE_UNSPECIFIED);
        int options = object.optInt("options", 0);
        int theme = object.optInt("theme", ProjectFileBean.THEME_NONE);
        if (orientation < ProjectFileBean.ORIENTATION_PORTRAIT || orientation > ProjectFileBean.ORIENTATION_BOTH) {
            throw new IOException("Invalid orientation in package metadata.");
        }
        if (keyboard != ProjectFileBean.KEYBOARD_STATE_UNSPECIFIED
                && keyboard != ProjectFileBean.KEYBOARD_STATE_VISIBLE
                && keyboard != ProjectFileBean.KEYBOARD_STATE_HIDDEN) {
            throw new IOException("Invalid keyboard setting in package metadata.");
        }
        if ((options & ~ProjectFileBean.OPTION_ACTIVITY_MASK) != 0) {
            throw new IOException("Invalid screen options in package metadata.");
        }
        if (theme != ProjectFileBean.THEME_NONE && theme != ProjectFileBean.THEME_DEFAULT
                && theme != ProjectFileBean.THEME_NOACTIONBAR && theme != ProjectFileBean.THEME_FULLSCREEN) {
            throw new IOException("Invalid theme setting in package metadata.");
        }
        if (fileType == ProjectFileBean.PROJECT_FILE_TYPE_DRAWER && options != 0) options = 0;
        ProjectFileBean bean = new ProjectFileBean(fileType, fileName, orientation, keyboard, options);
        String preset = object.optString("presetName", "");
        bean.presetName = preset.length() > 200 ? preset.substring(0, 200) : preset;
        bean.theme = theme;
        return bean;
    }

    private static void validateDataObject(JSONObject data, boolean includesLogic) throws Exception {
        JSONArray fields = data.names();
        if (fields == null || fields.length() == 0) throw new IOException("The package contains empty screen data.");
        Set<String> permitted = includesLogic ? ALL_DATA_FIELDS : VISUAL_DATA_FIELDS;
        for (int i = 0; i < fields.length(); i++) {
            String field = fields.optString(i, "");
            if (!permitted.contains(field)) throw new IOException("Unexpected field in screen data: " + field);
            JSONObject entries = data.optJSONObject(field);
            if (entries == null || entries.length() > 1000) throw new IOException("Invalid stored data map in the screen package.");
            JSONArray keys = entries.names();
            if (keys == null) continue;
            for (int k = 0; k < keys.length(); k++) {
                String key = keys.optString(k, "");
                if (key.isBlank() || key.length() > 250 || key.contains("..") || key.contains("/") || key.contains("\\")) {
                    throw new IOException("Invalid screen data key in the package.");
                }
            }
        }
    }

    private static boolean hasLayout(ScreenPayload payload, boolean includeLogic) {
        try {
            JSONObject data = payload.data;
            JSONObject layouts = data.optJSONObject("c");
            if (layouts == null) return false;
            Object raw = layouts.opt(payload.screen.getXmlName());
            if (raw == null || raw == JSONObject.NULL) return false;
            JsonElement element = JsonParser.parseString(raw.toString());
            return element.isJsonArray();
        } catch (Exception ignored) { return false; }
    }

    private static String findMissingCustomView(List<PlannedScreen> plans, List<ProjectFileBean> targetOthers, boolean includeLogic) {
        try {
            Set<String> customViewNames = new HashSet<>();
            for (ProjectFileBean bean : targetOthers) {
                if (bean != null && bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW && bean.fileName != null) customViewNames.add(bean.fileName.toLowerCase(Locale.ROOT));
            }
            for (PlannedScreen plan : plans) {
                JSONObject layoutData = plan.payload.data.optJSONObject("c");
                if (layoutData == null) continue;
                inspectCustomViews(layoutData.opt(plan.payload.screen.getXmlName()), customViewNames);
                if (plan.payload.drawer != null) inspectCustomViews(layoutData.opt(plan.payload.drawer.getXmlName()), customViewNames);
            }
        } catch (MissingCustomView e) { return e.name; }
        catch (Exception ignored) { return "(unreadable custom view reference)"; }
        return null;
    }

    private static void inspectCustomViews(Object raw, Set<String> existingNames) throws Exception {
        if (raw == null || raw == JSONObject.NULL) return;
        JsonElement element = JsonParser.parseString(raw.toString());
        Type listType = new TypeToken<ArrayList<ViewBean>>() { }.getType();
        ArrayList<ViewBean> views = GSON.fromJson(element, listType);
        if (views == null) return;
        for (ViewBean view : views) {
            if (view == null || !(view.type == 9 || view.type == 10 || view.type == 25 || view.type == 48 || view.type == 31)) continue;
            String name = view.customView;
            if (name == null || name.isBlank() || "none".equalsIgnoreCase(name)) continue;
            if (!existingNames.contains(name.toLowerCase(Locale.ROOT))) throw new MissingCustomView(name);
        }
    }

    private static void rewriteImportedReferences(Object value, Map<String, String> keyRenameMap) {
        if (value == null) return;
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        rewriteImportedReferencesRecursive(value, keyRenameMap, visited);
    }

    private static void rewriteImportedReferencesRecursive(Object value, Map<String, String> keyRenameMap, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || visited.put(value, Boolean.TRUE) != null) return;
        if (value instanceof BlockBean) {
            BlockBean block = (BlockBean) value;
            if ("intentSetScreen".equals(block.opCode) && block.parameters != null && block.parameters.size() > 1) {
                String mapped = keyRenameMap.get(block.parameters.get(1));
                if (mapped != null) block.parameters.set(1, mapped);
            }
            return;
        }
        if (value instanceof ViewBean) return;
        if (value instanceof Map<?, ?>) {
            for (Object item : ((Map<?, ?>) value).values()) rewriteImportedReferencesRecursive(item, keyRenameMap, visited);
        } else if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) rewriteImportedReferencesRecursive(item, keyRenameMap, visited);
        } else if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) rewriteImportedReferencesRecursive(java.lang.reflect.Array.get(value, i), keyRenameMap, visited);
        }
    }

    private static List<JavaSource> readJavaSources(String projectId) throws Exception {
        File root = new File(new FilePathUtil().getPathJava(projectId)).getCanonicalFile();
        ArrayList<JavaSource> output = new ArrayList<>();
        if (!root.isDirectory()) return output;
        long[] total = {0L};
        collectJavaSources(root, root, output, total);
        if (output.size() > MAX_JAVA_FILES || total[0] > MAX_JAVA_BYTES) throw new IOException("Custom Java sources exceed the package safety limits.");
        return output;
    }

    private static void collectJavaSources(File root, File current, List<JavaSource> output, long[] total) throws Exception {
        File[] children = current.listFiles();
        if (children == null) throw new IOException("Could not read the project's custom Java source directory.");
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) continue;
            File canonical = child.getCanonicalFile();
            requireInside(root, canonical);
            if (child.isDirectory()) {
                collectJavaSources(root, child, output, total);
            } else if (child.isFile() && child.getName().endsWith(".java")
                    && !child.getName().equals("SketchApplication.java") && !child.getName().equals("DebugActivity.java")) {
                String relative = root.toPath().relativize(child.toPath()).toString().replace(File.separatorChar, '/');
                validateJavaRelativePath(relative);
                if (output.size() >= MAX_JAVA_FILES) throw new IOException("The project has too many custom Java source files to export.");
                if (child.length() > MAX_ENTRY_BYTES) throw new IOException("Java source file is too large: " + relative);
                byte[] bytes = readBounded(new FileInputStream(child), MAX_ENTRY_BYTES);
                total[0] += bytes.length;
                if (total[0] > MAX_JAVA_BYTES) throw new IOException("Custom Java sources exceed 8 MB.");
                output.add(new JavaSource(relative, bytes));
            }
        }
    }

    private static void validateJavaRelativePath(String path) throws IOException {
        if (path == null || !JAVA_RELATIVE_PATH.matcher(path).matches() || path.startsWith("/") || path.startsWith(".")
                || path.contains("..") || path.contains("\\") || path.contains(":") || path.contains("\u0000")) {
            throw new IOException("Unsafe Java source path in package.");
        }
        for (String part : path.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) throw new IOException("Unsafe Java source path in package.");
        }
    }

    private static void putZipEntry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length > MAX_ENTRY_BYTES) throw new IOException("A screen package entry exceeds 8 MB.");
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] readBounded(InputStream input, long limit) throws Exception {
        if (input == null) throw new IOException("The package entry could not be opened.");
        try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if ((long) out.size() + read > limit) throw new IOException("A screen package entry exceeds the size limit.");
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static Map<String, String> buildKeyMap(ProjectFileBean source, ProjectFileBean target, ProjectFileBean sourceDrawer, ProjectFileBean targetDrawer) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        addKey(result, source.fileName, target.fileName);
        addKey(result, source.getXmlName(), target.getXmlName());
        addKey(result, source.getJavaName(), target.getJavaName());
        addKey(result, source.getActivityName(), target.getActivityName());
        if (sourceDrawer != null && targetDrawer != null) {
            addKey(result, sourceDrawer.fileName, targetDrawer.fileName);
            addKey(result, sourceDrawer.getXmlName(), targetDrawer.getXmlName());
            addKey(result, sourceDrawer.getDrawersJavaName(), targetDrawer.getDrawersJavaName());
        }
        return result;
    }

    private static void addKey(Map<String, String> keys, String oldKey, String newKey) {
        if (!isBlank(oldKey) && !isBlank(newKey)) keys.put(oldKey, newKey);
    }

    private static ProjectFileBean copyBean(ProjectFileBean source, String name) {
        ProjectFileBean copy = new ProjectFileBean(source.fileType, name, source.orientation, source.keyboardSetting, source.options);
        copy.presetName = source.presetName;
        copy.theme = source.theme;
        return copy;
    }

    private static ProjectFileBean findFile(List<ProjectFileBean> activities, List<ProjectFileBean> otherFiles, String name, int type) {
        if (activities != null) for (ProjectFileBean bean : activities) if (bean != null && bean.fileType == type && name.equals(bean.fileName)) return bean;
        if (otherFiles != null) for (ProjectFileBean bean : otherFiles) if (bean != null && bean.fileType == type && name.equals(bean.fileName)) return bean;
        return null;
    }

    private static boolean containsFile(List<ProjectFileBean> files, String name) {
        if (files == null || name == null) return false;
        for (ProjectFileBean bean : files) if (bean != null && bean.fileName != null && bean.fileName.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static String describeType(ProjectFileBean bean) {
        if (bean == null) return "Activity";
        String name = bean.fileName == null ? "" : bean.fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith("_bottomdialog_fragment")) return "Bottom Sheet DialogFragment";
        if (name.endsWith("_dialog_fragment")) return "DialogFragment";
        if (name.endsWith("_fragment")) return "Fragment";
        return "Activity";
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        }
        return null;
    }

    private static Type getMapValueType(Field field) throws IOException {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType)) throw new IOException("The project data map has no declared value type.");
        Type[] parameters = ((ParameterizedType) generic).getActualTypeArguments();
        if (parameters.length != 2) throw new IOException("The project data map type is unsupported.");
        return parameters[1];
    }

    private static void saveScreenData(eC data) throws Exception {
        for (Class<?> type = data.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Method method = type.getDeclaredMethod("k");
                method.setAccessible(true);
                method.invoke(data);
                return;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException("The screen data save method is unavailable.");
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory.isDirectory()) return;
        if (!directory.mkdirs() && !directory.isDirectory()) throw new IOException("Could not create the required directory.");
    }

    private static void requireInside(File root, File child) throws IOException {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (!childPath.equals(rootPath) && !childPath.startsWith(rootPath + File.separator)) throw new IOException("A path resolved outside its expected directory.");
    }

    private static void deleteRecursivelyNoLinks(File file) {
        if (file == null || !file.exists() || Files.isSymbolicLink(file.toPath())) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursivelyNoLinks(child);
        }
        file.delete();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static String safeDisplayName(String name) {
        if (name == null) return "(unknown)";
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }

    private static Result failure(String message) { return new Result(false, message, null); }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? (error == null ? "Unknown error" : error.getClass().getSimpleName()) : (message.length() > 400 ? message.substring(0, 400) + "…" : message);
    }

    private static final class MissingCustomView extends Exception {
        final String name;
        MissingCustomView(String name) { this.name = name; }
    }

    private static final class PairAdapter implements JsonSerializer<Pair<?, ?>>, JsonDeserializer<Pair<?, ?>> {
        @Override public JsonElement serialize(Pair<?, ?> src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject object = new JsonObject();
            object.add("first", src.first == null ? null : context.serialize(src.first));
            object.add("second", src.second == null ? null : context.serialize(src.second));
            return object;
        }
        @Override public Pair<?, ?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || !json.isJsonObject()) return null;
            Type firstType = Object.class;
            Type secondType = Object.class;
            if (typeOfT instanceof ParameterizedType) {
                Type[] args = ((ParameterizedType) typeOfT).getActualTypeArguments();
                if (args.length > 0) firstType = args[0];
                if (args.length > 1) secondType = args[1];
            }
            JsonObject object = json.getAsJsonObject();
            Object first = object.has("first") && !object.get("first").isJsonNull() ? context.deserialize(object.get("first"), firstType) : null;
            Object second = object.has("second") && !object.get("second").isJsonNull() ? context.deserialize(object.get("second"), secondType) : null;
            return new Pair<>(first, second);
        }
    }
}
