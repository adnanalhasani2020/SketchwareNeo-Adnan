package pro.sketchware.activities.main.fragments.templates;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;

final class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.Holder> {
    interface OnTemplateClick { void onClick(TemplateCatalogItem item); }
    private final List<TemplateCatalogItem> items = new ArrayList<>();
    private final OnTemplateClick click;

    TemplateAdapter(OnTemplateClick click) { this.click = click; }

    void submit(List<TemplateCatalogItem> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    TemplateCatalogItem getItem(int position) { return position >= 0 && position < items.size() ? items.get(position) : null; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_template, parent, false);
        return new Holder(view);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        TemplateCatalogItem item = items.get(position);
        holder.title.setText(item.title);
        holder.description.setText(item.description.isBlank() ? "No description provided." : item.description);
        holder.category.setText(item.category);
        StringBuilder metadata = new StringBuilder();
        if (item.screenCount >= 0) metadata.append(item.screenCount).append(item.screenCount == 1 ? " screen" : " screens");
        if (item.downloads >= 0) {
            if (metadata.length() > 0) metadata.append("  ·  ");
            metadata.append(formatCount(item.downloads)).append(" downloads");
        }
        holder.metadata.setText(metadata.toString());
        holder.metadata.setVisibility(metadata.length() == 0 ? View.GONE : View.VISIBLE);
        if (!item.previewImageUrl.isBlank()) {
            Glide.with(holder.preview).load(item.previewImageUrl).centerCrop()
                    .placeholder(R.drawable.ic_mtrl_preview).error(R.drawable.ic_mtrl_preview).into(holder.preview);
        } else {
            Glide.with(holder.preview).clear(holder.preview);
            holder.preview.setImageResource(R.drawable.ic_mtrl_preview);
        }
        holder.itemView.setOnClickListener(v -> { if (click != null) click.onClick(item); });
    }

    @Override public int getItemCount() { return items.size(); }

    private static String formatCount(long count) {
        if (count >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1_000_000d);
        if (count >= 1_000) return String.format(Locale.ROOT, "%.1fK", count / 1_000d);
        return Long.toString(count);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView preview;
        final TextView title;
        final TextView description;
        final TextView metadata;
        final com.google.android.material.chip.Chip category;
        Holder(View itemView) {
            super(itemView);
            preview = itemView.findViewById(R.id.template_preview);
            title = itemView.findViewById(R.id.template_title);
            description = itemView.findViewById(R.id.template_description);
            metadata = itemView.findViewById(R.id.template_metadata);
            category = itemView.findViewById(R.id.template_category);
        }
    }
}
