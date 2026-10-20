package com.besome.sketch.ctrls;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import pro.sketchware.R;
import pro.sketchware.databinding.VarIdSpinnerItemBinding;

public class ViewIdSpinnerItem extends LinearLayout {

    private boolean isDropDown;

    private final VarIdSpinnerItemBinding binding;

    public ViewIdSpinnerItem(Context context) {
        super(context);
        binding = VarIdSpinnerItemBinding.inflate(LayoutInflater.from(context), this, true);
    }

    public void a(int iconResId, String text, boolean isSelected) {
        a(iconResId, text, "", isSelected);
    }

    public void a(int iconResId, String text, String typeName, boolean isSelected) {
        if (isSelected) {
            binding.imgvSelected.setVisibility(View.VISIBLE);
        } else {
            binding.imgvSelected.setVisibility(View.GONE);
        }

        String viewId = text == null ? "" : text;
        if (!viewId.isEmpty() && viewId.charAt(0) == '_') {
            binding.name.setText(viewId.substring(1));
            a(false, 0xffff5555, 0xfff8f820);
        } else {
            binding.name.setText(viewId);
            a(true, getResources().getColor(R.color.view_property_spinner_filter), getResources().getColor(R.color.view_property_spinner_filter));
        }

        binding.icon.setImageResource(iconResId);
        binding.icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                com.google.android.material.color.MaterialColors.getColor(binding.icon, R.attr.colorOnSurfaceVariant)));
        if (binding.typeName != null) {
            binding.typeName.setText(typeName == null ? "" : typeName);
            binding.typeName.setVisibility(typeName == null || typeName.isEmpty() ? View.GONE : View.VISIBLE);
            binding.typeName.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    binding.typeName, R.attr.colorOnSurfaceVariant));
        }
    }

    public void a(boolean notSelected, int color, int var3) {
        if (notSelected) {
            if (!isDropDown) color = var3;
            binding.name.setTextColor(color);
            binding.name.setTypeface(null, Typeface.NORMAL);
        } else {
            if (!isDropDown) color = var3;
            binding.name.setTextColor(color);
            binding.name.setTypeface(null, Typeface.BOLD_ITALIC);
        }
    }

    public void setDropDown(boolean isDropDown) {
        this.isDropDown = isDropDown;
    }

    public void setTextSize(int textSize) {
    }
}