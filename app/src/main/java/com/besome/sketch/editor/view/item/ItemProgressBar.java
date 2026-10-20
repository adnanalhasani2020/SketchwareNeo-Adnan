package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;

import a.a.a.wB;
import pro.sketchware.R;

public class ItemProgressBar extends LinearLayout implements ItemView {

    private ViewBean viewBean;
    private boolean isSelected;
    private boolean isFixed;
    private Paint paint;
    private float dip;
    private ImageView imageView;

    public ItemProgressBar(Context context) {
        super(context);
        initialize(context);
    }

    private void initialize(Context context) {
        dip = wB.a(context, 1.0F);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x9599d5d0);
        setDrawingCacheEnabled(true);
        imageView = new ImageView(getContext());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        imageView.setLayoutParams(layoutParams);
        imageView.setImageResource(R.drawable.progressbar_circle_48dp);
        imageView.setScaleType(ScaleType.FIT_XY);
        imageView.setPadding(0, 0, 0, 0);
        addView(imageView);
        setGravity(Gravity.CENTER);
    }

    @Override
    public ViewBean getBean() {
        return viewBean;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        this.viewBean = viewBean;
    }

    @Override
    public boolean getFixed() {
        return isFixed;
    }

    @Override
    public void setFixed(boolean isFixed) {
        this.isFixed = isFixed;
    }

    public boolean getSelection() {
        return isSelected;
    }

    @Override
    public void setSelection(boolean selection) {
        isSelected = selection;
        invalidate();
    }

    @Override
    public void onDraw(Canvas var1) {
        if (isSelected) {
            var1.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
        }
        super.onDraw(var1);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) (left * dip),
                (int) (top * dip),
                (int) (right * dip),
                (int) (bottom * dip));
    }

    /** Updates the editor-only preview from the actual indicator class and its style attribute. */
    public void setProgressBarStyle(ViewBean bean) {
        if (bean == null) return;
        String convert = bean.convert == null ? "" : bean.convert;
        String style = new pro.sketchware.utility.InjectAttributeHandler(bean).getAttributeValueOf("style");
        boolean material = convert.contains(".progressindicator.");
        boolean linear = convert.endsWith("LinearProgressIndicator");
        boolean wavy = style.endsWith("CircularProgressIndicator.Wavy")
                || style.endsWith("LinearProgressIndicator.Wavy");

        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        if (material) {
            if (linear) {
                imageView.setImageResource(wavy
                        ? R.drawable.progressbar_linear_wave_48dp
                        : R.drawable.progressbar_horizontal_48dp);
                params.width = (int) (dip * 220F);
                params.height = (int) (dip * (wavy ? 28F : 8F));
            } else {
                imageView.setImageResource(wavy
                        ? R.drawable.progressbar_circular_wave_48dp
                        : R.drawable.progressbar_circle_48dp);
                int size = (int) (dip * (wavy ? 42F : 30F));
                params.width = size;
                params.height = size;
            }
        } else if ("?android:progressBarStyleHorizontal".equals(bean.progressStyle)) {
            imageView.setImageResource(R.drawable.progressbar_horizontal_48dp);
            params.width = (int) (dip * 220F);
            params.height = (int) (dip * 8F);
        } else {
            imageView.setImageResource(R.drawable.progressbar_circle_48dp);
            int size = (int) (dip * 30F);
            params.width = size;
            params.height = size;
        }
        imageView.setLayoutParams(params);
        requestLayout();
    }
}
