package com.besome.sketch.editor.manage.view;

import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.hC;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.project.ProjectTracker;
import pro.sketchware.R;

/** Imports a validated .swnscreen package through Sketchware Neo's native screen-data registry. */
public final class ScreenPackageImportActivity extends BaseAppCompatActivity {
    public static final String EXTRA_TARGET_PROJECT_ID = "target_sc_id";
    public static final String EXTRA_PACKAGE_PATH = "package_path";
    public static final String EXTRA_PACKAGE_URI = "package_uri";
    private static final int REQUEST_OPEN_PACKAGE = 731;
    private static final long MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<ScreenNameRow> nameRows = new ArrayList<>();
    private ScreenPackageManager.PackageContent packageContent;
    private String targetProjectId;
    private LinearLayout root;
    private ProgressBar progress;
    private TextView status;
    private SwitchMaterial includeLogic;
    private SwitchMaterial includeJava;
    private boolean busy;
    private File temporaryPackage;

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        targetProjectId = getIntent().getStringExtra(EXTRA_TARGET_PROJECT_ID);
        buildUi();
        String packagePath = getIntent().getStringExtra(EXTRA_PACKAGE_PATH);
        String packageUri = getIntent().getStringExtra(EXTRA_PACKAGE_URI);
        if (packagePath != null && !packagePath.isBlank()) {
            loadPackage(new File(packagePath));
        } else if (packageUri != null && !packageUri.isBlank()) {
            try { loadUri(Uri.parse(packageUri)); } catch (Exception error) { showError("Could not open the package: " + safeMessage(error)); }
        } else {
            openPackagePicker();
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(root, R.attr.colorSurface));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Import screen package");
        toolbar.setSubtitle("Sketchware Neo · .swnscreen");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));
        status = text("Choose a .swnscreen file to inspect its contents.", 14, R.attr.colorOnSurfaceVariant);
        status.setPadding(dp(18), dp(16), dp(18), dp(8));
        root.addView(status);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-2, -2);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(16);
        root.addView(progress, progressParams);
        MaterialButton choose = new MaterialButton(this);
        choose.setText("Choose screen package");
        choose.setOnClickListener(v -> openPackagePicker());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, dp(50));
        chooseParams.setMargins(dp(16), dp(16), dp(16), dp(16));
        root.addView(choose, chooseParams);
        setContentView(root);
    }

    private void openPackagePicker() {
        if (busy || isFinishing()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{ScreenPackageManager.MIME_TYPE, "application/zip", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQUEST_OPEN_PACKAGE);
        } catch (Exception error) {
            showError("No compatible file picker is available: " + safeMessage(error));
        }
    }

    @Override public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_PACKAGE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) { if (packageContent == null) finish(); return; }
        loadUri(data.getData());
    }

    private void loadUri(Uri uri) {
        setBusy(true, "Reading and validating package…");
        executor.execute(() -> {
            String error = null;
            ScreenPackageManager.PackageContent loaded = null;
            File copied = null;
            try {
                copied = copyUriToCache(uri);
                loaded = ScreenPackageManager.readPackage(copied);
            } catch (Exception exception) { error = safeMessage(exception); }
            final String message = error;
            final ScreenPackageManager.PackageContent parsed = loaded;
            if (copied != null && copied.exists()) copied.delete();
            runOnUiThread(() -> {
                setBusy(false, null);
                if (message != null) showError("The selected file is not a valid supported screen package: " + message);
                else onPackageReady(parsed);
            });
        });
    }

    private void loadPackage(File file) {
        setBusy(true, "Validating downloaded template…");
        executor.execute(() -> {
            String error = null;
            ScreenPackageManager.PackageContent loaded = null;
            try { loaded = ScreenPackageManager.readPackage(file); }
            catch (Exception exception) { error = safeMessage(exception); }
            final String message = error;
            final ScreenPackageManager.PackageContent parsed = loaded;
            runOnUiThread(() -> {
                setBusy(false, null);
                if (message != null) showError("The downloaded package could not be validated: " + message);
                else onPackageReady(parsed);
            });
        });
    }

    private File copyUriToCache(Uri uri) throws Exception {
        File target = new File(getCacheDir(), "import-" + System.currentTimeMillis() + ScreenPackageManager.FILE_EXTENSION);
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new java.io.IOException("The selected file could not be opened.");
            try (FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[16384];
                long total = 0L;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_ARCHIVE_BYTES) throw new java.io.IOException("Screen packages must be 64 MB or smaller.");
                    output.write(buffer, 0, read);
                }
                output.flush(); output.getFD().sync();
            }
        } catch (Exception error) {
            if (target.exists()) target.delete();
            throw error;
        }
        return target;
    }

    private void onPackageReady(ScreenPackageManager.PackageContent content) {
        if (isFinishing() || content == null) return;
        packageContent = content;
        status.setText("Validated package · " + content.getScreenCount() + " screen(s) · "
                + (content.includesLogic ? "layouts and logic" : "visual layouts only")
                + (content.getJavaSourceCount() > 0 ? " · " + content.getJavaSourceCount() + " Java source file(s)" : ""));
        if (targetProjectId == null || targetProjectId.isBlank() || jC.b(targetProjectId) == null) {
            chooseTargetProject();
        } else {
            showScreenSelection();
        }
    }

    private void chooseTargetProject() {
        ArrayList<HashMap<String, Object>> projects = lC.a();
        ArrayList<HashMap<String, Object>> valid = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (HashMap<String, Object> project : projects) {
            if (project == null) continue;
            String id = yB.c(project, "sc_id");
            if (id == null || id.isBlank() || jC.b(id) == null) continue;
            valid.add(project);
            String name = yB.c(project, "my_app_name");
            if (name == null || name.isBlank()) name = "Project " + id;
            labels.add(name + "  ·  ID " + id);
        }
        if (valid.isEmpty()) {
            showError("No Sketchware Neo projects are available. Create or open a project, then import this package again.");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose target project")
                .setMessage("The selected screens will be added to this project; existing screens will not be overwritten.")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    targetProjectId = yB.c(valid.get(which), "sc_id");
                    showScreenSelection();
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .show();
    }

    private void showScreenSelection() {
        if (packageContent == null || isFinishing()) return;
        nameRows.clear();
        Set<String> used = existingNames(targetProjectId);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(6), dp(16), dp(6));
        TextView guide = text("Select screens to import and edit destination names before adding them to the project. Required custom views must already exist in the target project; project-specific libraries, assets, fonts and plugins are not bundled by this screen format.", 14, R.attr.colorOnSurfaceVariant);
        content.addView(guide, margin(-1, -2, 0, 0, 0, 8));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        for (ScreenPackageManager.ScreenDescriptor descriptor : packageContent.getDescriptors()) {
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(dp(16)); card.setCardElevation(0f);
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(dp(12), dp(10), dp(12), dp(12));
            CheckBox selected = new CheckBox(this);
            selected.setText(descriptor.originalName + "  ·  " + descriptor.typeLabel);
            selected.setChecked(true);
            TextInputLayout nameLayout = new TextInputLayout(this);
            nameLayout.setHint("Destination screen name");
            TextInputEditText nameInput = new TextInputEditText(nameLayout.getContext());
            nameInput.setSingleLine(true);
            nameInput.setText(makeUniqueName(descriptor.originalName, used));
            nameInput.setSelectAllOnFocus(true);
            nameLayout.addView(nameInput);
            selected.setOnCheckedChangeListener((button, checked) -> { nameLayout.setEnabled(checked); nameInput.setEnabled(checked); });
            column.addView(selected);
            column.addView(nameLayout);
            card.addView(column);
            rowsContainer.addView(card, margin(-1, -2, 0, 3, 0, 7));
            nameRows.add(new ScreenNameRow(descriptor.originalName, selected, nameInput));
        }
        scroll.addView(rowsContainer);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(dp(360), dp(104) * Math.max(1, packageContent.getScreenCount()))));

        includeLogic = new SwitchMaterial(this);
        includeLogic.setText("Import event and block logic");
        includeLogic.setChecked(packageContent.includesLogic);
        includeLogic.setEnabled(packageContent.includesLogic);
        if (packageContent.includesLogic) content.addView(includeLogic, margin(-1, -2, 0, 8, 0, 0));
        else content.addView(text("This package contains visual layout data only; blocks and events were not exported.", 12, R.attr.colorOnSurfaceVariant), margin(-1, -2, 0, 4, 0, 4));

        includeJava = new SwitchMaterial(this);
        includeJava.setText("Import custom Java sources");
        includeJava.setChecked(false);
        includeJava.setEnabled(packageContent.getJavaSourceCount() > 0);
        if (packageContent.getJavaSourceCount() > 0) {
            content.addView(includeJava, margin(-1, -2, 0, 8, 0, 0));
            content.addView(text("Java code may be project-specific and is not sandboxed. Import it only if you trust the source.", 12, R.attr.colorOnSurfaceVariant), margin(-1, -2, 0, 0, 0, 4));
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Import " + packageContent.getScreenCount() + " screen(s)")
                .setView(content)
                .setPositiveButton("Import selected", null)
                .setNegativeButton("Cancel", (ignored, which) -> finish())
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            Map<String, String> selectedNames = new LinkedHashMap<>();
            Set<String> normalized = new HashSet<>();
            for (ScreenNameRow row : nameRows) {
                if (!row.selected.isChecked()) continue;
                String target = row.name.getText() == null ? "" : row.name.getText().toString().trim();
                String validation = ScreenPackageManager.validateTargetName(target);
                if (validation != null) { row.nameLayoutError(validation); return; }
                if (!normalized.add(target.toLowerCase(Locale.ROOT))) { row.nameLayoutError("Destination names must be unique."); return; }
                selectedNames.put(row.originalName, target);
            }
            if (selectedNames.isEmpty()) { showError("Select at least one screen to import."); return; }
            boolean includeLogicValue = includeLogic != null && includeLogic.isEnabled() && includeLogic.isChecked();
            boolean includeJavaValue = includeJava != null && includeJava.isEnabled() && includeJava.isChecked();
            dialog.dismiss();
            importSelected(selectedNames, includeLogicValue, includeJavaValue);
        }));
        dialog.show();
    }

    private Set<String> existingNames(String projectId) {
        Set<String> used = new HashSet<>();
        try {
            hC registry = jC.b(projectId);
            if (registry != null) {
                if (registry.b() != null) for (ProjectFileBean bean : registry.b()) if (bean != null && bean.fileName != null) used.add(bean.fileName.toLowerCase(Locale.ROOT));
                if (registry.c() != null) for (ProjectFileBean bean : registry.c()) if (bean != null && bean.fileName != null) used.add(bean.fileName.toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) { }
        return used;
    }

    private String makeUniqueName(String original, Set<String> used) {
        String base = original == null || original.isBlank() ? "ImportedScreen" : original;
        if (!used.contains(base.toLowerCase(Locale.ROOT))) { used.add(base.toLowerCase(Locale.ROOT)); return base; }
        String suffix = "_Copy";
        String candidate = base;
        int number = 1;
        while (used.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + suffix + (number == 1 ? "" : number);
            number++;
            if (candidate.length() > 100) {
                String prefix = base.substring(0, Math.min(base.length(), 80));
                candidate = prefix + suffix + number;
            }
        }
        used.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }

    private void importSelected(Map<String, String> names, boolean logic, boolean javaSources) {
        if (busy || packageContent == null) return;
        setBusy(true, "Importing screens safely…");
        String projectId = targetProjectId;
        ScreenPackageManager.PackageContent content = packageContent;
        executor.execute(() -> {
            ScreenPackageManager.Result result = ScreenPackageManager.importScreens(getApplicationContext(), projectId, content, names, logic, javaSources);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false, null);
                if (result.success) {
                    setResult(RESULT_OK);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Import complete")
                            .setMessage(result.message)
                            .setPositiveButton("Open project", (dialog, which) -> openProject(projectId))
                            .setNegativeButton("Done", (dialog, which) -> finish())
                            .setOnCancelListener(dialog -> finish())
                            .show();
                } else {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Import could not be completed")
                            .setMessage(result.message)
                            .setPositiveButton("Review selection", (dialog, which) -> showScreenSelection())
                            .setNegativeButton("Cancel", (dialog, which) -> finish())
                            .show();
                }
            });
        });
    }

    private void openProject(String projectId) {
        try {
            ProjectTracker.setScId(projectId);
            Intent intent = new Intent(this, DesignActivity.class);
            intent.putExtra("sc_id", projectId);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception error) { showError("Screens were imported, but the project could not be opened: " + safeMessage(error)); }
        finish();
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        if (progress != null) progress.setVisibility(value ? View.VISIBLE : View.GONE);
        if (status != null && message != null) status.setText(message);
        if (root != null) {
            for (int i = 0; i < root.getChildCount(); i++) root.getChildAt(i).setEnabled(!value);
        }
    }

    private void showError(String message) {
        if (isFinishing()) return;
        new MaterialAlertDialogBuilder(this).setTitle("Import screen package")
                .setMessage(message).setPositiveButton("OK", null).show();
    }

    private TextView text(String value, int size, int attrColor) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size);
        view.setTextColor(com.google.android.material.color.MaterialColors.getColor(view, attrColor));
        return view;
    }

    private LinearLayout.LayoutParams margin(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private static String safeMessage(Throwable error) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.isBlank() ? "Unknown error" : value;
    }

    @Override public void onDestroy() {
        executor.shutdownNow();
        if (temporaryPackage != null && temporaryPackage.exists()) temporaryPackage.delete();
        super.onDestroy();
    }

    private static final class ScreenNameRow {
        final String originalName;
        final CheckBox selected;
        final TextInputEditText name;
        ScreenNameRow(String originalName, CheckBox selected, TextInputEditText name) {
            this.originalName = originalName; this.selected = selected; this.name = name;
        }
        void nameLayoutError(String message) {
            // The row's editor stays focused, so the user can correct the invalid name without losing selections.
            name.setError(message);
            name.requestFocus();
        }
    }
}
