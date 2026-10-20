package com.besome.sketch.editor.property;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.util.Xml;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.design.DesignActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import a.a.a.Kw;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.mB;
import a.a.a.wB;
import mod.bobur.VectorDrawableLoader;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ImagePickerItemBinding;
import pro.sketchware.databinding.SearchWithRecyclerViewBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.SvgUtils;

public class PropertyResourceItem extends RelativeLayout implements View.OnClickListener {

    private final SvgUtils svgUtils;
    private final FilePathUtil fpu = new FilePathUtil();
    private final Map<String, View> imageCache = new HashMap<>();

    public String a;
    public String b;
    public String c;
    public boolean d;
    public TextView e;
    public TextView f;
    public ImageView g;
    public ImageView h;
    public RadioGroup i;
    public LinearLayout j;
    public View k;
    public View l;
    public int m;
    public Kw n;

    public PropertyResourceItem(Context context, boolean z, String str, boolean z2) {
        super(context);
        d = false;
        a = str;
        svgUtils = new SvgUtils(context);
        svgUtils.initImageLoader();
        a(context, z, z2);
    }

    public String getKey() {
        return b;
    }

    public void setKey(String str) {
        b = str;
        int identifier = getResources().getIdentifier(str, "string", getContext().getPackageName());
        if (identifier > 0) {
            e.setText(getResources().getString(identifier));
            if ("property_image".equals(b)) {
                m = R.drawable.ic_mtrl_image;
            } else if ("property_background_resource".equals(b)) {
                m = R.drawable.ic_mtrl_background_dots;
            }
            if (l.getVisibility() == VISIBLE) {
                ((ImageView) findViewById(R.id.img_icon)).setImageResource(m);
                ((TextView) findViewById(R.id.tv_title)).setText(getContext().getString(identifier));
            } else {
                h.setImageResource(m);
            }
        }
    }

    public String getValue() {
        return c;
    }

    public void setValue(String str) {
        if (str == null || str.trim().isEmpty() || "NONE".equalsIgnoreCase(str.trim())) {
            // NONE means no resource; never treat it as a drawable name such as none.xml.
            c = "";
            f.setText("NONE");
            g.setImageDrawable(null);
            g.setBackgroundColor(Color.TRANSPARENT);
            return;
        }

        c = str;
        f.setText(str);
        if (jC.d(a).h(str) == ProjectResourceBean.PROJECT_RES_TYPE_RESOURCE) {
            int resourceId = getContext().getResources().getIdentifier(str, "drawable", getContext().getPackageName());
            if (resourceId != 0) {
                g.setImageResource(resourceId);
                return;
            }
            // A project drawable may be an uncompiled shape XML; fall through to its file preview.
        } else if ("default_image".equals(str)) {
            int resourceId = getContext().getResources().getIdentifier(str, "drawable", getContext().getPackageName());
            if (resourceId != 0) {
                g.setImageResource(resourceId);
                return;
            }
        }

        File file = resolveDrawableFile(str);
        if (file != null && file.exists()) {
            if (file.getName().endsWith(".xml")) {
                if (isVectorXml(file)) {
                    String drawableName = file.getName().replaceFirst("\\.xml$", "");
                    svgUtils.loadImage(g, fpu.getSvgFullPath(a, drawableName));
                } else {
                    Drawable preview = inflateShapeDrawable(file);
                    if (preview != null) g.setImageDrawable(preview);
                    else g.setImageResource(R.drawable.ic_remove_grey600_24dp);
                }
                return;
            }
            Uri fromFile = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", file);
            Glide.with(getContext()).load(fromFile).signature(kC.n()).error(R.drawable.ic_remove_grey600_24dp).into(g);
            return;
        }
        int resourceId = getContext().getResources().getIdentifier(str, "drawable", getContext().getPackageName());
        if (resourceId != 0) g.setImageResource(resourceId);
        else g.setImageDrawable(null);
    }

    @Override
    public void onClick(View view) {
        if (mB.a()) {
            return;
        }
        a();
    }

    public void setOnPropertyValueChangeListener(Kw kw) {
        n = kw;
    }

    public void setOrientationItem(int i) {
        if (i == 0) {
            k.setVisibility(GONE);
            l.setVisibility(VISIBLE);
            k.setOnClickListener(null);
            l.setOnClickListener(this);
        } else {
            k.setVisibility(VISIBLE);
            l.setVisibility(GONE);
            k.setOnClickListener(this);
            l.setOnClickListener(null);
        }
    }

    public final void a(Context context, boolean z, boolean z2) {
        wB.a(context, this, R.layout.property_resource_item);
        e = findViewById(R.id.tv_name);
        f = findViewById(R.id.tv_value);
        g = findViewById(R.id.view_image);
        h = findViewById(R.id.img_left_icon);
        k = findViewById(R.id.property_item);
        l = findViewById(R.id.property_menu_item);
        d = z2;
    }

    public final void a() {
        boolean allowShapes = "property_background_resource".equals(b);
        showPickerDialog(Helper.getText(e), m, c, selected -> {
            String normalized = selected == null || "NONE".equalsIgnoreCase(selected) ? "" : selected;
            setValue(normalized);
            if (n != null) {
                n.a(b, normalized);
            }
        }, allowShapes);
    }

    public interface ResourcePickCallback {
        void onSelected(String resourceName);
    }

    public void showPickerDialog(String title, int iconRes, String currentValue, ResourcePickCallback callback) {
        showPickerDialog(title, iconRes, currentValue, callback, false);
    }

    public void showPickerDialog(String title, int iconRes, String currentValue,
                                 ResourcePickCallback callback, boolean allowShapes) {
        SearchWithRecyclerViewBinding binding = SearchWithRecyclerViewBinding.inflate(LayoutInflater.from(getContext()));
        imageCache.clear();

        ArrayList<String> images = new ArrayList<>();
        try {
            ArrayList<String> projectImages = jC.d(a).m();
            if (projectImages != null) images.addAll(projectImages);
        } catch (RuntimeException ignored) {
            // Keep the picker usable even if a project resource cache is not ready.
        }
        try {
            ArrayList<String> vectorDrawables = new VectorDrawableLoader().getVectorDrawables(a);
            if (vectorDrawables != null) images.addAll(vectorDrawables);
        } catch (RuntimeException ignored) {
            // Project raster resources remain available when vector discovery fails.
        }
        java.util.LinkedHashSet<String> uniqueImages = new java.util.LinkedHashSet<>();
        for (String image : images) {
            if (image != null && !image.trim().isEmpty() && !"NONE".equalsIgnoreCase(image)) uniqueImages.add(image);
        }
        images = new ArrayList<>(uniqueImages);

        ArrayList<String> shapeNames = findShapeDrawables();
        // NONE is a property sentinel, not a resource. Keep shape XML out of image/src pickers.
        images.removeIf(image -> shapeNames.contains(image.replaceFirst("\\.xml$", ""))
                || "none".equalsIgnoreCase(image.replaceFirst("\\.xml$", "")));
        if (allowShapes && !shapeNames.isEmpty()) shapeNames.add(0, d ? "default_image" : "NONE");

        images.add(0, d ? "default_image" : "NONE");
        // Capture the normalized list once; dialog listeners must not close over a reassigned local.
        final ArrayList<String> imageResources = new ArrayList<>(images);

        String initialSelection = getInitialPickerSelection(currentValue, imageResources, shapeNames);
        ImagePickerAdapter adapter = new ImagePickerAdapter(new ArrayList<>(imageResources), initialSelection);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.filter(s.toString().toLowerCase());
            }
        });

        LinearLayout dialogRoot = new LinearLayout(getContext());
        dialogRoot.setOrientation(LinearLayout.VERTICAL);

        if (allowShapes && !shapeNames.isEmpty()) {
            int dp8 = (int) (8 * wB.a(getContext(), 1f));
            MaterialButtonToggleGroup toggleGroup = new MaterialButtonToggleGroup(getContext());
            toggleGroup.setSingleSelection(true);
            toggleGroup.setSelectionRequired(true);
            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            toggleParams.gravity = Gravity.CENTER_HORIZONTAL;
            toggleParams.setMargins(dp8, dp8, dp8, dp8);
            toggleGroup.setLayoutParams(toggleParams);

            MaterialButton imagesButton = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            imagesButton.setText("Images");
            imagesButton.setId(View.generateViewId());
            imagesButton.setCheckable(true);
            imagesButton.setChecked(true);

            MaterialButton shapesButton = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            shapesButton.setText("Shapes");
            shapesButton.setId(View.generateViewId());
            shapesButton.setCheckable(true);

            toggleGroup.addView(imagesButton);
            toggleGroup.addView(shapesButton);

            imagesButton.setOnClickListener(v -> {
                imagesButton.setChecked(true);
                adapter.setSource(new ArrayList<>(imageResources));
            });
            shapesButton.setOnClickListener(v -> {
                shapesButton.setChecked(true);
                adapter.setSource(new ArrayList<>(shapeNames));
            });

            // Open the correct tab when the currently assigned background is a shape.
            if (!"NONE".equalsIgnoreCase(initialSelection) && !"default_image".equals(initialSelection)
                    && shapeNames.contains(initialSelection)) {
                shapesButton.setChecked(true);
                adapter.setSource(new ArrayList<>(shapeNames));
            }

            dialogRoot.addView(toggleGroup);
        }

        dialogRoot.addView(binding.getRoot(), new LinearLayout.LayoutParams(-1, -1));

        new MaterialAlertDialogBuilder(getContext())
                .setTitle(title)
                .setIcon(iconRes)
                .setView(dialogRoot)
                .setPositiveButton(R.string.common_word_select, (v, which) -> {
                    String selected = adapter.getSelected();
                    if (selected != null && !selected.isEmpty()) {
                        callback.onSelected(selected);
                    }
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private static final java.util.Set<String> KNOWN_SHAPE_ROOT_TAGS = new java.util.HashSet<>(Arrays.asList(
            "shape", "selector", "layer-list", "ripple", "inset", "clip",
            "animated-selector", "level-list", "rotate", "scale", "animated-rotate"
    ));

    private ArrayList<String> findShapeDrawables() {
        ArrayList<String> shapeNames = new ArrayList<>();
        try {
            File drawableDir = new File(pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + "/.sketchware/data/" + a + "/files/resource/drawable/");
            File[] files = drawableDir.listFiles();
            if (files == null) return shapeNames;

            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".xml")) continue;
                if (file.length() == 0) continue;

                String nameWithoutExt = name.substring(0, name.length() - 4);
                // NONE is a sentinel, not a drawable. Older picker flows could create bogus none.xml; keep this guard.
                if ("none".equalsIgnoreCase(nameWithoutExt)) continue;
                String rootTag = readRootTag(file);
                if (rootTag == null) continue;
                if ("vector".equals(rootTag)) continue;
                if (!KNOWN_SHAPE_ROOT_TAGS.contains(rootTag)) continue;

                shapeNames.add(nameWithoutExt);
            }
        } catch (Exception ignored) {
        }
        return shapeNames;
    }

    private Drawable inflateShapeDrawable(File file) {
        if (file == null || !file.exists() || file.length() == 0) return null;
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
            org.xmlpull.v1.XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, null);
            int eventType = parser.getEventType();
            while (eventType != org.xmlpull.v1.XmlPullParser.START_TAG) {
                if (eventType == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) return null;
                eventType = parser.next();
            }
            try {
                Drawable drawable = Drawable.createFromXml(getResources(), parser, getContext().getTheme());
                if (drawable != null) return drawable;
            } catch (Exception ignored) {
                // Project drawable XML may reference colors that are not compiled into the editor APK.
            }
        } catch (Exception ignored) {
            // Fall back to the lightweight renderer for common project shape XML.
        }
        return buildShapePreviewDrawable(file);
    }

    private Drawable buildShapePreviewDrawable(File file) {
        if (file == null || !file.exists()) return null;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor("#DCE3F2"));
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
            org.xmlpull.v1.XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, null);
            int eventType = parser.getEventType();
            boolean sawShape = false;
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (KNOWN_SHAPE_ROOT_TAGS.contains(tag)) sawShape = true;
                    if ("shape".equals(tag)) {
                        sawShape = true;
                        String shape = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "shape");
                        if ("oval".equals(shape)) drawable.setShape(GradientDrawable.OVAL);
                        else if ("line".equals(shape)) drawable.setShape(GradientDrawable.LINE);
                        else drawable.setShape(GradientDrawable.RECTANGLE);
                    } else if ("solid".equals(tag)) {
                        Integer color = parseDrawableColor(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "color"));
                        if (color != null) drawable.setColor(color);
                    } else if ("corners".equals(tag)) {
                        float radius = parseDrawableDimension(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "radius"), 0f);
                        float tl = parseDrawableDimension(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "topLeftRadius"), radius);
                        float tr = parseDrawableDimension(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "topRightRadius"), radius);
                        float br = parseDrawableDimension(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "bottomRightRadius"), radius);
                        float bl = parseDrawableDimension(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "bottomLeftRadius"), radius);
                        drawable.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
                    } else if ("stroke".equals(tag)) {
                        int width = Math.max(1, Math.round(parseDrawableDimension(
                                parser.getAttributeValue("http://schemas.android.com/apk/res/android", "width"), 1f)));
                        Integer color = parseDrawableColor(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "color"));
                        if (color != null) drawable.setStroke(width, color);
                    } else if ("gradient".equals(tag)) {
                        Integer start = parseDrawableColor(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "startColor"));
                        Integer end = parseDrawableColor(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "endColor"));
                        String orientation = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "orientation");
                        if (start != null && end != null) {
                            GradientDrawable.Orientation gradientOrientation = "top_bottom".equals(orientation)
                                    ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.LEFT_RIGHT;
                            drawable.setOrientation(gradientOrientation);
                            drawable.setColors(new int[]{start, end});
                        }
                    }
                }
                eventType = parser.next();
            }
            return sawShape ? drawable : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseDrawableColor(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String color = value.trim();
        try {
            if (color.startsWith("#")) return Color.parseColor(color);
            if (color.startsWith("@android:color/")) {
                String name = color.substring("@android:color/".length());
                int id = getResources().getIdentifier(name, "color", "android");
                if (id != 0) return getResources().getColor(id, getContext().getTheme());
            } else if (color.startsWith("@color/")) {
                int id = getResources().getIdentifier(color.substring("@color/".length()), "color", getContext().getPackageName());
                if (id != 0) return getResources().getColor(id, getContext().getTheme());
            }
        } catch (Exception ignored) { }
        return null;
    }

    private float parseDrawableDimension(String value, float fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String number = value.trim().replaceAll("(?i)(dp|dip|sp|px)$", "");
        try {
            float parsed = Float.parseFloat(number);
            if (value.endsWith("dp") || value.endsWith("dip")) return wB.a(getContext(), parsed);
            return parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean isVectorXml(File file) {
        return "vector".equals(readRootTag(file));
    }

    private String readRootTag(File file) {
        if (file == null || !file.exists() || file.length() == 0) return null;
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
            org.xmlpull.v1.XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, null);
            int eventType = parser.getEventType();
            while (eventType != org.xmlpull.v1.XmlPullParser.START_TAG) {
                if (eventType == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) return null;
                eventType = parser.next();
            }
            return parser.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private String getInitialPickerSelection(String currentValue, ArrayList<String> imageResources,
                                             ArrayList<String> shapeNames) {
        String candidate = currentValue == null ? "" : currentValue.trim();
        candidate = candidate.replaceFirst("^@(?:drawable|mipmap)/", "").replaceFirst("\\.xml$", "");
        if (!candidate.isEmpty() && !"NONE".equalsIgnoreCase(candidate)
                && (imageResources.contains(candidate) || shapeNames.contains(candidate))) {
            return candidate;
        }
        String fallback = d ? "default_image" : "NONE";
        if (imageResources.contains(fallback) || shapeNames.contains(fallback)) return fallback;
        return imageResources.isEmpty() ? "" : imageResources.get(0);
    }

    private class ImagePickerAdapter extends RecyclerView.Adapter<ImagePickerAdapter.ViewHolder> {

        private final ArrayList<String> allImages;
        private final ArrayList<String> filteredImages = new ArrayList<>();
        private String selectedImage;

        ImagePickerAdapter(ArrayList<String> images, String selectedImage) {
            this.allImages = images;
            this.selectedImage = selectedImage;
            this.filteredImages.addAll(images);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ImagePickerItemBinding binding = ImagePickerItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String image = filteredImages.get(position);
            holder.binding.textView.setText(image);
            holder.binding.radioButton.setChecked(image.equals(selectedImage));

            View imageView = imageCache.get(image);
            if (imageView == null) {
                imageView = setImageViewContent(image);
                imageCache.put(image, imageView);
            }

            if (imageView.getParent() != null) {
                ((ViewGroup) imageView.getParent()).removeView(imageView);
            }

            holder.binding.layoutImg.removeAllViews();
            holder.binding.layoutImg.addView(imageView);

            holder.binding.transparentOverlay.setOnClickListener(v -> {
                selectedImage = image;
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return filteredImages.size();
        }

        public void filter(String query) {
            filteredImages.clear();
            for (String s : allImages) {
                if (s.toLowerCase().contains(query)) {
                    filteredImages.add(s);
                }
            }
            notifyDataSetChanged();
        }

        public void setSource(ArrayList<String> newSource) {
            allImages.clear();
            allImages.addAll(newSource);
            filteredImages.clear();
            filteredImages.addAll(newSource);
            notifyDataSetChanged();
        }

        public String getSelected() {
            return selectedImage;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImagePickerItemBinding binding;
            ViewHolder(ImagePickerItemBinding b) {
                super(b.getRoot());
                this.binding = b;
            }
        }
    }

    private File resolveDrawableFile(String image) {
        if (image == null || image.trim().isEmpty() || "NONE".equalsIgnoreCase(image)) return null;
        String resourceName = image.trim().replaceFirst("^@(?:drawable|mipmap)/", "");
        if (resourceName.endsWith(".xml")) resourceName = resourceName.substring(0, resourceName.length() - 4);
        try {
            String kcPath = jC.d(a).f(resourceName);
            if (kcPath != null) {
                File file = new File(kcPath);
                if (file.exists() && file.length() > 0) return file;
            }
        } catch (Exception ignored) {
        }

        File direct = new File(pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/.sketchware/data/" + a + "/files/resource/drawable/" + resourceName + ".xml");
        if (direct.exists() && direct.length() > 0) return direct;

        return null;
    }

    private View setImageViewContent(String image) {
        ImageView imageView = new ImageView(getContext());
        int size = (int) (48 * wB.a(getContext(), 1f));
        imageView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundResource(R.drawable.bg_outline);

        if (image == null || image.trim().isEmpty() || "NONE".equalsIgnoreCase(image)) {
            imageView.setBackgroundColor(Color.TRANSPARENT);
            return imageView;
        }

        try {
            if ("default_image".equals(image)) {
                imageView.setImageResource(getResources().getIdentifier(image, "drawable", getContext().getPackageName()));
            } else {
                File file = resolveDrawableFile(image);
                if (file != null && file.exists()) {
                    if (file.getName().endsWith(".xml")) {
                        if (isVectorXml(file)) {
                            String drawableName = file.getName().replaceFirst("\\.xml$", "");
                            svgUtils.loadImage(imageView, fpu.getSvgFullPath(a, drawableName));
                        } else {
                            Drawable shapeDrawable = inflateShapeDrawable(file);
                            if (shapeDrawable != null) {
                                imageView.setImageDrawable(shapeDrawable);
                            } else {
                                imageView.setImageResource(R.drawable.ic_remove_grey600_24dp);
                            }
                        }
                    } else {
                        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", file);
                        Glide.with(getContext())
                                .load(uri)
                                .signature(kC.n())
                                .error(R.drawable.ic_remove_grey600_24dp)
                                .into(imageView);
                    }
                } else {
                    VectorDrawableLoader vectorDrawableLoader = new VectorDrawableLoader();
                    vectorDrawableLoader.setImageVectorFromFile(imageView, vectorDrawableLoader.getVectorFullPath(DesignActivity.sc_id, image));
                }
            }
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.ic_remove_grey600_24dp);
        }

        return imageView;
    }
}
