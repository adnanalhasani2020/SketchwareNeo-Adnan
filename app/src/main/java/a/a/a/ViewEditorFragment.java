package a.a.a;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.editor.LogicEditorActivity;
import com.besome.sketch.editor.PropertyActivity;
import com.besome.sketch.editor.view.DraggingListener;
import com.besome.sketch.editor.view.ItemView;
import com.besome.sketch.editor.view.ViewEditor;
import com.besome.sketch.editor.view.ViewProperty;
import com.besome.sketch.editor.view.palette.PaletteWidget;
import com.besome.sketch.editor.manage.library.material3.Material3LibraryManager;

import java.util.ArrayList;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.widgets.WidgetsCreatorManager;

public class ViewEditorFragment extends qA {

    public ViewEditor viewEditor;
    private ProjectFileBean projectFileBean;
    private boolean isFabEnabled = false;
    private ViewProperty viewProperty;
    private ObjectAnimator showPropertyViewAnimator;
    private ObjectAnimator hidePropertyViewAnimator;
    private boolean isPropertyViewVisible;
    private boolean isDragging = false;
    private String sc_id;

    private WidgetsCreatorManager widgetsCreatorManager;

    public ViewEditorFragment() {
    }

    private void initialize(ViewGroup viewGroup) {
        setHasOptionsMenu(true);
        viewEditor = viewGroup.findViewById(R.id.view_editor);
        viewEditor.setScreenType(getResources().getConfiguration().orientation);
        widgetsCreatorManager = new WidgetsCreatorManager(this);
        viewEditor.widgetsCreatorManager = widgetsCreatorManager;
        viewProperty = requireActivity().findViewById(R.id.view_property);
        viewProperty.setOnPropertyListener(new Iw() {
            @Override
            public void a() {
                viewEditor.setFavoriteData(Rp.h().f());
            }

            @Override
            public void a(String s, ViewBean viewBean) {
                openPropertyActivity(viewBean);
            }
        });
        viewProperty.setOnPropertyValueChangedListener(viewBean -> {
            a(viewBean.id);
            viewProperty.e();
            invalidateOptionsMenu();
        });
        viewProperty.setOnPropertyDeleted(viewBean -> {
            viewEditor.deleteWidget(viewBean);
            if (requireActivity() instanceof DesignActivity designActivity) {
                designActivity.hideViewPropertyView();
            }
            SketchwareUtil.toast(Helper.getResString(R.string.common_word_deleted));
        });
        viewProperty.setOnEventClickListener(eventBean -> toLogicEditorActivity(eventBean.targetId, eventBean.eventName, eventBean.eventName));
        viewProperty.setOnPropertyTargetChangeListener(viewEditor::updateSelection);
        viewProperty.setOnViewMoveListener(this::moveViewInTree);
        viewEditor.setOnWidgetSelectedListener(new cy() {
            @Override
            public void a() {
                n();
                viewProperty.e();
            }

            @Override
            public void a(String viewId) {
                n();
                viewProperty.a(viewId);
            }

            @Override
            public void a(boolean var1, String viewId) {
                if (!viewId.isEmpty()) {
                    a();
                    viewProperty.a(viewId);
                    viewProperty.e();
                }

                ViewEditorFragment.this.a(var1);
            }
        });
        viewEditor.setOnDraggingListener(new DraggingListener() {
            @Override
            public boolean isAdmobEnabled() {
                return jC.c(sc_id).b().isEnabled();
            }

            @Override
            public void b() {
                isDragging = true;
                ((DesignActivity) requireActivity()).setTouchEventEnabled(false);
            }

            @Override
            public boolean isGoogleMapEnabled() {
                return jC.c(sc_id).e().isEnabled();
            }

            @Override
            public void d() {
                isDragging = false;
                ((DesignActivity) requireActivity()).setTouchEventEnabled(true);
            }
        });
        viewEditor.setOnHistoryChangeListener(this::invalidateOptionsMenu);
        viewEditor.setFavoriteData(Rp.h().f());
    }

    public void initialize(ProjectFileBean projectFileBean) {
        this.projectFileBean = projectFileBean;
        isFabEnabled = projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB);
        viewEditor.initialize(sc_id, projectFileBean);
        viewEditor.h();
        viewProperty.a(sc_id, this.projectFileBean);
        e();
        i();
        invalidateOptionsMenu();
    }

    private void a(ViewBean viewBean) {
        viewEditor.removeFab();
        if (isFabEnabled) viewEditor.addFab(viewBean);
    }

    private void a(String viewId) {
        ViewBean viewBean;
        if (viewId.equals("_fab")) {
            viewBean = jC.a(sc_id).h(projectFileBean.getXmlName());
        } else {
            viewBean = jC.a(sc_id).c(projectFileBean.getXmlName(), viewId);
        }
        c(viewBean);
        viewProperty.e();
    }

    private boolean moveViewInTree(String sourceId, String targetParentId) {
        if (sourceId == null || targetParentId == null || sourceId.equals(targetParentId)
                || sourceId.startsWith("_") || projectFileBean == null || sc_id == null) return false;
        ArrayList<ViewBean> beans;
        try {
            beans = jC.a(sc_id).d(projectFileBean.getXmlName());
        } catch (RuntimeException exception) {
            SketchwareUtil.toastError("Unable to load the view tree");
            return false;
        }
        if (beans == null) return false;
        ViewBean source = null;
        ViewBean target = null;
        for (ViewBean bean : beans) {
            if (bean == null) continue;
            if (sourceId.equals(bean.id)) source = bean;
            if (targetParentId.equals(bean.id)) target = bean;
        }
        if (source == null || target == null || !isTreeContainer(target)) return false;

        String cursorId = target.id;
        java.util.HashSet<String> visited = new java.util.HashSet<>();
        while (cursorId != null && !cursorId.isEmpty() && !"root".equals(cursorId)) {
            if (source.id.equals(cursorId)) {
                SketchwareUtil.toastError("A view cannot be moved into itself or its own child");
                return false;
            }
            if (!visited.add(cursorId)) {
                SketchwareUtil.toastError("The current view hierarchy contains a parent cycle");
                return false;
            }
            ViewBean parent = null;
            for (ViewBean candidate : beans) {
                if (candidate != null && cursorId.equals(candidate.id)) { parent = candidate; break; }
            }
            cursorId = parent == null ? null : parent.parent;
        }

        String previousParent = source.parent;
        int previousIndex = source.index;
        int previousParentType = source.parentType;
        String previousPreParent = source.preParent;
        int previousPreIndex = source.preIndex;
        int previousPreParentType = source.preParentType;
        java.util.HashMap<String, String> previousParentAttributes = source.parentAttributes == null
                ? new java.util.HashMap<>() : new java.util.HashMap<>(source.parentAttributes);

        int nextIndex = 0;
        for (ViewBean bean : beans) {
            if (bean != null && !source.id.equals(bean.id) && target.id.equals(bean.parent)) {
                nextIndex = Math.max(nextIndex, bean.index + 1);
            }
        }
        source.preParent = previousParent == null || previousParent.isEmpty() ? "root" : previousParent;
        source.preIndex = previousIndex;
        source.preParentType = previousParentType;
        source.parent = target.id;
        source.parentType = target.type;
        source.index = nextIndex;
        source.parentAttributes = source.parentAttributes == null ? new java.util.HashMap<>() : source.parentAttributes;
        if (!target.id.equals(previousParent)) source.parentAttributes.clear();

        ItemView moved = viewEditor.b(source, true);
        if (moved == null) {
            source.parent = previousParent;
            source.index = previousIndex;
            source.parentType = previousParentType;
            source.preParent = previousPreParent;
            source.preIndex = previousPreIndex;
            source.preParentType = previousPreParentType;
            source.parentAttributes = previousParentAttributes;
            viewEditor.b(source, false);
            SketchwareUtil.toastError("The canvas could not apply this move");
            return false;
        }
        viewEditor.a(moved, true);
        n();
        viewProperty.a(source.id);
        viewProperty.e();
        invalidateOptionsMenu();
        return true;
    }

    private boolean isTreeContainer(ViewBean bean) {
        if (bean == null || bean.convert == null) return false;
        String name = bean.convert.substring(bean.convert.lastIndexOf('.') + 1);
        return switch (name) {
            case "LinearLayout", "RelativeLayout", "ConstraintLayout", "CoordinatorLayout", "FrameLayout",
                 "ScrollView", "NestedScrollView", "HorizontalScrollView", "RadioGroup", "TableLayout",
                 "TableRow", "GridLayout", "CardView", "MaterialCardView", "CollapsingToolbarLayout",
                 "TextInputLayout", "SwipeRefreshLayout", "TabLayout", "BottomNavigationView", "ChipGroup",
                 "MaterialButtonToggleGroup", "ViewPager", "ViewPager2", "RecyclerView", "ListView", "GridView" -> true;
            default -> false;
        };
    }

    private void toLogicEditorActivity(String eventId, String eventName, String eventName2) {
        Intent intent = new Intent(requireContext(), LogicEditorActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("id", eventId);
        intent.putExtra("event", eventName);
        intent.putExtra("project_file", projectFileBean);
        intent.putExtra("event_text", eventName2);
        requireContext().startActivity(intent);
    }

    public void a(ArrayList<ViewBean> viewBeans) {
        viewEditor.h();
        viewEditor.a(eC.a(viewBeans));
    }

    public void a(boolean var1) {
        startAnimation();
        if (!isPropertyViewVisible || !var1) {
            cancelAnimations();
            if (var1) {
                showPropertyViewAnimator.start();
            } else if (isPropertyViewVisible) {
                hidePropertyViewAnimator.start();
            }

            isPropertyViewVisible = var1;
        }
    }

    public void openPropertyActivity(ViewBean viewBean) {
        Intent intent = new Intent(requireContext(), PropertyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("bean", viewBean);
        intent.putExtra("project_file", projectFileBean);
        startActivityForResult(intent, 213);
    }

    private void b(ArrayList<ViewBean> viewBeans) {
        l();
        a(viewBeans);
    }

    private void cancelAnimations() {
        if (showPropertyViewAnimator.isRunning()) showPropertyViewAnimator.cancel();
        if (hidePropertyViewAnimator.isRunning()) hidePropertyViewAnimator.cancel();
    }

    public void c(ViewBean var1) {
        viewEditor.e(var1);
    }

    public void showHidePropertyView(boolean shouldShow) {
        viewProperty.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    public ProjectFileBean d() {
        return projectFileBean;
    }

    public void e() {
        viewEditor.removeWidgetsAndLayouts();
        viewEditor.setPaletteLayoutVisible(View.VISIBLE);
        viewEditor.addWidgetLayout(PaletteWidget.a.a, "");
        viewEditor.addWidgetLayout(PaletteWidget.a.b, "");
        viewEditor.addWidget(PaletteWidget.b.b, "", "TextView", "TextView");
        viewEditor.addWidgetLayout(PaletteWidget.a.c, "");
        viewEditor.addWidgetLayout(PaletteWidget.a.d, "");
        viewEditor.extraWidgetLayout("", "RadioGroup");
        viewEditor.extraWidgetLayout("", "RelativeLayout");
        
        viewEditor.extraWidgetLayout("", "ConstraintLayout");
        viewEditor.extraWidgetLayout("", "CoordinatorLayout");

        if (sc_id != null) {
            try {
                var activityLayouts = jC.b(sc_id).c();
                if (activityLayouts != null && !activityLayouts.isEmpty()) {
                    viewEditor.extraWidgetLayout("", "include", activityLayouts.get(0).fileName);
                }
            } catch (Exception e) {
                mod.jbk.util.LogUtil.e("ViewEditorFragment", "Unable to register include palette item", e);
            }
        }
        
        widgetsCreatorManager.addWidgetsByTitle("Layouts");

        viewEditor.paletteWidget.extraTitle("AndroidX", 0);
        viewEditor.extraWidgetLayout("", "TabLayout");
        viewEditor.extraWidgetLayout("", "BottomNavigationView");
        viewEditor.extraWidgetLayout("", "CollapsingToolbarLayout");
        viewEditor.extraWidgetLayout("", "NestedScrollView");
        viewEditor.extraWidgetLayout("", "CardView");
        viewEditor.extraWidgetLayout("", "SwipeRefreshLayout");
        widgetsCreatorManager.addWidgetsByTitle("AndroidX");

        viewEditor.addWidget(PaletteWidget.b.c, "", "EditText", "Edit Text");
        viewEditor.extraWidget("", "AutoCompleteTextView", "AutoCompleteTextView");
        viewEditor.extraWidget("", "MultiAutoCompleteTextView", "MultiAutoCompleteTextView");
        viewEditor.addWidget(PaletteWidget.b.a, "", "Button", "Button");
        viewEditor.addWidget(PaletteWidget.b.d, "", "ImageView", "default_image");
        viewEditor.extraWidget("", "CircleImageView", "default_image");
        viewEditor.addWidget(PaletteWidget.b.g, "", "CheckBox", "CheckBox");
        viewEditor.extraWidget("", "RadioButton", "RadioButton");
        viewEditor.addWidget(PaletteWidget.b.i, "", "Switch", "Switch");
        viewEditor.addWidget(PaletteWidget.b.j, "", "SeekBar", "SeekBar");
        viewEditor.addWidget(PaletteWidget.b.m, "", "ProgressBar", "ProgressBar");
        viewEditor.extraWidget("", "RatingBar", "RatingBar");
        viewEditor.extraWidget("", "SearchView", "SearchView");
        viewEditor.extraWidget("", "VideoView", "VideoView");
        viewEditor.addWidget(PaletteWidget.b.h, "", "WebView", "WebView");
        widgetsCreatorManager.addWidgetsByTitle("Widgets");

        viewEditor.paletteWidget.extraTitle("List", 1);
        viewEditor.addWidget(PaletteWidget.b.e, "", "ListView", "ListView");
        viewEditor.extraWidget("", "GridView", "GridView");
        viewEditor.extraWidget("", "RecyclerView", "RecyclerView");
        viewEditor.addWidget(PaletteWidget.b.f, "", "Spinner", "Spinner");
        viewEditor.extraWidget("", "ViewPager", "ViewPager");
        widgetsCreatorManager.addWidgetsByTitle("List");

        viewEditor.paletteWidget.extraTitle("Library", 1);
        viewEditor.extraWidget("", "WaveSideBar", "WaveSideBar");
        viewEditor.extraWidget("", "PatternLockView", "PatternLockView");
        viewEditor.extraWidget("", "CodeView", "CodeView");
        viewEditor.extraWidget("", "LottieAnimation", "LottieAnimation");
        viewEditor.extraWidget("", "OTPView", "OTPView");
        widgetsCreatorManager.addWidgetsByTitle("Library");

        viewEditor.paletteWidget.extraTitle("Google", 1);
        viewEditor.addWidget(PaletteWidget.b.l, "", "AdView", "AdView");
        viewEditor.addWidget(PaletteWidget.b.n, "", "MapView", "MapView");
        viewEditor.extraWidget("", "SignInButton", "SignInButton");
        viewEditor.extraWidget("", "YoutubePlayer", "YoutubePlayer");
        widgetsCreatorManager.addWidgetsByTitle("Google");

        viewEditor.paletteWidget.extraTitle("Date & Time", 1);
        viewEditor.extraWidget("", "AnalogClock", "AnalogClock");
        viewEditor.extraWidget("", "DigitalClock", "DigitalClock");
        viewEditor.extraWidget("", "TimePicker", "TimePicker");
        viewEditor.extraWidget("", "DatePicker", "DatePicker");
        viewEditor.addWidget(PaletteWidget.b.k, "", "CalendarView", "CalendarView");
        widgetsCreatorManager.addWidgetsByTitle("Date & Time");

        if (isMaterial3Enabled()) {
            viewEditor.paletteWidget.extraTitle("Material 3", 1);
            viewEditor.extraWidget("", "MaterialButton", "MaterialButton");
            viewEditor.extraWidget("", "MaterialIconButton", "MaterialIconButton");
            viewEditor.extraWidget("", "MaterialTextView", "MaterialTextView");
            viewEditor.extraWidget("", "TextInputEditText", "TextInputEditText");
            viewEditor.extraWidget("", "MaterialAutoCompleteTextView", "MaterialAutoCompleteTextView");
            viewEditor.extraWidget("", "MaterialSwitch", "MaterialSwitch");
            viewEditor.extraWidget("", "MaterialCheckBox", "MaterialCheckBox");
            viewEditor.extraWidget("", "MaterialRadioButton", "MaterialRadioButton");
            viewEditor.extraWidget("", "Chip", "Chip");
            viewEditor.extraWidget("", "MaterialProgressIndicator", "MaterialProgressIndicator");
            viewEditor.extraWidget("", "Slider", "Slider");
            viewEditor.extraWidget("", "ExtendedFloatingActionButton", "ExtendedFloatingActionButton");
            viewEditor.extraWidget("", "ShapeableImageView", "ShapeableImageView");

            viewEditor.paletteWidget.extraTitle("M3 Layouts", 0);
            viewEditor.extraWidgetLayout("", "TextInputLayout");
            viewEditor.extraWidgetLayout("", "MaterialCardView");
            viewEditor.extraWidgetLayout("", "MaterialButtonToggleGroup");
            viewEditor.extraWidgetLayout("", "ChipGroup");
        }

        if (isMaterial3Enabled()) {
            widgetsCreatorManager.addWidgetsByTitle("Material 3");
        }
        widgetsCreatorManager.addExtraClasses();
    }

    public boolean isMaterial3Enabled() {
        return sc_id != null && new Material3LibraryManager(sc_id).isMaterial3Enabled();
    }

    private void startAnimation() {
        if (showPropertyViewAnimator == null) {
            showPropertyViewAnimator = ObjectAnimator.ofFloat(viewProperty, View.TRANSLATION_Y, 0.0F);
            showPropertyViewAnimator.setDuration(700L);
            showPropertyViewAnimator.setInterpolator(new DecelerateInterpolator());
        }

        if (hidePropertyViewAnimator == null) {
            if (getActivity() == null) return;
            hidePropertyViewAnimator = ObjectAnimator.ofFloat(viewProperty, View.TRANSLATION_Y, wB.a(requireActivity(), (float) viewProperty.getHeight()));
            hidePropertyViewAnimator.setDuration(300L);
            hidePropertyViewAnimator.setInterpolator(new DecelerateInterpolator());
        }
    }

    public boolean isPropertyViewVisible() {
        return isPropertyViewVisible;
    }

    private void onRedo() {
        if (!isDragging) {
            HistoryViewBean historyViewBean = cC.c(sc_id).h(projectFileBean.getXmlName());
            if (historyViewBean != null) {
                int actionType = historyViewBean.getActionType();
                if (actionType == HistoryViewBean.ACTION_TYPE_ADD) {
                    for (ViewBean viewBean : historyViewBean.getAddedData()) {
                        jC.a(sc_id).a(projectFileBean.getXmlName(), viewBean);
                    }
                    viewEditor.a(viewEditor.a(eC.a(historyViewBean.getAddedData()), false), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_UPDATE) {
                    ViewBean prevUpdateData = historyViewBean.getPrevUpdateData();
                    ViewBean currentUpdateData = historyViewBean.getCurrentUpdateData();
                    if (!prevUpdateData.id.equals(currentUpdateData.id)) {
                        currentUpdateData.preId = prevUpdateData.id;
                    }

                    if (currentUpdateData.id.equals("_fab")) {
                        jC.a(sc_id).h(projectFileBean.getXmlName()).copy(currentUpdateData);
                    } else {
                        jC.a(sc_id).c(projectFileBean.getXmlName(), prevUpdateData.id).copy(currentUpdateData);
                    }

                    viewEditor.a(viewEditor.e(currentUpdateData), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_REMOVE) {
                    for (ViewBean viewBean : historyViewBean.getRemovedData()) {
                        jC.a(sc_id).a(projectFileBean, viewBean);
                    }
                    viewEditor.b(historyViewBean.getRemovedData(), false);
                    viewEditor.i();
                } else if (actionType == HistoryViewBean.ACTION_TYPE_MOVE) {
                    ViewBean movedData = historyViewBean.getMovedData();
                    ViewBean viewBean = jC.a(sc_id).c(projectFileBean.getXmlName(), movedData.id);
                    viewBean.copy(movedData);
                    viewEditor.a(viewEditor.b(viewBean, false), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_OVERRIDE) {
                    jC.a(sc_id).c.put(projectFileBean.getXmlName(), historyViewBean.getAddedData());
                    i();
                }
            }
            invalidateOptionsMenu();
        }
    }

    public void i() {
        invalidateOptionsMenu();
        if (projectFileBean != null) {
            viewEditor.j(); 
            viewEditor.a(eC.a(jC.a(sc_id).d(projectFileBean.getXmlName())), false); 
            a(jC.a(sc_id).h(projectFileBean.getXmlName())); 
        }
    }

    public void j() {
        viewEditor.setFavoriteData(Rp.h().f());
    }

    private void invalidateOptionsMenu() {
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
    }

    public void l() {
        viewEditor.j();
    }

    private void onUndo() {
        if (!isDragging) {
            HistoryViewBean historyViewBean = cC.c(sc_id).i(projectFileBean.getXmlName());
            if (historyViewBean != null) {
                int actionType = historyViewBean.getActionType();
                if (actionType == HistoryViewBean.ACTION_TYPE_ADD) {
                    for (ViewBean view : historyViewBean.getAddedData()) {
                        jC.a(sc_id).a(projectFileBean, view);
                    }
                    viewEditor.b(historyViewBean.getAddedData(), false);
                    viewEditor.i();
                } else if (actionType == HistoryViewBean.ACTION_TYPE_UPDATE) {
                    ViewBean prevUpdateData = historyViewBean.getPrevUpdateData();
                    ViewBean currentUpdateData = historyViewBean.getCurrentUpdateData();
                    if (!prevUpdateData.id.equals(currentUpdateData.id)) {
                        prevUpdateData.preId = currentUpdateData.id;
                    }
                    if (currentUpdateData.id.equals("_fab")) {
                        jC.a(sc_id).h(projectFileBean.getXmlName()).copy(prevUpdateData);
                    } else {
                        jC.a(sc_id).c(projectFileBean.getXmlName(), currentUpdateData.id).copy(prevUpdateData);
                    }
                    viewEditor.a(viewEditor.e(prevUpdateData), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_REMOVE) {
                    for (ViewBean view : historyViewBean.getRemovedData()) {
                        jC.a(sc_id).a(projectFileBean.getXmlName(), view);
                    }
                    viewEditor.a(viewEditor.a(eC.a(historyViewBean.getRemovedData()), false), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_MOVE) {
                    ViewBean movedData = historyViewBean.getMovedData();
                    ViewBean viewBean = jC.a(sc_id).c(projectFileBean.getXmlName(), movedData.id);
                    viewBean.preIndex = movedData.index;
                    viewBean.index = movedData.preIndex;
                    viewBean.parent = movedData.preParent;
                    viewBean.preParent = movedData.parent;
                    viewBean.parentType = movedData.preParentType;
                    viewBean.preParentType = movedData.parentType;
                    viewEditor.a(viewEditor.b(viewBean, false), false);
                } else if (actionType == HistoryViewBean.ACTION_TYPE_OVERRIDE) {
                    jC.a(sc_id).c.put(projectFileBean.getXmlName(), historyViewBean.getRemovedData());
                    i();
                }
            }
            invalidateOptionsMenu();
        }
    }

    public void n() {
        ArrayList<ViewBean> viewBeanArrayList = eC.a(jC.a(sc_id).d(projectFileBean.getXmlName()));
        ViewBean viewBean;
        if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB)) {
            viewBean = jC.a(sc_id).h(projectFileBean.getXmlName());
        } else {
            viewBean = null;
        }
        viewProperty.addActivityViews(viewBeanArrayList, viewBean);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        invalidateOptionsMenu();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 213) {
            if (resultCode == -1) {
                c(data.getParcelableExtra("bean"));
            }

            if (data != null && data.getBooleanExtra("is_edit_image", false)) {
                for (ViewBean viewBean : jC.a(sc_id).d(projectFileBean.getXmlName())) {
                    c(viewBean);
                }
                if (isFabEnabled) {
                    c(jC.a(sc_id).h(projectFileBean.getXmlName()));
                }
            }
            invalidateOptionsMenu();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfiguration) {
        super.onConfigurationChanged(newConfiguration);
        viewEditor.setScreenType(newConfiguration.orientation);
        viewEditor.isLayoutChanged = true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.design_view_menu, menu);
        menu.findItem(R.id.menu_view_redo).setEnabled(false);
        menu.findItem(R.id.menu_view_undo).setEnabled(false);
        if (projectFileBean != null) {
            menu.findItem(R.id.menu_view_redo).setEnabled(cC.c(sc_id).f(projectFileBean.getXmlName()));
            menu.findItem(R.id.menu_view_undo).setEnabled(cC.c(sc_id).g(projectFileBean.getXmlName()));
        }
    }

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup parent, Bundle bundle) {
        ViewGroup viewGroup = (ViewGroup) layoutInflater.inflate(R.layout.fr_graphic_editor, parent, false);
        initialize(viewGroup);
        if (bundle != null) {
            sc_id = bundle.getString("sc_id");
        } else {
            sc_id = requireActivity().getIntent().getStringExtra("sc_id");
        }

        return viewGroup;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_view_redo) {
            onRedo();
        } else if (itemId == R.id.menu_view_undo) {
            onUndo();
        }
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle newInstanceState) {
        newInstanceState.putString("sc_id", sc_id);
        super.onSaveInstanceState(newInstanceState);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (viewProperty != null) {
            viewProperty.d();
        }
    }
}
