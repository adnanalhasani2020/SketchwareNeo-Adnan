package com.besome.sketch.editor.manage.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.hC;
import a.a.a.jC;
import pro.sketchware.R;

/** UI for exporting one or more native screen definitions to a .swnscreen package. */
public final class ScreenPackageExportActivity extends BaseAppCompatActivity {
    private static final int REQUEST_CREATE_PACKAGE = 721;
    private static final int MAX_SELECTION = 50;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<ProjectFileBean> allScreens = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private ScreenAdapter adapter;
    private TextInputEditText searchInput;
    private CheckBox selectAll;
    private SwitchMaterial includeLogic;
    private SwitchMaterial includeJava;
    private MaterialButton exportButton;
    private ProgressBar progress;
    private TextView emptyView;
    private String projectId;
    private boolean includeLogicAfterPicker = true;
    private boolean includeJavaAfterPicker;
    private boolean busy;

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectId = getIntent().getStringExtra("sc_id");
        if (projectId == null || projectId.isBlank()) {
            finishWithMessage("No target project was supplied.");
            return;
        }
        buildUi();
        loadScreens();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(root, R.attr.colorSurface));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Export screen");
        toolbar.setSubtitle("Create a Sketchware Neo screen package");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(16), dp(10), dp(16), dp(24));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        TextView intro = text("Select one or more activity screens to export. The package preserves Sketchware Neo's native visual data and, optionally, event/block data.", 14, R.attr.colorOnSurfaceVariant);
        content.addView(intro, margin(LinearLayout.LayoutParams.MATCH_PARENT, -2, 0, 0, 0, 12));

        TextInputLayout searchLayout = new TextInputLayout(this);
        searchLayout.setHint("Search screens");
        searchInput = new TextInputEditText(searchLayout.getContext());
        searchInput.setSingleLine(true);
        searchLayout.addView(searchInput);
        content.addView(searchLayout, margin(-1, -2, 0, 0, 0, 8));

        selectAll = new CheckBox(this);
        selectAll.setText("Select all visible");
        selectAll.setOnCheckedChangeListener((button, checked) -> {
            if (adapter == null || busy) return;
            List<ProjectFileBean> visible = adapter.visibleScreens();
            if (checked) {
                for (ProjectFileBean item : visible) {
                    if (selected.size() >= MAX_SELECTION) break;
                    selected.add(item.fileName);
                }
            } else {
                for (ProjectFileBean item : visible) selected.remove(item.fileName);
            }
            adapter.notifyDataSetChanged();
            updateSelectionUi();
        });
        content.addView(selectAll);

        RecyclerView recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setNestedScrollingEnabled(false);
        adapter = new ScreenAdapter();
        recycler.setAdapter(adapter);
        content.addView(recycler, new LinearLayout.LayoutParams(-1, dp(330)));

        emptyView = text("Loading screens…", 14, R.attr.colorOnSurfaceVariant);
        emptyView.setGravity(Gravity.CENTER);
        content.addView(emptyView, new LinearLayout.LayoutParams(-1, dp(48)));

        MaterialCardView optionsCard = new MaterialCardView(this);
        optionsCard.setRadius(dp(20));
        optionsCard.setCardElevation(0f);
        LinearLayout options = new LinearLayout(this);
        options.setPadding(dp(14), dp(10), dp(14), dp(10));
        options.setOrientation(LinearLayout.VERTICAL);
        options.addView(text("Package contents", 16, R.attr.colorOnSurface));
        includeLogic = new SwitchMaterial(this);
        includeLogic.setText("Include event and block logic");
        includeLogic.setChecked(true);
        options.addView(includeLogic);
        options.addView(text("Turn off for a visual-layout-only package.", 12, R.attr.colorOnSurfaceVariant));
        includeJava = new SwitchMaterial(this);
        includeJava.setText("Include custom Java source files");
        includeJava.setChecked(false);
        options.addView(includeJava);
        options.addView(text("Java files may contain project-specific code. Review them before sharing or importing.", 12, R.attr.colorOnSurfaceVariant));
        optionsCard.addView(options);
        content.addView(optionsCard, margin(-1, -2, 0, 12, 0, 16));

        exportButton = new MaterialButton(this);
        exportButton.setText("Export selected screens");
        exportButton.setIconResource(R.drawable.ic_mtrl_download);
        exportButton.setOnClickListener(v -> beginExport());
        content.addView(exportButton, new LinearLayout.LayoutParams(-1, dp(52)));
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-2, -2);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(12);
        content.addView(progress, progressParams);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) adapter.filter(s == null ? "" : s.toString());
                updateSelectionUi();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void loadScreens() {
        try {
            hC registry = jC.b(projectId);
            if (registry != null && registry.b() != null) {
                for (ProjectFileBean bean : registry.b()) {
                    if (bean != null && bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && bean.fileName != null && !bean.fileName.isBlank()) allScreens.add(bean);
                }
            }
            allScreens.sort((a, b) -> a.fileName.compareToIgnoreCase(b.fileName));
            emptyView.setText(allScreens.isEmpty() ? "No activity screens were found in this project." : "");
            adapter.setItems(allScreens);
            selectAll.setEnabled(!allScreens.isEmpty());
            includeJava.setEnabled(true);
        } catch (Exception error) {
            emptyView.setText("Could not load the project's screens: " + safeMessage(error));
        }
    }

    private void beginExport() {
        if (busy) return;
        if (selected.isEmpty()) {
            showMessage("Choose at least one screen to export.");
            return;
        }
        if (selected.size() > MAX_SELECTION) {
            showMessage("A package can contain at most " + MAX_SELECTION + " screens.");
            return;
        }
        includeLogicAfterPicker = includeLogic.isChecked();
        includeJavaAfterPicker = includeJava.isChecked();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // A generic MIME type works with more SAF providers; the Sketchware Neo-only suffix identifies the actual file format.
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{ScreenPackageManager.MIME_TYPE, "application/zip", "application/octet-stream"});
        intent.putExtra(Intent.EXTRA_TITLE, "SketchwareScreens" + ScreenPackageManager.FILE_EXTENSION);
        try {
            startActivityForResult(intent, REQUEST_CREATE_PACKAGE);
        } catch (Exception error) {
            showMessage("No compatible file picker is available: " + safeMessage(error));
        }
    }

    @Override public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CREATE_PACKAGE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        List<String> names = new ArrayList<>(selected);
        setBusy(true, "Exporting selected screens…");
        executor.execute(() -> {
            File temp = null;
            String failure = null;
            try {
                temp = ScreenPackageManager.createExportFile(this, projectId, names,
                        new ScreenPackageManager.ExportOptions(includeLogicAfterPicker, includeJavaAfterPicker));
                try (InputStream input = new java.io.FileInputStream(temp);
                     OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new java.io.IOException("The selected location could not be opened for writing.");
                    byte[] buffer = new byte[16 * 1024];
                    long total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > 64L * 1024L * 1024L) throw new java.io.IOException("The exported package exceeded 64 MB.");
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                }
            } catch (Exception exception) { failure = safeMessage(exception); }
            finally { if (temp != null && temp.exists()) temp.delete(); }
            final String error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false, null);
                if (error == null) {
                    setResult(RESULT_OK);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Screen package exported")
                            .setMessage("Exported " + names.size() + " screen(s) to the selected location as " + ScreenPackageManager.FILE_EXTENSION + ".")
                            .setPositiveButton("Done", (dialog, which) -> finish())
                            .setOnCancelListener(dialog -> finish())
                            .show();
                } else showMessage("Export failed: " + error);
            });
        });
    }

    private void setBusy(boolean value, String status) {
        busy = value;
        if (progress != null) progress.setVisibility(value ? View.VISIBLE : View.GONE);
        if (exportButton != null) { exportButton.setEnabled(!value); exportButton.setText(value ? status : "Export selected screens"); }
        if (searchInput != null) searchInput.setEnabled(!value);
        if (includeLogic != null) includeLogic.setEnabled(!value);
        if (includeJava != null) includeJava.setEnabled(!value);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void updateSelectionUi() {
        if (exportButton != null && !busy) exportButton.setText(selected.isEmpty()
                ? "Export selected screens" : "Export " + selected.size() + " screen" + (selected.size() == 1 ? "" : "s"));
        if (selectAll != null && adapter != null) {
            boolean checked = !adapter.visibleScreens().isEmpty();
            for (ProjectFileBean item : adapter.visibleScreens()) checked &= selected.contains(item.fileName);
            selectAll.setOnCheckedChangeListener(null);
            selectAll.setChecked(checked);
            selectAll.setOnCheckedChangeListener((button, value) -> {
                if (adapter == null || busy) return;
                for (ProjectFileBean item : adapter.visibleScreens()) {
                    if (value && selected.size() < MAX_SELECTION) selected.add(item.fileName);
                    else if (!value) selected.remove(item.fileName);
                }
                adapter.notifyDataSetChanged();
                updateSelectionUi();
            });
        }
    }

    private void finishWithMessage(String message) {
        new MaterialAlertDialogBuilder(this).setTitle("Export screen").setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> finish()).show();
    }

    private void showMessage(String message) {
        if (isFinishing()) return;
        new MaterialAlertDialogBuilder(this).setTitle("Export screen").setMessage(message).setPositiveButton("OK", null).show();
    }

    private TextView text(String value, int size, int attrColor) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
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

    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }

    private final class ScreenAdapter extends RecyclerView.Adapter<ScreenHolder> {
        private final List<ProjectFileBean> source = new ArrayList<>();
        private final List<ProjectFileBean> visible = new ArrayList<>();
        void setItems(List<ProjectFileBean> values) { source.clear(); source.addAll(values); filter(searchInput == null || searchInput.getText() == null ? "" : searchInput.getText().toString()); }
        void filter(String query) {
            visible.clear();
            String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
            for (ProjectFileBean bean : source) if (needle.isEmpty() || bean.fileName.toLowerCase(java.util.Locale.ROOT).contains(needle)) visible.add(bean);
            notifyDataSetChanged();
            updateEmptyVisibility();
        }
        List<ProjectFileBean> visibleScreens() { return Collections.unmodifiableList(new ArrayList<>(visible)); }
        @NonNull @Override public ScreenHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(ScreenPackageExportActivity.this);
            card.setRadius(dp(14)); card.setCardElevation(0f); card.setStrokeWidth(dp(1));
            LinearLayout row = new LinearLayout(ScreenPackageExportActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8), dp(4), dp(12), dp(4));
            CheckBox check = new CheckBox(ScreenPackageExportActivity.this);
            LinearLayout details = new LinearLayout(ScreenPackageExportActivity.this); details.setOrientation(LinearLayout.VERTICAL);
            TextView name = text("Screen", 15, R.attr.colorOnSurface);
            TextView type = text("Activity", 12, R.attr.colorOnSurfaceVariant);
            details.addView(name); details.addView(type);
            row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(56)));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(row);
            return new ScreenHolder(card, check, name, type);
        }
        @Override public void onBindViewHolder(@NonNull ScreenHolder holder, int position) {
            ProjectFileBean bean = visible.get(position);
            holder.name.setText(bean.fileName);
            holder.type.setText(bean.fileName.endsWith("_fragment") ? "Fragment-based screen" : "Activity screen");
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setChecked(selected.contains(bean.fileName));
            holder.check.setEnabled(!busy || selected.contains(bean.fileName));
            holder.itemView.setOnClickListener(v -> holder.check.setChecked(!holder.check.isChecked()));
            holder.check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) {
                    if (selected.size() >= MAX_SELECTION && !selected.contains(bean.fileName)) {
                        button.setChecked(false); showMessage("Select up to " + MAX_SELECTION + " screens per package."); return;
                    }
                    selected.add(bean.fileName);
                } else selected.remove(bean.fileName);
                updateSelectionUi();
            });
        }
        @Override public int getItemCount() { return visible.size(); }
    }

    private static final class ScreenHolder extends RecyclerView.ViewHolder {
        final CheckBox check; final TextView name; final TextView type;
        ScreenHolder(View root, CheckBox check, TextView name, TextView type) { super(root); this.check = check; this.name = name; this.type = type; }
    }

    private void updateEmptyVisibility() {
        if (emptyView == null || adapter == null) return;
        if (allScreens.isEmpty()) emptyView.setVisibility(View.VISIBLE);
        else if (adapter.getItemCount() == 0) { emptyView.setText("No screens match this search."); emptyView.setVisibility(View.VISIBLE); }
        else emptyView.setVisibility(View.GONE);
    }
}
