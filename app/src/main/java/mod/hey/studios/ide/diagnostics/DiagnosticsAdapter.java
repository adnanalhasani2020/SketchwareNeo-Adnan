package mod.hey.studios.ide.diagnostics;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class DiagnosticsAdapter extends RecyclerView.Adapter<DiagnosticsAdapter.VH> {
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final OnDiagnosticClickListener listener;

    public interface OnDiagnosticClickListener {
        void onDiagnosticClick(Diagnostic diagnostic);
    }

    public DiagnosticsAdapter(List<Diagnostic> diagnostics, OnDiagnosticClickListener listener) {
        if (diagnostics != null) this.diagnostics.addAll(diagnostics);
        this.listener = listener;
    }

    public void update(List<Diagnostic> items) {
        diagnostics.clear();
        if (items != null) diagnostics.addAll(items);
        notifyDataSetChanged();
    }

    private static String formatLocation(Diagnostic d) {
        if (d.fileName == null || d.fileName.isEmpty()) return d.line > 0 ? "Line " + d.line : "";
        if (d.line <= 0) return d.fileName;
        return d.column > 0 ? d.fileName + ":" + d.line + ":" + d.column : d.fileName + ":" + d.line;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Programmatically creating M3 list item for performance & simplicity
        android.widget.LinearLayout root = new android.widget.LinearLayout(parent.getContext());
        root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        root.setPadding(SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12));
        
        android.util.TypedValue outValue = new android.util.TypedValue();
        parent.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        root.setBackgroundResource(outValue.resourceId);
        root.setClickable(true);
        root.setFocusable(true);

        ImageView icon = new ImageView(parent.getContext());
        icon.setId(View.generateViewId());
        root.addView(icon, new android.widget.LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

        android.widget.LinearLayout textContainer = new android.widget.LinearLayout(parent.getContext());
        textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textParams.setMarginStart(SketchwareUtil.dpToPx(16));
        root.addView(textContainer, textParams);

        TextView message = new TextView(parent.getContext());
        message.setId(View.generateViewId());
        message.setTextSize(14f);
        message.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
        textContainer.addView(message);

        TextView location = new TextView(parent.getContext());
        location.setId(View.generateViewId());
        location.setTextSize(12f);
        location.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurfaceVariant));
        textContainer.addView(location);

        return new VH(root, icon, message, location);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Diagnostic d = diagnostics.get(position);
        holder.message.setText(d.message);
        holder.location.setText(formatLocation(d));
        holder.location.setVisibility(holder.location.getText().length() == 0 ? View.GONE : View.VISIBLE);
        
        if (d.severity == Diagnostic.Severity.ERROR) {
            holder.icon.setImageResource(R.drawable.ic_mtrl_cancel);
            holder.icon.setColorFilter(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorError));
        } else if (d.severity == Diagnostic.Severity.WARNING) {
            holder.icon.setImageResource(R.drawable.ic_mtrl_warning);
            holder.icon.setColorFilter(Color.parseColor("#FFC107"));
        } else {
            holder.icon.setImageResource(R.drawable.ic_mtrl_info);
            holder.icon.setColorFilter(Color.parseColor("#2196F3"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDiagnosticClick(d);
        });
    }

    @Override
    public int getItemCount() { return diagnostics.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon; TextView message, location;
        VH(View itemView, ImageView icon, TextView message, TextView location) {
            super(itemView);
            this.icon = icon; this.message = message; this.location = location;
        }
    }
}
