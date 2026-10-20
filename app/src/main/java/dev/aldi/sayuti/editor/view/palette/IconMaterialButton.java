package dev.aldi.sayuti.editor.view.palette;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.AndroidxOrMaterialView;
import com.besome.sketch.editor.view.palette.IconBase;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

public class IconMaterialButton extends IconBase implements AndroidxOrMaterialView {

    private final boolean iconOnly;

    public IconMaterialButton(Context context) {
        this(context, false);
    }

    public IconMaterialButton(Context context, boolean iconOnly) {
        super(context);
        this.iconOnly = iconOnly;
        setWidgetImage(iconOnly ? R.drawable.ic_palette_m3_icon_button : R.drawable.ic_palette_m3_material_button);
        setWidgetName(iconOnly ? "Material Icon Button" : "MaterialButton");
    }

    @Override
    public ViewBean getBean() {
        ViewBean viewBean = new ViewBean();
        viewBean.type = ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON;
        viewBean.layout.orientation = VERTICAL;
        viewBean.layout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        viewBean.layout.gravity = Gravity.CENTER;
        viewBean.layout.paddingLeft = 8;
        viewBean.layout.paddingTop = 8;
        viewBean.layout.paddingRight = 8;
        viewBean.layout.paddingBottom = 8;
        viewBean.text.textColor = Color.WHITE;
        viewBean.text.text = iconOnly ? "" : "MaterialButton";
        viewBean.convert = "com.google.android.material.button.MaterialButton";
        viewBean.inject = iconOnly
                ? "style=\"@style/Widget.Material3Expressive.Button.IconButton.Filled\"\napp:iconGravity=\"top\"\napp:iconSize=\"24dp\"\napp:cornerRadius=\"24dp\""
                : "style=\"@style/Widget.Material3Expressive.Button\"\napp:cornerRadius=\"8dp\"";
        return viewBean;
    }
}
