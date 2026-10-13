package com.besome.sketch.editor.manage.library;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.beans.AdTestDeviceBean;
import com.besome.sketch.beans.AdUnitBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.admob.AdmobActivity;
import com.besome.sketch.editor.manage.library.admob.ManageAdmobActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import a.a.a.iC;
import mod.jbk.editor.manage.library.LibrarySettingsImporter;
import pro.sketchware.activities.editor.gradle.GradleSyncEngine;
import pro.sketchware.activities.editor.gradle.UnityAdsDependencyManager;
import pro.sketchware.databinding.ActivityManageAdNetworksBinding;
import pro.sketchware.databinding.DialogUnityPlacementBinding;
import pro.sketchware.databinding.ItemUnityPlacementBinding;

public class ManageAdNetworksActivity extends BaseAppCompatActivity {
    private static final int REQUEST_ADMOB_SETTINGS = 8501;

    private String scId;
    private ProjectLibraryBean networkBean;
    private boolean restoring;
    private boolean unityDependencySyncInProgress;
    
    private final ExecutorService unityDependencyExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean unityDependencySyncCancelled = new AtomicBoolean(false);
    
    @Nullable
    private ProgressDialog unityDependencyProgressDialog;
    
    private ActivityManageAdNetworksBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManageAdNetworksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scId = savedInstanceState == null ? getIntent().getStringExtra("sc_id") : savedInstanceState.getString("sc_id");
        networkBean = savedInstanceState == null ? getIntent().getParcelableExtra("admob") : savedInstanceState.getParcelable("admob");
        
        if (networkBean == null) networkBean = new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_ADMOB);
        if (networkBean.configurations == null) networkBean.configurations = new HashMap<>();
        
        if (!networkBean.configurations.containsKey(ProjectLibraryBean.CONFIG_ADMOB_ENABLED)) {
            networkBean.setConfiguration(ProjectLibraryBean.CONFIG_ADMOB_ENABLED, networkBean.isAdMobEnabled());
        }
        
        setupListeners();
        renderState();
    }

    private void setupListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> finishWithResult());

        binding.btnImport.setOnClickListener(v -> showImportDialog());
        binding.btnConfigureAdmob.setOnClickListener(v -> openAdMobSettings());
        binding.btnAdmobConsole.setOnClickListener(v -> openUrl("https://apps.admob.com/v2/home"));
        binding.btnUnityDashboard.setOnClickListener(v -> openUrl("https://dashboard.unity3d.com/"));
        binding.btnAddPlacement.setOnClickListener(v -> showPlacementDialog(-1, "", ""));
        binding.btnSave.setOnClickListener(v -> finishWithResult());

        binding.switchAdmob.setOnCheckedChangeListener((button, checked) -> {
            if (restoring) return;
            networkBean.setConfiguration(ProjectLibraryBean.CONFIG_ADMOB_ENABLED, checked);
            updateOverallEnabledState();
            renderState();
        });

        binding.switchUnity.setOnCheckedChangeListener((button, checked) -> {
            if (restoring) return;
            networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_ENABLED, checked);
            updateOverallEnabledState();
            renderState();
        });

        binding.switchTestMode.setOnCheckedChangeListener((button, checked) -> {
            if (!restoring) networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_TEST_MODE, checked);
        });

        binding.etGameId.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveGameId(false);
        });
    }

    private String emptyLabel(String value) {
        return TextUtils.isEmpty(value) ? "Not configured" : value;
    }

    private void renderState() {
        restoring = true;
        boolean admobEnabled = networkBean.isAdMobEnabled();
        boolean unityEnabled = networkBean.isUnityAdsEnabled();
        
        binding.switchAdmob.setChecked(admobEnabled);
        binding.switchUnity.setChecked(unityEnabled);
        binding.switchTestMode.setChecked(Boolean.parseBoolean(networkBean.getConfigurationString(ProjectLibraryBean.CONFIG_UNITY_TEST_MODE, "true")));
        
        binding.tvAdmobState.setText(admobEnabled ? "Enabled · App ID, ad units and test devices can be edited" : "Disabled · Enable to include AdMob in the project");
        
        int unitCount = networkBean.adUnits == null ? 0 : networkBean.adUnits.size();
        if (unitCount == 0) {
            if (!TextUtils.isEmpty(networkBean.reserved1)) unitCount++;
            if (!TextUtils.isEmpty(networkBean.reserved2)) unitCount++;
            if (!TextUtils.isEmpty(networkBean.reserved3)) unitCount++;
        }
        int deviceCount = networkBean.testDevices == null ? 0 : networkBean.testDevices.size();
        
        binding.tvAdmobCounts.setText("Ad Units (" + unitCount + ") · Test Devices (" + deviceCount + ")");
        binding.tvAdmobAppId.setText("App ID: " + emptyLabel(networkBean.appId));
        
        if (!binding.etGameId.hasFocus()) {
            binding.etGameId.setText(networkBean.getConfigurationString(ProjectLibraryBean.CONFIG_UNITY_GAME_ID, ""));
        }
        
        binding.tvUnityState.setText(unityEnabled ? "Enabled · Unity Ads will be initialized in MainActivity" : "Disabled · Unity SDK is not added to this project's build");
        
        binding.containerPlacements.removeAllViews();
        JSONArray placements = getPlacements();
        for (int i = 0; i < placements.length(); i++) {
            JSONObject item = placements.optJSONObject(i);
            if (item == null) continue;
            addPlacementRow(i, item.optString("name", "Placement"), item.optString("id", ""));
        }
        binding.tvPlacementsSummary.setText("Placements (" + placements.length() + ") · Add and manage Unity ad placements");
        
        restoring = false;
    }

    private void updateOverallEnabledState() {
        networkBean.useYn = networkBean.isAdMobEnabled() || networkBean.isUnityAdsEnabled()
                ? ProjectLibraryBean.LIB_USE_Y : ProjectLibraryBean.LIB_USE_N;
    }

    private void saveGameId(boolean showErrors) {
        String gameId = binding.etGameId.getText() == null ? "" : binding.etGameId.getText().toString().trim();
        if (!gameId.isEmpty() && !gameId.matches("[0-9]{4,20}")) {
            if (showErrors) binding.etGameId.setError("Enter a valid numeric Unity Game ID");
            return;
        }
        binding.etGameId.setError(null);
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_GAME_ID, gameId);
    }

    private JSONArray getPlacements() {
        String json = networkBean.getConfigurationString(ProjectLibraryBean.CONFIG_UNITY_PLACEMENTS, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private void savePlacements(JSONArray placements) {
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_PLACEMENTS, placements.toString());
        renderState();
    }

    private void addPlacementRow(int index, String name, String id) {
        ItemUnityPlacementBinding rowBinding = ItemUnityPlacementBinding.inflate(LayoutInflater.from(this), binding.containerPlacements, false);
        
        rowBinding.tvPlacementName.setText(name);
        rowBinding.tvPlacementId.setText(id);

        rowBinding.btnEdit.setOnClickListener(v -> showPlacementDialog(index, name, id));

        rowBinding.btnDelete.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Delete placement?")
                .setMessage("Remove “" + name + "” from this project's Unity Ads settings?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    JSONArray source = getPlacements();
                    JSONArray result = new JSONArray();
                    for (int i = 0; i < source.length(); i++) if (i != index) result.put(source.opt(i));
                    savePlacements(result);
                }).show());
        
        binding.containerPlacements.addView(rowBinding.getRoot());
    }

    private void showPlacementDialog(int index, String oldName, String oldId) {
        DialogUnityPlacementBinding dBinding = DialogUnityPlacementBinding.inflate(getLayoutInflater());
        dBinding.etName.setText(oldName);
        dBinding.etId.setText(oldId);

        dBinding.chipBanner.setOnClickListener(v -> {
            dBinding.etName.setText("Banner");
            dBinding.etId.setText("Banner_Android");
        });

        dBinding.chipInterstitial.setOnClickListener(v -> {
            dBinding.etName.setText("Interstitial");
            dBinding.etId.setText("Interstitial_Android");
        });

        dBinding.chipRewarded.setOnClickListener(v -> {
            dBinding.etName.setText("Rewarded");
            dBinding.etId.setText("Rewarded_Android");
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(index < 0 ? "Add Placement" : "Edit Placement")
                .setView(dBinding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
                
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = dBinding.etName.getText() == null ? "" : dBinding.etName.getText().toString().trim();
            String id = dBinding.etId.getText() == null ? "" : dBinding.etId.getText().toString().trim();
            
            if (name.isEmpty() || id.isEmpty()) {
                if (name.isEmpty()) dBinding.layoutName.setError("Enter a placement name");
                if (id.isEmpty()) dBinding.layoutId.setError("Enter a placement ID");
                return;
            }
            dBinding.layoutName.setError(null);
            dBinding.layoutId.setError(null);
            
            JSONArray old = getPlacements();
            JSONArray updated = new JSONArray();
            JSONObject replacement = new JSONObject();
            try {
                replacement.put("name", name);
                replacement.put("id", id);
                for (int i = 0; i < old.length(); i++) {
                    if (i == index) updated.put(replacement);
                    else updated.put(old.get(i));
                }
                if (index < 0 || index >= old.length()) updated.put(replacement);
            } catch (JSONException e) {
                Toast.makeText(this, "Could not save placement", Toast.LENGTH_SHORT).show();
                return;
            }
            savePlacements(updated);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void openAdMobSettings() {
        saveGameId(true);
        Intent intent;
        if (!TextUtils.isEmpty(networkBean.appId) && !TextUtils.isEmpty(networkBean.reserved1)) {
            intent = new Intent(this, ManageAdmobActivity.class);
        } else {
            intent = new Intent(this, AdmobActivity.class);
        }
        intent.putExtra("sc_id", scId);
        ProjectLibraryBean admobWorkingCopy = networkBean.clone();
        admobWorkingCopy.useYn = networkBean.isAdMobEnabled()
                ? ProjectLibraryBean.LIB_USE_Y : ProjectLibraryBean.LIB_USE_N;
        intent.putExtra("admob", admobWorkingCopy);
        startActivityForResult(intent, REQUEST_ADMOB_SETTINGS);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADMOB_SETTINGS && resultCode == RESULT_OK && data != null) {
            ProjectLibraryBean updated = data.getParcelableExtra("admob");
            if (updated != null) {
                networkBean = updated;
                if (networkBean.configurations == null) networkBean.configurations = new HashMap<>();
                networkBean.setConfiguration(ProjectLibraryBean.CONFIG_ADMOB_ENABLED, ProjectLibraryBean.LIB_USE_Y.equals(networkBean.useYn));
                updateOverallEnabledState();
                renderState();
            }
        }
    }

    private void showImportDialog() {
        LibrarySettingsImporter importer = new LibrarySettingsImporter(scId, iC::b);
        importer.addOnProjectSelectedListener(source -> {
            if (source == null) {
                Toast.makeText(this, "No ad-network settings found in the selected project", Toast.LENGTH_LONG).show();
                return;
            }
            new MaterialAlertDialogBuilder(this).setTitle("Replace ad-network settings?")
                    .setMessage("This will replace the current AdMob and Unity Ads settings with the selected project's settings.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Import", (confirm, result) -> {
                        copyNetworkSettings(source);
                        renderState();
                        Toast.makeText(this, "Ad-network settings imported", Toast.LENGTH_SHORT).show();
                    }).show();
        });
        importer.showDialog(this);
    }

    private void copyNetworkSettings(ProjectLibraryBean source) {
        networkBean.appId = source.appId;
        networkBean.reserved1 = source.reserved1;
        networkBean.reserved2 = source.reserved2;
        networkBean.reserved3 = source.reserved3;
        networkBean.adUnits = new ArrayList<>();
        if (source.adUnits != null) for (AdUnitBean item : source.adUnits) if (item != null) networkBean.adUnits.add(item.clone());
        networkBean.testDevices = new ArrayList<>();
        if (source.testDevices != null) for (AdTestDeviceBean item : source.testDevices) if (item != null) networkBean.testDevices.add(item.clone());
        networkBean.configurations = source.configurations == null ? new HashMap<>() : new HashMap<>(source.configurations);
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_ADMOB_ENABLED, source.isAdMobEnabled());
        String sourceGameId = source.getConfigurationString(ProjectLibraryBean.CONFIG_UNITY_GAME_ID, "");
        boolean validUnityConfig = source.isUnityAdsEnabled() && sourceGameId.matches("[0-9]{4,20}");
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_ENABLED, validUnityConfig);
        if (source.isUnityAdsEnabled() && !validUnityConfig) {
            Toast.makeText(this, "Unity Ads was disabled because the source project has no valid Game ID", Toast.LENGTH_LONG).show();
        }
        networkBean.useYn = source.isAdMobEnabled() || validUnityConfig ? ProjectLibraryBean.LIB_USE_Y : ProjectLibraryBean.LIB_USE_N;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "No browser available to open this link", Toast.LENGTH_SHORT).show();
        }
    }

    private void finishWithResult() {
        if (unityDependencySyncInProgress) return;
        saveGameId(false);
        if (binding.switchAdmob.isChecked()) {
            String appId = networkBean.appId == null ? "" : networkBean.appId.trim();
            boolean hasAdUnit = (networkBean.adUnits != null && !networkBean.adUnits.isEmpty())
                    || !TextUtils.isEmpty(networkBean.reserved1)
                    || !TextUtils.isEmpty(networkBean.reserved2)
                    || !TextUtils.isEmpty(networkBean.reserved3);
            if (appId.isEmpty()) {
                Toast.makeText(this, "Enter an AdMob App ID before saving", Toast.LENGTH_LONG).show();
                return;
            }
            if (!hasAdUnit) {
                Toast.makeText(this, "Add at least one AdMob ad unit before saving", Toast.LENGTH_LONG).show();
                return;
            }
        }
        if (binding.switchUnity.isChecked()) {
            String id = binding.etGameId.getText() == null ? "" : binding.etGameId.getText().toString().trim();
            if (id.isEmpty() || !id.matches("[0-9]{4,20}")) {
                binding.etGameId.setError("Enter a valid numeric Unity Game ID or disable Unity Ads");
                Toast.makeText(this, "Unity Ads needs a valid Game ID", Toast.LENGTH_LONG).show();
                return;
            }
            if (getPlacements().length() == 0) {
                Toast.makeText(this, "Add at least one Unity placement before saving", Toast.LENGTH_LONG).show();
                return;
            }
        }
        if (binding.switchUnity.isChecked() && !UnityAdsDependencyManager.isReady(scId)) {
            syncUnityDependenciesThenSave();
            return;
        }
        if (!binding.switchUnity.isChecked()) {
            try {
                UnityAdsDependencyManager.removeManagedDependencyIfDisabled(scId);
            } catch (Exception e) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Could not update Unity Ads dependencies")
                        .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
        }
        saveAndReturnResult();
    }

    private void syncUnityDependenciesThenSave() {
        if (unityDependencySyncInProgress) return;
        unityDependencySyncInProgress = true;
        unityDependencySyncCancelled.set(false);
        unityDependencyProgressDialog = new ProgressDialog(this);
        unityDependencyProgressDialog.setTitle("Preparing Unity Ads 4.20.1");
        unityDependencyProgressDialog.setMessage("Resolving SDK and required libraries…");
        unityDependencyProgressDialog.setIndeterminate(true);
        unityDependencyProgressDialog.setCancelable(false);
        unityDependencyProgressDialog.setCanceledOnTouchOutside(false);
        unityDependencyProgressDialog.show();

        unityDependencyExecutor.execute(() -> {
            StringBuilder recentLogs = new StringBuilder();
            GradleSyncEngine.Result result;
            try {
                result = UnityAdsDependencyManager.sync(this, scId, new GradleSyncEngine.Listener() {
                    @Override
                    public void onStatus(String message, int done, int total) {
                        runOnUiThread(() -> {
                            if (unityDependencyProgressDialog != null && unityDependencyProgressDialog.isShowing()
                                    && !isFinishing() && !isDestroyed()) {
                                unityDependencyProgressDialog.setMessage(message + " (" + done + "/" + total + ")");
                            }
                        });
                    }

                    @Override
                    public void onDependencyState(String key, GradleSyncEngine.DepState state, @Nullable String error) {
                        if (state == GradleSyncEngine.DepState.FAILED && error != null) {
                            synchronized (recentLogs) {
                                recentLogs.append(error).append('\n');
                            }
                        }
                    }

                    @Override
                    public void onLog(String line) {
                        synchronized (recentLogs) {
                            recentLogs.append(line).append('\n');
                            if (recentLogs.length() > 10000) recentLogs.delete(0, recentLogs.length() - 10000);
                        }
                    }
                }, unityDependencySyncCancelled);
            } catch (Throwable error) {
                result = UnityAdsDependencyManager.failureResult(
                        error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                        unityDependencySyncCancelled.get());
            }
            final GradleSyncEngine.Result completed = result;
            final String logs;
            synchronized (recentLogs) {
                logs = recentLogs.toString();
            }
            runOnUiThread(() -> {
                unityDependencySyncInProgress = false;
                if (unityDependencyProgressDialog != null && unityDependencyProgressDialog.isShowing()) {
                    unityDependencyProgressDialog.dismiss();
                }
                unityDependencyProgressDialog = null;
                if (isFinishing() || isDestroyed()) return;
                if (completed.success && UnityAdsDependencyManager.isReady(scId)) {
                    Toast.makeText(this, "Unity Ads 4.20.1 and its dependencies are ready", Toast.LENGTH_LONG).show();
                    saveAndReturnResult();
                } else {
                    String message = completed.message == null ? "Unity Ads dependency sync failed." : completed.message;
                    if (!logs.trim().isEmpty()) {
                        String[] lines = logs.trim().split("\n");
                        int start = Math.max(0, lines.length - 8);
                        StringBuilder tail = new StringBuilder();
                        for (int i = start; i < lines.length; i++) tail.append(lines[i]).append('\n');
                        message += "\n\nLast sync messages:\n" + tail.toString().trim();
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Unity Ads dependencies were not prepared")
                            .setMessage(message + "\n\nSettings were not saved. Check internet access, then retry.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    private void saveAndReturnResult() {
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_ADMOB_ENABLED, binding.switchAdmob.isChecked());
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_ENABLED, binding.switchUnity.isChecked());
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_TEST_MODE, binding.switchTestMode.isChecked());
        updateOverallEnabledState();
        Intent result = new Intent();
        result.putExtra("sc_id", scId);
        result.putExtra("admob", networkBean);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (unityDependencySyncInProgress) unityDependencySyncCancelled.set(true);
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onDestroy() {
        if (unityDependencySyncInProgress) unityDependencySyncCancelled.set(true);
        if (unityDependencyProgressDialog != null && unityDependencyProgressDialog.isShowing()) {
            unityDependencyProgressDialog.dismiss();
        }
        unityDependencyExecutor.shutdownNow();
        binding = null;
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", scId);
        outState.putParcelable("admob", networkBean);
        super.onSaveInstanceState(outState);
    }
}
