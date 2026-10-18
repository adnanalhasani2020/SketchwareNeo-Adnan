package pro.sketchware.activities.main.fragments.templates;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.editor.manage.view.ScreenPackageImportActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;

/** Details and verified import entry point for one server-published template. */
public final class TemplateDetailsActivity extends BaseAppCompatActivity {
    public static final String EXTRA_TEMPLATE_JSON = "template_json";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TemplateCatalogItem item;
    private MaterialButton useButton;
    private TextView status;
    private ImageView heroImage;
    private boolean busy;

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String json = getIntent().getStringExtra(EXTRA_TEMPLATE_JSON);
        try {
            if (json == null || json.length() > 64 * 1024) throw new IllegalArgumentException("Template details are missing or too large.");
            item = TemplateCatalogItem.fromJson(new JSONObject(json));
        } catch (Exception error) {
            new MaterialAlertDialogBuilder(this).setTitle("Template details unavailable")
                    .setMessage("The template metadata could not be validated: " + safeMessage(error))
                    .setPositiveButton("Close", (dialog, which) -> finish()).show();
            return;
        }
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, dp(24));
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Template details");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        heroImage = new ImageView(this);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(heroImage, R.attr.colorSurfaceContainerHigh));
        heroImage.setContentDescription("Template preview");
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(210));
        imageParams.setMargins(dp(16), dp(12), dp(16), 0);
        root.addView(heroImage, imageParams);
        loadImage(item.previewImageUrl, heroImage);

        LinearLayout titleArea = new LinearLayout(this);
        titleArea.setOrientation(LinearLayout.VERTICAL);
        titleArea.setPadding(dp(20), dp(16), dp(20), dp(12));
        root.addView(titleArea);
        TextView title = text(item.title, 24, R.attr.colorOnSurface, true);
        titleArea.addView(title);
        String subtitle = item.category;
        if (!item.developer.isBlank()) subtitle += "  ·  by " + item.developer;
        TextView developer = text(subtitle, 14, R.attr.colorOnSurfaceVariant, false);
        developer.setPadding(0, dp(4), 0, 0);
        titleArea.addView(developer);

        StringBuilder metadata = new StringBuilder();
        if (!item.version.isBlank()) metadata.append("Version ").append(item.version);
        if (item.screenCount >= 0) { if (metadata.length() > 0) metadata.append("  ·  "); metadata.append(item.screenCount).append(item.screenCount == 1 ? " screen" : " screens"); }
        if (item.downloads >= 0) { if (metadata.length() > 0) metadata.append("  ·  "); metadata.append(formatCount(item.downloads)).append(" downloads"); }
        if (!item.updatedAt.isBlank()) { if (metadata.length() > 0) metadata.append("  ·  "); metadata.append("Updated ").append(item.updatedAt); }
        if (metadata.length() > 0) titleArea.addView(text(metadata.toString(), 12, R.attr.colorOnSurfaceVariant, false));

        useButton = new MaterialButton(this);
        useButton.setText("Use template");
        useButton.setIconResource(R.drawable.ic_mtrl_download);
        useButton.setOnClickListener(v -> useTemplate());
        LinearLayout.LayoutParams useParams = new LinearLayout.LayoutParams(-1, dp(52));
        useParams.setMargins(dp(16), 0, dp(16), dp(10));
        root.addView(useButton, useParams);

        status = text("Templates are imported as editable native Sketchware Neo screens. Existing screens are not overwritten.", 13, R.attr.colorOnSurfaceVariant, false);
        status.setPadding(dp(20), dp(4), dp(20), dp(12));
        root.addView(status);

        if (!item.description.isBlank()) addSection(root, "Description", item.description);
        if (!item.features.isEmpty()) addSection(root, "What's included", String.join("\n•  ", item.features));
        if (!item.tags.isEmpty()) addSection(root, "Tags", String.join("  ·  ", item.tags));
        if (!item.minHostVersion.isBlank()) addSection(root, "Compatibility", "Minimum Sketchware Neo host version: " + item.minHostVersion + ". The screen package's schema will also be checked before import.");

        if (!item.screenshots.isEmpty()) {
            addHeading(root, "Preview gallery");
            HorizontalScrollView galleryScroll = new HorizontalScrollView(this);
            galleryScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout gallery = new LinearLayout(this);
            gallery.setOrientation(LinearLayout.HORIZONTAL);
            gallery.setPadding(dp(12), dp(4), dp(12), dp(4));
            galleryScroll.addView(gallery);
            for (String url : item.screenshots) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setContentDescription("Template screenshot");
                image.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(image, R.attr.colorSurfaceContainerHigh));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(230), dp(150));
                params.setMargins(dp(4), 0, dp(8), 0);
                gallery.addView(image, params);
                loadImage(url, image);
            }
            root.addView(galleryScroll);
        }

        if (!item.documentationUrl.isBlank() || !item.sourceCodeUrl.isBlank()) {
            addHeading(root, "Useful links");
            if (!item.documentationUrl.isBlank()) addLink(root, "Documentation", item.documentationUrl);
            if (!item.sourceCodeUrl.isBlank()) addLink(root, "Source / project page", item.sourceCodeUrl);
        }
        scroll.addView(root);
        setContentView(scroll);
    }

    private void useTemplate() {
        if (busy || item == null) return;
        busy = true;
        useButton.setEnabled(false);
        useButton.setText("Downloading and verifying…");
        status.setText("Downloading from the catalog and checking SHA-256 before import. This may take a moment.");
        TemplateCatalogItem selected = item;
        executor.execute(() -> {
            File packageFile = null;
            String failure = null;
            try {
                packageFile = TemplateCatalogRepository.downloadAndVerify(getApplicationContext(), selected);
                // readPackage performs a second, format-level validation before handing it to the importer.
                com.besome.sketch.editor.manage.view.ScreenPackageManager.readPackage(packageFile);
            } catch (Exception error) { failure = safeMessage(error); }
            File result = packageFile;
            String message = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                useButton.setEnabled(true);
                useButton.setText("Use template");
                if (message != null || result == null) {
                    status.setText("The template could not be verified and was not imported.");
                    new MaterialAlertDialogBuilder(this).setTitle("Template download failed")
                            .setMessage(message == null ? "Unknown download error" : message)
                            .setPositiveButton("OK", null).show();
                    return;
                }
                Intent intent = new Intent(this, ScreenPackageImportActivity.class);
                intent.putExtra(ScreenPackageImportActivity.EXTRA_PACKAGE_PATH, result.getAbsolutePath());
                try {
                    startActivity(intent);
                } catch (Exception error) {
                    status.setText("The package was downloaded and verified, but the importer could not be opened.");
                    new MaterialAlertDialogBuilder(this).setTitle("Could not open importer")
                            .setMessage("The template was downloaded and verified, but the importer could not be opened. Tap Use template to retry. " + safeMessage(error))
                            .setPositiveButton("OK", null).show();
                }
            });
        });
    }

    private void addSection(LinearLayout root, String heading, String body) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18)); card.setCardElevation(0f);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(dp(16), dp(12), dp(16), dp(14));
        column.addView(text(heading, 17, R.attr.colorOnSurface, true));
        TextView text = text(body, 14, R.attr.colorOnSurfaceVariant, false);
        text.setPadding(0, dp(8), 0, 0);
        column.addView(text);
        card.addView(column);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(16), dp(5), dp(16), dp(8));
        root.addView(card, params);
    }

    private void addHeading(LinearLayout root, String label) {
        TextView heading = text(label, 18, R.attr.colorOnSurface, true);
        heading.setPadding(dp(20), dp(16), dp(20), dp(8));
        root.addView(heading);
    }

    private void addLink(LinearLayout root, String label, String address) {
        MaterialButton link = new MaterialButton(this);
        link.setText(label);
        link.setIconResource(R.drawable.ic_mtrl_open_in_new);
        link.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        link.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address))); }
            catch (Exception error) { new MaterialAlertDialogBuilder(this).setMessage("No app could open this link.").setPositiveButton("OK", null).show(); }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(dp(16), dp(2), dp(16), dp(4));
        root.addView(link, params);
    }

    private void loadImage(String url, ImageView image) {
        if (url == null || url.isBlank()) {
            image.setImageResource(R.drawable.ic_mtrl_preview);
            return;
        }
        Glide.with(this).load(url).centerCrop().placeholder(R.drawable.ic_mtrl_preview).error(R.drawable.ic_mtrl_preview).into(image);
    }

    private TextView text(String value, int size, int attrColor, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size);
        view.setTextColor(com.google.android.material.color.MaterialColors.getColor(view, attrColor));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private static String formatCount(long count) {
        if (count >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", count / 1_000_000d);
        if (count >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fK", count / 1_000d);
        return Long.toString(count);
    }
    private static String safeMessage(Throwable error) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.isBlank() ? "Unknown error" : value;
    }
    @Override public void onDestroy() { executor.shutdownNow(); if (heroImage != null) Glide.with(this).clear(heroImage); super.onDestroy(); }
}
