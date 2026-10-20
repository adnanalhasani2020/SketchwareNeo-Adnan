package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;
import com.google.android.material.slider.Slider;

import a.a.a.wB;

/** Material Components Slider used by the visual editor. */
public class ItemMaterialSlider extends Slider implements ItemView {
    private ViewBean viewBean;
    private boolean selected;
    private boolean fixed;
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ItemMaterialSlider(Context context) {
        super(context);
        selectionPaint.setColor(0x3399D5D0);
        setDrawingCacheEnabled(true);
    }

    @Override public ViewBean getBean() { return viewBean; }
    @Override public void setBean(ViewBean bean) { viewBean = bean; }
    @Override public boolean getFixed() { return fixed; }
    @Override public void setFixed(boolean value) { fixed = value; }
    @Override public void setSelection(boolean value) { selected = value; invalidate(); }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        float density = wB.a(getContext(), 1.0f);
        super.setPadding(Math.round(left * density), Math.round(top * density),
                Math.round(right * density), Math.round(bottom * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (selected) canvas.drawRect(0, 0, getWidth(), getHeight(), selectionPaint);
        super.onDraw(canvas);
    }
}
