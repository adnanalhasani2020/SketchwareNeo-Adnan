package com.besome.sketch.editor.property;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import a.a.a.Jx;
import a.a.a.Kw;
import a.a.a.OB;
import a.a.a.SB;
import a.a.a.jC;
import a.a.a.lC;
import a.a.a.mB;
import a.a.a.uq;
import a.a.a.wB;
import a.a.a.yB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.resourceseditor.components.utils.StringsEditorManager;
import pro.sketchware.databinding.PropertyInputItemBinding;
import pro.sketchware.databinding.PropertyPopupHybridBinding;
import pro.sketchware.databinding.PropertyPopupInputTextBinding;
import pro.sketchware.databinding.PropertyPopupParentAttrBinding;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.lib.highlighter.SyntaxScheme;
import pro.sketchware.lib.validator.PropertyNameValidator;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

@SuppressLint("ViewConstructor")
public class PropertyInputItem extends RelativeLayout implements View.OnClickListener {

    private final String stringsStart = "@string/";
    private final ArrayList<HashMap<String, Object>> stringsListMap = new ArrayList<>();
    private Context context;
    private String typeView = "";
    private String key = "";
    private String value = "";
    private ImageView imgLeftIcon;
    private int icon;
    private TextView tvName;
    private TextView tvValue;
    private View propertyItem;
    private View propertyMenuItem;
    private String sc_id;
    private ProjectFileBean projectFileBean;
    private Kw valueChangeListener;
    private List<String> keysList = new ArrayList<>();
    private ViewBean bean;
    // Explicit property-backed values added through Custom attributes are tracked per view so
    // later edits to other properties do not silently replace the user's override.
    private final Map<String, LinkedHashMap<String, String>> explicitPropertyAttributeOverridesByView = new HashMap<>();
    private String lastOverrideViewKey;
    private String lastOverrideScopeKey;

    LinkedHashMap<String, String> getExplicitPropertyAttributeOverrides() {
        LinkedHashMap<String, String> overrides = explicitPropertyAttributeOverridesByView.get(getActiveOverrideViewKey());
        return overrides == null ? new LinkedHashMap<>() : new LinkedHashMap<>(overrides);
    }

    private String getOverrideScopeKey() {
        String fileName = projectFileBean == null ? "" : projectFileBean.getXmlName();
        return (sc_id == null ? "" : sc_id) + "|" + fileName;
    }

    private String getOverrideViewKey(ViewBean target) {
        return getOverrideScopeKey() + "|" + (target == null || target.id == null ? "" : target.id);
    }

    private String getActiveOverrideViewKey() {
        String scope = getOverrideScopeKey();
        String activeKey = getOverrideViewKey(bean);
        if (lastOverrideViewKey != null && lastOverrideScopeKey != null
                && lastOverrideScopeKey.equals(scope) && !lastOverrideViewKey.equals(activeKey)) {
            LinkedHashMap<String, String> oldOverrides = explicitPropertyAttributeOverridesByView.remove(lastOverrideViewKey);
            if (oldOverrides != null && !oldOverrides.isEmpty()) {
                LinkedHashMap<String, String> existing = explicitPropertyAttributeOverridesByView.get(activeKey);
                if (existing != null) oldOverrides.putAll(existing);
                explicitPropertyAttributeOverridesByView.put(activeKey, oldOverrides);
            }
        }
        lastOverrideScopeKey = scope;
        lastOverrideViewKey = activeKey;
        return activeKey;
    }

    public PropertyInputItem(Context context, boolean z) {
        super(context);
        initialize(context, z);
    }

    private void setIcon(ImageView imageView) {
        icon = 0;
        switch (key) {
            case "property_id" -> icon = R.drawable.ic_mtrl_id;
            case "property_text" -> icon = R.drawable.ic_mtrl_text_select;
            case "property_hint" -> icon = R.drawable.ic_mtrl_bulb;
            case "property_weight", "property_weight_sum" -> icon = R.drawable.ic_mtrl_weight;
            case "property_rotate" -> icon = R.drawable.ic_mtrl_rotate;
            case "property_lines" -> icon = R.drawable.ic_mtrl_numbers;
            case "property_progress" -> icon = R.drawable.ic_mtrl_prog_min;
            case "property_max" -> icon = R.drawable.ic_mtrl_prog_max;
            case "property_alpha" -> icon = R.drawable.ic_mtrl_opacity_full;
            case "property_translation_x" -> icon = R.drawable.ic_mtrl_move_x;
            case "property_translation_y" -> icon = R.drawable.ic_mtrl_move_y;
            case "property_scale_y" -> icon = R.drawable.ic_mtrl_scale_y;
            case "property_scale_x" -> icon = R.drawable.ic_mtrl_scale_x;
            case "property_inject" -> icon = R.drawable.ic_mtrl_code;
            case "property_convert" -> icon = R.drawable.ic_mtrl_switch;
            case "property_text_size" -> icon = R.drawable.ic_mtrl_font;
            case "property_elevation" -> icon = R.drawable.ic_mtrl_elevation;
            case "property_visibility" -> icon = R.drawable.ic_mtrl_visibility;
            case "property_material_icon" -> icon = R.drawable.ic_property_material_icon;
            case "property_material_icon_size" -> icon = R.drawable.ic_property_material_icon_size;
            case "property_material_corner_radius" -> icon = R.drawable.ic_property_material_corner_radius;
            case "property_material_slider_value_from", "property_material_slider_value_to",
                 "property_material_slider_value", "property_material_slider_step_size" -> icon = R.drawable.ic_property_material_slider;
            case "property_material_slider_thumb_radius", "property_material_slider_track_height",
                 "property_material_slider_halo_radius", "property_material_slider_label_behavior",
                 "property_material_slider_style" -> icon = R.drawable.ic_property_material_slider;
            case "property_nested_scroll_fill_viewport", "property_nested_scroll_nested_enabled",
                 "property_nested_scroll_clip_to_padding", "property_nested_scroll_over_scroll_mode",
                 "property_nested_scroll_behavior" -> icon = R.drawable.ic_property_nested_scroll;
            case "property_collapsing_title", "property_collapsing_title_enabled",
                 "property_collapsing_scrim_animation_duration", "property_collapsing_scrim_visible_height_trigger",
                 "property_collapsing_expanded_title_margin_start", "property_collapsing_expanded_title_margin_end",
                 "property_collapsing_expanded_title_margin_top", "property_collapsing_expanded_title_margin_bottom",
                 "property_collapsing_toolbar_id", "property_collapsing_content_scrim",
                 "property_collapsing_status_bar_scrim", "property_collapsing_title_collapse_mode",
                 "property_collapsing_parallax_multiplier" -> icon = R.drawable.ic_property_collapsing_toolbar;
        }
        if (icon == 0) icon = R.drawable.ic_mtrl_code;
        imageView.setImageResource(icon);
        imageView.setImageTintList(android.content.res.ColorStateList.valueOf(
                com.google.android.material.color.MaterialColors.getColor(
                        imageView, R.attr.colorOnSurfaceVariant)));
    }

    public void setTypeView(String typeView) {
        this.typeView = typeView;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
        int identifier = getResources().getIdentifier(key, "string", getContext().getPackageName());
        
        if (identifier > 0) {
            tvName.setText(Helper.getResString(identifier));
        } else {
            String fallback = key.replace("property_", "");
            tvName.setText(fallback.substring(0, 1).toUpperCase() + fallback.substring(1));
        }
        
        if (propertyMenuItem.getVisibility() == VISIBLE) {
            setIcon(findViewById(R.id.img_icon));
            ((TextView) findViewById(R.id.tv_title)).setText(tvName.getText());
            propertyMenuItem.setClickable(true);
            propertyMenuItem.setFocusable(true);
            propertyMenuItem.setOnClickListener(this);
            return;
        }
        setIcon(imgLeftIcon);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        tvValue.setText(value);
    }

    public void setBean(ViewBean bean) {
        boolean sameBean = this.bean == bean;
        this.bean = bean;
        if (sameBean) {
            // This also migrates per-view overrides if this same bean was renamed in place.
            getActiveOverrideViewKey();
        } else {
            lastOverrideScopeKey = getOverrideScopeKey();
            lastOverrideViewKey = getOverrideViewKey(bean);
        }
    }

    @Override
    public void onClick(View v) {
        // Specialized Material/container properties open their own safe dialog or picker.
        // Do not let the legacy generic input guard swallow these newly-added property tiles;
        // this was the reason their cards appeared inert in the editor.
        if (!mB.a() || isSpecializedPropertyAction()) {
            switch (key) {
                case "property_id" -> showViewIdDialog();
                case "property_text", "property_hint" -> showTextInputDialog(9999, false);
                case "property_rotate" -> showHybridSliderDialog(
                        Helper.getText(tvName),
                        Float.parseFloat(value.isEmpty() ? "0" : value),
                        -360f, 360f, 1f, true);
                case "property_alpha" -> showHybridSliderDialog(
                        Helper.getText(tvName),
                        Float.parseFloat(value.isEmpty() ? "1" : value),
                        0f, 1f, 0.1f, false);
                case "property_translation_x", "property_translation_y" -> showHybridSliderDialog(
                        Helper.getText(tvName),
                        Float.parseFloat(value.isEmpty() ? "0" : value),
                        -200f, 200f, 1f, true);
                case "property_scale_x", "property_scale_y" -> {
                    float currentVal = Float.parseFloat(value.isEmpty() ? "1" : value);
                    float maxRange = Math.max(10f, currentVal * 1.2f);
                    showHybridSliderDialog(
                            Helper.getText(tvName),
                            currentVal,
                            0.1f, maxRange, 0.1f, false);
                }

                case "property_text_size" -> {
                    float currentVal = Float.parseFloat(value.isEmpty() ? "14" : value);
                    float maxRange = Math.max(100f, currentVal);
                    float minRange = Math.min(10f, currentVal);
                    showHybridSliderDialog(
                            Helper.getText(tvName),
                            currentVal,
                            minRange, maxRange, 1f, true);
                }
                case "property_weight", "property_weight_sum" -> showHybridSliderDialog(
                        Helper.getText(tvName),
                        Float.parseFloat(value.isEmpty() ? "0" : value),
                        0f, 10f, 1f, true);
                case "property_lines" -> {
                    float currentVal = Float.parseFloat(value.isEmpty() ? "0" : value);
                    float maxRange = Math.max(20f, currentVal);
                    float minRange = Math.min(0f, currentVal);
                    showHybridSliderDialog(
                            Helper.getText(tvName),
                            currentVal,
                            minRange, maxRange, 1f, true);
                }
                case "property_max" -> showHybridSliderDialog(
                        Helper.getText(tvName),
                        Float.parseFloat(value.isEmpty() ? "100" : value),
                        1f, 1000f, 1f, true);
                case "property_progress" -> {
                    float maxValue = bean != null ? bean.max : 100f;
                    showHybridSliderDialog(
                            Helper.getText(tvName),
                            Float.parseFloat(value.isEmpty() ? "0" : value),
                            0f, maxValue, 1f, true);
                }
                case "property_divider_height" -> showHybridSliderDialog(
                        Helper.getText(tvName),
                        Float.parseFloat(value.isEmpty() ? "1" : value),
                        0f, 50f, 1f, true);
                case "property_elevation" -> showHybridSliderDialog(
                        Helper.getText(tvName),
                        Float.parseFloat(value.isEmpty() ? "0" : value.replaceAll("[^0-9.]", "")),
                        0f, 50f, 1f, true);
                case "property_visibility" -> showVisibilityDialog();
                case "property_material_icon" -> showMaterialIconPicker();
                case "property_material_icon_size" -> showDimensionSlider(24f, 1f, 96f);
                case "property_material_corner_radius" -> showDimensionSlider(8f, 0f, 100f);
                case "property_nested_scroll_behavior" -> showNestedScrollBehaviorDialog();
                case "property_material_slider_value_from" -> showHybridSliderDialog(Helper.getText(tvName), parseNumber(value, 0f), -10000f, 10000f, 1f, false);
                case "property_material_slider_value_to" -> showHybridSliderDialog(Helper.getText(tvName), parseNumber(value, 100f), 1f, 10000f, 1f, false);
                case "property_material_slider_step_size" -> showHybridSliderDialog(Helper.getText(tvName), parseNumber(value, 0f), 0f, 1000f, 0.1f, false);
                case "property_material_slider_value" -> {
                    float maxValue = bean == null ? 100f : Math.max(1f, parseNumber(getInjectedAttribute("android:valueTo"), 100f));
                    showHybridSliderDialog(Helper.getText(tvName), parseNumber(value, 0f), -10000f, maxValue, 1f, false);
                }
                case "property_material_slider_thumb_radius", "property_material_slider_track_height", "property_material_slider_halo_radius" -> showDimensionSlider(Helper.getText(tvName), parseNumber(value, 8f), 0f, 64f);
                case "property_collapsing_title", "property_collapsing_toolbar_id",
                     "property_collapsing_content_scrim", "property_collapsing_status_bar_scrim" -> showTextInputDialog(9999, false);
                case "property_collapsing_scrim_animation_duration" -> showHybridSliderDialog(Helper.getText(tvName), parseNumber(value, 600f), 0f, 5000f, 50f, true);
                case "property_collapsing_scrim_visible_height_trigger" -> showDimensionSlider(Helper.getText(tvName), parseNumber(value, 0f), 0f, 1000f);
                case "property_collapsing_expanded_title_margin_start", "property_collapsing_expanded_title_margin_end",
                     "property_collapsing_expanded_title_margin_top", "property_collapsing_expanded_title_margin_bottom" -> showDimensionSlider(Helper.getText(tvName), parseNumber(value, 0f), 0f, 200f);
                case "property_convert" -> showAutoCompleteDialog();
                case "property_inject" -> showInjectDialog();
            }
        }
    }

    private boolean isSpecializedPropertyAction() {
        return key != null && (key.startsWith("property_material_")
                || key.startsWith("property_nested_scroll_")
                || key.startsWith("property_collapsing_"));
    }

    private float parseNumber(String raw, float fallback) {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        try {
            String cleaned = raw.trim().replaceAll("[^0-9.-]", "");
            if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("-")) return fallback;
            return Float.parseFloat(cleaned);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String getInjectedAttribute(String attributeName) {
        if (bean == null || bean.inject == null) return "";
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader("<tag " + bean.inject + "></tag>"));
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String localName = parser.getAttributeName(i);
                        String prefix = parser.getAttributePrefix(i);
                        String qualifiedName = prefix == null || prefix.isEmpty() ? localName : prefix + ":" + localName;
                        if (attributeName.equals(localName) || attributeName.equals(qualifiedName)
                                || attributeName.endsWith(":" + localName)) return parser.getAttributeValue(i);
                    }
                }
                event = parser.next();
            }
        } catch (Exception ignored) { }
        return "";
    }

    private void showDimensionSlider(float fallback, float min, float max) {
        showDimensionSlider(Helper.getText(tvName), parseNumber(value, fallback), min, max);
    }

    private void showDimensionSlider(String title, float current, float min, float max) {
        showHybridSliderDialog(title, Math.max(min, Math.min(max, current)), min, max, 1f, true);
    }

    private void showMaterialIconPicker() {
        if (sc_id == null || sc_id.trim().isEmpty()) {
            SketchwareUtil.toastError("Open a project before selecting a Material icon.");
            return;
        }
        String current = value == null ? "" : value.trim();
        current = current.replaceFirst("^@(?:drawable|mipmap)/", "");
        PropertyResourceItem picker = new PropertyResourceItem(getContext(), false, sc_id, false);
        picker.showPickerDialog(Helper.getText(tvName), R.drawable.ic_property_material_icon, current, selected -> {
            String resolved = selected == null || selected.isEmpty() || "NONE".equalsIgnoreCase(selected)
                    ? "" : selected.startsWith("@") ? selected : "@drawable/" + selected;
            setValue(resolved);
            if (valueChangeListener != null) valueChangeListener.a(key, resolved);
        }, false);
    }

    private void showNestedScrollBehaviorDialog() {
        if (bean == null) {
            SketchwareUtil.toastError("Select a NestedScrollView before editing its scrolling behavior.");
            return;
        }
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_nested_scroll_behavior, null, false);
        android.widget.CompoundButton fillViewport = dialogView.findViewById(R.id.switch_fill_viewport);
        android.widget.CompoundButton nestedScrolling = dialogView.findViewById(R.id.switch_nested_scrolling);
        android.widget.CompoundButton clipToPadding = dialogView.findViewById(R.id.switch_clip_to_padding);
        MaterialAutoCompleteTextView overScrollMode = dialogView.findViewById(R.id.input_over_scroll_mode);

        fillViewport.setChecked(Boolean.parseBoolean(getInjectedAttribute("android:fillViewport")));
        String nestedValue = getInjectedAttribute("android:nestedScrollingEnabled");
        nestedScrolling.setChecked(nestedValue.isEmpty() || Boolean.parseBoolean(nestedValue));
        String clipValue = getInjectedAttribute("android:clipToPadding");
        clipToPadding.setChecked(clipValue.isEmpty() || Boolean.parseBoolean(clipValue));

        String[] labels = {
                Helper.getResString(R.string.property_nested_scroll_over_scroll_always),
                Helper.getResString(R.string.property_nested_scroll_over_scroll_if_content_scrolls),
                Helper.getResString(R.string.property_nested_scroll_over_scroll_never)
        };
        String[] values = {"always", "ifContentScrolls", "never"};
        overScrollMode.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, labels));
        String storedMode = getInjectedAttribute("android:overScrollMode");
        int selectedMode = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(storedMode)) { selectedMode = i; break; }
        }
        overScrollMode.setText(labels[selectedMode], false);
        overScrollMode.setOnClickListener(v -> overScrollMode.showDropDown());

        new MaterialAlertDialogBuilder(getContext())
                .setTitle(Helper.getText(tvName))
                .setIcon(icon)
                .setView(dialogView)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_save, (dialog, which) -> {
                    int modeIndex = 1;
                    String selectedLabel = Helper.getText(overScrollMode).trim();
                    for (int i = 0; i < labels.length; i++) {
                        if (labels[i].equals(selectedLabel)) { modeIndex = i; break; }
                    }
                    // This is an editor-only payload. ViewPropertyItems expands it to real XML attributes.
                    String config = fillViewport.isChecked() + "|" + nestedScrolling.isChecked() + "|"
                            + clipToPadding.isChecked() + "|" + values[modeIndex];
                    setValue(config);
                    if (valueChangeListener != null) valueChangeListener.a(key, config);
                })
                .show();
    }

    private void showVisibilityDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(Helper.getText(tvName));
        dialog.setIcon(icon);

        String[] options = {"visible", "invisible", "gone"};
        int checkedItem = -1;
        
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                checkedItem = i;
                break;
            }
        }

        dialog.setSingleChoiceItems(options, checkedItem, (d, which) -> {
            setValue(options[which]);
            if (valueChangeListener != null) {
                valueChangeListener.a(key, value);
            }
            d.dismiss();
        });

        // Add a Reset button to clear XML injection
        dialog.setNeutralButton(Helper.getResString(R.string.common_word_reset), (d, which) -> {
            setValue("");
            if (valueChangeListener != null) {
                valueChangeListener.a(key, "");
            }
            d.dismiss();
        });

        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void showHybridSliderDialog(String propertyName, float currentValue, float minValue, float maxValue, float stepSize, boolean isInteger) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(propertyName);
        dialog.setIcon(icon);

        PropertyPopupHybridBinding binding = PropertyPopupHybridBinding.inflate(LayoutInflater.from(getContext()));

        float validCurrentValue = Math.max(minValue, Math.min(maxValue, currentValue));

        binding.slider.setValueFrom(minValue);
        binding.slider.setValueTo(maxValue);
        binding.slider.setStepSize(stepSize);

        boolean isCenteredProperty = key.equals("property_translation_x") ||
                key.equals("property_translation_y") ||
                key.equals("property_rotate");

        if (isCenteredProperty) {
            binding.slider.setCentered(true);
        }

        binding.slider.setValue(minValue);

        binding.edInput.setText(isInteger ? String.valueOf((int) validCurrentValue) : String.valueOf(validCurrentValue));
        binding.tiInput.setHint(String.format(Helper.getResString(R.string.property_enter_value), propertyName));

        updateValueDisplay(binding.tvCurrentValue, validCurrentValue, isInteger);

        binding.slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) {
                float roundedSliderValue = Math.round(value * 10.0f) / 10.0f;
                updateValueDisplay(binding.tvCurrentValue, roundedSliderValue, isInteger);
                binding.edInput.setText(isInteger ? String.valueOf((int) roundedSliderValue) :
                        String.format(Locale.US, "%.1f", roundedSliderValue));
            }
        });

        binding.edInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float inputValue = Float.parseFloat(s.toString());
                    boolean isUnlimited = key.equals("property_translation_x") || key.equals("property_translation_y") ||
                            key.equals("property_text_size") || key.equals("property_lines") ||
                            key.equals("property_scale_x") || key.equals("property_scale_y");

                    if (isUnlimited) {
                        float minLimit = 0.0f;
                        if (key.equals("property_translation_x") || key.equals("property_translation_y")) {
                            minLimit = Float.NEGATIVE_INFINITY;
                        } else if (key.equals("property_scale_x") || key.equals("property_scale_y")) {
                            minLimit = 0.1f;
                        }

                        if (inputValue >= minLimit) {
                            if (inputValue >= minValue && inputValue <= maxValue) {
                                binding.slider.setValue(inputValue);
                            }
                            binding.tiInput.setError(null);
                        } else {
                            if (key.equals("property_scale_x") || key.equals("property_scale_y")) {
                                binding.tiInput.setError("Value must be 0.1 or greater");
                            } else {
                                binding.tiInput.setError("Value must be 0 or greater");
                            }
                        }
                    } else if (key.equals("property_progress") && bean != null) {
                        float maxLimit = bean.max;
                        if (inputValue >= 0 && inputValue <= maxLimit) {
                            binding.slider.setValue(inputValue);
                            binding.tiInput.setError(null);
                        } else {
                            binding.tiInput.setError(String.format("Value must be between 0 and %.0f", maxLimit));
                        }
                    } else {
                        if (inputValue >= minValue && inputValue <= maxValue) {
                            binding.slider.setValue(inputValue);
                            binding.tiInput.setError(null);
                        } else {
                            binding.tiInput.setError(String.format("Value must be between %.1f and %.1f", minValue, maxValue));
                        }
                    }
                } catch (NumberFormatException e) {
                    binding.tiInput.setError("Invalid value");
                }
            }
        });

        dialog.setView(binding.getRoot());
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            float finalValue;
            try {
                finalValue = Float.parseFloat(binding.edInput.getText().toString());
            } catch (NumberFormatException e) {
                finalValue = validCurrentValue;
            }

            if (binding.sliderSection.getVisibility() == View.VISIBLE) {
                setValue(isInteger ? String.valueOf((int) finalValue) :
                        String.format(Locale.US, "%.1f", Math.round(finalValue * 10.0f) / 10.0f));
            } else {
                setValue(isInteger ? String.valueOf((int) finalValue) :
                        String.format(Locale.US, "%.2f", finalValue));
            }

            if (valueChangeListener != null) {
                valueChangeListener.a(key, value);
            }

            v.dismiss();
        });

        // Negative button explicitly clears the XML attribute entirely
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_reset), (v, which) -> {
            setValue("");
            if (valueChangeListener != null) {
                valueChangeListener.a(key, "");
            }
            v.dismiss();
        });
        
        dialog.setNeutralButton("Custom", null);

        AlertDialog alertDialog = dialog.create();

        alertDialog.setOnShowListener(dialogInterface -> {
            float animationStartValue;
            if (key.equals("property_translation_x") || key.equals("property_translation_y") || key.equals("property_rotate")) {
                animationStartValue = 0f;
            } else {
                animationStartValue = minValue;
            }

            ValueAnimator sliderAnimator = ValueAnimator.ofFloat(animationStartValue, validCurrentValue);
            sliderAnimator.setDuration(800);
            sliderAnimator.setInterpolator(new DecelerateInterpolator());

            sliderAnimator.addUpdateListener(animation -> {
                float animatedValue = (float) animation.getAnimatedValue();
                float validAnimatedValue = Math.round(animatedValue / stepSize) * stepSize;
                validAnimatedValue = Math.max(minValue, Math.min(maxValue, validAnimatedValue));
                binding.slider.setValue(validAnimatedValue);
                updateValueDisplay(binding.tvCurrentValue, validAnimatedValue, isInteger);
            });

            sliderAnimator.start();

            Button customButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);

            customButton.setOnClickListener(v -> {
                if (binding.sliderSection.getVisibility() == View.VISIBLE) {
                    binding.sliderSection.setVisibility(View.GONE);
                    binding.tiInput.setVisibility(View.VISIBLE);
                    binding.edInput.requestFocus();
                    customButton.setText("Slider");
                } else {
                    binding.sliderSection.setVisibility(View.VISIBLE);
                    binding.tiInput.setVisibility(View.GONE);
                    customButton.setText("Custom");
                }
            });
        });

        alertDialog.show();
    }

    private void updateValueDisplay(TextView textView, float value, boolean isInteger) {
        if (key.equals("property_rotate")) {
            textView.setText(Math.round(value) + "°");
        } else if (key.equals("property_text_size")) {
            textView.setText(Math.round(value) + "sp");
        } else if (key.equals("property_divider_height")) {
            textView.setText(Math.round(value) + "dp");
        } else if (isInteger) {
            textView.setText(String.valueOf(Math.round(value)));
        } else {
            textView.setText(String.format("%.2f", value));
        }
    }

    private float getDefaultValue(String key) {
        return switch (key) {
            case "property_alpha", "property_scale_x", "property_scale_y" -> 1.0f;
            case "property_text_size" -> 14.0f;
            case "property_max" -> 100.0f;
            default -> 0.0f;
        };
    }

    public void setOnPropertyValueChangeListener(Kw onPropertyValueChangeListener) {
        valueChangeListener = onPropertyValueChangeListener;
    }

    public void setOrientationItem(int orientationItem) {
        if (orientationItem == 0) {
            propertyItem.setVisibility(GONE);
            propertyMenuItem.setVisibility(VISIBLE);
            propertyItem.setOnClickListener(null);
            propertyMenuItem.setOnClickListener(this);
        } else {
            propertyItem.setVisibility(VISIBLE);
            propertyMenuItem.setVisibility(GONE);
            propertyItem.setOnClickListener(this);
            propertyMenuItem.setOnClickListener(null);
        }
    }

    private void initialize(Context context, boolean z) {
        this.context = context;
        wB.a(context, this, R.layout.property_input_item);
        tvName = findViewById(R.id.tv_name);
        tvValue = findViewById(R.id.tv_value);
        imgLeftIcon = findViewById(R.id.img_left_icon);
        propertyItem = findViewById(R.id.property_item);
        propertyMenuItem = findViewById(R.id.property_menu_item);
    }

    private void showViewIdDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(Helper.getText(tvName));
        dialog.setIcon(icon);

        PropertyPopupInputTextBinding binding = PropertyPopupInputTextBinding.inflate(LayoutInflater.from(getContext()));

        binding.tiInput.setHint(String.format(Helper.getResString(R.string.property_enter_value), "widget ID"));

        binding.edInput.setSingleLine();
        PropertyNameValidator validator = new PropertyNameValidator(context, binding.tiInput, uq.b, uq.a(), jC.a(sc_id).a(projectFileBean), value);
        validator.a(value);
        dialog.setView(binding.getRoot());
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            if (validator.b()) {
                setValue(Helper.getText(binding.edInput));
                if (valueChangeListener != null) valueChangeListener.a(key, value);
                v.dismiss();
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    public void a(String projectId, ProjectFileBean projectFileBean) {
        sc_id = projectId;
        this.projectFileBean = projectFileBean;
    }

    private void showTextInputDialog(int maxValue, boolean isInject) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(Helper.getText(tvName));
        dialog.setIcon(icon);

        PropertyPopupInputTextBinding binding = PropertyPopupInputTextBinding.inflate(LayoutInflater.from(getContext()));

        binding.tiInput.setHint(String.format(Helper.getResString(R.string.property_enter_value), Helper.getText(tvName)));

        SB lengthValidator;

        if (isInject) {
            lengthValidator = new SB(context, binding.tiInput, 0, maxValue);
            binding.tiAutoCompleteInput.setVisibility(View.GONE);
            SyntaxScheme.setXMLHighlighter(binding.edInput);
        } else {
            loadStringsListMap();
            setupTextWatcher(binding.tiAutoCompleteInput, binding.edTiAutoCompleteInput);

            lengthValidator = new SB(context, binding.tiAutoCompleteInput, 0, maxValue);
            binding.tiAutoCompleteInput.setVisibility(View.VISIBLE);
            binding.tiInput.setVisibility(View.GONE);

            dialog.setView(binding.getRoot());

            setupAutoCompleteTextView(binding.edTiAutoCompleteInput);
        }

        lengthValidator.a(value);
        dialog.setView(binding.getRoot());
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) ->
                handleSave(lengthValidator, binding.edInput, binding.edTiAutoCompleteInput, binding.tiAutoCompleteInput, isInject, v));
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);

        AlertDialog alertDialog = dialog.create();

        alertDialog.setOnShowListener(d -> {
            if (!isInject) {
                Button neutralButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                neutralButton.setVisibility(View.VISIBLE);
                neutralButton.setText(Helper.getResString(R.string.strings_xml));
                neutralButton.setOnClickListener(view -> {
                    if (binding.edTiAutoCompleteInput.getText().toString().isEmpty()) {
                        binding.edTiAutoCompleteInput.setText(stringsStart);
                        binding.edTiAutoCompleteInput.setSelection(stringsStart.length());
                    }
                    binding.edTiAutoCompleteInput.requestFocus();
                });
            }
        });
        alertDialog.show();
    }

    private void setupAutoCompleteTextView(MaterialAutoCompleteTextView autoCompleteTextView) {
        keysList = new ArrayList<>();
        List<String> mergedList = new ArrayList<>();

        for (HashMap<String, Object> map : stringsListMap) {
            String keyValue = map.get("key").toString();
            keysList.add(stringsStart + keyValue);
            mergedList.add(stringsStart + keyValue + " ( " + map.get("text") + " )");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, mergedList);
        autoCompleteTextView.setAdapter(adapter);
        autoCompleteTextView.setThreshold(1);
        autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            String value = Helper.getText(autoCompleteTextView);
            autoCompleteTextView.setText(value.substring(0, value.indexOf(" (")));
            autoCompleteTextView.setSelection(autoCompleteTextView.getText().length());
        });
    }

    private void handleSave(SB lengthValidator, EditText input,
                            MaterialAutoCompleteTextView autoCompleteTextView, TextInputLayout textAutoCompleteInput,
                            boolean isInject, DialogInterface dialog) {
        if (lengthValidator.b() && textAutoCompleteInput.getError() == null) {
            if (isInject) {
                setValue(Helper.getText(input));
            } else {
                setValue(Helper.getText(autoCompleteTextView));
            }
            if (valueChangeListener != null) {
                String inputText = Helper.getText(autoCompleteTextView);

                if (inputText.equals(stringsStart)) {

                    String errorMessage = MessageFormat.format(
                            "Please select a String\n" +
                                    "or remove \"{0}\" and add the value directly",
                            stringsStart
                    );
                    textAutoCompleteInput.setError(errorMessage);
                } else {
                    valueChangeListener.a(key, value);
                    dialog.dismiss();
                }
            }
        }
    }

    private void loadStringsListMap() {
        String filePath = FileUtil.getExternalStorageDir().concat("/.sketchware/data/").concat(sc_id.concat("/files/resource/values/strings.xml"));
        StringsEditorManager stringsEditorManager = new StringsEditorManager();
        stringsEditorManager.convertXmlStringsToListMap(FileUtil.readFileIfExist(filePath), stringsListMap);

        if (!stringsEditorManager.isXmlStringsExist(stringsListMap, "app_name") &&
                filePath != null) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("key", "app_name");
            map.put("text", yB.c(lC.b(sc_id), "my_app_name"));
            stringsListMap.add(0, map);
        }
    }

    public void setupTextWatcher(TextInputLayout textAutoCompleteInput, MaterialAutoCompleteTextView editText) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (!text.startsWith(stringsStart) || text.equals(stringsStart)) {
                    textAutoCompleteInput.setError(null);
                    return;
                }

                boolean isExactMatch = keysList.contains(text);
                textAutoCompleteInput.setError(isExactMatch ? null : "Not found in strings.xml");
            }
        });
    }

    private void showNumberDecimalInputDialog(int minValue, int maxValue) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(Helper.getText(tvName));
        dialog.setIcon(icon);

        PropertyPopupInputTextBinding binding = PropertyPopupInputTextBinding.inflate(LayoutInflater.from(getContext()));
        binding.tiInput.setHint(String.format(Helper.getResString(R.string.property_enter_value), Helper.getText(tvName)));

        binding.edInput.setInputType((minValue < 0)
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        binding.edInput.setText(value);

        OB validator = new OB(context, binding.tiInput, minValue, maxValue);

        dialog.setView(binding.getRoot());
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            if (validator.b()) {
                setValue(Helper.getText(binding.edInput));
                if (valueChangeListener != null) valueChangeListener.a(key, value);
                v.dismiss();
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void showAutoCompleteDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(Helper.getText(tvName));
        dialog.setIcon(icon);

        PropertyPopupInputTextBinding binding = PropertyPopupInputTextBinding.inflate(LayoutInflater.from(getContext()));
        MaterialAutoCompleteTextView input = binding.edTiAutoCompleteInput;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, getPrioritizedSuggestions(typeView));
        input.setText(value);
        input.setAdapter(adapter);
        binding.tiInput.setVisibility(View.GONE);
        binding.tiAutoCompleteInput.setVisibility(View.VISIBLE);
        SB lengthValidator = new SB(context, binding.tiInput, 0, 99);
        lengthValidator.a(value);
        dialog.setView(binding.getRoot());
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            if (lengthValidator.b()) {
                setValue(Helper.getText(input));
                if (valueChangeListener != null) valueChangeListener.a(key, value);
                v.dismiss();
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    public List<String> getPrioritizedSuggestions(String typeView) {
        List<String> prioritizedSuggestions = new ArrayList<>();

        switch (typeView) {
            case "0":
            case "1":
            case "50":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "LinearLayout", "FrameLayout", "RelativeLayout",
                        "androidx.constraintlayout.widget.ConstraintLayout",
                        "TableLayout", "GridLayout",
                        "androidx.coordinatorlayout.widget.CoordinatorLayout",
                        "Space"
                ));
                break;

            case "2":
            case "12":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "HorizontalScrollView", "VerticalScrollView", "ScrollView",
                        "androidx.core.widget.NestedScrollView"
                ));
                break;

            case "9":
            case "25":
            case "48":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "ListView", "GridView", "androidx.recyclerview.widget.RecyclerView",
                        "ExpandableListView", "androidx.viewpager2.widget.ViewPager2"
                ));
                break;

            case "3":
            case "41":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "Button", "com.google.android.material.button.MaterialButton",
                        "ImageButton", "ToggleButton", "CompoundButton",
                        "androidx.appcompat.widget.AppCompatButton"
                ));
                break;

            case "4":
            case "5":
            case "38":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "TextView", "EditText",
                        "com.google.android.material.textfield.TextInputLayout",
                        "com.google.android.material.textfield.TextInputEditText",
                        "AutoCompleteTextView",
                        "androidx.appcompat.widget.AppCompatTextView",
                        "androidx.appcompat.widget.AppCompatEditText"
                ));
                break;

            case "21":
            case "7":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "VideoView", "android.webkit.WebView",
                        "androidx.media.widget.VideoView",
                        "android.webkit.WebViewClient", "android.webkit.WebChromeClient"
                ));
                break;

            case "8":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "ProgressBar",
                        "com.google.android.material.progressindicator.CircularProgressIndicator",
                        "com.google.android.material.progressindicator.LinearProgressIndicator",
                        "com.google.android.material.progressindicator.LoadingIndicator"
                ));
                break;

            case "14":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "SeekBar", "com.google.android.material.slider.Slider",
                        "HorizontalScrollBar", "VerticalSeekBar"
                ));
                break;

            case "11":
            case "19":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "CheckBox", "RadioButton",
                        "androidx.appcompat.widget.AppCompatCheckBox",
                        "androidx.appcompat.widget.AppCompatRadioButton"
                ));
                break;

            case "30":
            case "31":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "com.google.android.material.tabs.TabLayout",
                        "androidx.viewpager.widget.ViewPager",
                        "androidx.viewpager2.widget.ViewPager2",
                        "androidx.fragment.app.FragmentPagerAdapter",
                        "androidx.fragment.app.FragmentStatePagerAdapter",
                        "com.google.android.material.tabs.TabLayoutMediator"
                ));
                break;

            case "32":
                prioritizedSuggestions.add("com.google.android.material.bottomnavigation.BottomNavigationView");
                break;

            case "36":
                prioritizedSuggestions.addAll(Arrays.asList(
                        "androidx.cardview.widget.CardView",
                        "com.google.android.material.card.MaterialCardView"
                ));
                break;

            case "39":
                prioritizedSuggestions.add("androidx.swiperefreshlayout.widget.SwipeRefreshLayout");
                break;
        }

        List<String> allSuggestions = new ArrayList<>(getFullSuggestions());

        allSuggestions.removeAll(prioritizedSuggestions);

        List<String> finalSuggestions = new ArrayList<>(prioritizedSuggestions);
        finalSuggestions.addAll(allSuggestions);

        return finalSuggestions;
    }

    public List<String> getFullSuggestions() {
        return Arrays.asList(getResources().getStringArray(R.array.property_convert_options));
    }

    private List<String> populateAttributes() {
        List<String> attrs = new ArrayList<>();

        if (bean == null) return attrs;
        var simpleName = getSimpleName(bean);
        var classInfo = bean.getClassInfo();

        if (classInfo.b("CardView") || simpleName.equals("MaterialCardView")) {
            attrs.add("app:cardBackgroundColor");
            attrs.add("app:cardElevation");
            attrs.add("app:cardCornerRadius");
            attrs.add("app:cardUseCompatPadding");
            if (simpleName.equals("MaterialCardView")) {
                attrs.add("app:strokeColor");
                attrs.add("app:strokeWidth");
                attrs.add("app:rippleColor");
                attrs.add("app:checkedIconVisible");
            }
        }

        if (classInfo.b("TextView")) {
            attrs.add("android:drawableStart");
            attrs.add("android:drawableTop");
            attrs.add("android:drawableEnd");
            attrs.add("android:drawableBottom");
            attrs.add("android:drawableLeft");
            attrs.add("android:drawableRight");
        }

        if (classInfo.b("ImageView") || simpleName.equals("ShapeableImageView")) {
            attrs.add("android:src");
        }

        if (classInfo.b("MaterialButton") || classInfo.b("Button")
                || simpleName.equals("ExtendedFloatingActionButton")) {
            attrs.add("style");
            attrs.add("app:cornerRadius");
            attrs.add("app:backgroundTint");
            attrs.add("app:strokeWidth");
            attrs.add("app:strokeColor");
            attrs.add("app:icon");
            attrs.add("app:iconTint");
            attrs.add("app:iconGravity");
            attrs.add("app:iconSize");
            attrs.add("app:iconPadding");
            attrs.add("app:iconTintMode");
            attrs.add("app:rippleColor");
            attrs.add("app:insetTop");
            attrs.add("app:insetBottom");
        }

        if (classInfo.b("TextInputLayout") || simpleName.equals("TextInputLayout")) {
            attrs.add("app:boxBackgroundMode");
            attrs.add("app:boxBackgroundColor");
            attrs.add("app:boxStrokeColor");
            attrs.add("app:boxStrokeWidth");
            attrs.add("app:boxStrokeWidthFocused");
            attrs.add("app:boxCornerRadiusTopStart");
            attrs.add("app:boxCornerRadiusTopEnd");
            attrs.add("app:boxCornerRadiusBottomStart");
            attrs.add("app:boxCornerRadiusBottomEnd");
            attrs.add("app:hintEnabled");
            attrs.add("app:helperText");
            attrs.add("app:errorEnabled");
            attrs.add("app:endIconMode");
            attrs.add("app:startIconDrawable");
            attrs.add("app:endIconDrawable");
            attrs.add("app:counterEnabled");
        }

        if (simpleName.equals("MaterialAutoCompleteTextView") || simpleName.equals("TextInputEditText")) {
            attrs.add("app:simpleItems");
            attrs.add("app:dropDownBackgroundTint");
        }

        if (classInfo.b("Chip") || simpleName.equals("Chip")) {
            attrs.add("app:chipBackgroundColor");
            attrs.add("app:chipStrokeColor");
            attrs.add("app:chipStrokeWidth");
            attrs.add("app:chipCornerRadius");
            attrs.add("app:chipMinHeight");
            attrs.add("app:chipIcon");
            attrs.add("app:chipIconTint");
            attrs.add("app:checkedIcon");
            attrs.add("app:checkedIconVisible");
            attrs.add("app:closeIcon");
            attrs.add("app:closeIconTint");
            attrs.add("app:rippleColor");
        }

        if (classInfo.b("ChipGroup") || classInfo.b("MaterialButtonToggleGroup")
                || simpleName.equals("ChipGroup") || simpleName.equals("MaterialButtonToggleGroup")) {
            attrs.add("app:singleSelection");
            attrs.add("app:selectionRequired");
            attrs.add("app:checkedButton");
            attrs.add("app:chipSpacingHorizontal");
            attrs.add("app:chipSpacingVertical");
            attrs.add("app:singleLine");
        }

        if (classInfo.b("ImageView") || classInfo.b("CircleImageView") || simpleName.equals("ShapeableImageView")) {
            attrs.add("app:tint");
            if (classInfo.b("CircleImageView")) {
                attrs.add("app:civ_border_width");
                attrs.add("app:civ_border_color");
                attrs.add("app:civ_circle_background_color");
            }
            if (simpleName.equals("ShapeableImageView")) {
                attrs.add("app:shapeAppearance");
                attrs.add("app:shapeAppearanceOverlay");
                attrs.add("app:strokeColor");
                attrs.add("app:strokeWidth");
            }
        }

        if (classInfo.b("Switch") || classInfo.b("CheckBox") || classInfo.b("RadioButton")
                || simpleName.equals("MaterialSwitch") || simpleName.equals("MaterialCheckBox")
                || simpleName.equals("MaterialRadioButton")) {
            attrs.add("app:buttonTint");
            attrs.add("app:buttonIcon");
            attrs.add("app:buttonIconTint");
            if (classInfo.b("Switch") || simpleName.equals("MaterialSwitch")) {
                attrs.add("app:thumbTint");
                attrs.add("app:trackTint");
                attrs.add("app:thumbIcon");
                attrs.add("app:thumbIconTint");
                attrs.add("app:trackDecorationTint");
            }
        }

        if (classInfo.b("ProgressBar") || classInfo.b("SeekBar")
                || simpleName.equals("CircularProgressIndicator")
                || simpleName.equals("LinearProgressIndicator")
                || simpleName.equals("LoadingIndicator")) {
            attrs.add("app:indicatorColor");
            attrs.add("app:trackColor");
            attrs.add("app:trackThickness");
            if (simpleName.equals("CircularProgressIndicator")) {
                attrs.add("app:indicatorSize");
                attrs.add("app:trackCornerRadius");
                attrs.add("app:indicatorDirectionCircular");
            }
            if (simpleName.equals("LinearProgressIndicator")) {
                attrs.add("app:trackCornerRadius");
                attrs.add("app:indeterminateAnimationType");
                attrs.add("app:showAnimationBehavior");
                attrs.add("app:hideAnimationBehavior");
                attrs.add("app:indicatorDirectionLinear");
            }
            if (classInfo.b("SeekBar") || simpleName.equals("Slider") || simpleName.equals("MaterialSlider")) {
                attrs.add("style");
                attrs.add("android:valueFrom");
                attrs.add("android:valueTo");
                attrs.add("android:stepSize");
                attrs.add("android:value");
                attrs.add("app:labelBehavior");
                attrs.add("app:thumbColor");
                attrs.add("app:haloColor");
                attrs.add("app:tickColorActive");
                attrs.add("app:tickColorInactive");
                attrs.add("app:trackColorActive");
                attrs.add("app:trackColorInactive");
                attrs.add("app:thumbRadius");
                attrs.add("app:haloRadius");
                attrs.add("app:trackHeight");
            }
        }

        if (simpleName.equals("NestedScrollView")) {
            attrs.add("android:fillViewport");
            attrs.add("android:nestedScrollingEnabled");
            attrs.add("android:clipToPadding");
            attrs.add("android:overScrollMode");
        }

        if (simpleName.equals("CollapsingToolbarLayout")) {
            attrs.add("app:title");
            attrs.add("app:titleEnabled");
            attrs.add("app:titleCollapseMode");
            attrs.add("app:scrimAnimationDuration");
            attrs.add("app:scrimVisibleHeightTrigger");
            attrs.add("app:expandedTitleMarginStart");
            attrs.add("app:expandedTitleMarginEnd");
            attrs.add("app:expandedTitleMarginTop");
            attrs.add("app:expandedTitleMarginBottom");
            attrs.add("app:toolbarId");
            attrs.add("app:contentScrim");
            attrs.add("app:statusBarScrim");
        }

        if (classInfo.b("TabLayout")) {
            attrs.add("app:tabIndicatorColor");
            attrs.add("app:tabIndicatorHeight");
            attrs.add("app:tabTextColor");
            attrs.add("app:tabSelectedTextColor");
            attrs.add("app:tabMode");
            attrs.add("app:tabGravity");
        }
        if (classInfo.b("BottomNavigationView")) {
            attrs.add("app:itemIconTint");
            attrs.add("app:itemTextColor");
            attrs.add("app:itemRippleColor");
        }
        return attrs.stream().distinct().collect(Collectors.toList());
    }

    private String getSimpleName(ViewBean bean) {
        return Jx.WIDGET_NAME_PATTERN.matcher(bean.convert).replaceAll("");
    }

    private void showInjectDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        var binding = PropertyPopupParentAttrBinding.inflate(LayoutInflater.from(getContext()));
        dialog.setContentView(binding.getRoot());
        dialog.show();

        binding.title.setText(Helper.getText(tvName));

        var adapter = new AttributesAdapter();
        adapter.setOnItemClickListener(
                new AttributesAdapter.ItemClickListener() {
                    @Override
                    public void onItemClick(LinkedHashMap<String, String> attributes, String attr) {
                        setAttributeValue(attr, attributes);
                        dialog.dismiss();
                    }

                    @Override
                    public void onItemLongClick(LinkedHashMap<String, String> attributes, String attr) {
                        dialog.dismiss();
                        var builder =
                                new MaterialAlertDialogBuilder(getContext())
                                        .setTitle("Delete")
                                        .setMessage("Are you sure you want to delete " + attr + "?")
                                        .setPositiveButton(
                                                R.string.common_word_yes,
                                                (d, w) -> {
                                                    attributes.remove(attr);
                                                    saveAttributes(attributes);
                                                })
                                        .setNegativeButton(R.string.common_word_no, null);
                        var deleteDialog = builder.create();
                        deleteDialog.setOnDismissListener(d -> showInjectDialog());
                        deleteDialog.show();
                    }
                });
        binding.recyclerView.setAdapter(adapter);
        var dividerItemDecoration =
                new DividerItemDecoration(
                        binding.recyclerView.getContext(), LinearLayoutManager.VERTICAL);
        binding.recyclerView.addItemDecoration(dividerItemDecoration);
        var attributes = readAttributes();
        adapter.setAttributes(attributes);
        List<String> keys = new ArrayList<>(attributes.keySet());
        adapter.submitList(keys);

        binding.add.setOnClickListener(
                v -> {
                    addNewAttribute(attributes);
                    dialog.dismiss();
                });
        binding.sourceCode.setVisibility(View.VISIBLE);
        binding.sourceCode.setOnClickListener(
                v -> {
                    showTextInputDialog(9999, true);
                    dialog.dismiss();
                });
    }

    private void addNewAttribute(Map<String, String> attributes) {
        var builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle("Add new attribute");

        PropertyPopupInputTextBinding binding =
                PropertyPopupInputTextBinding.inflate(LayoutInflater.from(getContext()));

        var input = binding.edTiAutoCompleteInput;
        binding.tiInput.setVisibility(View.GONE);
        binding.tiAutoCompleteInput.setVisibility(View.VISIBLE);
        binding.tiAutoCompleteInput.setHint("Enter new attribute");
        input.setAdapter(
                new ArrayAdapter<>(
                        getContext(),
                        android.R.layout.simple_list_item_1,
                        populateAttributes().stream()
                                .filter(attr -> !attributes.containsKey(attr))
                                .collect(Collectors.toList())));

        builder.setView(binding.getRoot());
        builder.setPositiveButton(
                R.string.common_word_next,
                (d, w) -> {
                    var inputValue = Helper.getText(input).trim();
                    if (!inputValue.isEmpty()) {
                        setAttributeValue(inputValue, attributes);
                    } else {
                        d.cancel();
                    }
                });
        builder.setNegativeButton(R.string.common_word_cancel, (d, w) -> d.cancel());
        var dialog = builder.create();
        dialog.setOnCancelListener(d -> showInjectDialog());
        dialog.setOnShowListener(
                d -> {
                    var positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    positiveButton.setEnabled(!Helper.getText(input).trim().isEmpty());

                    input.addTextChangedListener(
                            new BaseTextWatcher() {
                                @Override
                                public void onTextChanged(
                                        CharSequence charSequence,
                                        int start,
                                        int before,
                                        int after) {
                                    positiveButton.setEnabled(
                                            !Helper.getText(input).trim().isEmpty());
                                }
                            });
                });
        dialog.show();
    }

    private void setAttributeValue(String attr, Map<String, String> attributes) {
        var builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(attr);

        PropertyPopupInputTextBinding binding =
                PropertyPopupInputTextBinding.inflate(LayoutInflater.from(getContext()));

        var input = binding.edInput;
        if (attributes.containsKey(attr)) {
            input.setText(attributes.get(attr));
        }

        binding.tiInput.setHint(
                String.format(Helper.getResString(R.string.property_enter_value), attr));

        if (getContext() instanceof Activity) {
            AttributeInputHelper.wireFixed((Activity) getContext(), sc_id, attr, binding.tiInput, input);
        }

        builder.setView(binding.getRoot());
        builder.setPositiveButton(
                R.string.common_word_save,
                (d, w) -> {
                    var inputValue = Helper.getText(input).trim();
                    if (!inputValue.isEmpty()) {
                        attributes.put(attr, inputValue);
                        saveAttributes(attributes);
                    } else {
                        d.cancel();
                    }
                });
        builder.setNegativeButton(R.string.common_word_cancel, null);
        var dialog = builder.create();
        dialog.setOnShowListener(
                d -> {
                    var positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    positiveButton.setEnabled(!Helper.getText(input).trim().isEmpty());

                    input.addTextChangedListener(
                            new BaseTextWatcher() {
                                @Override
                                public void onTextChanged(
                                        CharSequence charSequence,
                                        int start,
                                        int before,
                                        int after) {
                                    positiveButton.setEnabled(
                                            !Helper.getText(input).trim().isEmpty());
                                }
                            });
                });
        dialog.show();
        dialog.setOnDismissListener(d -> showInjectDialog());
    }

    private boolean isPropertyBackedAttribute(String name) {
        if (name == null) return false;
        if ("android:elevation".equals(name) || "android:visibility".equals(name)
                || "elevation".equals(name) || "visibility".equals(name)) return true;
        if (bean == null || bean.convert == null) return false;

        String convert = bean.convert;
        int classSeparator = convert.lastIndexOf('.');
        String simpleName = classSeparator >= 0 ? convert.substring(classSeparator + 1) : convert;
        String local = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        boolean materialButton = simpleName.endsWith("MaterialButton") || simpleName.equals("MaterialIconButton")
                || simpleName.endsWith("ExtendedFloatingActionButton");
        if (materialButton && ("style".equals(name) || "icon".equals(local)
                || "iconGravity".equals(local) || "iconSize".equals(local) || "cornerRadius".equals(local))) return true;

        boolean progress = simpleName.endsWith("MaterialProgressIndicator") || simpleName.endsWith("CircularProgressIndicator")
                || simpleName.endsWith("LinearProgressIndicator") || simpleName.endsWith("LoadingIndicator");
        if (progress && "style".equals(name)) return true;

        boolean slider = simpleName.endsWith("Slider") || simpleName.endsWith("MaterialSlider");
        if (slider && ("style".equals(name) || "valueFrom".equals(local) || "valueTo".equals(local)
                || "stepSize".equals(local) || "value".equals(local) || "thumbRadius".equals(local)
                || "trackHeight".equals(local) || "haloRadius".equals(local) || "labelBehavior".equals(local))) return true;

        boolean nestedScroll = simpleName.endsWith("NestedScrollView");
        if (nestedScroll && ("fillViewport".equals(local) || "nestedScrollingEnabled".equals(local)
                || "clipToPadding".equals(local) || "overScrollMode".equals(local))) return true;

        boolean collapsingToolbar = simpleName.endsWith("CollapsingToolbarLayout");
        if (collapsingToolbar && ("title".equals(local) || "titleEnabled".equals(local)
                || "titleCollapseMode".equals(local) || "scrimAnimationDuration".equals(local)
                || "scrimVisibleHeightTrigger".equals(local) || "expandedTitleMarginStart".equals(local)
                || "expandedTitleMarginEnd".equals(local) || "expandedTitleMarginTop".equals(local)
                || "expandedTitleMarginBottom".equals(local) || "toolbarId".equals(local)
                || "contentScrim".equals(local) || "statusBarScrim".equals(local))) return true;

        if (("layout_collapseMode".equals(local) || "layout_collapseParallaxMultiplier".equals(local))
                && isChildOfCollapsingToolbar()) return true;
        if (simpleName.endsWith("CoordinatorLayout") && ("keylines".equals(local) || "statusBarBackground".equals(local))) return true;
        return false;
    }

    private boolean isChildOfCollapsingToolbar() {
        if (bean == null || bean.parent == null || sc_id == null || projectFileBean == null) return false;
        try {
            for (ViewBean candidate : jC.a(sc_id).d(projectFileBean.getXmlName())) {
                if (bean.parent.equals(candidate.id)) {
                    return candidate.convert != null && candidate.convert.endsWith("CollapsingToolbarLayout");
                }
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    private String canonicalizePropertyBackedAttribute(String name) {
        if ("elevation".equals(name)) return "android:elevation";
        if ("visibility".equals(name)) return "android:visibility";
        return name;
    }

    private void saveAttributes(Map<String, String> attributes) {
        String viewKey = getActiveOverrideViewKey();
        final LinkedHashMap<String, String> previousOverrides = explicitPropertyAttributeOverridesByView.getOrDefault(viewKey, new LinkedHashMap<>());

        final LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        readAllAttributes().forEach((name, existingValue) -> {
            if (isPropertyBackedAttribute(name)) {
                String canonicalName = canonicalizePropertyBackedAttribute(name);
                if (!previousOverrides.containsKey(canonicalName)) merged.put(canonicalName, existingValue);
            }
        });

        LinkedHashMap<String, String> updatedOverrides = new LinkedHashMap<>();
        attributes.forEach((name, attributeValue) -> {
            String canonicalName = canonicalizePropertyBackedAttribute(name);
            merged.put(canonicalName, attributeValue);
            if (isPropertyBackedAttribute(name)) updatedOverrides.put(canonicalName, attributeValue);
        });

        if (updatedOverrides.isEmpty()) explicitPropertyAttributeOverridesByView.remove(viewKey);
        else explicitPropertyAttributeOverridesByView.put(viewKey, updatedOverrides);

        String result =
                merged.entrySet().stream()
                        .map(entry -> entry.getKey() + "=\"" + pro.sketchware.utility.XmlUtil.escapeAttributeValue(entry.getValue()) + "\"")
                        .collect(Collectors.joining("\n"));
        setValue(result);
        if (valueChangeListener != null) valueChangeListener.a(key, value);
    }

    private LinkedHashMap<String, String> readAttributes() {
        LinkedHashMap<String, String> attributes = readAllAttributes();
        attributes.entrySet().removeIf(entry -> isPropertyBackedAttribute(entry.getKey())
                && !isExplicitPropertyAttributeOverride(entry.getKey()));
        return attributes;
    }

    private boolean isExplicitPropertyAttributeOverride(String name) {
        LinkedHashMap<String, String> overrides = explicitPropertyAttributeOverridesByView.get(getActiveOverrideViewKey());
        return overrides != null && overrides.containsKey(canonicalizePropertyBackedAttribute(name));
    }

    private LinkedHashMap<String, String> readAllAttributes() {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader("<tag " + value + "></tag>"));

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

    public static class AttributesAdapter extends ListAdapter<String, AttributesAdapter.ViewHolder> {

        private static final DiffUtil.ItemCallback<String> DIFF_CALLBACK =
                new DiffUtil.ItemCallback<>() {
                    @Override
                    public boolean areItemsTheSame(
                            @NonNull String oldItem, @NonNull String newItem) {
                        return oldItem.equals(newItem);
                    }

                    @Override
                    public boolean areContentsTheSame(
                            @NonNull String oldItem, @NonNull String newItem) {
                        return true;
                    }
                };

        public AttributesAdapter() {
            super(DIFF_CALLBACK);
        }

        private LinkedHashMap<String, String> attributes;
        private ItemClickListener listener;

        public void setAttributes(LinkedHashMap<String, String> attributes) {
            this.attributes = attributes;
        }

        public void setOnItemClickListener(ItemClickListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout root = new FrameLayout(parent.getContext());
            return new ViewHolder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(getItem(position));
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            private final PropertyInputItemBinding binding;

            public ViewHolder(FrameLayout view) {
                super(view);
                binding =
                        PropertyInputItemBinding.inflate(
                                LayoutInflater.from(view.getContext()), view, true);
            }

            void bind(String attr) {
                binding.tvName.setText(attr);
                binding.tvValue.setText(attributes.get(attr));
                binding.imgLeftIcon.setImageResource(R.drawable.ic_mtrl_code);
                binding.getRoot().findViewById(R.id.property_menu_item).setVisibility(View.GONE);
                itemView.setOnClickListener(
                        view -> {
                            if (listener != null) listener.onItemClick(attributes, attr);
                        });
                itemView.setOnLongClickListener(
                        v -> {
                            if (listener != null) listener.onItemLongClick(attributes, attr);
                            return true;
                        });
            }
        }

        public interface ItemClickListener {

            void onItemClick(LinkedHashMap<String, String> attributes, String item);

            void onItemLongClick(LinkedHashMap<String, String> attributes, String item);
        }
    }
}
