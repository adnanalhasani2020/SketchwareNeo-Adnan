package com.besome.sketch.editor.property;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.manage.image.ManageImageActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import a.a.a.Cx;
import a.a.a.Gx;
import a.a.a.Kw;
import a.a.a.Lw;
import a.a.a.cC;
import a.a.a.jC;
import a.a.a.mB;
import a.a.a.oq;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.Helper;
import mod.pranav.viewbinding.ViewBindingBuilder;
import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.XmlUtil;

public class ViewPropertyItems extends LinearLayout implements Kw, View.OnClickListener {
    private final boolean b = false;
    private final HashMap<String, View> f = new HashMap<>();
    private String sc_id;
    private ViewBean c;
    private Lw d;
    private ProjectFileBean e;
    private ProjectSettings settings;

    public ViewPropertyItems(Context var1) {
        super(var1);
        Cx.a().a(var1);
    }

    private void setupViews() {
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        setLayoutParams(params);
        setGravity(Gravity.CENTER);
        TextView label = new TextView(getContext());
        label.setGravity(Gravity.CENTER);
        label.setPadding(8, 8, 8, 8);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setText(R.string.design_property_recent_message_nodata);
        addView(label);
    }

    private void a(ViewBean bean) {
        if (getOrientation() == LinearLayout.VERTICAL) {
            a(getContext().getString(R.string.property_header_layout));
        }

        a(bean, "property_margin");
        a(bean, "property_layout_gravity");
    }

    private void a(ViewBean bean, String property) {
        boolean isNotAdview = !bean.getClassInfo().a("AdView");
        boolean isGeneratedId = bean.id.charAt(0) == '_';

        switch (property) {
            case "property_id" -> {
                String id = isGeneratedId ? bean.id.substring(1) : bean.id;
                a(property, id, isGeneratedId, null);
            }
            case "property_layout_width" -> a(property, bean.layout.width, isNotAdview);
            case "property_layout_height" -> a(property, bean.layout.height, isNotAdview);
            case "property_parent_attr" -> setupAttributes(property, bean.parentAttributes);
            case "property_margin" -> {
                LayoutBean layoutBean = bean.layout;
                a(property, layoutBean.marginLeft, layoutBean.marginTop, layoutBean.marginRight, layoutBean.marginBottom);
            }
            case "property_padding" -> {
                LayoutBean layoutBean = bean.layout;
                a("property_padding", layoutBean.paddingLeft, layoutBean.paddingTop, layoutBean.paddingRight, layoutBean.paddingBottom);
            }
            case "property_orientation" -> c(property, bean.layout.orientation);
            case "property_weight_sum" -> b(property, String.valueOf(bean.layout.weightSum));
            case "property_gravity" -> b(property, bean.layout.gravity);
            case "property_layout_gravity" -> b(property, bean.layout.layoutGravity);
            case "property_weight" -> b(property, String.valueOf(bean.layout.weight));
            case "property_text" -> b(property, bean.text.text);
            
            case "property_text_size" -> b(property, String.valueOf(bean.text.textSize));
            
            case "property_text_style" -> c(property, bean.text.textType);
            case "property_text_color" -> r(property, bean.text.resTextColor, bean.text.textColor);
            case "property_hint" -> b(property, bean.text.hint);
            case "property_hint_color" -> r(property, bean.text.resHintColor, bean.text.hintColor);
            case "property_single_line" -> e(property, bean.text.singleLine);
            case "property_lines" -> b(property, String.valueOf(bean.text.line));
            case "property_input_type" -> c(property, bean.text.inputType);
            case "property_ime_option" -> c(property, bean.text.imeOption);
            case "property_image" -> b(property, bean.image.resName, true);
            case "property_scale_type" -> d(property, bean.image.scaleType);
            case "property_background_resource" ->
                    b(property, bean.layout.backgroundResource, false);
            case "property_background_color" ->
                    r(property, bean.layout.backgroundResColor, bean.layout.backgroundColor);
            case "property_enabled" -> e(property, bean.enabled);
            case "property_elevation" -> {
                String el = getInjectedAttr(bean, "android:elevation").replaceAll("[^0-9.]", "");
                b(property, el);
            }
            case "property_visibility" -> b(property, getInjectedAttr(bean, "android:visibility"));
            case "property_rotate" -> b(property, String.valueOf(bean.image.rotate));
            case "property_alpha" -> b(property, String.valueOf(bean.alpha));
            case "property_translation_x" -> b(property, String.valueOf(bean.translationX));
            case "property_translation_y" -> b(property, String.valueOf(bean.translationY));
            case "property_scale_x" -> b(property, String.valueOf(bean.scaleX));
            case "property_scale_y" -> b(property, String.valueOf(bean.scaleY));
            case "property_spinner_mode" -> c(property, bean.spinnerMode);
            case "property_divider_height" -> d(property, bean.dividerHeight);
            case "property_custom_view_listview", "property_include_layout" -> a(property, bean.customView);
            case "property_checked" -> e(property, bean.checked);
            case "property_max" -> b(property, String.valueOf(bean.max));
            case "property_progress" -> b(property, String.valueOf(bean.progress));
            case "property_first_day_of_week" -> c(property, bean.firstDayOfWeek);
            case "property_ad_size" -> d(property, bean.adSize);
            case "property_progressbar_style" -> c(property, bean.progressStyle);
            case "property_material_progress_style" -> c(property, getMaterialProgressStyleSummary(bean));
            case "property_material_button_style" -> c(property, getMaterialButtonStyle(bean));
            case "property_material_icon_gravity" -> c(property, getMaterialIconGravity(bean));
            case "property_material_icon" -> b(property, getInjectedAttr(bean, "app:icon"));
            case "property_material_icon_size" -> b(property, getInjectedAttr(bean, "app:iconSize"));
            case "property_material_corner_radius" -> b(property, getInjectedAttr(bean, "app:cornerRadius"));
            case "property_material_slider_style" -> c(property, getInjectedAttrOrDefault(bean, "style", "default"));
            case "property_material_slider_value_from" -> b(property, getInjectedAttrOrDefault(bean, "android:valueFrom", "0"));
            case "property_material_slider_value_to" -> b(property, getInjectedAttrOrDefault(bean, "android:valueTo", "100"));
            case "property_material_slider_step_size" -> b(property, getInjectedAttrOrDefault(bean, "android:stepSize", "0"));
            case "property_material_slider_value" -> b(property, getInjectedAttrOrDefault(bean, "android:value", "0"));
            case "property_material_slider_thumb_radius" -> b(property, getInjectedAttr(bean, "app:thumbRadius"));
            case "property_material_slider_track_height" -> b(property, getInjectedAttr(bean, "app:trackHeight"));
            case "property_material_slider_halo_radius" -> b(property, getInjectedAttr(bean, "app:haloRadius"));
            case "property_material_slider_label_behavior" -> c(property, getInjectedAttrOrDefault(bean, "app:labelBehavior", "floating"));
            case "property_nested_scroll_behavior" -> b(property, getNestedScrollBehaviorSummary(bean));
            case "property_collapsing_title" -> b(property, getInjectedAttr(bean, "app:title"));
            case "property_collapsing_title_enabled" -> e(property, !"false".equals(getInjectedAttr(bean, "app:titleEnabled")) ? 1 : 0);
            case "property_collapsing_scrim_animation_duration" -> b(property, getInjectedAttrOrDefault(bean, "app:scrimAnimationDuration", "600"));
            case "property_collapsing_scrim_visible_height_trigger" -> b(property, getInjectedAttr(bean, "app:scrimVisibleHeightTrigger"));
            case "property_collapsing_expanded_title_margin_start" -> b(property, getInjectedAttr(bean, "app:expandedTitleMarginStart"));
            case "property_collapsing_expanded_title_margin_end" -> b(property, getInjectedAttr(bean, "app:expandedTitleMarginEnd"));
            case "property_collapsing_expanded_title_margin_top" -> b(property, getInjectedAttr(bean, "app:expandedTitleMarginTop"));
            case "property_collapsing_expanded_title_margin_bottom" -> b(property, getInjectedAttr(bean, "app:expandedTitleMarginBottom"));
            case "property_collapsing_toolbar_id" -> b(property, getInjectedAttr(bean, "app:toolbarId"));
            case "property_collapsing_content_scrim" -> b(property, getInjectedAttr(bean, "app:contentScrim"));
            case "property_collapsing_status_bar_scrim" -> b(property, getInjectedAttr(bean, "app:statusBarScrim"));
            case "property_collapsing_title_collapse_mode" -> c(property, getInjectedAttrOrDefault(bean, "app:titleCollapseMode", "scale"));
            case "property_collapsing_parallax_multiplier" -> b(property,
                    bean.parentAttributes == null ? "0.5" : bean.parentAttributes.getOrDefault("app:layout_collapseParallaxMultiplier", "0.5"));
            case "property_indeterminate" -> d(property, bean.indeterminate);
            case "property_inject" -> b(property, bean.inject);
            case "property_convert" -> b(property, bean.convert, String.valueOf(bean.type));
        }
    }

    private void a(String header) {
        var sub = new PropertySubheader(getContext());
        sub.setHeaderName(header);
        addView(sub);
    }

    private void a(String name, int value) {
        PropertyColorItem colorItem = (PropertyColorItem) f.get(name);
        if (colorItem == null) {
            colorItem = new PropertyColorItem(getContext(), !b);
            colorItem.setOrientationItem(getOrientation());
            colorItem.setKey(name);
            colorItem.setValue(value);
            colorItem.setTag(name);
            colorItem.setOnPropertyValueChangeListener(this);
            f.put(name, colorItem);
        } else {
            colorItem.setValue(value);
        }

        addView(colorItem);
    }

    private void a(String key, int left, int top, int right, int bottom) {
        PropertyIndentItem indentItem = (PropertyIndentItem) f.get(key);
        if (indentItem == null) {
            indentItem = new PropertyIndentItem(getContext(), !b);
            indentItem.setOrientationItem(getOrientation());
            indentItem.setKey(key);
            indentItem.a(left, top, right, bottom);
            indentItem.setTag(key);
            indentItem.setOnPropertyValueChangeListener(this);
            f.put(key, indentItem);
        } else {
            indentItem.a(left, top, right, bottom);
        }

        addView(indentItem);
    }

    private void r(String name, String value, int value2) {
        PropertyColorItem colorItem = (PropertyColorItem) f.get(name);
        if (colorItem == null) {
            colorItem = new PropertyColorItem(getContext(), !b, sc_id);
            colorItem.setOrientationItem(getOrientation());
            colorItem.setKey(name);
            colorItem.setValue(value2, value);
            colorItem.setTag(name);
            colorItem.setOnPropertyValueChangeListener(this);
            f.put(name, colorItem);
        } else {
            colorItem.setValue(value2, value);
        }
        addView(colorItem);
    }

    private void a(String key, int value, boolean isEnable) {
        PropertyMeasureItem measureItem = (PropertyMeasureItem) f.get(key);
        int isEnabled;
        if (isEnable) {
            isEnabled = 7;
        } else {
            isEnabled = 3;
        }

        if (measureItem == null) {
            measureItem = new PropertyMeasureItem(getContext(), !b);
            measureItem.setOrientationItem(getOrientation());
            measureItem.setItemEnabled(isEnabled);
            measureItem.setKey(key);
            measureItem.setValue(value);
            measureItem.setTag(key);
            measureItem.setOnPropertyValueChangeListener(this);
            f.put(key, measureItem);
        } else {
            measureItem.setItemEnabled(isEnabled);
            measureItem.setValue(value);
        }

        addView(measureItem);
    }

    private void a(String header, OnClickListener listener) {
        PropertySubheader propertySubheader = new PropertySubheader(getContext());
        propertySubheader.setHeaderName(header);
        propertySubheader.setOnClickListener(listener);
        addView(propertySubheader);
    }

    public void a(String scId, ViewBean bean) {
        sc_id = scId;
        c = bean;
        Cx.a().b(c.getClassInfo().getClassName());
        removeAllViews();
        if (bean.id.equals("_fab")) {
            b(bean);
        } else {
            if (getOrientation() == LinearLayout.VERTICAL) {
                b("property_id", bean.id);
            }

            f(bean);
            d(bean);
            g(bean);
            h(bean);
            if (getOrientation() == LinearLayout.HORIZONTAL) {
                b("property_id", bean.id);
            }
        }
    }

    @Override
    public void a(String var1, Object var2) {
        Cx.a().a(c.getClassInfo().getClassName(), var1);
        if (d != null) {
            ViewBean cloned = c.clone();
            i(c);
            // Property editors are applied in i(c). If the user explicitly enters a
            // property-backed attribute in Custom attributes, reapply that explicit value
            // last so it wins in generated XML instead of being silently overwritten.
            View view = f.get("property_inject");
            if (view instanceof PropertyInputItem inputItem) {
                LinkedHashMap<String, String> overrides = inputItem.getExplicitPropertyAttributeOverrides();
                for (Map.Entry<String, String> override : overrides.entrySet()) {
                    c.inject = setInjectedAttr(c, override.getKey(), override.getValue());
                }
            }
            if (!b) {
                cC.c(sc_id).a(e.getXmlName(), cloned, c.clone());
                d.a(c);
            }
        }
    }

    private void a(String key, String value) {
        PropertyCustomViewItem propertyCustomViewItem = (PropertyCustomViewItem) f.get(key);
        if (propertyCustomViewItem == null) {
            propertyCustomViewItem = new PropertyCustomViewItem(getContext(), !b);
            propertyCustomViewItem.setOrientationItem(getOrientation());
            propertyCustomViewItem.setKey(key);
            propertyCustomViewItem.setTag(key);
            propertyCustomViewItem.setOnPropertyValueChangeListener(this);
            f.put(key, propertyCustomViewItem);
        }

        propertyCustomViewItem.setCustomView(jC.b(sc_id).c());
        propertyCustomViewItem.setValue(value);
        addView(propertyCustomViewItem);
    }

    private void a(String key, String value, boolean z, String typeView) {
        PropertyInputItem inputItem = (PropertyInputItem) f.get(key);
        if (inputItem == null) {
            inputItem = new PropertyInputItem(getContext(), !z);
            inputItem.setOrientationItem(getOrientation());
            inputItem.setTypeView(typeView);
            inputItem.a(sc_id, e);
            inputItem.setKey(key);
            inputItem.setValue(value);
            inputItem.setTag(key);
            inputItem.setOnPropertyValueChangeListener(this);
            f.put(key, inputItem);
        } else {
            inputItem.setTypeView(typeView);
            inputItem.a(sc_id, e);
            inputItem.setValue(value);
        }
        inputItem.setBean(c);

        addView(inputItem);
    }

    private String normalizeNumericValue(String rawValue) {
        if (rawValue == null) return "";
        String value = rawValue.trim();
        if (value.isEmpty()) return "";
        try {
            Float.parseFloat(value);
            return value;
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private String normalizeIntegerValue(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) return "";
        try {
            return String.valueOf(Math.max(0, Integer.parseInt(rawValue.trim())));
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private String normalizeIdReference(String rawValue) {
        if (rawValue == null) return "";
        String value = rawValue.trim();
        if (value.isEmpty() || value.equals("@null")) return "";
        if (value.startsWith("@id/") || value.startsWith("@+id/")) return value;
        if (value.matches("[A-Za-z_][A-Za-z0-9_]*")) return "@id/" + value;
        return "";
    }

    private String normalizeMaterialDimension(String rawValue) {
        if (rawValue == null) return "";
        String value = rawValue.trim();
        if (value.isEmpty()) return "";
        if (value.matches("[0-9]+(?:\\.[0-9]+)?")) return value + "dp";
        if (value.matches("[0-9]+(?:\\.[0-9]+)?(?:dp|sp|px)")) return value;
        if (value.matches("@[a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+") || value.matches("\\?attr/[a-zA-Z0-9_]+")) return value;
        // Never write arbitrary user text as a resource dimension into Android XML.
        return "";
    }

    public void save() {
        Cx.a().b();
    }

    private void b(ViewBean bean) {
        if (getOrientation() == LinearLayout.VERTICAL) {
            a(bean, "property_id");
        }

        a(bean);
        c(bean);
        if (getOrientation() == LinearLayout.HORIZONTAL) {
            a(bean, "property_id");
        }
    }

    private void b(String key, int value) {
        PropertyGravityItem gravityItem = (PropertyGravityItem) f.get(key);
        if (gravityItem == null) {
            gravityItem = new PropertyGravityItem(getContext(), !b);
            gravityItem.setOrientationItem(getOrientation());
            gravityItem.setKey(key);
            gravityItem.setValue(value);
            gravityItem.setTag(key);
            gravityItem.setOnPropertyValueChangeListener(this);
            f.put(key, gravityItem);
        } else {
            gravityItem.setValue(value);
        }

        addView(gravityItem);
    }

    private void b(String key, String value) {
        a(key, value, b, null);
    }

    private void b(String key, String value, String viewType) {
        a(key, value, b, viewType);
    }

    private void b(String key, String value, boolean z) {
        PropertyResourceItem drawableItem = (PropertyResourceItem) f.get(key);
        if (drawableItem == null) {
            drawableItem = new PropertyResourceItem(getContext(), !b, sc_id, z);
            drawableItem.setOrientationItem(getOrientation());
            drawableItem.setKey(key);
            drawableItem.setValue(value);
            drawableItem.setTag(key);
            drawableItem.setOnPropertyValueChangeListener(this);
            f.put(key, drawableItem);
        } else {
            drawableItem.setValue(value);
        }
        addView(drawableItem);
    }

    private void c(ViewBean bean) {
        if (getOrientation() == LinearLayout.VERTICAL) {
            a(getContext().getString(R.string.property_header_image), this);
        }

        a(bean, "property_image");
        a(bean, "property_elevation");
        a(bean, "property_visibility");
        a(bean, "property_rotate");
        a(bean, "property_alpha");
        a(bean, "property_translation_x");
        a(bean, "property_translation_y");
        a(bean, "property_scale_x");
        a(bean, "property_scale_y");
    }

    private String getInjectedAttr(ViewBean bean, String attrName) {
        LinkedHashMap<String, String> attributes = parseInjectString(bean.inject);
        String value = attributes.get(attrName);
        return value == null ? "" : value;
    }

    private String getMaterialProgressStyle(ViewBean bean) {
        String convert = bean == null || bean.convert == null ? "" : bean.convert;
        return convert.endsWith("LinearProgressIndicator") ? "Linear" : "Circular";
    }

    private String getMaterialProgressStyleSummary(ViewBean bean) {
        return getMaterialProgressStyle(bean) + (isMaterialProgressWavy(bean) ? " · Wavy" : "");
    }

    private String getNestedScrollBehaviorSummary(ViewBean bean) {
        String viewport = "true".equalsIgnoreCase(getInjectedAttr(bean, "android:fillViewport")) ? "Viewport" : "Viewport off";
        String nested = "false".equalsIgnoreCase(getInjectedAttr(bean, "android:nestedScrollingEnabled")) ? "Nested off" : "Nested";
        String clip = "false".equalsIgnoreCase(getInjectedAttr(bean, "android:clipToPadding")) ? "Clip off" : "Clip";
        String overscroll = getInjectedAttrOrDefault(bean, "android:overScrollMode", "ifContentScrolls");
        String mode = switch (overscroll) {
            case "always" -> "Always";
            case "never" -> "Never";
            default -> "If content scrolls";
        };
        return viewport + " · " + nested + " · " + clip + " · " + mode;
    }

    private void applyNestedScrollBehavior(ViewBean bean, String config) {
        if (bean == null || config == null || !config.contains("|")) return;
        String[] parts = config.split("\\|", -1);
        if (parts.length != 4) return;
        if (!("true".equals(parts[0]) || "false".equals(parts[0]))) return;
        if (!("true".equals(parts[1]) || "false".equals(parts[1]))) return;
        if (!("true".equals(parts[2]) || "false".equals(parts[2]))) return;
        if (!("always".equals(parts[3]) || "ifContentScrolls".equals(parts[3]) || "never".equals(parts[3]))) return;

        bean.inject = setInjectedAttr(bean, "android:fillViewport", parts[0]);
        bean.inject = setInjectedAttr(bean, "android:nestedScrollingEnabled", parts[1]);
        bean.inject = setInjectedAttr(bean, "android:clipToPadding", parts[2]);
        // Leave the framework default unforced, but preserve the selectable default in the dialog.
        bean.inject = setInjectedAttr(bean, "android:overScrollMode", "ifContentScrolls".equals(parts[3]) ? "" : parts[3]);
    }

    private void applyMaterialProgressStyle(ViewBean bean, String selected) {
        if (bean == null || selected == null) return;
        String[] parts = selected.split("\\|", -1);
        String style = parts.length > 1 ? parts[0] : selected.split(" · ", 2)[0];
        boolean wavy = parts.length > 1
                ? Boolean.parseBoolean(parts[1])
                : selected.toLowerCase(java.util.Locale.ROOT).contains("wavy");
        if (!"Linear".equals(style) && !"Circular".equals(style)) return;

        boolean wasWavy = isMaterialProgressWavy(bean);
        bean.progressStyle = style;
        bean.convert = "com.google.android.material.progressindicator."
                + ("Linear".equals(style) ? "LinearProgressIndicator" : "CircularProgressIndicator");
        if (wavy) {
            bean.inject = setInjectedAttr(bean, "style", getWavyMaterialProgressStyle(bean));
        } else if (wasWavy) {
            bean.inject = setInjectedAttr(bean, "style", "");
        }
    }

    private boolean isMaterialProgressWavy(ViewBean bean) {
        String style = getInjectedAttr(bean, "style");
        return style.endsWith("CircularProgressIndicator.Wavy")
                || style.endsWith("LinearProgressIndicator.Wavy");
    }

    private String getWavyMaterialProgressStyle(ViewBean bean) {
        return "@style/Widget.Material3Expressive."
                + (bean != null && bean.convert != null && bean.convert.endsWith("LinearProgressIndicator")
                ? "LinearProgressIndicator.Wavy" : "CircularProgressIndicator.Wavy");
    }

    private String getMaterialButtonStyle(ViewBean bean) {
        String style = getInjectedAttr(bean, "style");
        if (style.isEmpty()) return "default";
        return switch (style) {
            case "?attr/materialButtonStyle", "?attr/materialButtonOutlinedStyle", "?attr/materialButtonTextButtonStyle",
                 "@style/Widget.Material3Expressive.Button",
                 "@style/Widget.Material3Expressive.Button.OutlinedButton",
                 "@style/Widget.Material3Expressive.Button.TonalButton",
                 "@style/Widget.Material3Expressive.Button.IconButton.Filled",
                 "@style/Widget.Material3Expressive.Button.IconButton.Tonal",
                 "@style/Widget.Material3Expressive.Button.IconButton.Outlined" -> style;
            default -> "default";
        };
    }

    private String getMaterialIconGravity(ViewBean bean) {
        String gravity = getInjectedAttr(bean, "app:iconGravity");
        if (gravity.isEmpty()) return bean.convert != null && bean.convert.contains("MaterialIconButton") ? "top" : "textStart";
        return gravity;
    }

    private boolean isMaterialButton(ViewBean bean) {
        if (bean == null || bean.convert == null) return false;
        String name = bean.convert.substring(bean.convert.lastIndexOf('.') + 1);
        return name.equals("MaterialButton") || name.equals("MaterialIconButton")
                || name.equals("ExtendedFloatingActionButton");
    }

    private boolean isMaterialProgressIndicator(ViewBean bean) {
        if (bean == null || bean.convert == null) return false;
        String name = bean.convert.substring(bean.convert.lastIndexOf('.') + 1);
        return name.equals("MaterialProgressIndicator") || name.equals("CircularProgressIndicator")
                || name.equals("LinearProgressIndicator") || name.equals("LoadingIndicator");
    }

    private boolean isMaterialSlider(ViewBean bean) {
        if (bean == null || bean.convert == null) return false;
        String name = bean.convert.substring(bean.convert.lastIndexOf('.') + 1);
        return name.equals("Slider") || name.equals("MaterialSlider");
    }

    private boolean isNestedScrollView(ViewBean bean) {
        if (bean == null || bean.convert == null) return false;
        return bean.convert.endsWith("NestedScrollView");
    }

    private boolean isCollapsingToolbarLayout(ViewBean bean) {
        return bean != null && bean.convert != null && bean.convert.endsWith("CollapsingToolbarLayout");
    }

    private boolean isParentCoordinatorLayout(ViewBean bean) {
        if (bean == null || bean.parent == null || bean.parent.isEmpty() || e == null || sc_id == null) return false;
        try {
            for (ViewBean parent : jC.a(sc_id).d(e.getXmlName())) {
                if (bean.parent.equals(parent.id)) {
                    return parent.convert != null && parent.convert.endsWith("CoordinatorLayout");
                }
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    private boolean isParentCollapsingToolbarLayout(ViewBean bean) {
        if (bean == null || bean.parent == null || bean.parent.isEmpty() || e == null || sc_id == null) return false;
        try {
            for (ViewBean parent : jC.a(sc_id).d(e.getXmlName())) {
                if (bean.parent.equals(parent.id)) {
                    return parent.convert != null && parent.convert.endsWith("CollapsingToolbarLayout");
                }
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    private String getInjectedAttrOrDefault(ViewBean bean, String attrName, String fallback) {
        String value = getInjectedAttr(bean, attrName);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private String setInjectedAttr(ViewBean bean, String attrName, String newValue) {
        LinkedHashMap<String, String> attributes = parseInjectString(bean.inject);
        if (newValue == null || newValue.isEmpty()) {
            attributes.remove(attrName);
        } else {
            attributes.put(attrName, newValue);
        }
        return attributes.entrySet().stream()
                .map(entry -> entry.getKey() + "=\"" + XmlUtil.escapeAttributeValue(entry.getValue()) + "\"")
                .collect(Collectors.joining("\n"));
    }

    private LinkedHashMap<String, String> parseInjectString(String inject) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        if (inject == null || inject.trim().isEmpty()) return attributes;

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader("<tag " + inject + "></tag>"));

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String localName = parser.getAttributeName(i);
                        String prefix = parser.getAttributePrefix(i);
                        String qualifiedName = prefix == null || prefix.isEmpty()
                                ? localName : prefix + ":" + localName;
                        // Preserve the namespace prefix. Property-backed attributes are stored
                        // as app:icon, android:value, etc.; dropping the prefix creates duplicate
                        // unqualified XML attributes and prevents property/custom precedence from
                        // being deterministic.
                        attributes.put(qualifiedName, parser.getAttributeValue(i));
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException | RuntimeException ignored) {
        }

        return attributes;
    }

    private void c(String key, int value) {
        PropertySelectorItem selectorItem = (PropertySelectorItem) f.get(key);
        if (selectorItem == null) {
            selectorItem = new PropertySelectorItem(getContext(), !b);
            selectorItem.setOrientationItem(getOrientation());
            selectorItem.setKey(key);
            selectorItem.setValue(value);
            selectorItem.setTag(key);
            selectorItem.setOnPropertyValueChangeListener(this);
            f.put(key, selectorItem);
        } else {
            selectorItem.setValue(value);
        }

        addView(selectorItem);
    }

    private void c(String key, String value) {
        PropertyStringPairSelectorItem pairSelectorItem = (PropertyStringPairSelectorItem) f.get(key);
        if (pairSelectorItem == null) {
            pairSelectorItem = new PropertyStringPairSelectorItem(getContext(), !b);
            pairSelectorItem.setOrientationItem(getOrientation());
            pairSelectorItem.setKey(key);
            pairSelectorItem.setValue(value);
            pairSelectorItem.setTag(key);
            pairSelectorItem.setOnPropertyValueChangeListener(this);
            f.put(key, pairSelectorItem);
        } else {
            pairSelectorItem.setValue(value);
        }

        addView(pairSelectorItem);
    }

    public void d(ViewBean bean) {
        if (getOrientation() == LinearLayout.VERTICAL) {
            a(getContext().getString(R.string.property_header_layout));
        }

        Gx classInfo = bean.getClassInfo();
        Gx parentClassInfo = bean.getParentClassInfo();
        a(bean, "property_layout_width");
        a(bean, "property_layout_height");
        
        if (bean.parentType == ViewBean.VIEW_TYPE_LAYOUT_RELATIVE
                || bean.parentType == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT
                || isParentCoordinatorLayout(bean)
                || isParentCollapsingToolbarLayout(bean)) {
            a(bean, "property_parent_attr");
        }
        
        a(bean, "property_padding");
        a(bean, "property_margin");
        if (classInfo.a("LinearLayout")) {
            a(bean, "property_orientation");
            a(bean, "property_weight_sum");
            a(bean, "property_gravity");
        }

        if (classInfo.a("TextView")) {
            a(bean, "property_gravity");
        }

        if (parentClassInfo != null) {
            if (parentClassInfo.a("LinearLayout")) {
                a(bean, "property_layout_gravity");
                a(bean, "property_weight");
            }

            if (parentClassInfo.a("ScrollView") || parentClassInfo.a("HorizontalScrollView")) {
                a(bean, "property_layout_gravity");
            }
        }
    }

    private void d(String key, int value) {
        PropertySizeItem propertySizeItem = (PropertySizeItem) f.get(key);
        if (propertySizeItem == null) {
            propertySizeItem = new PropertySizeItem(getContext(), !b);
            propertySizeItem.setOrientationItem(getOrientation());
            propertySizeItem.setKey(key);
            propertySizeItem.setValue(value);
            propertySizeItem.setTag(key);
            propertySizeItem.setOnPropertyValueChangeListener(this);
            f.put(key, propertySizeItem);
        } else {
            propertySizeItem.setValue(value);
        }

        addView(propertySizeItem);
    }

    private void d(String key, String value) {
        PropertyStringSelectorItem stringSelectorItem = (PropertyStringSelectorItem) f.get(key);
        if (stringSelectorItem == null) {
            stringSelectorItem = new PropertyStringSelectorItem(getContext(), !b);
            stringSelectorItem.setOrientationItem(getOrientation());
            stringSelectorItem.setKey(key);
            stringSelectorItem.setValue(value);
            stringSelectorItem.setTag(key);
            stringSelectorItem.setOnPropertyValueChangeListener(this);
            f.put(key, stringSelectorItem);
        } else {
            stringSelectorItem.setValue(value);
        }

        addView(stringSelectorItem);
    }

    public void e(ViewBean bean) {
        c = bean;
        removeAllViews();
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.LEFT;
        setLayoutParams(params);
        setGravity(Gravity.LEFT);
        ArrayList<String> items = Cx.a().a(bean.getClassInfo().getClassName());
        if (items == null) {
            setupViews();
        } else {
            for (String s : items) {
                a(bean, s);
            }
        }
    }

    private void e(String key, int value) {
        PropertySwitchSingleLineItem switchSingleLineItem = (PropertySwitchSingleLineItem) f.get(key);
        boolean isEnabled = false;
        if (switchSingleLineItem == null) {
            switchSingleLineItem = new PropertySwitchSingleLineItem(getContext(), !b);
            switchSingleLineItem.setOrientationItem(getOrientation());
            switchSingleLineItem.setKey(key);
            if (value == 1) {
                isEnabled = true;
            }

            switchSingleLineItem.setValue(isEnabled);
            switchSingleLineItem.setTag(key);
            switchSingleLineItem.setOnPropertyValueChangeListener(this);
            f.put(key, switchSingleLineItem);
        } else {
            if (value == 1) {
                isEnabled = true;
            }

            switchSingleLineItem.setValue(isEnabled);
        }

        addView(switchSingleLineItem);
    }

    private void setupAttributes(String key, HashMap<String, String> value) {
        ArrayList<ViewBean> viewBeans = jC.a(sc_id).d(e.getXmlName());
        List<String> ids = new ArrayList<>();
        for (ViewBean bean : viewBeans) {
            if (!bean.id.equals(c.id) && bean.parent.equals(c.parent)) {
                ids.add(bean.id);
            }
        }
        PropertyAttributesItem item = (PropertyAttributesItem) f.get(key);
        if (item == null) {
            item = new PropertyAttributesItem(getContext(), !b);
            item.setOrientationItem(getOrientation());
            item.setKey(key);
            item.setValue(value);
            item.setTag(key);
            item.setOnPropertyValueChangeListener(this);
            f.put(key, item);
        } else {
            item.setValue(value);
        }
        item.setBean(c);
        item.setBeans(viewBeans);
        item.setAvailableIds(ids);

        addView(item);
    }

    public void f(ViewBean bean) {
        if (!bean.id.equals("_fab")) {
            Gx classInfo = bean.getClassInfo();
            a(bean, "property_inject");
            a(bean, "property_convert");
            if ("include".equals(bean.convert)) {
                a(bean, "property_include_layout");
            }
            if (isMaterialButton(bean)) {
                // The extended FAB has its own shape/style; expose icon-related properties
                // without letting a generic button-style choice flatten it into a button.
                if (bean.convert == null || !bean.convert.endsWith("ExtendedFloatingActionButton")) {
                    a(bean, "property_material_button_style");
                }
                a(bean, "property_material_icon");
                a(bean, "property_material_icon_gravity");
                a(bean, "property_material_icon_size");
                a(bean, "property_material_corner_radius");
            }
            if (isMaterialProgressIndicator(bean)) {
                a(bean, "property_material_progress_style");
            }
            if (isMaterialSlider(bean)) {
                a(bean, "property_material_slider_style");
                a(bean, "property_material_slider_value_from");
                a(bean, "property_material_slider_value_to");
                a(bean, "property_material_slider_step_size");
                a(bean, "property_material_slider_value");
                a(bean, "property_material_slider_thumb_radius");
                a(bean, "property_material_slider_track_height");
                a(bean, "property_material_slider_halo_radius");
                a(bean, "property_material_slider_label_behavior");
            }
            if (isNestedScrollView(bean)) {
                a(bean, "property_nested_scroll_behavior");
            }
            if (isCollapsingToolbarLayout(bean)) {
                a(bean, "property_collapsing_title");
                a(bean, "property_collapsing_title_enabled");
                a(bean, "property_collapsing_title_collapse_mode");
                a(bean, "property_collapsing_scrim_animation_duration");
                a(bean, "property_collapsing_scrim_visible_height_trigger");
                a(bean, "property_collapsing_expanded_title_margin_start");
                a(bean, "property_collapsing_expanded_title_margin_end");
                a(bean, "property_collapsing_expanded_title_margin_top");
                a(bean, "property_collapsing_expanded_title_margin_bottom");
                a(bean, "property_collapsing_toolbar_id");
                a(bean, "property_collapsing_content_scrim");
                a(bean, "property_collapsing_status_bar_scrim");
            }
            if (isParentCollapsingToolbarLayout(bean)) {
                // These are layout params supplied by the CollapsingToolbarLayout parent.
                a(bean, "property_collapsing_parallax_multiplier");
            }
            if (classInfo.b("Spinner")) {
                a(bean, "property_spinner_mode");
            }

            if (classInfo.b("ListView")) {
                a(bean, "property_divider_height");
                a(bean, "property_custom_view_listview");
            }

            if (classInfo.b("GridView")) {
                a(bean, "property_custom_view_listview");
            }

            if (classInfo.b("RecyclerView")) {
                a(bean, "property_custom_view_listview");
            }

            if (classInfo.b("ViewPager")) {
                a(bean, "property_custom_view_listview");
            }

            if (classInfo.b("Spinner")) {
                a(bean, "property_custom_view_listview");
            }

            if (classInfo.b("AutoCompleteTextView")) {
                a(bean, "property_hint");
                a(bean, "property_hint_color");
            }

            if (classInfo.b("MultiAutoCompleteTextView")) {
                a(bean, "property_hint");
                a(bean, "property_hint_color");
            }

            if (classInfo.b("WaveSideBar")) {
                b("property_text_size", String.valueOf(bean.text.textSize));
                a(bean, "property_text_color");
            }

            if (classInfo.a("CompoundButton") && getOrientation() == LinearLayout.VERTICAL) {
                a(bean, "property_checked");
            }

            if (classInfo.b("SeekBar") && !isMaterialSlider(bean)) {
                a(bean, "property_max");
                a(bean, "property_progress");
            }

            if (classInfo.b("CalendarView")) {
                a(bean, "property_first_day_of_week");
            }

            if (classInfo.b("AdView")) {
                a(bean, "property_ad_size");
            }

            if (classInfo.b("ProgressBar")) {
                a(bean, "property_max");
                a(bean, "property_progress");
                // Material indicators use their own three-value selector, not platform ProgressBar styles.
                if (!isMaterialProgressIndicator(bean)) {
                    a(bean, "property_progressbar_style");
                }
                a(bean, "property_indeterminate");
            }
        }
    }

    public void g(ViewBean bean) {
        Gx classInfo = bean.getClassInfo();
        if (classInfo.a("TextView")) {
            if (getOrientation() == LinearLayout.VERTICAL) {
                a(getContext().getString(R.string.property_header_text));
            }

            a(bean, "property_text");
            b("property_text_size", String.valueOf(bean.text.textSize));
            a(bean, "property_text_style");
            a(bean, "property_text_color");
            if (classInfo.b("EditText")) {
                a(bean, "property_hint");
                a(bean, "property_hint_color");
                if (getOrientation() == LinearLayout.VERTICAL) {
                    a(bean, "property_single_line");
                }

                a(bean, "property_lines");
                a(bean, "property_input_type");
                a(bean, "property_ime_option");
            }

            if (classInfo.b("TextView")) {
                if (getOrientation() == LinearLayout.VERTICAL) {
                    a(bean, "property_single_line");
                }

                a(bean, "property_lines");
            }
        }
    }

    public void h(ViewBean bean) {
        Gx classInfo = bean.getClassInfo();
        if (getOrientation() == LinearLayout.VERTICAL) {
            if (classInfo.a("ImageView")) {
                a(getContext().getString(R.string.property_header_image), this);
                a(bean, "property_image");
                a(bean, "property_scale_type");
            } else {
                a(getContext().getString(R.string.property_header_image));
            }
        } else if (classInfo.a("ImageView")) {
            a(bean, "property_image");
            a(bean, "property_scale_type");
        }

        if (bean.type != ViewBean.VIEW_TYPE_WIDGET_MAPVIEW) {
            a(bean, "property_background_resource");
            a(bean, "property_background_color");
        }

        if (getOrientation() == LinearLayout.VERTICAL
                && !classInfo.b("LinearLayout")
                && !classInfo.b("ScrollView")
                && !classInfo.b("HorizontalScrollView")
                && !classInfo.b("ListView")
                && !classInfo.b("FloatingActionButton")) {
            a(bean, "property_enabled");
        }

        a(bean, "property_elevation");
        a(bean, "property_visibility");

        a(bean, "property_rotate");
        a(bean, "property_alpha");
        a(bean, "property_translation_x");
        a(bean, "property_translation_y");
        a(bean, "property_scale_x");
        a(bean, "property_scale_y");
    }

    public void i(ViewBean bean) {
        int childCount = getChildCount();

        for (int i = 0; i < childCount; ++i) {
            View view = getChildAt(i);
            if (view instanceof PropertyInputItem inputItem) {
                switch (inputItem.getKey()) {
                    case "property_id" -> {
                        String var5 = bean.id;
                        bean.preId = var5;
                        if (var5.charAt(0) != '_') {
                            bean.id = inputItem.getValue();
                        }
                    }
                    case "property_convert" -> bean.convert = inputItem.getValue();
                    case "property_inject" -> bean.inject = inputItem.getValue();
                    case "property_material_icon" -> bean.inject = setInjectedAttr(bean, "app:icon", inputItem.getValue().trim());
                    case "property_material_icon_size" -> bean.inject = setInjectedAttr(bean, "app:iconSize", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_material_corner_radius" -> bean.inject = setInjectedAttr(bean, "app:cornerRadius", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_nested_scroll_behavior" -> applyNestedScrollBehavior(bean, inputItem.getValue());
                    case "property_material_slider_value_from" -> bean.inject = setInjectedAttr(bean, "android:valueFrom", normalizeNumericValue(inputItem.getValue()));
                    case "property_material_slider_value_to" -> bean.inject = setInjectedAttr(bean, "android:valueTo", normalizeNumericValue(inputItem.getValue()));
                    case "property_material_slider_step_size" -> bean.inject = setInjectedAttr(bean, "android:stepSize", normalizeNumericValue(inputItem.getValue()));
                    case "property_material_slider_value" -> bean.inject = setInjectedAttr(bean, "android:value", normalizeNumericValue(inputItem.getValue()));
                    case "property_material_slider_thumb_radius" -> bean.inject = setInjectedAttr(bean, "app:thumbRadius", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_material_slider_track_height" -> bean.inject = setInjectedAttr(bean, "app:trackHeight", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_material_slider_halo_radius" -> bean.inject = setInjectedAttr(bean, "app:haloRadius", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_collapsing_title" -> bean.inject = setInjectedAttr(bean, "app:title", inputItem.getValue().trim());
                    case "property_collapsing_scrim_animation_duration" -> bean.inject = setInjectedAttr(bean, "app:scrimAnimationDuration", normalizeIntegerValue(inputItem.getValue()));
                    case "property_collapsing_scrim_visible_height_trigger" -> bean.inject = setInjectedAttr(bean, "app:scrimVisibleHeightTrigger", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_collapsing_expanded_title_margin_start" -> bean.inject = setInjectedAttr(bean, "app:expandedTitleMarginStart", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_collapsing_expanded_title_margin_end" -> bean.inject = setInjectedAttr(bean, "app:expandedTitleMarginEnd", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_collapsing_expanded_title_margin_top" -> bean.inject = setInjectedAttr(bean, "app:expandedTitleMarginTop", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_collapsing_expanded_title_margin_bottom" -> bean.inject = setInjectedAttr(bean, "app:expandedTitleMarginBottom", normalizeMaterialDimension(inputItem.getValue()));
                    case "property_collapsing_toolbar_id" -> bean.inject = setInjectedAttr(bean, "app:toolbarId", normalizeIdReference(inputItem.getValue()));
                    case "property_collapsing_content_scrim" -> bean.inject = setInjectedAttr(bean, "app:contentScrim", inputItem.getValue().trim());
                    case "property_collapsing_status_bar_scrim" -> bean.inject = setInjectedAttr(bean, "app:statusBarScrim", inputItem.getValue().trim());
                    case "property_collapsing_parallax_multiplier" -> {
                        if (bean.parentAttributes == null) bean.parentAttributes = new LinkedHashMap<>();
                        String normalized = normalizeNumericValue(inputItem.getValue());
                        if (normalized.isEmpty()) bean.parentAttributes.remove("app:layout_collapseParallaxMultiplier");
                        else bean.parentAttributes.put("app:layout_collapseParallaxMultiplier", normalized);
                    }
                    case "property_text" -> bean.text.text = inputItem.getValue();
                    case "property_hint" -> bean.text.hint = inputItem.getValue();
                    case "property_text_size" -> bean.text.textSize = Integer.parseInt(inputItem.getValue());
                    case "property_weight" ->
                            bean.layout.weight = Integer.parseInt(inputItem.getValue());
                    case "property_weight_sum" ->
                            bean.layout.weightSum = Integer.parseInt(inputItem.getValue());
                    case "property_elevation" -> {
                        String val = inputItem.getValue();
                        if (!val.isEmpty() && !val.contains("dp")) {
                            val += "dp";
                        }
                        bean.inject = setInjectedAttr(bean, "android:elevation", val);
                    }
                    case "property_visibility" ->
                            bean.inject = setInjectedAttr(bean, "android:visibility", inputItem.getValue());
                    case "property_rotate" ->
                            bean.image.rotate = Integer.parseInt(inputItem.getValue());
                    case "property_alpha" -> bean.alpha = Float.parseFloat(inputItem.getValue());
                    case "property_translation_x" ->
                            bean.translationX = Float.parseFloat(inputItem.getValue());
                    case "property_translation_y" ->
                            bean.translationY = Float.parseFloat(inputItem.getValue());
                    case "property_scale_x" -> bean.scaleX = Float.parseFloat(inputItem.getValue());
                    case "property_scale_y" -> bean.scaleY = Float.parseFloat(inputItem.getValue());
                    case "property_lines" ->
                            bean.text.line = Integer.parseInt(inputItem.getValue());
                    case "property_max" -> bean.max = Integer.parseInt(inputItem.getValue());
                    case "property_progress" ->
                            bean.progress = Integer.parseInt(inputItem.getValue());
                }
            } else if (view instanceof PropertyMeasureItem measureItem) {
                if (measureItem.getKey().equals("property_layout_width")) {
                    bean.layout.width = measureItem.getValue();
                } else if (measureItem.getKey().equals("property_layout_height")) {
                    bean.layout.height = measureItem.getValue();
                }
            } else if (view instanceof PropertySelectorItem selectorItem) {
                switch (selectorItem.getKey()) {
                    case "property_orientation" ->
                            bean.layout.orientation = selectorItem.getValue();
                    case "property_text_style" -> bean.text.textType = selectorItem.getValue();
                    case "property_input_type" -> bean.text.inputType = selectorItem.getValue();
                    case "property_ime_option" -> bean.text.imeOption = selectorItem.getValue();
                    case "property_spinner_mode" -> bean.spinnerMode = selectorItem.getValue();
                    case "property_first_day_of_week" ->
                            bean.firstDayOfWeek = selectorItem.getValue();
                }
            } else if (view instanceof PropertyStringSelectorItem stringSelectorItem) {
                switch (stringSelectorItem.getKey()) {
                    case "property_scale_type" ->
                            bean.image.scaleType = stringSelectorItem.getValue();
                    case "property_ad_size" -> bean.adSize = stringSelectorItem.getValue();
                    case "property_indeterminate" ->
                            bean.indeterminate = stringSelectorItem.getValue();
                }
            } else if (view instanceof PropertyStringPairSelectorItem stringPairSelectorItem) {
                String key = stringPairSelectorItem.getKey();
                String selected = stringPairSelectorItem.getValue();
                if (key.equals("property_progressbar_style")) {
                    bean.progressStyle = selected;
                } else if (key.equals("property_material_button_style")) {
                    bean.inject = setInjectedAttr(bean, "style", "default".equals(selected) ? "" : selected);
                } else if (key.equals("property_material_icon_gravity")) {
                    bean.inject = setInjectedAttr(bean, "app:iconGravity", selected);
                } else if (key.equals("property_material_progress_style")) {
                    applyMaterialProgressStyle(bean, selected);
                } else if (key.equals("property_material_slider_style")) {
                    bean.inject = setInjectedAttr(bean, "style", "default".equals(selected) ? "" : selected);
                } else if (key.equals("property_material_slider_label_behavior")) {
                    bean.inject = setInjectedAttr(bean, "app:labelBehavior", selected);
                } else if (key.equals("property_collapsing_title_collapse_mode")) {
                    bean.inject = setInjectedAttr(bean, "app:titleCollapseMode", "scale".equals(selected) ? "" : selected);
                }
            } else if (view instanceof PropertyCustomViewItem listview_item) {
                if (listview_item.getKey().equals("property_custom_view_listview")) {
                    bean.customView = listview_item.getValue();
                } else if (listview_item.getKey().equals("property_include_layout")) {
                    String selectedLayout = listview_item.getValue();
                    bean.customView = selectedLayout == null || selectedLayout.isEmpty() || "none".equals(selectedLayout)
                            ? "" : selectedLayout.replaceFirst("\\.xml$", "");
                }
            } else if (view instanceof PropertySwitchSingleLineItem switchSingleLineItem) {
                switch (switchSingleLineItem.getKey()) {
                    case "property_single_line" ->
                            bean.text.singleLine = switchSingleLineItem.getValue() ? 1 : 0;
                    case "property_enabled" ->
                            bean.enabled = switchSingleLineItem.getValue() ? 1 : 0;
                    case "property_clickable" ->
                            bean.clickable = switchSingleLineItem.getValue() ? 1 : 0;
                    case "property_checked" ->
                            bean.checked = switchSingleLineItem.getValue() ? 1 : 0;
                    case "property_material_progress_wavy" -> {
                        String currentStyle = getInjectedAttr(bean, "style");
                        boolean currentlyWavy = currentStyle.endsWith("CircularProgressIndicator.Wavy")
                                || currentStyle.endsWith("LinearProgressIndicator.Wavy");
                        if (switchSingleLineItem.getValue()) {
                            bean.inject = setInjectedAttr(bean, "style", getWavyMaterialProgressStyle(bean));
                        } else if (currentlyWavy) {
                            bean.inject = setInjectedAttr(bean, "style", "");
                        }
                    }
                    case "property_nested_scroll_fill_viewport" -> bean.inject = setInjectedAttr(bean, "android:fillViewport", String.valueOf(switchSingleLineItem.getValue()));
                    case "property_nested_scroll_nested_enabled" -> bean.inject = setInjectedAttr(bean, "android:nestedScrollingEnabled", String.valueOf(switchSingleLineItem.getValue()));
                    case "property_nested_scroll_clip_to_padding" -> bean.inject = setInjectedAttr(bean, "android:clipToPadding", String.valueOf(switchSingleLineItem.getValue()));
                    case "property_collapsing_title_enabled" -> bean.inject = setInjectedAttr(bean, "app:titleEnabled", String.valueOf(switchSingleLineItem.getValue()));
                }
            } else if (view instanceof PropertyColorItem colorItem) {
                switch (colorItem.getKey()) {
                    case "property_text_color" -> {
                        bean.text.textColor = colorItem.getValue();
                        bean.text.resTextColor = colorItem.getResValue();
                    }
                    case "property_hint_color" -> {
                        bean.text.hintColor = colorItem.getValue();
                        bean.text.resHintColor = colorItem.getResValue();
                    }
                    case "property_background_color" -> {
                        bean.layout.backgroundResColor = colorItem.getResValue();
                        bean.layout.backgroundColor = colorItem.getValue();
                    }
                }
            } else if (view instanceof PropertyIndentItem indentItem) {
                if (indentItem.getKey().equals("property_margin")) {
                    LayoutBean layout = bean.layout;
                    layout.marginLeft = indentItem.j;
                    layout.marginTop = indentItem.k;
                    layout.marginRight = indentItem.l;
                    layout.marginBottom = indentItem.m;
                } else if (indentItem.getKey().equals("property_padding")) {
                    LayoutBean layout = bean.layout;
                    layout.paddingLeft = indentItem.j;
                    layout.paddingTop = indentItem.k;
                    layout.paddingRight = indentItem.l;
                    layout.paddingBottom = indentItem.m;
                }
            } else if (view instanceof PropertyGravityItem gravityItem) {
                if (gravityItem.getKey().equals("property_gravity")) {
                    bean.layout.gravity = gravityItem.getValue();
                } else if (gravityItem.getKey().equals("property_layout_gravity")) {
                    bean.layout.layoutGravity = gravityItem.getValue();
                }
            } else if (view instanceof PropertyResourceItem image_item) {
                if (image_item.getKey().equals("property_image")) {
                    bean.image.resName = image_item.getValue();
                } else if (image_item.getKey().equals("property_background_resource")) {
                    String selectedBackground = image_item.getValue();
                    if (selectedBackground == null
                            || selectedBackground.trim().isEmpty()
                            || "NONE".equalsIgnoreCase(selectedBackground.trim())
                            || "@drawable/".equalsIgnoreCase(selectedBackground.trim())) {
                        // Keep the no-resource selection absent in the model. Writing an
                        // empty drawable name would produce android:background="@drawable/".
                        bean.layout.backgroundResource = null;
                    } else {
                        String normalizedBackground = selectedBackground.trim();
                        if (normalizedBackground.startsWith("@drawable/")) {
                            normalizedBackground = normalizedBackground.substring("@drawable/".length()).trim();
                        }
                        bean.layout.backgroundResource = normalizedBackground.isEmpty()
                                || "NONE".equalsIgnoreCase(normalizedBackground)
                                ? null : normalizedBackground;
                    }
                }
            } else if (view instanceof PropertySizeItem sizeItem) {
                if (sizeItem.getKey().equals("property_divider_height")) {
                    bean.dividerHeight = sizeItem.getValue();
                }
            } else if (view instanceof PropertyAttributesItem item) {
                if (item.getKey().equals("property_parent_attr")) {
                    bean.parentAttributes = item.getValue();
                }
            }
        }

        if (!bean.id.equals(bean.preId)) {
            boolean viewBinding = settings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, "false").equals("true");
            for (ViewBean viewBean : jC.a(sc_id).d(e.getXmlName())) {
                if (viewBean.parent.equals(bean.preId)) {
                    viewBean.parent = bean.id;
                }
            }

            if (e.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                for (EventBean eventBean : jC.a(sc_id).g(e.getJavaName())) {
                    if (eventBean.targetId.equals(bean.preId)) {
                        eventBean.targetId = bean.id;
                    }
                }

                HashMap<String, ArrayList<BlockBean>> beanMap = jC.a(sc_id).b(e.getJavaName());

                for (String events : oq.getEventsForClass(bean.getClassInfo())) {
                    StringBuilder eventBodyBuilder = new StringBuilder();
                    eventBodyBuilder.append(bean.preId);
                    eventBodyBuilder.append("_");
                    eventBodyBuilder.append(events);
                    String body = eventBodyBuilder.toString();
                    if (beanMap.containsKey(body)) {
                        eventBodyBuilder = new StringBuilder();
                        eventBodyBuilder.append(bean.id);
                        eventBodyBuilder.append("_");
                        eventBodyBuilder.append(events);
                        beanMap.put(eventBodyBuilder.toString(), beanMap.get(body));
                        beanMap.remove(body);
                    }
                }

                ArrayList<String> events = new ArrayList<>();
                if (beanMap.containsKey("onCreate_initializeLogic")) {
                    events.add("onCreate_initializeLogic");
                }

                for (Pair<String, String> moreblocks : jC.a(sc_id).i(e.getJavaName())) {
                    String key = moreblocks.first +
                            "_" +
                            "moreBlock";
                    if (beanMap.containsKey(key)) {
                        events.add(key);
                    }
                }

                for (EventBean eventBean : jC.a(sc_id).g(e.getJavaName())) {
                    if (!eventBean.eventName.equals("onBindCustomView")) {
                        String eventKey = eventBean.getEventKey();
                        if (beanMap.containsKey(eventKey)) {
                            events.add(eventKey);
                        }
                    }
                }

                for (String event : events) {
                    ArrayList<BlockBean> blockBeans = beanMap.get(event);
                    if (blockBeans != null) {
                        for (BlockBean blockBean : blockBeans) {
                            Gx classInfo = blockBean.getClassInfo();

                            if (classInfo != null && classInfo.d() && blockBean.spec.equals(viewBinding ? "binding." + ViewBindingBuilder.generateParameterFromId(bean.preId) : bean.preId)) {
                                blockBean.spec = viewBinding ? "binding." + ViewBindingBuilder.generateParameterFromId(bean.id) : bean.id;
                            } else {
                                ArrayList<Gx> paramClassInfo = blockBean.getParamClassInfo();
                                if (paramClassInfo != null && !paramClassInfo.isEmpty()) {
                                    for (int i = 0; i < paramClassInfo.size(); ++i) {
                                        if (paramClassInfo.get(i).d() && blockBean.parameters.get(i).equals(viewBinding ? "binding." + ViewBindingBuilder.generateParameterFromId(bean.preId) : bean.preId)) {
                                            blockBean.parameters.set(i, viewBinding ? "binding." + ViewBindingBuilder.generateParameterFromId(bean.id) : bean.id);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                ArrayList<Pair<String, String>> viewBeanItems = new ArrayList<>();

                for (ProjectFileBean projectFileBean : jC.b(sc_id).b()) {
                    for (ViewBean viewBean : jC.a(sc_id).f(projectFileBean.getXmlName())) {
                        if (viewBean.customView.equals(e.fileName)) {
                            String javaName = projectFileBean.getJavaName();
                            String key = viewBean.id +
                                    "_" +
                                    "onBindCustomView";
                            viewBeanItems.add(new Pair<>(javaName, key));
                        }
                    }
                }

                for (Pair<String, String> viewBean : viewBeanItems) {
                    ArrayList<BlockBean> blockBeans = jC.a(sc_id).b(viewBean.first).get(viewBean.second);
                    if (blockBeans != null) {
                        for (BlockBean blockBean : blockBeans) {
                            Gx classInfo = blockBean.getClassInfo();

                            if (classInfo != null && classInfo.d() && blockBean.spec.equals(viewBinding ? "binding." + ViewBindingBuilder.generateParameterFromId(bean.preId) : bean.preId)) {
                                blockBean.spec = viewBinding ? "binding." + ViewBindingBuilder.generateParameterFromId(bean.id) : bean.id;
                            } else {
                                ArrayList<Gx> paramClassInfo = blockBean.getParamClassInfo();
                                if (paramClassInfo != null && !paramClassInfo.isEmpty()) {
                                    for (int i = 0; i < paramClassInfo.size(); ++i) {
                                        if (paramClassInfo.get(i).d() && blockBean.parameters.get(i).equals(viewBinding ? "binding." + ViewBindingBuilder.generateParameterFromId(bean.preId) : bean.preId)) {
                                            blockBean.parameters.set(i, viewBinding ? "binding." + ViewBindingBuilder.generateParameterFromId(bean.id) : bean.id);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bean.type == ViewBean.VIEW_TYPE_WIDGET_LISTVIEW) {
            ViewBean viewBean = jC.a(sc_id).c(e.getXmlName(), bean.preId);
            String custom = bean.customView;
            if (custom != null && viewBean != null) {
                String customView = viewBean.customView;
                if (customView != null && !customView.equals(custom)) {
                    ArrayList<EventBean> eventBeans = jC.a(sc_id).g(e.getJavaName());
                    int size = eventBeans.size();

                    while (true) {
                        childCount = size - 1;
                        if (childCount < 0) {
                            if (bean.customView.isEmpty() || bean.customView.equals("none")) {
                                Iterator<Entry<String, ArrayList<BlockBean>>> blocks = jC.a(sc_id).b(e.getJavaName()).entrySet().iterator();

                                while (blocks.hasNext()) {
                                    for (BlockBean blockBean : blocks.next().getValue()) {
                                        if ("listSetCustomViewData".equals(blockBean.opCode) && bean.id.equals(blockBean.parameters.get(0))) {
                                            blockBean.parameters.set(0, "");
                                        }
                                    }
                                }
                            }
                            break;
                        }

                        EventBean eventBean = eventBeans.get(childCount);
                        size = childCount;
                        if (eventBean.targetId.equals(bean.id)) {
                            if (eventBean.eventName.equals("onBindCustomView")) {
                                eventBeans.remove(eventBean);
                                HashMap<String, ArrayList<BlockBean>> blocks = jC.a(sc_id).b(e.getJavaName());
                                blocks.remove(eventBean.getEventKey());
                            }
                        }
                    }
                }
            }
        }
    }

    public void setProjectSettings(ProjectSettings settings) {
        this.settings = settings;
    }

    public void setOnPropertyValueChangedListener(Lw listener) {
        d = listener;
    }

    public void setProjectFileBean(ProjectFileBean fileBean) {
        e = fileBean;
    }

    @Override
    public void onClick(View v) {
        if (!mB.a() && !b) {
            Intent intent = new Intent(getContext(), ManageImageActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("sc_id", sc_id);
            ((Activity) getContext()).startActivityForResult(intent, 209);
        }
    }
}
