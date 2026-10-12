package com.besome.sketch.editor.manage.library;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.beans.AdTestDeviceBean;
import com.besome.sketch.beans.AdUnitBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.editor.manage.library.admob.AdmobActivity;
import com.besome.sketch.editor.manage.library.admob.ManageAdmobActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.iC;
import mod.jbk.editor.manage.library.LibrarySettingsImporter;
import pro.sketchware.R;
import pro.sketchware.activities.editor.gradle.UnityAdsDependencyManager;
import pro.sketchware.utility.ThemeUtils;

/** Project-level settings for Google AdMob and Unity Ads. */
public class ManageAdNetworksActivity extends BaseAppCompatActivity {
    private static final int REQUEST_ADMOB_SETTINGS = 8501;
    private static final int GAP = 12;

    private String scId;
    private ProjectLibraryBean networkBean;
    private LinearLayout content;
    private MaterialSwitch admobSwitch;
    private MaterialSwitch unitySwitch;
    private MaterialSwitch testModeSwitch;
    private TextView admobState;
    private TextView admobCounts;
    private TextView admobAppIdView;
    private TextView unityState;
    private TextView placementsSummary;
    private TextInputEditText gameIdInput;
    private LinearLayout placementsContainer;
    private boolean restoring;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scId = savedInstanceState == null ? getIntent().getStringExtra("sc_id") : savedInstanceState.getString("sc_id");
        networkBean = savedInstanceState == null ? getIntent().getParcelableExtra("admob") : savedInstanceState.getParcelable("admob");
        if (networkBean == null) networkBean = new ProjectLibraryBean(ProjectLibraryBean.PROJECT_LIB_TYPE_ADMOB);
        if (networkBean.configurations == null) networkBean.configurations = new HashMap<>();
        // Backward-compatible migration: projects created before Ad Networks used useYn for AdMob.
        if (!networkBean.configurations.containsKey(ProjectLibraryBean.CONFIG_ADMOB_ENABLED)) {
            networkBean.setConfiguration(ProjectLibraryBean.CONFIG_ADMOB_ENABLED, networkBean.isAdMobEnabled());
        }
        buildUi();
        renderState();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private MaterialButton button(String label, boolean outlined) {
        MaterialButton button = new MaterialButton(this, null,
                outlined ? com.google.android.material.R.attr.materialButtonOutlinedStyle
                        : com.google.android.material.R.attr.materialButtonStyle);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(20));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerLow));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(14), dp(8), dp(14), dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private void buildUi() {
        LinearLayout root = column();
        root.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Ad Networks & GMA");
        toolbar.setSubtitle("Manage ad networks for your project");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finishWithResult());
        toolbar.setTitleTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        toolbar.setSubtitleTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = column();
        content.setPadding(0, dp(4), 0, dp(18));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        MaterialButton importButton = button("Import from another project", true);
        importButton.setIconResource(R.drawable.ic_mtrl_folder);
        importButton.setOnClickListener(v -> showImportDialog());
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        importParams.setMargins(dp(14), dp(8), dp(14), dp(8));
        content.addView(importButton, importParams);

        buildAdMobCard();
        buildUnityCard();

        MaterialButton save = button("Save", false);
        save.setOnClickListener(v -> finishWithResult());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.setMargins(dp(14), dp(10), dp(14), dp(12));
        root.addView(save, saveParams);
        setContentView(root);
    }

    private void buildAdMobCard() {
        MaterialCardView card = card();
        LinearLayout body = column();
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout header = row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_mtrl_ads);
        icon.setContentDescription("Google AdMob");
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout titleColumn = column();
        TextView title = text("Google AdMob", 18, true);
        TextView subtitle = text("Monetize with Google Ads (AdMob & GMA)", 12, false);
        subtitle.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        titleColumn.addView(title);
        titleColumn.addView(subtitle);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(10), 0, dp(8), 0);
        header.addView(titleColumn, titleParams);
        admobSwitch = new MaterialSwitch(this);
        admobSwitch.setContentDescription("Enable Google AdMob");
        header.addView(admobSwitch);
        body.addView(header);

        admobState = text("Configure App ID, ad units and test devices", 13, false);
        admobState.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stateParams.setMargins(dp(52), dp(4), 0, dp(10));
        body.addView(admobState, stateParams);

        LinearLayout details = row();
        admobAppIdView = text("App ID: " + emptyLabel(networkBean.appId), 12, false);
        admobAppIdView.setMaxLines(2);
        admobAppIdView.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(admobAppIdView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton configure = button("Configure AdMob", false);
        configure.setOnClickListener(v -> openAdMobSettings());
        details.addView(configure);
        body.addView(details);
        admobCounts = text("Ad Units (0) · Test Devices (0)", 12, false);
        admobCounts.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams countsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countsParams.topMargin = dp(8);
        body.addView(admobCounts, countsParams);
        MaterialButton console = button("Open AdMob Console", true);
        console.setIconResource(R.drawable.ic_mtrl_ads);
        console.setOnClickListener(v -> openUrl("https://apps.admob.com/v2/home"));
        LinearLayout.LayoutParams consoleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        consoleParams.topMargin = dp(10);
        body.addView(console, consoleParams);
        card.addView(body);
        content.addView(card);
        admobSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (restoring) return;
            networkBean.setConfiguration(ProjectLibraryBean.CONFIG_ADMOB_ENABLED, checked);
            updateOverallEnabledState();
            renderState();
        });
    }

    private void buildUnityCard() {
        MaterialCardView card = card();
        LinearLayout body = column();
        body.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout header = row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_mtrl_unity);
        icon.setContentDescription("Unity Ads");
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout titleColumn = column();
        titleColumn.addView(text("Unity Ads", 18, true));
        TextView subtitle = text("Game monetization (Banner & Video)", 12, false);
        subtitle.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        titleColumn.addView(subtitle);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(10), 0, dp(8), 0);
        header.addView(titleColumn, titleParams);
        unitySwitch = new MaterialSwitch(this);
        unitySwitch.setContentDescription("Enable Unity Ads");
        header.addView(unitySwitch);
        body.addView(header);
        unityState = text("Unity Ads SDK is included only when enabled", 13, false);
        unityState.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stateParams.setMargins(dp(52), dp(4), 0, dp(12));
        body.addView(unityState, stateParams);

        TextInputLayout gameIdLayout = new TextInputLayout(this);
        gameIdLayout.setHint("Unity Game ID");
        gameIdInput = new TextInputEditText(gameIdLayout.getContext());
        gameIdInput.setSingleLine(true);
        gameIdInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        gameIdInput.setText(networkBean.getConfigurationString(ProjectLibraryBean.CONFIG_UNITY_GAME_ID, ""));
        gameIdLayout.addView(gameIdInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(gameIdLayout);

        testModeSwitch = new MaterialSwitch(this);
        testModeSwitch.setText("Test Mode");
        testModeSwitch.setTextSize(14);
        testModeSwitch.setContentDescription("Show test ads for development");
        testModeSwitch.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_mtrl_tube, 0, 0, 0);
        testModeSwitch.setCompoundDrawablePadding(dp(8));
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        testParams.topMargin = dp(8);
        body.addView(testModeSwitch, testParams);
        TextView testDescription = text("Show test ads during development", 12, false);
        testDescription.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        body.addView(testDescription);

        View divider = new View(this);
        divider.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.setMargins(0, dp(12), 0, dp(12));
        body.addView(divider, dividerParams);
        LinearLayout placementHeader = row();
        LinearLayout placementTitle = column();
        placementTitle.addView(text("Placements", 15, true));
        placementsSummary = text("Add and manage Unity ad placements", 12, false);
        placementsSummary.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        placementTitle.addView(placementsSummary);
        placementHeader.addView(placementTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton addPlacement = button("Add Placement", false);
        addPlacement.setOnClickListener(v -> showPlacementDialog(-1, "", ""));
        placementHeader.addView(addPlacement);
        body.addView(placementHeader);
        placementsContainer = column();
        LinearLayout.LayoutParams placementsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        placementsParams.topMargin = dp(8);
        body.addView(placementsContainer, placementsParams);
        MaterialButton dashboard = button("Open Unity Ads Dashboard", true);
        dashboard.setIconResource(R.drawable.ic_mtrl_unity);
        dashboard.setOnClickListener(v -> openUrl("https://dashboard.unity3d.com/"));
        LinearLayout.LayoutParams dashboardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dashboardParams.topMargin = dp(10);
        body.addView(dashboard, dashboardParams);
        card.addView(body);
        content.addView(card);

        unitySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (restoring) return;
            networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_ENABLED, checked);
            updateOverallEnabledState();
            renderState();
        });
        testModeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!restoring) networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_TEST_MODE, checked);
        });
        gameIdInput.setOnFocusChangeListener((v, hasFocus) -> {
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
        admobSwitch.setChecked(admobEnabled);
        unitySwitch.setChecked(unityEnabled);
        testModeSwitch.setChecked(Boolean.parseBoolean(networkBean.getConfigurationString(ProjectLibraryBean.CONFIG_UNITY_TEST_MODE, "true")));
        admobState.setText(admobEnabled ? "Enabled · App ID, ad units and test devices can be edited" : "Disabled · Enable to include AdMob in the project");
        int unitCount = networkBean.adUnits == null ? 0 : networkBean.adUnits.size();
        if (unitCount == 0) {
            if (!TextUtils.isEmpty(networkBean.reserved1)) unitCount++;
            if (!TextUtils.isEmpty(networkBean.reserved2)) unitCount++;
            if (!TextUtils.isEmpty(networkBean.reserved3)) unitCount++;
        }
        int deviceCount = networkBean.testDevices == null ? 0 : networkBean.testDevices.size();
        admobCounts.setText("Ad Units (" + unitCount + ") · Test Devices (" + deviceCount + ")");
        admobAppIdView.setText("App ID: " + emptyLabel(networkBean.appId));
        if (!gameIdInput.hasFocus()) {
            gameIdInput.setText(networkBean.getConfigurationString(ProjectLibraryBean.CONFIG_UNITY_GAME_ID, ""));
        }
        unityState.setText(unityEnabled ? "Enabled · Unity Ads will be initialized in MainActivity" : "Disabled · Unity SDK is not added to this project's build");
        placementsContainer.removeAllViews();
        JSONArray placements = getPlacements();
        for (int i = 0; i < placements.length(); i++) {
            JSONObject item = placements.optJSONObject(i);
            if (item == null) continue;
            addPlacementRow(i, item.optString("name", "Placement"), item.optString("id", ""));
        }
        placementsSummary.setText("Placements (" + placements.length() + ") · Add and manage Unity ad placements");
        restoring = false;
    }

    private void updateOverallEnabledState() {
        networkBean.useYn = networkBean.isAdMobEnabled() || networkBean.isUnityAdsEnabled()
                ? ProjectLibraryBean.LIB_USE_Y : ProjectLibraryBean.LIB_USE_N;
    }

    private void saveGameId(boolean showErrors) {
        String gameId = gameIdInput.getText() == null ? "" : gameIdInput.getText().toString().trim();
        if (!gameId.isEmpty() && !gameId.matches("[0-9]{4,20}")) {
            if (showErrors) gameIdInput.setError("Enter a valid numeric Unity Game ID");
            return;
        }
        gameIdInput.setError(null);
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
        LinearLayout row = row();
        row.setPadding(dp(10), dp(9), dp(4), dp(9));
        MaterialCardView rowCard = new MaterialCardView(this);
        rowCard.setRadius(dp(12));
        rowCard.setCardElevation(0);
        rowCard.setStrokeWidth(dp(1));
        rowCard.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        rowCard.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));
        LinearLayout inner = row();
        LinearLayout names = column();
        names.addView(text(name, 13, true));
        TextView idView = text(id, 12, false);
        idView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        idView.setMaxLines(1);
        idView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        names.addView(idView);
        inner.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton edit = button("Edit", true);
        edit.setMinWidth(0);
        edit.setOnClickListener(v -> showPlacementDialog(index, name, id));
        inner.addView(edit);
        MaterialButton delete = button("×", true);
        delete.setMinWidth(dp(42));
        delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Delete placement?")
                .setMessage("Remove “" + name + "” from this project's Unity Ads settings?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    JSONArray source = getPlacements();
                    JSONArray result = new JSONArray();
                    for (int i = 0; i < source.length(); i++) if (i != index) result.put(source.opt(i));
                    savePlacements(result);
                }).show());
        inner.addView(delete);
        rowCard.addView(inner);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        placementsContainer.addView(rowCard, params);
    }

    private void showPlacementDialog(int index, String oldName, String oldId) {
        LinearLayout form = column();
        form.setPadding(dp(4), dp(8), dp(4), 0);
        TextInputLayout nameLayout = new TextInputLayout(this);
        nameLayout.setHint("Placement name");
        TextInputEditText nameInput = new TextInputEditText(nameLayout.getContext());
        nameInput.setSingleLine(true);
        nameInput.setText(oldName);
        nameLayout.addView(nameInput);
        form.addView(nameLayout);
        TextInputLayout idLayout = new TextInputLayout(this);
        idLayout.setHint("Placement ID");
        TextInputEditText idInput = new TextInputEditText(idLayout.getContext());
        idInput.setSingleLine(true);
        idInput.setText(oldId);
        idLayout.addView(idInput);
        LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        idParams.topMargin = dp(8);
        form.addView(idLayout, idParams);
        if (index < 0) {
            TextInputLayout typeLayout = new TextInputLayout(this);
            typeLayout.setHint("Ad type");
            Spinner typeSpinner = new Spinner(this);
            String[] types = {"Banner", "Interstitial", "Rewarded"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, types);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            typeSpinner.setAdapter(adapter);
            typeLayout.addView(typeSpinner);
            // Place the selector first in the dialog so the chosen type controls defaults.
            form.addView(typeLayout, 0);
            nameInput.setText("banner");
            idInput.setText("banner-Android");
            typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String name;
                    String placementId;
                    if (position == 1) {
                        name = "interstitial";
                        placementId = "interstitial-Android";
                    } else if (position == 2) {
                        name = "rewarded";
                        placementId = "rewarded-Android";
                    } else {
                        name = "banner";
                        placementId = "banner-Android";
                    }
                    nameInput.setText(name);
                    idInput.setText(placementId);
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(index < 0 ? "Add Placement" : "Edit Placement")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
            String id = idInput.getText() == null ? "" : idInput.getText().toString().trim();
            if (name.isEmpty() || id.isEmpty()) {
                if (name.isEmpty()) nameLayout.setError("Enter a placement name");
                if (id.isEmpty()) idLayout.setError("Enter a placement ID");
                return;
            }
            nameLayout.setError(null);
            idLayout.setError(null);
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
        // The legacy AdMob screens interpret useYn as the AdMob switch. In the new
        // combined screen useYn represents either network, so pass a working copy
        // whose switch reflects AdMob alone and retain the Unity configuration.
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
        saveGameId(false);
        if (admobSwitch != null && admobSwitch.isChecked()) {
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
        if (unitySwitch != null && unitySwitch.isChecked()) {
            String id = gameIdInput.getText() == null ? "" : gameIdInput.getText().toString().trim();
            if (id.isEmpty() || !id.matches("[0-9]{4,20}")) {
                gameIdInput.setError("Enter a valid numeric Unity Game ID or disable Unity Ads");
                Toast.makeText(this, "Unity Ads needs a valid Game ID", Toast.LENGTH_LONG).show();
                return;
            }
            if (getPlacements().length() == 0) {
                Toast.makeText(this, "Add at least one Unity placement before saving", Toast.LENGTH_LONG).show();
                return;
            }
        }
        // Remove a stale root SDK copy left by the earlier save-time sync implementation.
        // This is cleanup only: it does not download or add any library.
        try {
            UnityAdsDependencyManager.removeBundledSdkDuplicate(scId);
        } catch (Exception error) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Could not clean up the old Unity Ads library copy")
                    .setMessage(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        // Persist settings directly. ProjectBuilder loads the SDK from libs.zip and
        // prepares any missing transitive dependencies only when the project is built.
        saveAndReturnResult();
    }

    private void saveAndReturnResult() {
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_ADMOB_ENABLED, admobSwitch.isChecked());
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_ENABLED, unitySwitch.isChecked());
        networkBean.setConfiguration(ProjectLibraryBean.CONFIG_UNITY_TEST_MODE, testModeSwitch.isChecked());
        updateOverallEnabledState();
        Intent result = new Intent();
        result.putExtra("sc_id", scId);
        result.putExtra("admob", networkBean);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", scId);
        outState.putParcelable("admob", networkBean);
        super.onSaveInstanceState(outState);
    }
}
