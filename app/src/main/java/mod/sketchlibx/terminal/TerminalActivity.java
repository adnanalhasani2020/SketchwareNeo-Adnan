package mod.sketchlibx.terminal;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;

import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

/**
 * Mode C (Drawer/Activity): standalone full-screen terminal. Manifest entry already sets
 * {@code windowSoftInputMode="adjustResize"}, which is the correct flag for a dedicated
 * Activity (unlike the embedded Tab mode, which lives inside DesignActivity's own window
 * and has to handle IME insets manually — see NeoTerminalView.applyImeAwarePadding()).
 */
public class TerminalActivity extends BaseAppCompatActivity {
    private NeoTerminalView terminalView;
    private String scId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scId = getIntent().getStringExtra("sc_id");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        AppBarLayout appBarLayout = new AppBarLayout(this);
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Terminal");
        toolbar.setSubtitle(scId);
        toolbar.setNavigationIcon(R.drawable.ic_mtrl_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));
        toolbar.setTitleTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        toolbar.setSubtitleTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        setSupportActionBar(toolbar);

        appBarLayout.addView(toolbar);
        root.addView(appBarLayout);

        terminalView = new NeoTerminalView(this, scId, true); // The terminal strip owns session switching and close-all actions.
        terminalView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView(terminalView);

        setContentView(root);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (terminalView != null) terminalView.destroy(); // detaches only — sessions keep running, see NeoTerminalView
    }
}
