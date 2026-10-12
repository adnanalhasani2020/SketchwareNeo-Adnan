package com.besome.sketch.editor.manage.library;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.besome.sketch.beans.ProjectLibraryBean;
import com.google.android.material.divider.MaterialDivider;

import a.a.a.wB;
import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

public class LibraryItemView extends FrameLayout {
    public final ViewGroup container;
    public final ImageView icon;
    public final TextView title;
    public final TextView description;
    public final TextView enabled;
    public final MaterialDivider divider;
    protected final Context context;

    public LibraryItemView(Context context) {
        super(context);
        this.context = context;

        wB.a(context, this, R.layout.manage_library_base_item);
        container = findViewById(R.id.container);
        icon = findViewById(R.id.lib_icon);
        title = findViewById(R.id.lib_title);
        description = findViewById(R.id.lib_desc);
        enabled = findViewById(R.id.tv_enable);
        divider = findViewById(R.id.divider);

        setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int dividerColor = ThemeUtils.isDarkThemeEnabled(context) ? R.attr.colorSurfaceContainerHighest : R.attr.colorOutlineVariant;
        divider.setDividerColor(ThemeUtils.getColor(context, dividerColor));
    }

    public void setData(ProjectLibraryBean projectLibraryBean) {
        boolean enabledChecked = ProjectLibraryBean.LIB_USE_Y.equals(projectLibraryBean.useYn);
        CharSequence enabledLabel = enabledChecked ? "ON" : "OFF";

        int iconResource = ProjectLibraryBean.getLibraryIcon(projectLibraryBean.libType);
        // The AdMob library entry now represents the combined Ad Networks feature.
        // Resolve its icon explicitly and apply the Neo theme tint so it remains visible
        // even when drawable and ImageView tint sources differ across theme variants.
        if (projectLibraryBean.libType == ProjectLibraryBean.PROJECT_LIB_TYPE_ADMOB) {
            iconResource = R.drawable.ic_mtrl_ads;
        }
        if (iconResource != 0) {
            icon.setImageResource(iconResource);
            icon.setVisibility(View.VISIBLE);
            icon.setAlpha(1f);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            ViewGroup.LayoutParams iconParams = icon.getLayoutParams();
            if (iconParams != null) {
                iconParams.width = dp(24);
                iconParams.height = dp(24);
                icon.setLayoutParams(iconParams);
            }
            icon.setColorFilter(ThemeUtils.getColor(context, R.attr.colorOnSurfaceVariant));
            int titleResource = ProjectLibraryBean.getLibraryResName(projectLibraryBean.libType);
            icon.setContentDescription(titleResource != 0 ? context.getString(titleResource) : "Library icon");
        } else {
            icon.setImageDrawable(null);
            icon.setVisibility(View.INVISIBLE);
        }
        title.setText(ProjectLibraryBean.getLibraryResName(projectLibraryBean.libType));
        description.setText(ProjectLibraryBean.getLibraryResDesc(projectLibraryBean.libType));
        enabled.setText(enabledLabel);
        enabled.setSelected(enabledChecked);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    public void setHideEnabled() {
        enabled.setVisibility(View.GONE);
    }

    public void showDivider(boolean showDivider) {
        divider.setVisibility(showDivider ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setOnClickListener(View.OnClickListener listener) {
        container.setOnClickListener(listener);
    }

    @Override
    public Object getTag() {
        return container.getTag();
    }

    @Override
    public void setTag(Object tag) {
        container.setTag(tag);
    }
}
