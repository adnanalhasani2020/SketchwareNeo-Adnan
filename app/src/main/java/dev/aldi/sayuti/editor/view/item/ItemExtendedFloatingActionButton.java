package dev.aldi.sayuti.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import a.a.a.wB;

/** Editor canvas wrapper for a genuine Material ExtendedFloatingActionButton. */
public class ItemExtendedFloatingActionButton extends ExtendedFloatingActionButton implements ItemView {
    private final Paint selectionPaint;
    private final Rect selectionRect;
    private final float paddingFactor;
    private ViewBean viewBean;
    private boolean selected;
    private boolean fixed;

    public ItemExtendedFloatingActionButton(Context context) {
        super(context);
        paddingFactor = wB.a(context, 1.0f);
        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(0x9599d5d0);
        selectionRect = new Rect();
        setDrawingCacheEnabled(true);
        setFocusable(false);
    }

    @Override public ViewBean getBean() { return viewBean; }
    @Override public void setBean(ViewBean bean) { viewBean = bean; }
    @Override public boolean getFixed() { return fixed; }
    @Override public void setFixed(boolean value) { fixed = value; }
    public boolean getSelection() { return selected; }

    @Override
    public void setSelection(boolean value) {
        selected = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (selected) {
            selectionRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRect(selectionRect, selectionPaint);
        }
        super.onDraw(canvas);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        float factor = paddingFactor > 0 ? paddingFactor : 1.0f;
        super.setPadding((int) (left * factor), (int) (top * factor),
                (int) (right * factor), (int) (bottom * factor));
    }
}
