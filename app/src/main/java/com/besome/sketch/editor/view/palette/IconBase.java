package com.besome.sketch.editor.view.palette;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.lib.base.BaseWidget;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

/**
 * Generic palette icon for components which do not need a specialized visual icon.
 * Every registered name resolves to a concrete ViewBean. Returning null is reserved
 * for a name that is genuinely not registered, and the drag controller reports it.
 */
public class IconBase extends BaseWidget {
    private String name;
    private String initialCustomView = "";

    public IconBase(Context context) {
        super(context);
        setBackgroundResource(R.drawable.icon_bg);
    }

    public ViewBean getBean() {
        if (name == null || name.trim().isEmpty()) return null;
        String viewName = name.trim();
        int type;
        String convert = "";
        switch (viewName) {
            case "include" -> {
                type = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
                convert = "include";
            }
            case "MaterialIconButton" -> {
                type = ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON;
                convert = "com.google.android.material.button.MaterialButton";
            }
            case "MaterialButton" -> {
                type = ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON;
                convert = "com.google.android.material.button.MaterialButton";
            }
            case "TextInputEditText" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_EDITTEXT;
                convert = "com.google.android.material.textfield.TextInputEditText";
            }
            case "MaterialAutoCompleteTextView" -> {
                type = ViewBeans.VIEW_TYPE_WIDGET_AUTOCOMPLETETEXTVIEW;
                convert = "com.google.android.material.textfield.MaterialAutoCompleteTextView";
            }
            case "MaterialTextView" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW;
                convert = "com.google.android.material.textview.MaterialTextView";
            }
            case "MaterialSwitch" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_SWITCH;
                convert = "com.google.android.material.materialswitch.MaterialSwitch";
            }
            case "SwitchMaterial" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_SWITCH;
                convert = "com.google.android.material.switchmaterial.SwitchMaterial";
            }
            case "MaterialCheckBox" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_CHECKBOX;
                convert = "com.google.android.material.checkbox.MaterialCheckBox";
            }
            case "MaterialRadioButton" -> {
                type = ViewBeans.VIEW_TYPE_WIDGET_RADIOBUTTON;
                convert = "com.google.android.material.radiobutton.MaterialRadioButton";
            }
            case "MaterialCardView" -> {
                type = ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW;
                convert = "com.google.android.material.card.MaterialCardView";
            }
            case "CardView" -> {
                type = ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW;
                convert = "androidx.cardview.widget.CardView";
            }
            case "TextInputLayout" -> {
                type = ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT;
                convert = "com.google.android.material.textfield.TextInputLayout";
            }
            case "MaterialProgressIndicator" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR;
                convert = "com.google.android.material.progressindicator.CircularProgressIndicator";
            }
            case "CircularProgressIndicator" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR;
                convert = "com.google.android.material.progressindicator.CircularProgressIndicator";
            }
            case "LinearProgressIndicator" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR;
                convert = "com.google.android.material.progressindicator.LinearProgressIndicator";
            }
            case "LoadingIndicator" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR;
                convert = "com.google.android.material.progressindicator.LoadingIndicator";
            }
            case "Slider", "MaterialSlider" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_SEEKBAR;
                convert = "com.google.android.material.slider.Slider";
            }
            case "Chip" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_CHECKBOX;
                convert = "com.google.android.material.chip.Chip";
            }
            case "ChipGroup" -> {
                type = ViewBeans.VIEW_TYPE_LAYOUT_RADIOGROUP;
                convert = "com.google.android.material.chip.ChipGroup";
            }
            case "MaterialButtonToggleGroup" -> {
                type = ViewBeans.VIEW_TYPE_LAYOUT_RADIOGROUP;
                convert = "com.google.android.material.button.MaterialButtonToggleGroup";
            }
            case "ExtendedFloatingActionButton" -> {
                // Treat this button-like M3 control as a MaterialButton in the editor model;
                // type 16 is reserved for Sketchware's floating action button overlay.
                type = ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON;
                convert = "com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton";
            }
            case "ShapeableImageView" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW;
                convert = "com.google.android.material.imageview.ShapeableImageView";
            }
            case "ImageButton" -> {
                type = ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW;
                convert = "android.widget.ImageButton";
            }
            case "NestedScrollView" -> {
                type = ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW;
                convert = "androidx.core.widget.NestedScrollView";
            }
            case "CoordinatorLayout" -> {
                type = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
                convert = "androidx.coordinatorlayout.widget.CoordinatorLayout";
            }
            default -> {
                type = ViewBean.getViewTypeByTypeName(viewName);
                if (type == ViewBean.VIEW_TYPE_LAYOUT_LINEAR && !viewName.equals("LinearLayout")) {
                    return null;
                }
                if (type == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT) {
                    convert = "androidx.constraintlayout.widget.ConstraintLayout";
                }
            }
        }

        ViewBean bean = new ViewBean();
        bean.type = type;
        bean.convert = convert;
        bean.id = "";
        bean.name = viewName;
        bean.layout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        bean.layout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        bean.layout.gravity = Gravity.CENTER;
        bean.layout.paddingLeft = 8;
        bean.layout.paddingTop = 8;
        bean.layout.paddingRight = 8;
        bean.layout.paddingBottom = 8;

        if (viewName.equals("include")) {
            bean.customView = initialCustomView == null ? "" : initialCustomView;
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bean.layout.orientation = VERTICAL;
        } else if (type == ViewBean.VIEW_TYPE_LAYOUT_LINEAR
                || type == ViewBean.VIEW_TYPE_LAYOUT_RELATIVE
                || type == ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW
                || type == ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW
                || type == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT
                || type == ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW
                || type == ViewBeans.VIEW_TYPE_LAYOUT_TEXTINPUTLAYOUT
                || type == ViewBeans.VIEW_TYPE_LAYOUT_SWIPEREFRESHLAYOUT
                || type == ViewBeans.VIEW_TYPE_LAYOUT_RADIOGROUP
                || type == ViewBeans.VIEW_TYPE_LAYOUT_TABLAYOUT
                || type == ViewBeans.VIEW_TYPE_LAYOUT_VIEWPAGER
                || type == ViewBeans.VIEW_TYPE_LAYOUT_BOTTOMNAVIGATIONVIEW
                || type == ViewBeans.VIEW_TYPE_LAYOUT_COLLAPSINGTOOLBARLAYOUT) {
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bean.layout.orientation = VERTICAL;
        }

        switch (type) {
            case ViewBean.VIEW_TYPE_WIDGET_BUTTON,
                    ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW,
                    ViewBean.VIEW_TYPE_WIDGET_CHECKBOX,
                    ViewBean.VIEW_TYPE_WIDGET_SWITCH,
                    ViewBeans.VIEW_TYPE_WIDGET_RADIOBUTTON,
                    ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON -> bean.text.text = "MaterialIconButton".equals(viewName) ? "" : viewName;
            case ViewBean.VIEW_TYPE_WIDGET_EDITTEXT -> bean.text.hint = viewName;
            case ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW -> bean.image.resName = "";
        }
        if (viewName.equals("MaterialIconButton")) {
            bean.text.textColor = Color.WHITE;
            bean.inject = "style=\"@style/Widget.Material3Expressive.Button.IconButton.Filled\"\napp:iconGravity=\"top\"\napp:iconSize=\"24dp\"\napp:cornerRadius=\"24dp\"";
        } else if (viewName.equals("ExtendedFloatingActionButton")) {
            // Show an extended pill-shaped action in the editor, not a generic square button.
            bean.text.text = "Action";
            bean.inject = "app:cornerRadius=\"24dp\"";
        } else if (viewName.equals("MaterialProgressIndicator")
                || viewName.equals("CircularProgressIndicator")) {
            bean.progressStyle = "Circular";
        } else if (viewName.equals("LinearProgressIndicator")) {
            bean.progressStyle = "Linear";
        }
        return bean;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public void setInitialCustomView(String customView) { this.initialCustomView = customView == null ? "" : customView; }

    public void setText(String widgetName) {
        setWidgetNameTextSize(11);
        setWidgetName(widgetName);
    }
}
