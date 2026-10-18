package neo.sketchware.plugin.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import neo.sketchware.plugin.PluginManager;
import neo.sketchware.plugin.marketplace.PluginCatalogItem;
import neo.sketchware.plugin.marketplace.PluginMarketplaceRepository;
import neo.sketchware.plugin.marketplace.PluginVersionUtils;
import pro.sketchware.BuildConfig;
import pro.sketchware.R;

public final class PluginMarketplaceAdapter extends RecyclerView.Adapter<PluginMarketplaceAdapter.ViewHolder> {

    public interface Listener {
        void onOpenDetails(PluginCatalogItem item);
        void onInstallOrUpdate(PluginCatalogItem item);
    }

    private final List<PluginCatalogItem> allItems = new ArrayList<>();
    private final List<PluginCatalogItem> visibleItems = new ArrayList<>();
    private final Map<String, PluginManager.PluginInfo> installedPlugins = new HashMap<>();
    private final Listener listener;

    public PluginMarketplaceAdapter(List<PluginCatalogItem> items, Listener listener) {
        this.listener = listener;
        setItems(items);
    }

    public void setItems(List<PluginCatalogItem> items) {
        allItems.clear();
        if (items != null) allItems.addAll(items);
        visibleItems.clear();
        visibleItems.addAll(allItems);
        notifyDataSetChanged();
    }

    /** Supply installed metadata loaded off the UI thread; bind operations must not read plugin archives from disk. */
    public void setInstalledPlugins(List<PluginManager.PluginInfo> plugins) {
        installedPlugins.clear();
        if (plugins != null) {
            for (PluginManager.PluginInfo plugin : plugins) {
                if (plugin != null && plugin.pluginId() != null) installedPlugins.put(plugin.pluginId(), plugin);
            }
        }
        notifyDataSetChanged();
    }

    public void filter(String query, String category) {
        visibleItems.clear();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String selectedCategory = category == null ? "All" : category;
        for (PluginCatalogItem item : allItems) {
            if (!"All".equalsIgnoreCase(selectedCategory) && !item.category().equalsIgnoreCase(selectedCategory)) continue;
            String searchable = (item.name() + " " + item.pluginId() + " " + item.developer() + " " + item.description() + " " + item.category()).toLowerCase(Locale.ROOT);
            if (normalized.isEmpty() || searchable.contains(normalized)) visibleItems.add(item);
        }
        notifyDataSetChanged();
    }

    public List<PluginCatalogItem> getAllItems() { return new ArrayList<>(allItems); }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plugin_marketplace, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PluginCatalogItem item = visibleItems.get(position);
        Context context = holder.itemView.getContext();
        PluginManager.PluginInfo installed = installedPlugins.get(item.pluginId());
        boolean hasInstalled = installed != null;
        String installedVersion = hasInstalled ? installed.version() : "";
        if (installedVersion == null || installedVersion.isBlank()) installedVersion = PluginMarketplaceRepository.getInstalledVersion(context, item.pluginId());
        boolean updateAvailable = hasInstalled && (installedVersion.isBlank() || PluginVersionUtils.compareVersions(item.version(), installedVersion) > 0);
        boolean compatible = item.apiVersion() <= PluginManager.getCurrentApiVersion()
                && (item.minHostVersion().isBlank() || PluginVersionUtils.compareVersions(BuildConfig.VERSION_NAME, item.minHostVersion()) >= 0);

        holder.name.setText(item.name());
        StringBuilder meta = new StringBuilder();
        if (!item.developer().isBlank()) meta.append(item.developer()).append(" · ");
        meta.append("v").append(item.version());
        if (item.rating() >= 0) meta.append(" · ★ ").append(String.format(Locale.ROOT, "%.1f", item.rating()));
        holder.meta.setText(meta.toString());
        holder.description.setText(item.description().isBlank() ? "No description provided." : item.description());
        holder.state.setText(compatible ? (hasInstalled ? (updateAvailable ? "Update available" : installed.loaded() ? "Installed · Active" : installed.enabled() ? "Installed · Not loaded" : "Installed · Disabled") : "Available · " + item.category()) : "Not compatible with this version");

        if (!item.iconUrl().isBlank() && item.iconUrl().startsWith("https://")) {
            holder.icon.setImageTintList(null);
            Glide.with(holder.icon).load(item.iconUrl()).centerCrop().placeholder(R.drawable.ic_mtrl_plugin).error(R.drawable.ic_mtrl_plugin).into(holder.icon);
        } else {
            Glide.with(holder.icon).clear(holder.icon);
            holder.icon.setImageResource(R.drawable.ic_mtrl_plugin);
            holder.icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(holder.icon, R.attr.colorOnPrimaryContainer)));
        }

        holder.action.setEnabled(compatible && (!hasInstalled || updateAvailable));
        holder.action.setText(!compatible ? "Incompatible" : !hasInstalled ? "Install" : updateAvailable ? "Update" : "Installed");
        holder.action.setOnClickListener(v -> {
            if (compatible && listener != null && (!hasInstalled || updateAvailable)) listener.onInstallOrUpdate(item);
            else if (listener != null) listener.onOpenDetails(item);
        });
        holder.itemView.setOnClickListener(v -> { if (listener != null) listener.onOpenDetails(item); });
    }

    @Override public int getItemCount() { return visibleItems.size(); }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView meta;
        final TextView description;
        final TextView state;
        final MaterialButton action;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.marketplace_plugin_icon);
            name = itemView.findViewById(R.id.marketplace_plugin_name);
            meta = itemView.findViewById(R.id.marketplace_plugin_meta);
            description = itemView.findViewById(R.id.marketplace_plugin_description);
            state = itemView.findViewById(R.id.marketplace_plugin_state);
            action = itemView.findViewById(R.id.marketplace_plugin_action);
        }
    }
}
