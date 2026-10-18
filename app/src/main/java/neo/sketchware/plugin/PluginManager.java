package neo.sketchware.plugin;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import dalvik.system.DexClassLoader;
import dev.aldi.sayuti.block.ExtraBlockFile;
import neo.sketchware.plugin.marketplace.PluginMarketplaceRepository;
import neo.sketchware.plugin.marketplace.PluginVersionUtils;
import pro.sketchware.BuildConfig;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Runtime extension host for Sketchware Neo. Plugins execute inside the app process. */
public final class PluginManager {

    private static final String TAG = "PluginManager";
    private static final int CURRENT_API_VERSION = 1;
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;
    private static final long MAX_PLUGIN_FILE_BYTES = 100L * 1024L * 1024L;
    private static final int MAX_METADATA_LIST_ITEMS = 100;
    private static final Pattern PLUGIN_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Pattern COLOR_PATTERN = Pattern.compile("#(?:[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})");

    private static final Map<String, NeoPluginInterface> loadedPlugins = new ConcurrentHashMap<>();
    private static final Map<String, NeoPluginContext> pluginContexts = new ConcurrentHashMap<>();
    private static final Map<String, File> pluginFiles = new ConcurrentHashMap<>();
    private static final Map<String, String> disabledPluginIds = new ConcurrentHashMap<>();
    private static final List<String> lastLoadErrors = new CopyOnWriteArrayList<>();
    private static volatile boolean initialized = false;

    private PluginManager() {}

    public static synchronized void init(Context appContext) {
        if (appContext == null) return;
        if (initialized) return;
        Context context = appContext.getApplicationContext();
        loadDisabledState(context);
        initialized = true;
        scan(context);
    }

    public static synchronized void rescan(Context appContext) {
        if (appContext == null) return;
        Context context = appContext.getApplicationContext();
        if (!initialized) {
            loadDisabledState(context);
            initialized = true;
        }
        scan(context);
    }

    private static void scan(Context appContext) {
        lastLoadErrors.clear();
        File installDir = getInstallDir(appContext);
        if (!ensureDirectory(installDir)) {
            lastLoadErrors.add("Plugin directory is not available: " + installDir.getAbsolutePath());
            return;
        }

        recoverInterruptedInstalls(installDir);
        File[] found = installDir.listFiles();
        if (found == null) return;
        Arrays.sort(found, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        Map<String, List<PluginCandidate>> candidatesById = new LinkedHashMap<>();
        for (File file : found) {
            if (!isPluginArchive(file)) continue;
            JSONObject manifest = readPluginManifest(file);
            if (manifest == null) {
                lastLoadErrors.add(file.getName() + ": missing or invalid root plugin.json");
                continue;
            }
            try {
                validateManifest(manifest);
                String id = manifest.getString("pluginId");
                candidatesById.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new PluginCandidate(file, manifest));
            } catch (Exception e) {
                lastLoadErrors.add(file.getName() + ": " + safeMessage(e));
            }
        }

        // Duplicate IDs are ambiguous. Do not arbitrarily load one of the archives.
        Set<String> ambiguousIds = new HashSet<>();
        for (Map.Entry<String, List<PluginCandidate>> entry : candidatesById.entrySet()) {
            if (entry.getValue().size() > 1) {
                ambiguousIds.add(entry.getKey());
                lastLoadErrors.add("Plugin ID '" + entry.getKey() + "' exists in more than one archive. Keep only one copy.");
            }
        }
        for (String id : ambiguousIds) {
            unloadOnly(id);
            deleteBlockContributions(id);
        }

        // Drop registrations for files removed or replaced outside the manager.
        for (Map.Entry<String, File> entry : new ArrayList<>(pluginFiles.entrySet())) {
            List<PluginCandidate> candidates = candidatesById.get(entry.getKey());
            boolean stillSame = candidates != null && candidates.size() == 1
                    && sameFile(entry.getValue(), candidates.get(0).file)
                    && !ambiguousIds.contains(entry.getKey());
            if (!stillSame) {
                String id = entry.getKey();
                unloadOnly(id);
                deleteBlockContributions(id);
            }
        }

        List<PluginCandidate> pending = new ArrayList<>();
        for (Map.Entry<String, List<PluginCandidate>> entry : candidatesById.entrySet()) {
            if (ambiguousIds.contains(entry.getKey())) continue;
            PluginCandidate candidate = entry.getValue().get(0);
            if (disabledPluginIds.containsKey(entry.getKey())) continue;
            if (loadedPlugins.containsKey(entry.getKey()) && sameFile(pluginFiles.get(entry.getKey()), candidate.file)) continue;
            pending.add(candidate);
        }

        // Try dependencies repeatedly so a plugin does not depend on directory ordering.
        boolean progressed;
        do {
            progressed = false;
            for (int i = 0; i < pending.size();) {
                PluginCandidate candidate = pending.get(i);
                String id = candidate.manifest.optString("pluginId", "unknown");
                if (!dependenciesPresent(candidate.manifest)) {
                    i++;
                    continue;
                }
                try {
                    loadPlugin(appContext, candidate.file, candidate.manifest);
                    pending.remove(i);
                    progressed = true;
                } catch (Throwable t) {
                    lastLoadErrors.add(candidate.file.getName() + ": " + safeMessage(t));
                    Log.e(TAG, "Failed to load plugin " + id, t);
                    pending.remove(i);
                    // Do not spin on a permanently failed plugin.
                }
            }
        } while (progressed && !pending.isEmpty());

        for (PluginCandidate candidate : pending) {
            String id = candidate.manifest.optString("pluginId", candidate.file.getName());
            lastLoadErrors.add(candidate.file.getName() + ": required plugin dependencies are missing, disabled, or form a cycle (" + id + ")");
        }
    }

    /** Install a trusted plugin archive. Existing plugin IDs are atomically replaced with rollback on failure. */
    public static synchronized PluginInstallResult installAndLoad(Context appContext, File sourceArchive) {
        return installAndLoad(appContext, sourceArchive, null, -1, null);
    }

    /** Same as installAndLoad, optionally requiring catalog identity/version metadata to match the archive. */
    public static synchronized PluginInstallResult installAndLoad(Context appContext, File sourceArchive, String expectedPluginId) {
        return installAndLoad(appContext, sourceArchive, expectedPluginId, -1, null);
    }

    public static synchronized PluginInstallResult installAndLoad(Context appContext, File sourceArchive, String expectedPluginId, int expectedApiVersion, String expectedVersion) {
        if (appContext == null) return new PluginInstallResult(false, null, "Application context is unavailable");
        Context context = appContext.getApplicationContext();
        if (sourceArchive == null || !sourceArchive.isFile() || !sourceArchive.canRead()) {
            return new PluginInstallResult(false, null, "Selected plugin file is missing or cannot be read");
        }
        if (!isAllowedArchiveName(sourceArchive.getName())) {
            return new PluginInstallResult(false, null, "Only .jar and .apk plugin files are supported");
        }
        if (sourceArchive.length() <= 0 || sourceArchive.length() > MAX_PLUGIN_FILE_BYTES) {
            return new PluginInstallResult(false, null, "Plugin archive is empty or exceeds the 100 MB safety limit");
        }

        File installDir = getInstallDir(context);
        if (!ensureDirectory(installDir)) return new PluginInstallResult(false, null, "Cannot access the plugin directory");
        recoverInterruptedInstalls(installDir);

        JSONObject manifest = readPluginManifest(sourceArchive);
        if (manifest == null) return new PluginInstallResult(false, null, "Invalid plugin archive: root plugin.json was not found or is too large");
        String pluginId;
        try {
            validateManifest(manifest);
            pluginId = manifest.getString("pluginId");
            if (expectedPluginId != null && !expectedPluginId.equals(pluginId)) {
                return new PluginInstallResult(false, null, "Downloaded plugin ID does not match the marketplace listing");
            }
            if (expectedApiVersion > 0 && manifest.optInt("apiVersion", 1) != expectedApiVersion) {
                return new PluginInstallResult(false, null, "Plugin API version does not match the marketplace listing");
            }
            String archiveVersion = manifest.optString("version", "").trim();
            if (expectedVersion != null && !expectedVersion.isBlank() && !archiveVersion.isBlank()
                    && PluginVersionUtils.compareVersions(archiveVersion, expectedVersion) != 0) {
                return new PluginInstallResult(false, null, "Plugin version does not match the marketplace listing");
            }
        } catch (Exception e) {
            return new PluginInstallResult(false, null, safeMessage(e));
        }

        // The source may itself be an already installed plugin; do not overwrite it while reading it.
        File oldFile = findInstalledFile(context, pluginId);
        if (oldFile != null && sameFile(sourceArchive, oldFile)) {
            if (loadedPlugins.containsKey(pluginId)) {
                return new PluginInstallResult(true, pluginId, "Plugin is already installed and loaded");
            }
            if (isEnabled(pluginId) && dependenciesPresent(manifest)) {
                try {
                    loadPlugin(context, oldFile, manifest);
                    return new PluginInstallResult(true, pluginId, null);
                } catch (Throwable t) {
                    return new PluginInstallResult(false, pluginId, safeMessage(t));
                }
            }
            return new PluginInstallResult(true, pluginId, "Plugin is installed but disabled or waiting for dependencies");
        }

        File staged = new File(installDir, "." + pluginId + ".installing");
        File target = oldFile;
        if (target == null) {
            String extension = sourceArchive.getName().toLowerCase(Locale.ROOT).endsWith(".apk") ? ".apk" : ".jar";
            target = new File(installDir, pluginId + extension);
            if (target.exists()) {
                JSONObject otherManifest = readPluginManifest(target);
                if (otherManifest == null || !pluginId.equals(otherManifest.optString("pluginId", null))) {
                    return new PluginInstallResult(false, null, "The destination file already exists and belongs to another or invalid plugin");
                }
            }
        }

        File backup = new File(installDir, "." + pluginId + ".backup");
        deleteFileQuietly(staged);
        deleteFileQuietly(backup);
        try {
            copyBounded(sourceArchive, staged, MAX_PLUGIN_FILE_BYTES);
            JSONObject stagedManifest = readPluginManifest(staged);
            if (stagedManifest == null || !pluginId.equals(stagedManifest.optString("pluginId", null))) {
                throw new IllegalStateException("Staged archive validation failed");
            }
            validateManifest(stagedManifest);
            if (expectedApiVersion > 0 && stagedManifest.optInt("apiVersion", 1) != expectedApiVersion) {
                throw new IllegalStateException("Staged plugin API version does not match the marketplace listing");
            }
            String stagedVersion = stagedManifest.optString("version", "").trim();
            if (expectedVersion != null && !expectedVersion.isBlank() && !stagedVersion.isBlank()
                    && PluginVersionUtils.compareVersions(stagedVersion, expectedVersion) != 0) {
                throw new IllegalStateException("Staged plugin version does not match the marketplace listing");
            }

            boolean wasLoaded = loadedPlugins.containsKey(pluginId);
            boolean wasEnabled = isEnabled(pluginId);
            File originalPath = oldFile;
            JSONObject previousManifest = oldFile == null ? null : readPluginManifest(oldFile);

            if (wasLoaded) unloadOnly(pluginId);
            if (oldFile != null && oldFile.exists()) {
                if (!oldFile.renameTo(backup)) {
                    if (wasLoaded && wasEnabled) reloadAfterRollback(context, originalPath, previousManifest);
                    throw new IllegalStateException("Could not safely replace the existing plugin file");
                }
            }

            if (!staged.renameTo(target)) {
                if (backup.exists()) backup.renameTo(oldFile);
                if (wasLoaded && wasEnabled && oldFile != null) reloadAfterRollback(context, oldFile, previousManifest);
                throw new IllegalStateException("Could not finalize the plugin installation");
            }

            // Keep the plugin disabled when updating a disabled installation.
            try {
                if (wasEnabled || oldFile == null) {
                    if (dependenciesPresent(stagedManifest)) {
                        loadPlugin(context, target, stagedManifest);
                    } else {
                        throw new IllegalStateException("Required plugin dependencies are missing or disabled");
                    }
                } else {
                    unloadOnly(pluginId);
                    pluginFiles.put(pluginId, target);
                }
            } catch (Throwable loadError) {
                deleteFileQuietly(target);
                if (backup.exists() && !backup.renameTo(oldFile == null ? target : oldFile)) {
                    lastLoadErrors.add(pluginId + ": update failed and backup could not be restored at " + backup.getAbsolutePath());
                } else if (backup.exists()) {
                    lastLoadErrors.add(pluginId + ": previous plugin file could not be restored");
                }
                if (wasLoaded && wasEnabled && oldFile != null && oldFile.exists()) {
                    try {
                        JSONObject restored = readPluginManifest(oldFile);
                        if (restored != null) loadPlugin(context, oldFile, restored);
                    } catch (Throwable rollbackError) {
                        lastLoadErrors.add(pluginId + ": rollback reload failed: " + safeMessage(rollbackError));
                    }
                }
                throw new IllegalStateException("Plugin failed to load; the previous installation was restored when possible. " + safeMessage(loadError), loadError);
            }

            // The backup is no longer needed after the new plugin has been accepted.
            deleteFileQuietly(backup);
            if (oldFile != null && !sameFile(oldFile, target)) deleteFileQuietly(oldFile);
            if (expectedVersion == null || expectedVersion.isBlank()) {
                PluginMarketplaceRepository.clearInstalledVersion(context, pluginId);
            }
            return new PluginInstallResult(true, pluginId, null);
        } catch (Exception e) {
            deleteFileQuietly(staged);
            String error = safeMessage(e);
            lastLoadErrors.add(0, sourceArchive.getName() + ": " + error);
            Log.e(TAG, "Plugin install failed", e);
            return new PluginInstallResult(false, pluginId, error);
        }
    }

    public static synchronized boolean deletePlugin(Context appContext, String pluginId) {
        if (appContext == null || !isValidPluginId(pluginId)) return false;
        unloadOnly(pluginId);
        File installDir = getInstallDir(appContext);
        File[] installed = installDir.listFiles();
        boolean deleted = installed != null;
        if (installed != null) {
            for (File file : installed) {
                if (!isPluginArchive(file)) continue;
                JSONObject manifest = readPluginManifest(file);
                if (manifest != null && pluginId.equals(manifest.optString("pluginId", null)) && file.exists() && !file.delete()) {
                    deleted = false;
                }
            }
        }
        if (deleted) {
            pluginFiles.remove(pluginId);
            disabledPluginIds.remove(pluginId);
            PluginMarketplaceRepository.clearInstalledVersion(appContext, pluginId);
            deleteRecursively(new File(appContext.getFilesDir(), "plugins/" + pluginId));
            deleteRecursively(new File(appContext.getCacheDir(), "plugin-dex/" + pluginId));
        } else {
            // If an archive could not be removed, keep it disabled and preserve plugin data for retry.
            disabledPluginIds.put(pluginId, "true");
            lastLoadErrors.add(0, pluginId + ": archive removal failed; plugin remains disabled for safety");
        }
        persistDisabledState(appContext);
        deleteBlockContributions(pluginId);
        return deleted;
    }

    private static void unloadOnly(String pluginId) {
        if (pluginId == null) return;
        NeoPluginInterface plugin = loadedPlugins.remove(pluginId);
        NeoPluginContext context = pluginContexts.remove(pluginId);
        if (context != null) context.clearEditorActions();
        if (plugin != null) {
            try {
                plugin.onUnload();
            } catch (Throwable t) {
                Log.e(TAG, "Error unloading plugin " + pluginId, t);
            }
        }
        if (plugin == null) pluginFiles.remove(pluginId);
    }

    public static synchronized void setEnabled(Context appContext, String pluginId, boolean enabled) {
        if (appContext == null || !isValidPluginId(pluginId)) return;
        if (enabled) {
            disabledPluginIds.remove(pluginId);
            persistDisabledState(appContext);
            if (!loadedPlugins.containsKey(pluginId)) {
                File file = findInstalledFile(appContext, pluginId);
                if (file != null) {
                    try {
                        JSONObject manifest = readPluginManifest(file);
                        if (manifest == null) throw new IllegalStateException("Invalid plugin manifest");
                        validateManifest(manifest);
                        if (!dependenciesPresent(manifest)) throw new IllegalStateException("Required plugin dependencies are missing or disabled");
                        loadPlugin(appContext, file, manifest);
                    } catch (Throwable t) {
                        lastLoadErrors.add(0, pluginId + ": " + safeMessage(t));
                    }
                } else {
                    lastLoadErrors.add(0, pluginId + ": installed plugin file was not found");
                }
            }
        } else {
            disabledPluginIds.put(pluginId, "true");
            persistDisabledState(appContext);
            unloadOnly(pluginId);
            deleteBlockContributions(pluginId);
        }
    }

    public static boolean isEnabled(String pluginId) {
        return isValidPluginId(pluginId) && !disabledPluginIds.containsKey(pluginId);
    }

    public static boolean isLoaded(String pluginId) {
        return pluginId != null && loadedPlugins.containsKey(pluginId);
    }

    private static File findInstalledFile(Context appContext, String pluginId) {
        if (!isValidPluginId(pluginId) || appContext == null) return null;
        File file = pluginFiles.get(pluginId);
        if (file != null && file.isFile() && file.exists()) return file;
        File installDir = getInstallDir(appContext);
        File[] files = installDir.listFiles();
        if (files == null) return null;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File candidate : files) {
            if (!isPluginArchive(candidate)) continue;
            JSONObject manifest = readPluginManifest(candidate);
            if (manifest != null && pluginId.equals(manifest.optString("pluginId", null))) return candidate;
        }
        return null;
    }

    public static File getInstallDir(Context appContext) {
        // Keep the existing public install path for backward compatibility with manually copied plugins.
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/plugins");
    }

    private static File getStateFile(Context appContext) {
        return new File(getInstallDir(appContext), ".state.json");
    }

    private static void loadDisabledState(Context appContext) {
        disabledPluginIds.clear();
        File stateFile = getStateFile(appContext);
        File backupFile = new File(stateFile.getParentFile(), ".state.json.bak");
        File[] candidates = new File[]{stateFile, backupFile};
        for (File candidate : candidates) {
            if (!candidate.isFile()) continue;
            try {
                JSONArray array = new JSONArray(readBoundedFile(candidate, 256 * 1024));
                disabledPluginIds.clear();
                for (int i = 0; i < array.length(); i++) {
                    String id = array.optString(i, "");
                    if (isValidPluginId(id)) disabledPluginIds.put(id, "true");
                }
                if (candidate.equals(backupFile) && !stateFile.exists()) backupFile.renameTo(stateFile);
                else if (candidate.equals(stateFile)) deleteFileQuietly(backupFile);
                return;
            } catch (Exception e) {
                Log.w(TAG, "Unable to read plugin state from " + candidate.getName(), e);
            }
        }
        disabledPluginIds.clear();
    }

    private static void persistDisabledState(Context appContext) {
        if (appContext == null) return;
        File stateFile = getStateFile(appContext);
        File parent = stateFile.getParentFile();
        File temp = new File(parent, ".state.json.tmp");
        File backup = new File(parent, ".state.json.bak");
        File previous = new File(parent, ".state.json.previous");
        try {
            if (!ensureDirectory(parent)) return;
            if (!stateFile.exists() && backup.exists() && !backup.renameTo(stateFile)) {
                throw new IllegalStateException("Cannot restore previous plugin state backup");
            }
            JSONArray array = new JSONArray();
            List<String> ids = new ArrayList<>(disabledPluginIds.keySet());
            Collections.sort(ids);
            for (String id : ids) if (isValidPluginId(id)) array.put(id);
            writeUtf8(temp, array.toString());
            deleteFileQuietly(previous);
            if (stateFile.exists() && !stateFile.renameTo(previous)) {
                throw new IllegalStateException("Cannot safely stage current plugin state");
            }
            if (!temp.renameTo(stateFile)) {
                if (previous.exists() && !previous.renameTo(stateFile)) {
                    Log.e(TAG, "Plugin state commit failed and the previous state remains at " + previous.getAbsolutePath());
                }
                throw new IllegalStateException("Cannot commit plugin state file");
            }
            deleteFileQuietly(previous);
            deleteFileQuietly(backup);
        } catch (Exception e) {
            Log.e(TAG, "Unable to persist plugin state", e);
            deleteFileQuietly(temp);
            if (!stateFile.exists()) {
                if (backup.exists()) backup.renameTo(stateFile);
                else if (previous.exists()) previous.renameTo(stateFile);
            }
        }
    }

    public static List<String> getLastLoadErrors() {
        return new ArrayList<>(lastLoadErrors);
    }

    private static String loadPlugin(Context appContext, File pluginFile, JSONObject manifest) throws Exception {
        validateManifest(manifest);
        String pluginId = manifest.getString("pluginId");
        String entryClassName = manifest.getString("entryClass");
        int apiVersion = manifest.optInt("apiVersion", 1);
        if (apiVersion > CURRENT_API_VERSION) throw new IllegalStateException("Plugin requires host API v" + apiVersion + ", current API is v" + CURRENT_API_VERSION);
        if (loadedPlugins.containsKey(pluginId)) throw new IllegalStateException("Plugin " + pluginId + " is already loaded");
        if (!dependenciesPresent(manifest)) throw new IllegalStateException("Required plugin dependencies are missing or disabled");

        File dexOutputDir = new File(new File(appContext.getApplicationContext().getCacheDir(), "plugin-dex"), pluginId);
        if (!ensureDirectory(dexOutputDir)) throw new IllegalStateException("Cannot create plugin dex directory");
        File optimizedDir = new File(dexOutputDir, "optimized");
        if (!ensureDirectory(optimizedDir)) throw new IllegalStateException("Cannot create plugin optimized directory");

        DexClassLoader loader = new DexClassLoader(
                pluginFile.getAbsolutePath(),
                optimizedDir.getAbsolutePath(),
                null,
                NeoPluginInterface.class.getClassLoader()
        );
        Class<?> clazz = loader.loadClass(entryClassName);
        if (!NeoPluginInterface.class.isAssignableFrom(clazz)) {
            throw new SecurityException("Entry class does not implement NeoPluginInterface: " + entryClassName);
        }

        Object instance = clazz.getDeclaredConstructor().newInstance();
        NeoPluginInterface plugin = (NeoPluginInterface) instance;
        if (!pluginId.equals(plugin.getPluginId())) throw new SecurityException("plugin.json pluginId does not match NeoPluginInterface#getPluginId()");
        int runtimeApiVersion = plugin.getPluginApiVersion();
        if (runtimeApiVersion < 1 || runtimeApiVersion > CURRENT_API_VERSION) {
            throw new IllegalStateException("Plugin entry point reports unsupported API version " + runtimeApiVersion);
        }

        Set<String> permissions = parsePermissions(manifest.optJSONArray("permissions"));
        NeoPluginContext pluginContext = new NeoPluginContext(appContext, pluginId, permissions);
        boolean loadCompleted = false;
        try {
            plugin.onLoad(pluginContext);
            loadCompleted = true;
            loadedPlugins.put(pluginId, plugin);
            pluginContexts.put(pluginId, pluginContext);
            pluginFiles.put(pluginId, pluginFile);
            try {
                writeBlockContributions(pluginId, plugin.getBlockContributions());
            } catch (Throwable contributionError) {
                Log.e(TAG, "Invalid block contributions from plugin " + pluginId, contributionError);
                lastLoadErrors.add(pluginId + ": block contributions could not be applied: " + safeMessage(contributionError));
                deleteBlockContributions(pluginId);
            }
            return pluginId;
        } finally {
            if (!loadCompleted) {
                pluginContext.clearEditorActions();
                try {
                    plugin.onUnload();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static JSONObject readPluginManifest(File pluginFile) {
        if (pluginFile == null || isSymbolicLink(pluginFile) || !pluginFile.isFile() || !pluginFile.canRead() || pluginFile.length() <= 0
                || pluginFile.length() > MAX_PLUGIN_FILE_BYTES) return null;
        try (ZipFile zip = new ZipFile(pluginFile)) {
            ZipEntry entry = zip.getEntry("plugin.json");
            if (entry == null || entry.isDirectory() || (entry.getSize() > MAX_MANIFEST_BYTES)) return null;
            try (InputStream is = zip.getInputStream(entry)) {
                byte[] bytes = readLimited(is, MAX_MANIFEST_BYTES);
                return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "Invalid plugin manifest in " + pluginFile.getName(), e);
            return null;
        }
    }

    private static void validateManifest(JSONObject manifest) {
        if (manifest == null) throw new IllegalArgumentException("Missing plugin manifest");
        String pluginId = manifest.optString("pluginId", "").trim();
        if (!isValidPluginId(pluginId)) throw new IllegalArgumentException("plugin.json has an invalid pluginId");
        String entryClass = manifest.optString("entryClass", "").trim();
        if (entryClass.length() > 512 || !CLASS_NAME_PATTERN.matcher(entryClass).matches()) {
            throw new IllegalArgumentException("plugin.json has an invalid entryClass");
        }
        int apiVersion = 1;
        if (manifest.has("apiVersion") && !manifest.isNull("apiVersion")) {
            Object rawApiVersion = manifest.opt("apiVersion");
            if (!(rawApiVersion instanceof Number)) throw new IllegalArgumentException("apiVersion must be an integer");
            double numericApiVersion = ((Number) rawApiVersion).doubleValue();
            if (!Double.isFinite(numericApiVersion) || numericApiVersion < 1
                    || numericApiVersion != Math.rint(numericApiVersion) || numericApiVersion > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("apiVersion must be a positive integer");
            }
            apiVersion = (int) numericApiVersion;
        }
        if (apiVersion > CURRENT_API_VERSION) throw new IllegalArgumentException("Plugin requires a newer Sketchware Neo plugin API (v" + apiVersion + ")");
        validateString(manifest, "minHostVersion", 80);
        String minHostVersion = manifest.optString("minHostVersion", "").trim();
        if (!minHostVersion.isEmpty() && !PluginVersionUtils.isValidVersion(minHostVersion)) {
            throw new IllegalArgumentException("Invalid minHostVersion value");
        }
        if (!minHostVersion.isEmpty() && PluginVersionUtils.compareVersions(BuildConfig.VERSION_NAME, minHostVersion) < 0) {
            throw new IllegalArgumentException("Plugin requires Sketchware Neo " + minHostVersion + " or later");
        }
        validateString(manifest, "name", 160);
        validateString(manifest, "developer", 160);
        validateString(manifest, "version", 80);
        validateString(manifest, "description", 12000);
        validateString(manifest, "category", 80);
        validateString(manifest, "license", 120);
        validateString(manifest, "homepageUrl", 2048);
        validateString(manifest, "documentationUrl", 2048);
        validateString(manifest, "changelogUrl", 2048);
        validateString(manifest, "sourceCodeUrl", 2048);
        validateString(manifest, "iconUrl", 2048);
        validateUrlIfPresent(manifest, "homepageUrl");
        validateUrlIfPresent(manifest, "documentationUrl");
        validateUrlIfPresent(manifest, "changelogUrl");
        validateUrlIfPresent(manifest, "sourceCodeUrl");
        validateUrlIfPresent(manifest, "iconUrl");
        validateStringList(manifest, "screenshots");
        validateStringList(manifest, "features");
        validateStringList(manifest, "components");
        validateStringList(manifest, "blocks");
        JSONArray permissions = manifest.optJSONArray("permissions");
        if (manifest.has("permissions") && !manifest.isNull("permissions") && permissions == null) {
            throw new IllegalArgumentException("Plugin permissions must be an array of strings");
        }
        parsePermissions(permissions);
        JSONArray deps = manifest.optJSONArray("dependencies");
        if (manifest.has("dependencies") && !manifest.isNull("dependencies") && deps == null) {
            throw new IllegalArgumentException("Plugin dependencies must be an array of plugin IDs");
        }
        if (deps != null) {
            if (deps.length() > 64) throw new IllegalArgumentException("Too many plugin dependencies");
            for (int i = 0; i < deps.length(); i++) {
                Object rawDependency = deps.opt(i);
                if (!(rawDependency instanceof String)) throw new IllegalArgumentException("Plugin dependency IDs must be strings");
                String dependency = ((String) rawDependency).trim();
                if (!isValidPluginId(dependency) || pluginId.equals(dependency)) {
                    throw new IllegalArgumentException("Invalid plugin dependency at index " + i);
                }
            }
        }
        if (manifest.has("rating") && !manifest.isNull("rating")) {
            Object value = manifest.opt("rating");
            if (!(value instanceof Number)) throw new IllegalArgumentException("rating must be numeric");
            double rating = ((Number) value).doubleValue();
            if (!Double.isFinite(rating) || rating < 0 || rating > 5) throw new IllegalArgumentException("rating must be between 0 and 5");
        }
        if (manifest.has("downloads") && !manifest.isNull("downloads")) {
            Object value = manifest.opt("downloads");
            if (!(value instanceof Number)) throw new IllegalArgumentException("downloads must be numeric");
            double downloads = ((Number) value).doubleValue();
            if (!Double.isFinite(downloads) || downloads < 0 || downloads != Math.rint(downloads) || downloads > Long.MAX_VALUE) {
                throw new IllegalArgumentException("downloads must be a non-negative integer");
            }
        }
    }

    private static void validateString(JSONObject json, String key, int maxLength) {
        if (!json.has(key) || json.isNull(key)) return;
        Object value = json.opt(key);
        if (!(value instanceof String) || ((String) value).length() > maxLength) {
            throw new IllegalArgumentException("Invalid '" + key + "' metadata");
        }
    }

    private static void validateUrlIfPresent(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) return;
        String value = json.optString(key, "").trim();
        if (value.isEmpty()) return;
        try {
            java.net.URI uri = new java.net.URI(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Invalid URL in '" + key + "'");
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL in '" + key + "'");
        }
    }

    private static void validateStringList(JSONObject json, String key) {
        JSONArray array = json.optJSONArray(key);
        if (json.has(key) && !json.isNull(key) && array == null) {
            throw new IllegalArgumentException("'" + key + "' metadata must be an array of strings");
        }
        if (array == null) return;
        if (array.length() > MAX_METADATA_LIST_ITEMS) throw new IllegalArgumentException("Too many values in '" + key + "'");
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (!(raw instanceof String)) throw new IllegalArgumentException("Metadata values in '" + key + "' must be strings");
            String value = (String) raw;
            if (value.length() > 2048) throw new IllegalArgumentException("Metadata item in '" + key + "' is too long");
        }
    }

    private static Set<String> parsePermissions(JSONArray values) {
        Set<String> permissions = new HashSet<>();
        if (values == null) return permissions;
        if (values.length() > 64) throw new IllegalArgumentException("Too many plugin permissions");
        for (int i = 0; i < values.length(); i++) {
            Object rawPermission = values.opt(i);
            if (!(rawPermission instanceof String)) throw new IllegalArgumentException("Plugin permission names must be strings");
            String permission = ((String) rawPermission).trim();
            if (!permission.matches("[a-zA-Z][a-zA-Z0-9._-]{0,63}")) throw new IllegalArgumentException("Invalid plugin permission");
            permissions.add(permission);
        }
        return permissions;
    }

    private static boolean dependenciesPresent(JSONObject manifest) {
        JSONArray dependencies = manifest.optJSONArray("dependencies");
        if (dependencies == null) return true;
        for (int i = 0; i < dependencies.length(); i++) {
            String id = dependencies.optString(i, "");
            if (!loadedPlugins.containsKey(id) || disabledPluginIds.containsKey(id)) return false;
        }
        return true;
    }

    private static void writeBlockContributions(String pluginId, List<NeoBlockContribution> contributions) throws Exception {
        if (!isValidPluginId(pluginId)) throw new SecurityException("Invalid plugin ID for block contribution directory");
        File root = ExtraBlockFile.PLUGIN_BLOCKS_DIR;
        if (!ensureDirectory(root)) throw new IllegalStateException("Cannot create plugin block directory");
        File pluginBlocksDir = new File(root, pluginId);
        if (!isContainedChild(root, pluginBlocksDir)) {
            throw new SecurityException("Plugin block directory escapes its root");
        }
        if (contributions == null || contributions.isEmpty()) {
            deleteRecursively(pluginBlocksDir);
            return;
        }
        if (!ensureDirectory(pluginBlocksDir)) throw new IllegalStateException("Cannot create plugin block contribution directory");

        JSONArray paletteArray = new JSONArray();
        List<Object> allBlocks = new ArrayList<>();
        int categoryCount = 0;
        for (NeoBlockContribution contribution : contributions) {
            if (contribution == null) continue;
            if (++categoryCount > 100) throw new IllegalArgumentException("Plugin contributed too many block categories");
            String categoryName = contribution.categoryName() == null ? "" : contribution.categoryName().trim();
            String color = contribution.categoryColorHex() == null ? "" : contribution.categoryColorHex().trim();
            if (categoryName.isEmpty() || categoryName.length() > 120) throw new IllegalArgumentException("Invalid plugin block category name");
            if (!COLOR_PATTERN.matcher(color).matches()) throw new IllegalArgumentException("Invalid plugin block category color");
            JSONObject paletteEntry = new JSONObject();
            paletteEntry.put("name", categoryName);
            paletteEntry.put("color", color);
            paletteArray.put(paletteEntry);
            if (contribution.blocks() != null) {
                if (allBlocks.size() + contribution.blocks().size() > 5000) throw new IllegalArgumentException("Plugin contributed too many blocks");
                allBlocks.addAll(contribution.blocks());
            }
        }
        writeUtf8Atomic(new File(pluginBlocksDir, "palette.json"), paletteArray.toString());
        writeUtf8Atomic(new File(pluginBlocksDir, "block.json"), new Gson().toJson(allBlocks));
    }

    private static void deleteBlockContributions(String pluginId) {
        if (!isValidPluginId(pluginId)) return;
        File root = ExtraBlockFile.PLUGIN_BLOCKS_DIR;
        File pluginDirectory = new File(root, pluginId);
        if (!isContainedChild(root, pluginDirectory)) {
            Log.w(TAG, "Refusing to delete plugin block directory outside its root: " + pluginDirectory);
            return;
        }
        deleteRecursively(pluginDirectory);
    }

    private static void writeUtf8Atomic(File destination, String content) throws Exception {
        File parent = destination.getParentFile();
        if (!ensureDirectory(parent)) throw new IllegalStateException("Cannot create destination directory");
        File temp = new File(parent, "." + destination.getName() + ".tmp");
        File backup = new File(parent, "." + destination.getName() + ".bak");
        if (!destination.exists() && backup.exists() && !backup.renameTo(destination)) {
            throw new IllegalStateException("Cannot recover previous " + destination.getName());
        }
        if (temp.exists() && !temp.delete()) throw new IllegalStateException("Cannot clear temporary " + destination.getName());
        if (destination.exists() && backup.exists()) deleteFileQuietly(backup);
        writeUtf8(temp, content);
        if (destination.exists() && !destination.renameTo(backup)) {
            deleteFileQuietly(temp);
            throw new IllegalStateException("Cannot back up " + destination.getName());
        }
        if (!temp.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination);
            deleteFileQuietly(temp);
            throw new IllegalStateException("Cannot commit " + destination.getName());
        }
        deleteFileQuietly(backup);
    }

    private static void writeUtf8(File file, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.getFD().sync();
        }
    }

    private static String readBoundedFile(File file, int maxBytes) throws Exception {
        try (InputStream in = new FileInputStream(file)) {
            return new String(readLimited(in, maxBytes), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > maxBytes) throw new IllegalArgumentException("Content exceeds allowed size");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void copyBounded(File source, File destination, long maxBytes) throws Exception {
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException("Plugin archive exceeds the 100 MB safety limit");
                out.write(buffer, 0, read);
            }
            out.flush();
            out.getFD().sync();
        }
    }

    /** Restores a previous archive left in the hidden backup name by an interrupted update. */
    private static void recoverInterruptedInstalls(File installDir) {
        File[] files = installDir.listFiles();
        if (files == null) return;
        for (File backup : files) {
            String name = backup.getName();
            if (isSymbolicLink(backup) || !backup.isFile() || !name.startsWith(".") || !name.endsWith(".backup") || name.length() <= 8) continue;
            String id = name.substring(1, name.length() - ".backup".length());
            if (!isValidPluginId(id)) continue;
            JSONObject oldManifest = readPluginManifest(backup);
            if (oldManifest == null || !id.equals(oldManifest.optString("pluginId", null))) {
                lastLoadErrors.add("Cannot recover interrupted plugin update for " + id + ": backup manifest is invalid");
                continue;
            }

            File jarTarget = new File(installDir, id + ".jar");
            File apkTarget = new File(installDir, id + ".apk");
            File existing = findMatchingInstalledArchive(installDir, id);
            if (existing != null) {
                // The new archive was committed; this is only the old, now-obsolete backup.
                deleteFileQuietly(backup);
                deleteFileQuietly(new File(installDir, "." + id + ".installing"));
                continue;
            }
            if (jarTarget.exists() || apkTarget.exists()) {
                lastLoadErrors.add("Cannot recover interrupted plugin update for " + id + ": target path is occupied by another or invalid archive");
                continue;
            }

            boolean apk = containsArchiveEntry(backup, "AndroidManifest.xml");
            File target = apk ? apkTarget : jarTarget;
            if (backup.renameTo(target)) {
                lastLoadErrors.add("Recovered the previous installation of plugin " + id + " after an interrupted update");
                deleteFileQuietly(new File(installDir, "." + id + ".installing"));
            } else {
                lastLoadErrors.add("Cannot restore previous plugin archive for " + id + "; backup was kept at " + backup.getAbsolutePath());
            }
        }
    }

    private static File findMatchingInstalledArchive(File installDir, String pluginId) {
        File[] candidates = installDir == null ? null : installDir.listFiles();
        if (candidates == null) return null;
        for (File candidate : candidates) {
            if (!isPluginArchive(candidate)) continue;
            JSONObject manifest = readPluginManifest(candidate);
            if (manifest != null && pluginId.equals(manifest.optString("pluginId", null))) return candidate;
        }
        return null;
    }

    private static boolean containsArchiveEntry(File archive, String entryName) {
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry entry = zip.getEntry(entryName);
            return entry != null && !entry.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean ensureDirectory(File dir) {
        if (dir == null) return false;
        if (dir.isDirectory()) return true;
        if (dir.exists()) return false;
        return dir.mkdirs() || dir.isDirectory();
    }

    private static boolean isAllowedArchiveName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".apk");
    }

    private static boolean isPluginArchive(File file) {
        return file != null && !isSymbolicLink(file) && file.isFile()
                && isAllowedArchiveName(file.getName()) && !file.getName().startsWith(".");
    }

    private static boolean isValidPluginId(String id) {
        return id != null && PLUGIN_ID_PATTERN.matcher(id).matches() && !id.equals(".") && !id.equals("..");
    }

    private static boolean sameFile(File first, File second) {
        if (first == null || second == null) return false;
        try {
            return first.getCanonicalFile().equals(second.getCanonicalFile());
        } catch (Exception e) {
            return first.getAbsolutePath().equals(second.getAbsolutePath());
        }
    }

    private static void deleteFileQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) Log.w(TAG, "Unable to delete file: " + file.getAbsolutePath());
    }

    private static boolean isContainedChild(File root, File child) {
        if (root == null || child == null) return false;
        try {
            String rootPath = root.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            return childPath.startsWith(rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSymbolicLink(File target) {
        if (target == null) return false;
        try {
            return Files.isSymbolicLink(target.toPath());
        } catch (Exception e) {
            // Fail closed when the path type cannot be inspected.
            return true;
        }
    }

    private static void deleteRecursively(File target) {
        if (target == null || !target.exists()) return;
        if (isSymbolicLink(target)) {
            if (!target.delete()) Log.w(TAG, "Unable to remove symbolic link: " + target.getAbsolutePath());
            return;
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (!target.delete()) Log.w(TAG, "Unable to delete: " + target.getAbsolutePath());
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "Unknown error";
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) return throwable.getClass().getSimpleName();
        if (message.length() > 500) message = message.substring(0, 500) + "…";
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    private static void reloadAfterRollback(Context context, File file, JSONObject manifest) {
        try {
            if (manifest != null && file != null && file.exists() && dependenciesPresent(manifest)) loadPlugin(context, file, manifest);
        } catch (Throwable t) {
            lastLoadErrors.add("Unable to reload previous plugin: " + safeMessage(t));
        }
    }

    public static List<NeoDrawerEntry> getDrawerEntries() {
        List<NeoDrawerEntry> entries = new ArrayList<>();
        for (NeoPluginInterface plugin : loadedPlugins.values()) {
            try {
                List<NeoDrawerEntry> pluginEntries = plugin.getDrawerEntries();
                if (pluginEntries != null) entries.addAll(pluginEntries);
            } catch (Throwable t) {
                Log.e(TAG, "Plugin drawer entry failed", t);
            }
        }
        return entries;
    }

    public static void notifyBuildError(String scId, String errorText) {
        NeoBuildErrorInfo errorInfo = NeoBuildErrorInfo.parse(errorText);
        for (NeoPluginInterface plugin : loadedPlugins.values()) {
            try { plugin.onBuildError(scId, errorText); } catch (Throwable t) { Log.e(TAG, "Plugin build-error callback failed", t); }
            try { plugin.onBuildError(scId, errorInfo); } catch (Throwable t) { Log.e(TAG, "Plugin diagnostic callback failed", t); }
        }
        publishEvent(new NeoEvent.CompileError(scId, errorText, errorInfo));
    }

    public static List<NeoEditorAction> getAllEditorActions() {
        List<NeoEditorAction> actions = new ArrayList<>();
        for (NeoPluginContext context : pluginContexts.values()) {
            try { actions.addAll(context.getEditorActions()); } catch (Throwable t) { Log.e(TAG, "Cannot collect editor actions", t); }
        }
        return actions;
    }

    public static List<NeoEditorAction> getEditorActions(String pluginId) {
        NeoPluginContext context = pluginContexts.get(pluginId);
        return context == null ? Collections.emptyList() : new ArrayList<>(context.getEditorActions());
    }

    public static List<NeoBlockConverter> getAllBlockConverters() {
        List<NeoBlockConverter> converters = new ArrayList<>();
        for (NeoPluginInterface plugin : loadedPlugins.values()) {
            try {
                NeoBlockConverter converter = plugin.getBlockConverter();
                if (converter != null) converters.add(converter);
            } catch (Throwable t) { Log.e(TAG, "Cannot collect block converter", t); }
        }
        return converters;
    }

    public static void notifyBuildSuccess(String scId) {
        for (NeoPluginInterface plugin : loadedPlugins.values()) {
            try { plugin.onBuildSuccess(scId); } catch (Throwable t) { Log.e(TAG, "Plugin build-success callback failed", t); }
        }
        publishEvent(new NeoEvent.BuildFinished(scId, true));
        publishEvent(new NeoEvent.ProjectBuilt(scId));
    }

    public static void publishEvent(NeoEvent event) {
        if (event == null) return;
        for (NeoPluginInterface plugin : loadedPlugins.values()) {
            try { plugin.onEvent(event); }
            catch (Throwable t) { Log.e(TAG, "Plugin " + safePluginId(plugin) + " threw while handling " + event.getClass().getSimpleName(), t); }
        }
    }

    private static String safePluginId(NeoPluginInterface plugin) {
        try { return plugin.getPluginId(); } catch (Throwable ignored) { return "<unknown>"; }
    }

    public static void notifyProjectOpened(String scId) { publishEvent(new NeoEvent.ProjectOpened(scId)); }
    public static void notifyProjectClosed(String scId) { publishEvent(new NeoEvent.ProjectClosed(scId)); }
    public static void notifyProjectSaved(String scId) { publishEvent(new NeoEvent.ProjectSaved(scId)); }
    public static void notifyBuildStarted(String scId) { publishEvent(new NeoEvent.BuildStarted(scId)); }
    public static void notifyLibraryImported(String scId, String libraryName) { publishEvent(new NeoEvent.LibraryImported(scId, libraryName)); }

    public static List<PluginInfo> getInstalledPluginInfos(Context appContext) {
        Map<String, PluginInfo> result = new HashMap<>();
        File installDir = getInstallDir(appContext);
        File[] files = installDir.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                if (!isPluginArchive(file)) continue;
                JSONObject manifest = readPluginManifest(file);
                if (manifest == null) continue;
                String pluginId = manifest.optString("pluginId", "");
                if (!isValidPluginId(pluginId) || result.containsKey(pluginId)) continue;
                result.put(pluginId, toPluginInfo(file, manifest));
            }
        }
        // An active registration can be surfaced even if external storage enumeration temporarily fails.
        for (Map.Entry<String, NeoPluginInterface> entry : loadedPlugins.entrySet()) {
            String id = entry.getKey();
            if (!result.containsKey(id)) {
                File file = pluginFiles.get(id);
                JSONObject manifest = readPluginManifest(file);
                if (manifest != null) result.put(id, toPluginInfo(file, manifest));
            }
        }
        return new ArrayList<>(result.values());
    }

    public static PluginInfo getInstalledPluginInfo(Context appContext, String pluginId) {
        if (!isValidPluginId(pluginId)) return null;
        File file = findInstalledFile(appContext, pluginId);
        JSONObject manifest = readPluginManifest(file);
        return manifest == null ? null : toPluginInfo(file, manifest);
    }

    private static PluginInfo toPluginInfo(File file, JSONObject manifest) {
        String id = manifest.optString("pluginId", "");
        double rating = manifest.has("rating") ? manifest.optDouble("rating", -1) : -1;
        long downloads = manifest.has("downloads") ? manifest.optLong("downloads", -1) : -1;
        return new PluginInfo(
                id,
                manifest.optInt("apiVersion", 1),
                file == null ? "" : file.getName(),
                !disabledPluginIds.containsKey(id),
                firstNonBlank(manifest.optString("name", ""), id),
                manifest.optString("developer", ""),
                manifest.optString("version", ""),
                manifest.optString("description", ""),
                manifest.optString("category", "Other"),
                manifest.optString("minHostVersion", ""),
                manifest.optString("homepageUrl", ""),
                manifest.optString("documentationUrl", ""),
                manifest.optString("changelogUrl", ""),
                manifest.optString("sourceCodeUrl", ""),
                manifest.optString("license", ""),
                manifest.optString("iconUrl", ""),
                rating,
                downloads,
                jsonArrayToStrings(manifest.optJSONArray("screenshots")),
                jsonArrayToStrings(manifest.optJSONArray("features")),
                jsonArrayToStrings(manifest.optJSONArray("components")),
                jsonArrayToStrings(manifest.optJSONArray("blocks")),
                jsonArrayToStrings(manifest.optJSONArray("dependencies")),
                loadedPlugins.containsKey(id),
                file == null ? 0 : file.length()
        );
    }

    private static List<String> jsonArrayToStrings(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public static List<String> getLoadedPluginIds() { return new ArrayList<>(loadedPlugins.keySet()); }

    public static int getCurrentApiVersion() { return CURRENT_API_VERSION; }

    private record PluginCandidate(File file, JSONObject manifest) {}

    /** Optional metadata fields are ignored when absent, preserving the original three-field plugin.json format. */
    public record PluginInfo(
            String pluginId,
            int apiVersion,
            String fileName,
            boolean enabled,
            String name,
            String developer,
            String version,
            String description,
            String category,
            String minHostVersion,
            String homepageUrl,
            String documentationUrl,
            String changelogUrl,
            String sourceCodeUrl,
            String license,
            String iconUrl,
            double rating,
            long downloads,
            List<String> screenshots,
            List<String> features,
            List<String> components,
            List<String> blocks,
            List<String> dependencies,
            boolean loaded,
            long fileSizeBytes
    ) {
        public PluginInfo {
            screenshots = screenshots == null ? List.of() : List.copyOf(screenshots);
            features = features == null ? List.of() : List.copyOf(features);
            components = components == null ? List.of() : List.copyOf(components);
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        }
    }

    public record PluginInstallResult(boolean success, String pluginId, String error) {}
}
