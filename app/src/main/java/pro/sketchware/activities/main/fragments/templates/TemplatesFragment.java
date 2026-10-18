package pro.sketchware.activities.main.fragments.templates;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.transition.MaterialFadeThrough;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.besome.sketch.editor.manage.view.ScreenPackageImportActivity;
import pro.sketchware.R;

/** Template catalog browser. Server-backed entries only; never inserts fake demo items. */
public final class TemplatesFragment extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<TemplateCatalogItem> allItems = new ArrayList<>();
    private View root;
    private TextInputEditText search;
    private ChipGroup categories;
    private RecyclerView list;
    private TextView state;
    private MaterialButton retry;
    private View featuredCard;
    private ImageView featuredImage;
    private TextView featuredTitle;
    private TextView featuredDescription;
    private MaterialButton featuredUse;
    private TemplateAdapter adapter;
    private String selectedCategory = "All";
    private String sortMode = "Featured";
    private boolean loading;
    private int loadGeneration;

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialFadeThrough());
        setReturnTransition(new MaterialFadeThrough());
        setExitTransition(new MaterialFadeThrough());
        setReenterTransition(new MaterialFadeThrough());
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_templates, container, false);
        search = root.findViewById(R.id.templates_search);
        categories = root.findViewById(R.id.templates_categories);
        list = root.findViewById(R.id.templates_list);
        state = root.findViewById(R.id.templates_state);
        retry = root.findViewById(R.id.templates_retry);
        featuredCard = root.findViewById(R.id.templates_featured_card);
        featuredImage = root.findViewById(R.id.templates_featured_image);
        featuredTitle = root.findViewById(R.id.templates_featured_title);
        featuredDescription = root.findViewById(R.id.templates_featured_description);
        featuredUse = root.findViewById(R.id.templates_featured_use);
        adapter = new TemplateAdapter(this::openDetails);
        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        list.setAdapter(adapter);
        root.findViewById(R.id.templates_import_local).setOnClickListener(v -> openLocalPackageImport());
        root.findViewById(R.id.templates_sort).setOnClickListener(this::showSortMenu);
        retry.setOnClickListener(v -> loadCatalog(true));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        categories.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            View checked = group.findViewById(checkedIds.get(0));
            if (checked instanceof Chip) selectedCategory = String.valueOf(((Chip) checked).getTag());
            applyFilters();
        });
        loadCatalog(false);
        return root;
    }

    @Override public void onDestroyView() {
        loadGeneration++;
        loading = false;
        if (featuredImage != null) Glide.with(this).clear(featuredImage);
        root = null; search = null; categories = null; list = null; state = null; retry = null;
        featuredCard = null; featuredImage = null; featuredTitle = null; featuredDescription = null; featuredUse = null; adapter = null;
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadCatalog(boolean fromRetry) {
        if (!isAdded() || root == null || loading) return;
        loading = true;
        int generation = ++loadGeneration;
        Context appContext = requireContext().getApplicationContext();
        state.setVisibility(View.VISIBLE);
        state.setText(fromRetry ? "Refreshing templates…" : "Loading templates from template.sketchlib.in…");
        retry.setVisibility(View.GONE);
        executor.execute(() -> {
            TemplateCatalogRepository.CatalogResult loaded = null;
            String error = null;
            try { loaded = TemplateCatalogRepository.fetchCatalogResult(appContext); }
            catch (Exception exception) { error = safeMessage(exception); }
            TemplateCatalogRepository.CatalogResult result = loaded;
            String failure = error;
            mainHandler.post(() -> {
                if (!isAdded() || root == null || generation != loadGeneration) return;
                loading = false;
                if (failure != null) {
                    state.setText("Template catalog is unavailable. Check your connection and try again.\n\n" + failure);
                    state.setVisibility(View.VISIBLE);
                    retry.setVisibility(View.VISIBLE);
                    allItems.clear(); adapter.submit(List.of()); updateFeatured(null);
                    rebuildCategories();
                    return;
                }
                allItems.clear();
                if (result != null) allItems.addAll(result.items);
                rebuildCategories();
                applyFilters();
                if (result != null && result.staleCache && root != null) {
                    Snackbar.make(root, "Showing saved templates. Refresh failed: " + result.refreshError, Snackbar.LENGTH_LONG).show();
                }
                if (allItems.isEmpty()) {
                    state.setText("No templates have been published to the catalog yet.");
                    state.setVisibility(View.VISIBLE);
                    retry.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void rebuildCategories() {
        if (categories == null) return;
        categories.removeAllViews();
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        distinct.add("All");
        for (TemplateCatalogItem item : allItems) if (!item.category.isBlank()) distinct.add(item.category);
        if (!distinct.contains(selectedCategory)) selectedCategory = "All";
        for (String value : distinct) {
            Chip chip = new Chip(requireContext());
            chip.setText(value);
            chip.setTag(value);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setCheckedIconVisible(false);
            chip.setId(View.generateViewId());
            categories.addView(chip);
            if (value.equals(selectedCategory)) categories.check(chip.getId());
        }
    }

    private void applyFilters() {
        if (adapter == null || state == null || root == null) return;
        String needle = search == null || search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<TemplateCatalogItem> visible = new ArrayList<>();
        for (TemplateCatalogItem item : allItems) {
            if (!"All".equals(selectedCategory) && !selectedCategory.equals(item.category)) continue;
            if (!needle.isEmpty() && !(item.title.toLowerCase(Locale.ROOT).contains(needle)
                    || item.description.toLowerCase(Locale.ROOT).contains(needle)
                    || item.category.toLowerCase(Locale.ROOT).contains(needle)
                    || String.join(" ", item.tags).toLowerCase(Locale.ROOT).contains(needle))) continue;
            visible.add(item);
        }
        Comparator<TemplateCatalogItem> nameComparator = Comparator.comparing(item -> item.title.toLowerCase(Locale.ROOT));
        switch (sortMode) {
            case "Name A–Z": visible.sort(nameComparator); break;
            case "Newest": visible.sort(Comparator.comparingLong((TemplateCatalogItem item) -> item.updatedAtMillis() < 0 ? Long.MIN_VALUE : item.updatedAtMillis()).reversed().thenComparing(nameComparator)); break;
            case "Most downloaded": visible.sort(Comparator.comparingLong((TemplateCatalogItem item) -> item.downloads < 0 ? Long.MIN_VALUE : item.downloads).reversed().thenComparing(nameComparator)); break;
            default:
                visible.sort(Comparator.comparing((TemplateCatalogItem item) -> !item.featured).thenComparing(nameComparator));
                break;
        }
        adapter.submit(visible);
        if (visible.isEmpty()) {
            state.setText(allItems.isEmpty() ? "No templates are available." : "No templates match your search or category.");
            state.setVisibility(View.VISIBLE);
        } else state.setVisibility(View.GONE);
        retry.setVisibility(allItems.isEmpty() ? View.VISIBLE : View.GONE);
        TemplateCatalogItem featured = null;
        if (needle.isEmpty() && "All".equals(selectedCategory)) {
            for (TemplateCatalogItem item : allItems) if (item.featured) { featured = item; break; }
        }
        updateFeatured(featured);
    }

    private void updateFeatured(TemplateCatalogItem item) {
        if (featuredCard == null) return;
        if (item == null) {
            featuredCard.setVisibility(View.GONE);
            if (featuredImage != null) Glide.with(this).clear(featuredImage);
            return;
        }
        featuredCard.setVisibility(View.VISIBLE);
        featuredTitle.setText(item.title);
        featuredDescription.setText(item.description.isBlank() ? "Open this template to inspect included screens and import options." : item.description);
        if (!item.previewImageUrl.isBlank()) {
            Glide.with(this).load(item.previewImageUrl).centerCrop().placeholder(R.drawable.ic_mtrl_preview).error(R.drawable.ic_mtrl_preview).into(featuredImage);
        } else {
            Glide.with(this).clear(featuredImage);
            featuredImage.setImageResource(R.drawable.ic_mtrl_preview);
        }
        featuredUse.setOnClickListener(v -> openDetails(item));
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        String[] choices = {"Featured", "Newest", "Most downloaded", "Name A–Z"};
        for (int i = 0; i < choices.length; i++) menu.getMenu().add(0, i, i, choices[i]);
        menu.setOnMenuItemClickListener(item -> {
            sortMode = choices[item.getItemId()];
            TextView button = anchor instanceof TextView ? (TextView) anchor : null;
            if (button != null) button.setText(sortMode);
            applyFilters();
            return true;
        });
        menu.show();
    }

    private void openDetails(TemplateCatalogItem item) {
        if (!isAdded() || item == null) return;
        try {
            Intent intent = new Intent(requireContext(), TemplateDetailsActivity.class);
            intent.putExtra(TemplateDetailsActivity.EXTRA_TEMPLATE_JSON, item.toJson().toString());
            startActivity(intent);
        } catch (Exception exception) {
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Template details")
                    .setMessage("Could not open this template: " + safeMessage(exception)).setPositiveButton("OK", null).show();
        }
    }

    private void openLocalPackageImport() {
        if (!isAdded()) return;
        try {
            startActivity(new Intent(requireContext(), ScreenPackageImportActivity.class));
        } catch (Exception error) {
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Import screen package")
                    .setMessage("Could not open the screen-package importer: " + safeMessage(error))
                    .setPositiveButton("OK", null).show();
        }
    }

    private static String safeMessage(Throwable exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.isBlank() ? "Unknown error" : message;
    }
}
