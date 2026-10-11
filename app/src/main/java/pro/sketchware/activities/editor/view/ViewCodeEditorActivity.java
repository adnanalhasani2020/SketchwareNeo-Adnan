package pro.sketchware.activities.editor.view;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import a.a.a.ProjectBuilder;
import a.a.a.cC;
import a.a.a.jC;
import a.a.a.Lx;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.text.ContentListener;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.component.Magnifier;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.ide.diagnostics.Diagnostic;
import mod.hey.studios.ide.diagnostics.DiagnosticsAdapter;
import mod.hey.studios.ide.diagnostics.GradleLogParser;
import mod.hey.studios.util.Helper;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import mod.jbk.code.CodeEditorLanguages.LanguageSpec;
import pro.sketchware.R;
import pro.sketchware.activities.appcompat.ManageAppCompatActivity;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.databinding.ViewCodeEditorBinding;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;
import pro.sketchware.utility.relativelayout.CircularDependencyDetector;

@SuppressWarnings({"deprecation", "removal"})
public class ViewCodeEditorActivity extends BaseAppCompatActivity {
	private ViewCodeEditorBinding binding;
	private CodeEditor editor;
	
	private SharedPreferences prefs;
	private String sc_id;
	private String filename;
	private String content;
	private String beforeContent;
	private boolean isEdited = false;
	
	private ContentListener editorContentListener;
	
	private ProjectFileBean projectFile;
	private ProjectLibraryBean projectLibrary;
	private InjectRootLayoutManager rootLayoutManager;
	
	private MaterialCardView searchCard;
	private ImageView prevBtn, nextBtn;
	private EditText findEdit, replaceEdit;
	
	private DrawerLayout drawerLayout;
	private BottomSheetBehavior<LinearLayout> diagnosticsBehavior;
	private TextView tvCursorPos;
	private TextView tvLanguage;
    private TextView tvIssueStatus;
    private ImageView imgIssueStatus;
    private MaterialCardView issueStatusCard;
	private TabLayout editorTabs;
	private RecyclerView rvExplorer;
    private EditText explorerSearch;
    private ExplorerTreeAdapter explorerAdapter;
    private boolean suppressTabChanges;
    private boolean isLoadingTab;
    private boolean tabListenerAdded;
    private final List<EditorTab> openTabs = new ArrayList<>();
    private EditorTab activeTab;
    private String projectName = "MyProject";
    private CheckBox matchCaseCheck;
    private CheckBox wholeWordCheck;
    private CheckBox regexCheck;
    private TextView searchCountView;
    private LinearLayout searchResultsContainer;
    private MaterialButton findTabButton;
    private MaterialButton replaceTabButton;
    private boolean showReplaceControls = true;
    private io.github.rosemoe.sora.event.SubscriptionReceipt<SelectionChangeEvent> selectionSubscription;

    private final Handler diagHandler = new Handler(Looper.getMainLooper());
	private Runnable diagRunnable;
	
	private final BroadcastReceiver buildDiagnosticsReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String output = intent.getStringExtra("build_output");
			if (output != null) {
				List<Diagnostic> parsedErrors = GradleLogParser.parseLogs(output);
				showDiagnostics(parsedErrors);
			}
		}
	};
	
	private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END);
                return;
            }
            if (searchCard != null && searchCard.getVisibility() == View.VISIBLE) {
                try { editor.getSearcher().stopSearch(); } catch (Exception ignored) { }
                searchCard.setVisibility(View.GONE);
                if (findEdit != null) findEdit.setText("");
                return;
            }
            if (hasUnsavedTabs()) {
                new MaterialAlertDialogBuilder(ViewCodeEditorActivity.this)
                        .setIcon(R.drawable.ic_mtrl_warning)
                        .setTitle(Helper.getResString(R.string.common_word_warning))
                        .setMessage("Some files have unsaved changes. Save them before leaving?")
                        .setPositiveButton("Save all", (dialog, which) -> {
                            if (saveAllTabs()) finishWithResultIfNeeded();
                        })
                        .setNegativeButton("Discard", (dialog, which) -> finish())
                        .setNeutralButton(Helper.getResString(R.string.common_word_cancel), null)
                        .show();
            } else {
                finishWithResultIfNeeded();
            }
        }
    };

	@Override
	public void onCreate(Bundle savedInstanceState) {
		enableEdgeToEdgeNoContrast();
		super.onCreate(savedInstanceState);
		
		binding = ViewCodeEditorBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		
		prefs = getSharedPreferences("dce", Activity.MODE_PRIVATE);
		sc_id = savedInstanceState == null ? getIntent().getStringExtra("sc_id") : savedInstanceState.getString("sc_id");
		filename = getIntent().getStringExtra("title");
		content = getIntent().getStringExtra("content");
        if (content == null) content = "";
		beforeContent = content;
		
        if (sc_id == null || sc_id.trim().isEmpty()) {
            SketchwareUtil.toastError("Project ID is missing. Reopen the editor from Design Activity.");
            finish();
            return;
        }
		rootLayoutManager = new InjectRootLayoutManager(sc_id);
		projectFile = filename == null ? null : findProjectFileBean(filename);
		projectLibrary = jC.c(sc_id).c();
		editor = binding.editor;
        loadProjectHeaderData();
        activeTab = new EditorTab(filename == null ? "layout.xml" : filename, null, projectFile,
                projectFile != null && filename != null && filename.endsWith(".xml"), content == null ? "" : content);
        openTabs.add(activeTab);
		
		getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
		
		drawerLayout = findViewById(R.id.drawer_layout);
		tvCursorPos = findViewById(R.id.tv_cursor_pos);
		tvLanguage = findViewById(R.id.tv_language);
        tvIssueStatus = findViewById(R.id.tv_issue_status);
        imgIssueStatus = findViewById(R.id.img_issue_status);
        issueStatusCard = findViewById(R.id.issue_status_card);
        View languageSelector = findViewById(R.id.language_selector);
        if (languageSelector != null) languageSelector.setOnClickListener(v -> showLanguageSelector());
        if (issueStatusCard != null) issueStatusCard.setOnClickListener(v -> {
            if (diagnosticsBehavior != null) diagnosticsBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
		editorTabs = findViewById(R.id.editor_tabs);
		
		setupToolbar();
		setupEditor();
        selectionSubscription = editor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> updateCursorPosition());
        updateCursorPosition();
		applySyntaxHighlighting();
		setupTabs();
		setupSearchPanel();
		setupDiagnosticsPanel();
		setupProjectExplorer();
        if (filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
            triggerRealtimeDiagnostics();
        }
		
		if (projectFile != null && projectFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && projectLibrary != null && projectLibrary.isEnabled()) {
			setNote("Use AppCompat Manager to modify attributes for CoordinatorLayout, Toolbar, and other appcompat layouts/widgets.");
		}
		
		if (binding.close != null) binding.close.setOnClickListener(v -> { prefs.edit().putInt("note_" + sc_id, 1).apply(); setNote(null); });
		if (binding.noteCard != null) binding.noteCard.setOnClickListener(v -> toAppCompat());
		
		View appBarLayout = findViewById(R.id.app_bar_layout);
		if (appBarLayout != null) UI.addSystemWindowInsetToPadding(appBarLayout, true, true, true, false);
		UI.addSystemWindowInsetToMargin(editor, true, false, true, false);
		
		View navView = findViewById(R.id.nav_view_explorer);
		if(navView != null) UI.addSystemWindowInsetToPadding(navView, true, true, true, true);
	}
	
    private void updateCursorPosition() {
        if (editor == null || tvCursorPos == null) return;
        try {
            io.github.rosemoe.sora.text.Cursor cursor = editor.getCursor();
            if (cursor == null) return;
            int line = cursor.getRightLine() + 1;
            int column = cursor.getRightColumn() + 1;
            tvCursorPos.setText("Ln " + line + ", Col " + column);
        } catch (Exception ignored) { }
    }

	private void applySyntaxHighlighting() {
		if (filename == null) return;
		LanguageSpec spec = CodeEditorLanguages.resolveLanguageSpec(filename);
		SrcCodeEditor.applyLanguageSpec(this, editor, spec);
		SrcCodeEditor.languageId = spec.id;
		if (tvLanguage != null) tvLanguage.setText(spec.label);
	}

    private void showLanguageSelector() {
        SrcCodeEditor.showSwitchLanguageDialog(this, editor, (dialog, which) -> {
            SrcCodeEditor.selectLanguage(this, editor, which);
            LanguageSpec selectedLanguage = LanguageSpec.fromId(which);
            if (tvLanguage != null) tvLanguage.setText(selectedLanguage.label);
            dialog.dismiss();
        });
    }
	
	private void loadProjectHeaderData() {
        try {
            HashMap<String, Object> metadata = a.a.a.lC.b(sc_id);
            if (metadata != null) {
                String name = a.a.a.yB.c(metadata, "my_ws_name");
                if (!TextUtils.isEmpty(name)) projectName = name;
            }
        } catch (Exception ignored) { }
        if (TextUtils.isEmpty(projectName)) projectName = "MyProject";
    }

	private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        Drawable projectIcon = resolveProjectIcon();
        binding.toolbar.setLogo(projectIcon);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setLogo(projectIcon);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayUseLogoEnabled(true);
            getSupportActionBar().setTitle(projectName);
            getSupportActionBar().setSubtitle(sc_id == null ? "" : sc_id);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_mtrl_menu);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (drawerLayout != null && drawerLayout.getDrawerLockMode(GravityCompat.END) != DrawerLayout.LOCK_MODE_LOCKED_CLOSED) {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) drawerLayout.closeDrawer(GravityCompat.END);
                else openProjectExplorer();
            } else {
                onBackPressedCallback.handleOnBackPressed();
            }
        });
        View closeExplorer = findViewById(R.id.btn_close_explorer);
        if (closeExplorer != null) closeExplorer.setOnClickListener(v -> {
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.END);
        });
    }

    private Drawable resolveProjectIcon() {
        int iconSize = Math.max(1, (int) (36 * getResources().getDisplayMetrics().density));
        ArrayList<File> candidates = new ArrayList<>();
        String root = FileUtil.getExternalStorageDir();
        candidates.add(new File(root, ".sketchware/resources/icons/" + sc_id + "/icon.png"));
        candidates.add(new File(root, ".sketchware/resources/icons/" + sc_id + "/mipmaps/mipmap-xxxhdpi/ic_launcher.png"));
        candidates.add(new File(root, ".sketchware/resources/icons/" + sc_id + "/mipmaps/mipmap-xxhdpi/ic_launcher.png"));
        candidates.add(new File(root, ".sketchware/resources/icons/" + sc_id + "/temp_icons/mipmap-xxxhdpi/ic_launcher.png"));
        candidates.add(new File(root, ".sketchware/resources/icons/" + sc_id + "/temp_icons/mipmap-xxhdpi/ic_launcher.png"));
        candidates.add(new File(root, ".sketchware/data/" + sc_id + "/files/app-icon/icon.png"));
        candidates.add(new File(root, ".sketchware/data/" + sc_id + "/files/app-icon/ic_launcher.png"));
        candidates.add(new File(root, ".sketchware/data/" + sc_id + "/files/app-icon/mipmap-xxxhdpi/ic_launcher.png"));
        for (File candidate : candidates) {
            if (candidate.isFile() && candidate.canRead()) {
                Bitmap bitmap = BitmapFactory.decodeFile(candidate.getAbsolutePath());
                if (bitmap != null) {
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, iconSize, iconSize, true);
                    if (scaled != bitmap) bitmap.recycle();
                    return new BitmapDrawable(getResources(), scaled);
                }
            }
        }
        try {
            Drawable fallback = AppCompatResources.getDrawable(this, R.mipmap.ic_launcher);
            if (fallback != null) return fallback;
        } catch (Exception ignored) { }
        return AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_android);
    }

    private void setupTabs() {
        if (editorTabs == null) return;
        if (!tabListenerAdded) {
            editorTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) {
                    if (suppressTabChanges || tab == null) return;
                    int position = tab.getPosition();
                    if (position < 0 || position >= openTabs.size()) return;
                    EditorTab target = openTabs.get(position);
                    if (target != activeTab) requestTabSwitch(target);
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) { }
                @Override public void onTabReselected(TabLayout.Tab tab) { }
            });
            tabListenerAdded = true;
        }
        rebuildTabStrip(activeTab);
    }

    private void rebuildTabStrip(EditorTab selected) {
        if (editorTabs == null) return;
        suppressTabChanges = true;
        editorTabs.removeAllTabs();
        for (EditorTab model : openTabs) {
            TabLayout.Tab tab = editorTabs.newTab().setText(model.title);
            tab.setCustomView(createTabView(model));
            editorTabs.addTab(tab, false);
        }
        int index = openTabs.indexOf(selected);
        if (index >= 0 && index < editorTabs.getTabCount()) editorTabs.selectTab(editorTabs.getTabAt(index));
        suppressTabChanges = false;
        for (EditorTab model : openTabs) updateTabTitle(model);
    }

    private View createTabView(EditorTab model) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), 0, dp(8), 0);
        TextView title = new TextView(this);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setMaxWidth(dp(170));
        title.setTextSize(13);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        title.setText(model.title);
        row.addView(title, new LinearLayout.LayoutParams(-2, -1));
        ImageView close = new ImageView(this);
        close.setImageResource(R.drawable.ic_mtrl_close);
        close.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        close.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        closeParams.setMarginStart(dp(4));
        row.addView(close, closeParams);
        model.tabTitleView = title;
        close.setContentDescription("Close " + model.title);
        close.setOnClickListener(v -> closeTab(model));
        return row;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateTabTitle(EditorTab tab) {
        if (tab == null || tab.tabTitleView == null) return;
        boolean modified = isTabModified(tab);
        tab.tabTitleView.setText(modified ? tab.title + "  •" : tab.title);
        tab.tabTitleView.setTextColor(ThemeUtils.getColor(this,
                tab == activeTab ? R.attr.colorPrimary : R.attr.colorOnSurface));
    }

    private static final class EditorTab {
        final String title;
        final String path;
        final ProjectFileBean layoutBean;
        final boolean databaseLayout;
        String savedContent;
        String workingContent;
        TextView tabTitleView;

        EditorTab(String title, String path, ProjectFileBean layoutBean, boolean databaseLayout, String initialContent) {
            this.title = title == null ? "File" : title;
            this.path = path;
            this.layoutBean = layoutBean;
            this.databaseLayout = databaseLayout;
            this.savedContent = initialContent == null ? "" : initialContent;
            this.workingContent = this.savedContent;
        }

        String key() {
            if (databaseLayout) return "layout:" + title;
            if (path == null) return "name:" + title;
            try { return "file:" + new File(path).getCanonicalPath(); }
            catch (Exception ignored) { return "file:" + new File(path).getAbsolutePath(); }
        }
    }

	private void registerEditorContentListener() {
		if (editorContentListener != null) {
			editor.getText().addContentListener(editorContentListener);
		}
	}
	
	private void loadXmlToEditor(ProjectFileBean bean) {
		try {
			a.a.a.jq buildConfig = new a.a.a.jq();
			a.a.a.Ox xmlGenerator = new a.a.a.Ox(buildConfig, bean);
			xmlGenerator.setExcludeAppCompat(true);
			ArrayList<com.besome.sketch.beans.ViewBean> viewBeans = a.a.a.jC.a(sc_id).d(bean.getXmlName());
			com.besome.sketch.beans.ViewBean viewFab = a.a.a.jC.a(sc_id).h(bean.getXmlName());
			xmlGenerator.a(a.a.a.eC.a(viewBeans), viewFab);
			
			content = xmlGenerator.b();
			beforeContent = content;
			editor.setText(content);
			registerEditorContentListener();
		} catch(Exception e) {
			SketchwareUtil.toastError("Failed to generate layout.");
		}
	}
	
	// --- LIVE SNIPPETS LOGIC STARTS HERE ---
	private void checkAndApplySnippets(int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
		if (insertedContent == null || insertedContent.length() != 1) return;
		char trigger = insertedContent.charAt(0);
		// Only trigger snippet replacement when user hits Space or Enter
		if (trigger != ' ' && trigger != '\n') return;
		
		try {
			Content content = editor.getText();
			String lineText = content.getLineString(startLine);
			
			int wordEnd = startColumn;
			int wordStart = wordEnd - 1;
			
			while (wordStart >= 0 && Character.isLetterOrDigit(lineText.charAt(wordStart))) {
				wordStart--;
			}
			wordStart++; 
			
			if (wordStart < wordEnd) {
				String word = lineText.substring(wordStart, wordEnd);
				String snippet = getSnippetFor(word);
				
				if (snippet != null) {
					final int fWordStart = wordStart;
					editor.post(() -> {
						try {
							int cursorIndex = snippet.indexOf('|');
							String cleanSnippet = snippet.replace("|", "");
							
							editor.getText().delete(startLine, fWordStart, endLine, endColumn);
							editor.getText().insert(startLine, fWordStart, cleanSnippet);
							
							if (cursorIndex != -1) {
								int linesAdded = 0;
								int lastNewLine = -1;
								for(int i=0; i<cursorIndex; i++) {
									if(cleanSnippet.charAt(i) == '\n') {
										linesAdded++;
										lastNewLine = i;
									}
								}
								int targetLine = startLine + linesAdded;
								int targetColumn = (lastNewLine == -1) ? (fWordStart + cursorIndex) : (cursorIndex - lastNewLine - 1);
								editor.setSelection(targetLine, targetColumn);
							}
						} catch (Exception e) { e.printStackTrace(); }
					});
				}
			}
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	private String getSnippetFor(String word) {
		boolean isJava = (SrcCodeEditor.languageId == 1) || (filename != null && filename.endsWith(".java"));
		boolean isXml = (filename != null && filename.endsWith(".xml"));
		
		if (isJava) {
			switch (word) {
				case "toast": return "Toast.makeText(getApplicationContext(), \"|\", Toast.LENGTH_SHORT).show();";
				case "logd": return "Log.d(\"TAG\", \"|\");";
				case "loge": return "Log.e(\"TAG\", \"|\", e);";
				case "intent": return "Intent intent = new Intent(|);\nstartActivity(intent);";
				case "fori": return "for (int i = 0; i < |; i++) {\n    \n}";
				case "psfs": return "public static final String | = \"\";";
				case "if": return "if (|) {\n    \n}";
				case "ifelse": return "if (|) {\n    \n} else {\n    \n}";
				case "tryc": return "try {\n    |\n} catch (Exception e) {\n    e.printStackTrace();\n}";
				case "find": return "findViewById(R.id.|);";
				// Java Snippets
				
				case "et": return "EditText | = findViewById(R.id.|);";
				case "tvf": return "TextView | = findViewById(R.id.|);";
				case "btnf": return "Button | = findViewById(R.id.|);";
				case "ivf": return "ImageView | = findViewById(R.id.|);";
				case "cbf": return "CheckBox | = findViewById(R.id.|);";
				case "rbf": return "RadioButton | = findViewById(R.id.|);";
				case "swf": return "Switch | = findViewById(R.id.|);";
				case "spf": return "Spinner | = findViewById(R.id.|);";
				case "rvf": return "RecyclerView | = findViewById(R.id.|);";
				case "lvf": return "ListView | = findViewById(R.id.|);";
				case "gvf": return "GridView | = findViewById(R.id.|);";
				case "cvf": return "CardView | = findViewById(R.id.|);";
				case "pbf": return "ProgressBar | = findViewById(R.id.|);";
				case "fabf": return "FloatingActionButton | = findViewById(R.id.|);";
				
				case "onclick": return "setOnClickListener(v -> {\n    |\n});";
				case "thread": return "new Thread(() -> {\n    |\n}).start();";
				case "runui": return "runOnUiThread(() -> {\n    |\n});";
				case "handler": return "new Handler().postDelayed(() -> {\n    |\n}, 1000);";
				case "finish": return "finish();";
				case "finisha": return "finishAffinity();";
				case "back": return "onBackPressed();";
				case "random": return "Random random = new Random();\nint | = random.nextInt();";
				case "array": return "ArrayList<String> | = new ArrayList<>();";
				case "hashmap": return "HashMap<String, Object> | = new HashMap<>();";
				case "dialog": return "new AlertDialog.Builder(this)\n.setTitle(\"|\")\n.setMessage(\"\")\n.setPositiveButton(\"OK\", null)\n.show();";
				case "date": return "String | = new SimpleDateFormat(\"dd/MM/yyyy\", Locale.getDefault()).format(new Date());";
				case "time": return "String | = new SimpleDateFormat(\"HH:mm\", Locale.getDefault()).format(new Date());";
				case "switch": return "switch (|) {\n    case :\n        break;\n    default:\n        break;\n}";
				case "while": return "while (|) {\n    \n}";
				case "dowhile": return "do {\n    |\n} while ();";
				case "foreach": return "for (Object item : |) {\n    \n}";
				case "return": return "return |;";
				case "newobj": return "| obj = new |();";
				case "bundle": return "Bundle bundle = new Bundle();";
				case "putextra": return "intent.putExtra(\"|\", \"\");";
				case "getextra": return "String | = getIntent().getStringExtra(\"\");";
				case "shared": return "SharedPreferences sp = getSharedPreferences(\"data\", MODE_PRIVATE);";
				case "editor": return "SharedPreferences.Editor editor = sp.edit();";
				case "commit": return "editor.commit();";
				case "apply": return "editor.apply();";
				case "prefput": return "editor.putString(\"|\", \"\");";
				case "prefget": return "String | = sp.getString(\"\", \"\");";
				case "anim": return "overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);";
				case "post": return "view.post(() -> {\n    |\n});";
				case "visible": return "|.setVisibility(View.VISIBLE);";
				case "gone": return "|.setVisibility(View.GONE);";
				case "invisible": return "|.setVisibility(View.INVISIBLE);";
				case "enable": return "|.setEnabled(true);";
				case "disable": return "|.setEnabled(false);";
				case "text": return "|.setText(\"\");";
				case "gettext": return "|.getText().toString();";
				case "hint": return "|.setHint(\"\");";
				case "focus": return "|.requestFocus();";
				case "clear": return "|.setText(\"\");";
				case "img": return "|.setImageResource(R.drawable.|);";
				case "bg": return "|.setBackgroundResource(R.drawable.|);";
				case "color": return "|.setTextColor(Color.parseColor(\"#000000\"));";
				case "parse": return "Integer.parseInt(|);";
				case "str": return "String.valueOf(|);";
				case "equals": return "|.equals(\"\")";
				case "isempty": return "TextUtils.isEmpty(|)";
				case "len": return "|.length()";
				case "trim": return "|.trim()";
				case "upper": return "|.toUpperCase()";
				case "lower": return "|.toLowerCase()";
				case "contains": return "|.contains(\"\")";
				case "replace": return "|.replace(\"\", \"\")";
				case "split": return "|.split(\",\")";
				case "sleep": return "Thread.sleep(|);";
				case "exit": return "System.exit(0);";
			}
		} else if (isXml) {
			switch (word) {
				case "ll": return "<LinearLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:orientation=\"vertical\">\n    |\n</LinearLayout>";
				case "rl": return "<RelativeLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\">\n    |\n</RelativeLayout>";
				case "tv": return "<TextView\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"|\" />";
				case "btn": return "<Button\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"|\" />";
				case "iv": return "<ImageView\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:src=\"@drawable/|\" />";
				// XML Snippets
				
				case "cl": return "<androidx.constraintlayout.widget.ConstraintLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n    |\n\n</androidx.constraintlayout.widget.ConstraintLayout>";
				
				case "sv": return "<ScrollView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n    |\n\n</ScrollView>";
				
				case "hsv": return "<HorizontalScrollView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\">\n\n    |\n\n</HorizontalScrollView>";
				
				case "et": return "<EditText\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:hint=\"|\" />";
				
				case "cb": return "<CheckBox\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"|\" />";
				
				case "rb": return "<RadioButton\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"|\" />";
				
				case "rg": return "<RadioGroup\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\">\n    |\n</RadioGroup>";
				
				case "sw": return "<Switch\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\" />";
				
				case "pb": return "<ProgressBar\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\" />";
				
				case "seek": return "<SeekBar\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\" />";
				
				case "rating": return "<RatingBar\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\" />";
				
				case "spinner": return "<Spinner\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\" />";
				
				case "lv": return "<ListView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\" />";
				
				case "gv": return "<GridView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\" />";
				
				case "rv": return "<androidx.recyclerview.widget.RecyclerView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\" />";
				
				case "card": return "<androidx.cardview.widget.CardView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\">\n\n    |\n\n</androidx.cardview.widget.CardView>";
				
				case "space": return "<Space\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"16dp\" />";
				
				case "view": return "<View\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"1dp\"\n    android:background=\"#DDDDDD\" />";
				
				case "include": return "<include layout=\"@layout/|\" />";
				
				case "merge": return "<merge>\n    |\n</merge>";
				
				case "frag": return "<fragment\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:name=\"|\" />";
			}
		}
		return null;
	}
	// --- LIVE SNIPPETS LOGIC ENDS HERE ---
	
    private static final class ExplorerNode {
        final String name;
        final File file;
        final ProjectFileBean layoutBean;
        final ProjectFileBean javaBean;
        final boolean directory;
        final int depth;
        final List<ExplorerNode> children = new ArrayList<>();
        boolean expanded;

        ExplorerNode(String name, File file, ProjectFileBean layoutBean, ProjectFileBean javaBean,
                     boolean directory, int depth, boolean expanded) {
            this.name = name;
            this.file = file;
            this.layoutBean = layoutBean;
            this.javaBean = javaBean;
            this.directory = directory;
            this.depth = depth;
            this.expanded = expanded;
        }
    }

    private void setupProjectExplorer() {
        rvExplorer = findViewById(R.id.rv_explorer);
        explorerSearch = findViewById(R.id.et_explorer_search);
        if (rvExplorer == null) return;
        rvExplorer.setLayoutManager(new LinearLayoutManager(this));
        explorerAdapter = new ExplorerTreeAdapter(buildExplorerTree());
        rvExplorer.setAdapter(explorerAdapter);
        TextView projectIdView = findViewById(R.id.tv_explorer_project_id);
        if (projectIdView != null) projectIdView.setText(sc_id == null ? "" : sc_id);
        if (explorerSearch != null) {
            explorerSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (explorerAdapter != null) explorerAdapter.setQuery(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
    }

    private void openProjectExplorer() {
        if (drawerLayout == null) return;
        if (explorerAdapter == null) {
            setupProjectExplorer();
        } else {
            explorerAdapter.setRoot(buildExplorerTree());
            if (explorerSearch != null) explorerAdapter.setQuery(explorerSearch.getText() == null ? "" : explorerSearch.getText().toString());
        }
        drawerLayout.openDrawer(GravityCompat.END);
    }

    private boolean isCustomJavaEnabled() {
        return new mod.hey.studios.project.ProjectSettings(sc_id)
                .getValue(mod.hey.studios.project.ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, "false")
                .equals("true");
    }

    private ExplorerNode buildExplorerTree() {
        ExplorerNode root = new ExplorerNode(projectName, null, null, null, true, 0, true);
        ExplorerNode app = childDirectory(root, "app", 1, true);
        ExplorerNode src = childDirectory(app, "src", 2, true);
        ExplorerNode main = childDirectory(src, "main", 3, true);

        if (isCustomJavaEnabled()) {
            ExplorerNode javaNode = childDirectory(main, "java", 4, true);
            File customJavaDir = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_java");
            Set<String> javaNames = new HashSet<>();
            ArrayList<ProjectFileBean> beans = getProjectFileBeans();
            for (ProjectFileBean bean : beans) {
                if (bean == null) continue;
                String javaName;
                try { javaName = bean.getJavaName(); } catch (Exception ignored) { javaName = null; }
                if (TextUtils.isEmpty(javaName) || !javaName.toLowerCase(java.util.Locale.ROOT).endsWith(".java")) continue;
                String normalized = javaName.toLowerCase(java.util.Locale.ROOT);
                if (javaNames.add(normalized)) {
                    javaNode.children.add(new ExplorerNode(javaName, new File(customJavaDir, javaName), null, bean,
                            false, 5, false));
                }
            }
            if (customJavaDir.isDirectory()) {
                addRealFiles(javaNode, customJavaDir, 5, javaNames, true, 5);
            }
        }

        ExplorerNode res = childDirectory(main, "res", 4, true);
        ExplorerNode layout = childDirectory(res, "layout", 5, true);
        Set<String> layoutNames = new HashSet<>();
        for (ProjectFileBean bean : getProjectFileBeans()) {
            if (bean == null) continue;
            String xmlName;
            try { xmlName = bean.getXmlName(); } catch (Exception ignored) { xmlName = null; }
            if (TextUtils.isEmpty(xmlName) || !xmlName.endsWith(".xml")) continue;
            String normalized = xmlName.toLowerCase(java.util.Locale.ROOT);
            if (layoutNames.add(normalized)) {
                layout.children.add(new ExplorerNode(xmlName, null, bean, null, false, 6, false));
            }
        }
        File layoutFolder = new File(new pro.sketchware.utility.FilePathUtil().getPathResource(sc_id), "layout");
        addRealFiles(layout, layoutFolder, 6, layoutNames, false, 5);
        layout.children.sort(Comparator.comparing(node -> node.name.toLowerCase(java.util.Locale.ROOT)));

        File resourceDir = new File(new pro.sketchware.utility.FilePathUtil().getPathResource(sc_id));
        addResourceDirectories(res, resourceDir, 5);

        String customManifestPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_manifest.xml";
        if (new mod.hey.studios.project.ProjectSettings(sc_id)
                .getValue(mod.hey.studios.project.ProjectSettings.SETTING_ENABLE_CUSTOM_MANIFEST, "false").equals("true")) {
            File manifest = new File(customManifestPath);
            if (manifest.isFile()) main.children.add(new ExplorerNode("AndroidManifest.xml", manifest, null, null, false, 4, false));
        }

        File customGradle = new File(new pro.sketchware.utility.FilePathUtil().getPathCustomGradle(sc_id), "app_build.gradle");
        if (customGradle.isFile()) root.children.add(new ExplorerNode("app_build.gradle", customGradle, null, null, false, 1, false));

        root.children.sort(Comparator.comparing(node -> node.name.equals("app") ? "" : node.name.toLowerCase(java.util.Locale.ROOT)));
        return root;
    }

    private ExplorerNode childDirectory(ExplorerNode parent, String name, int depth, boolean expanded) {
        ExplorerNode node = new ExplorerNode(name, null, null, null, true, depth, expanded);
        parent.children.add(node);
        return node;
    }

    private ArrayList<ProjectFileBean> getProjectFileBeans() {
        ArrayList<ProjectFileBean> result = new ArrayList<>();
        try {
            ArrayList<ProjectFileBean> mainFiles = jC.b(sc_id).b();
            ArrayList<ProjectFileBean> extraFiles = jC.b(sc_id).c();
            if (mainFiles != null) result.addAll(mainFiles);
            if (extraFiles != null) {
                Set<String> seen = new HashSet<>();
                for (ProjectFileBean bean : result) if (bean != null && bean.getXmlName() != null) seen.add(bean.getXmlName());
                for (ProjectFileBean bean : extraFiles) {
                    if (bean != null && (bean.getXmlName() == null || seen.add(bean.getXmlName()))) result.add(bean);
                }
            }
        } catch (Exception ignored) { }
        return result;
    }

    private ProjectFileBean findProjectFileBean(String xmlName) {
        if (TextUtils.isEmpty(xmlName)) return null;
        for (ProjectFileBean bean : getProjectFileBeans()) {
            if (bean != null && xmlName.equals(bean.getXmlName())) return bean;
        }
        return null;
    }

    private void addResourceDirectories(ExplorerNode parent, File realRoot, int depth) {
        if (realRoot == null || !realRoot.isDirectory() || depth > 8) return;
        File[] children = realRoot.listFiles();
        if (children == null) return;
        ArrayList<File> dirs = new ArrayList<>();
        ArrayList<File> files = new ArrayList<>();
        for (File child : children) {
            if (child == null || child.getName().startsWith(".")) continue;
            if (child.isDirectory()) {
                if (!"layout".equalsIgnoreCase(child.getName())) dirs.add(child);
            } else if (isTextFile(child) && (isCustomJavaEnabled() || !child.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".java"))) files.add(child);
        }
        dirs.sort(Comparator.comparing(file -> file.getName().toLowerCase(java.util.Locale.ROOT)));
        files.sort(Comparator.comparing(file -> file.getName().toLowerCase(java.util.Locale.ROOT)));
        for (File dir : dirs) {
            ExplorerNode folder = new ExplorerNode(dir.getName(), dir, null, null, true, depth, false);
            parent.children.add(folder);
            addResourceDirectories(folder, dir, depth + 1);
        }
        for (File file : files) parent.children.add(new ExplorerNode(file.getName(), file, null, null, false, depth, false));
    }

    private void addRealFiles(ExplorerNode parent, File directory, int depth, Set<String> alreadyAdded,
                              boolean javaOnly, int maxDepth) {
        if (directory == null || !directory.isDirectory() || depth > maxDepth) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        ArrayList<File> ordered = new ArrayList<>();
        Collections.addAll(ordered, files);
        ordered.sort((first, second) -> {
            if (first.isDirectory() != second.isDirectory()) return first.isDirectory() ? -1 : 1;
            return first.getName().compareToIgnoreCase(second.getName());
        });
        for (File file : ordered) {
            if (file == null || file.getName().startsWith(".")) continue;
            if (file.isDirectory()) {
                ExplorerNode child = new ExplorerNode(file.getName(), file, null, null, true, depth, false);
                parent.children.add(child);
                addRealFiles(child, file, depth + 1, alreadyAdded, javaOnly, maxDepth);
                continue;
            }
            if (!isTextFile(file)) continue;
            if (!isCustomJavaEnabled() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".java")) continue;
            if (javaOnly && !file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".java")) continue;
            String normalized = file.getName().toLowerCase(java.util.Locale.ROOT);
            if (alreadyAdded != null && !alreadyAdded.add(normalized)) continue;
            parent.children.add(new ExplorerNode(file.getName(), file, null, null, false, depth, false));
        }
    }

    private boolean isTextFile(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt")
                || name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".gradle")
                || name.endsWith(".properties") || name.endsWith(".pro") || name.endsWith(".md")
                || name.endsWith(".sh") || name.endsWith(".c") || name.endsWith(".h")
                || name.endsWith(".cpp") || name.endsWith(".hpp") || name.endsWith(".cmake");
    }

    private void onExplorerFileClick(ExplorerNode node) {
        if (node.javaBean != null) {
            if (!isCustomJavaEnabled()) {
                SketchwareUtil.toastError("Custom Java is disabled. Enable it in Advanced Settings first.");
                return;
            }
            openCustomJavaTab(node.javaBean, node.name);
            return;
        }
        if (node.layoutBean != null) {
            openLayoutTab(node.layoutBean);
            return;
        }
        if (node.file != null && node.file.isFile()) openPhysicalFileTab(node.file);
    }

    private void openLayoutTab(ProjectFileBean bean) {
        if (bean == null) return;
        k();
        new Thread(() -> {
            String xml = generateXmlContent(bean);
            runOnUiThread(() -> {
                h();
                if (isFinishing() || isDestroyed()) return;
                if (xml == null) {
                    SketchwareUtil.toastError("Could not load layout " + bean.getXmlName());
                    return;
                }
                requestOpenTab(new EditorTab(bean.getXmlName(), null, bean, true, xml));
            });
        }, "sketchware-layout-load").start();
    }

    private String generateXmlContent(ProjectFileBean bean) {
        try {
            a.a.a.jq buildConfig = new a.a.a.jq();
            a.a.a.Ox xmlGenerator = new a.a.a.Ox(buildConfig, bean);
            xmlGenerator.setExcludeAppCompat(true);
            ArrayList<ViewBean> viewBeans = jC.a(sc_id).d(bean.getXmlName());
            ViewBean viewFab = jC.a(sc_id).h(bean.getXmlName());
            xmlGenerator.a(a.a.a.eC.a(viewBeans), viewFab);
            return xmlGenerator.b();
        } catch (Exception e) {
            return null;
        }
    }

    private void openCustomJavaTab(ProjectFileBean bean, String javaName) {
        if (!isCustomJavaEnabled()) {
            SketchwareUtil.toastError("Custom Java is disabled. Enable it in Advanced Settings first.");
            return;
        }
        File target = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_java/" + javaName);
        if (target.isFile()) {
            openPhysicalFileTab(target);
            return;
        }
        if (bean == null || TextUtils.isEmpty(javaName)) {
            SketchwareUtil.toastError("No Java source is available for this file.");
            return;
        }
        k();
        new Thread(() -> {
            Exception failure = null;
            try {
                String source = new a.a.a.yq(getApplicationContext(), sc_id)
                        .getFileSrc(javaName, jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
                if (TextUtils.isEmpty(source)) {
                    File generated = new File(new pro.sketchware.utility.FilePathUtil().getPathJava(sc_id), javaName);
                    if (generated.isFile()) source = FileUtil.readFile(generated.getAbsolutePath());
                }
                if (TextUtils.isEmpty(source)) throw new IOException("Generated source is empty.");
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("Could not create the custom Java directory.");
                }
                FileUtil.writeFile(target.getAbsolutePath(), source);
                String persisted = FileUtil.readFile(target.getAbsolutePath());
                if (!Objects.equals(source, persisted)) throw new IOException("The generated source could not be verified after writing.");
            } catch (Exception error) {
                failure = error;
            }
            final Exception finalFailure = failure;
            runOnUiThread(() -> {
                h();
                if (isFinishing() || isDestroyed()) return;
                if (finalFailure != null) {
                    SketchwareUtil.toastError("Could not prepare custom Java file: " + finalFailure.getMessage());
                    return;
                }
                openPhysicalFileTab(target);
            });
        }, "sketchware-java-source-load").start();
    }

    private void openPhysicalFileTab(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            SketchwareUtil.toastError("File cannot be opened.");
            return;
        }
        if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".java") && !isCustomJavaEnabled()) {
            SketchwareUtil.toastError("Custom Java is disabled. Enable it in Advanced Settings first.");
            return;
        }
        if (!isTextFile(file)) {
            SketchwareUtil.toastError("This file type is not supported by the text editor.");
            return;
        }
        try {
            String text = FileUtil.readFile(file.getAbsolutePath());
            requestOpenTab(new EditorTab(file.getName(), file.getAbsolutePath(), null, false, text == null ? "" : text));
        } catch (Exception e) {
            SketchwareUtil.toastError("Could not read file: " + e.getMessage());
        }
    }

    private void requestOpenTab(EditorTab requested) {
        for (EditorTab existing : openTabs) {
            if (existing.key().equals(requested.key())) {
                requestTabSwitch(existing);
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.END);
                return;
            }
        }
        Runnable openRequested = () -> {
            openTabs.add(requested);
            rebuildTabStrip(requested);
            switchToTab(requested);
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.END);
        };
        if (isContentModified()) {
            EditorTab current = activeTab;
            showUnsavedChangesDialog(current, "open " + requested.title,
                    () -> { if (saveCurrentTab()) openRequested.run(); },
                    () -> { current.workingContent = current.savedContent; openRequested.run(); });
        } else {
            if (activeTab != null) activeTab.workingContent = editor.getText().toString();
            openRequested.run();
        }
    }

    private void requestTabSwitch(EditorTab target) {
        if (target == null || target == activeTab) return;
        if (isContentModified()) {
            EditorTab current = activeTab;
            restoreSelectedTabVisual();
            showUnsavedChangesDialog(current, "switch files",
                    () -> { if (saveCurrentTab()) switchToTab(target); },
                    () -> { current.workingContent = current.savedContent; switchToTab(target); });
        } else {
            if (activeTab != null) activeTab.workingContent = editor.getText().toString();
            switchToTab(target);
        }
    }

    private void restoreSelectedTabVisual() {
        int selected = openTabs.indexOf(activeTab);
        if (selected >= 0 && selected < editorTabs.getTabCount()) {
            suppressTabChanges = true;
            editorTabs.selectTab(editorTabs.getTabAt(selected));
            suppressTabChanges = false;
        }
    }

    private void switchToTab(EditorTab target) {
        if (target == null || editor == null) return;
        int targetIndex = openTabs.indexOf(target);
        if (targetIndex >= 0 && targetIndex < editorTabs.getTabCount()
                && editorTabs.getSelectedTabPosition() != targetIndex) {
            suppressTabChanges = true;
            editorTabs.selectTab(editorTabs.getTabAt(targetIndex));
            suppressTabChanges = false;
        }
        try { editor.getText().removeContentListener(editorContentListener); } catch (Exception ignored) { }
        activeTab = target;
        filename = target.title;
        projectFile = target.layoutBean;
        content = target.workingContent;
        beforeContent = target.savedContent;
        isLoadingTab = true;
        editor.setText(target.workingContent == null ? "" : target.workingContent);
        isLoadingTab = false;
        updateCursorPosition();
        registerEditorContentListener();
        getIntent().putExtra("title", target.title);
        getIntent().putExtra("sc_id", sc_id);
        // Keep the editor text in the intent, not the physical path. The activity may
        // be recreated by Android; using the path here would show the path as code.
        getIntent().putExtra("content", target.workingContent == null ? "" : target.workingContent);
        if (target.path != null) getIntent().putExtra("file_path", target.path);
        else getIntent().removeExtra("file_path");
        applySyntaxHighlighting();
        updateTabTitle(target);
        invalidateOptionsMenu();
        updateMenuState();
        if (diagRunnable != null) diagHandler.removeCallbacks(diagRunnable);
        diagGeneration.incrementAndGet();
        if (filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
            triggerRealtimeDiagnostics();
        } else {
            updateIssueStatus(java.util.Collections.emptyList());
            if (diagnosticsBehavior != null) diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
        updateFindResults();
    }

    private void closeTab(EditorTab target) {
        if (target == null || openTabs.size() <= 1) {
            SketchwareUtil.toast("Keep at least one file open.");
            return;
        }
        boolean modified = isTabModified(target);
        Runnable closeWithoutSave = () -> closeTabNow(target);
        if (modified) {
            String text = target == activeTab ? editor.getText().toString() : target.workingContent;
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_mtrl_warning)
                    .setTitle("Unsaved changes")
                    .setMessage("Save changes to " + target.title + " before closing this tab?")
                    .setPositiveButton("Save", (dialog, which) -> {
                        if (saveTab(target, text)) closeTabNow(target);
                    })
                    .setNegativeButton("Don't save", (dialog, which) -> closeWithoutSave.run())
                    .setNeutralButton("Cancel", null)
                    .show();
        } else closeTabNow(target);
    }

    private void closeTabNow(EditorTab target) {
        int index = openTabs.indexOf(target);
        if (index < 0 || openTabs.size() <= 1) return;
        EditorTab next = target == activeTab
                ? openTabs.get(index > 0 ? index - 1 : Math.min(index + 1, openTabs.size() - 1))
                : activeTab;
        openTabs.remove(target);
        if (target == activeTab) {
            activeTab = null;
            rebuildTabStrip(next);
            switchToTab(next);
        } else {
            rebuildTabStrip(activeTab);
        }
    }

    private void showUnsavedChangesDialog(EditorTab tab, String action, Runnable onSave, Runnable onDiscard) {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_warning)
                .setTitle("Unsaved changes")
                .setMessage("Save changes to " + (tab == null ? "this file" : tab.title) + " before you " + action + "?")
                .setPositiveButton("Save", (dialog, which) -> { if (onSave != null) onSave.run(); })
                .setNegativeButton("Don't save", (dialog, which) -> { if (onDiscard != null) onDiscard.run(); })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private class ExplorerTreeAdapter extends RecyclerView.Adapter<ExplorerTreeAdapter.NodeHolder> {
        private ExplorerNode root;
        private final List<ExplorerNode> visibleNodes = new ArrayList<>();
        private String query = "";

        ExplorerTreeAdapter(ExplorerNode root) {
            this.root = root;
            rebuild();
        }

        void setQuery(String value) {
            query = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
            rebuild();
        }

        void setRoot(ExplorerNode value) {
            if (value != null) root = value;
            rebuild();
        }

        private void rebuild() {
            visibleNodes.clear();
            if (query.isEmpty()) addExpanded(root);
            else addMatches(root);
            notifyDataSetChanged();
        }

        private void addExpanded(ExplorerNode node) {
            visibleNodes.add(node);
            if (node.directory && node.expanded) for (ExplorerNode child : node.children) addExpanded(child);
        }

        private void addMatches(ExplorerNode node) {
            if (node.name.toLowerCase(java.util.Locale.ROOT).contains(query)) visibleNodes.add(node);
            for (ExplorerNode child : node.children) addMatches(child);
        }

        @NonNull @Override
        public NodeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(42));
            row.setPadding(dp(8), dp(4), dp(10), dp(4));
            ImageView chevron = new ImageView(parent.getContext());
            row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(24)));
            ImageView icon = new ImageView(parent.getContext());
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
            iconParams.setMarginStart(dp(4));
            row.addView(icon, iconParams);
            TextView title = new TextView(parent.getContext());
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            title.setTextSize(14);
            title.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
            titleParams.setMarginStart(dp(10));
            row.addView(title, titleParams);
            return new NodeHolder(row, chevron, icon, title);
        }

        @Override public void onBindViewHolder(@NonNull NodeHolder holder, int position) {
            ExplorerNode node = visibleNodes.get(position);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            if (params != null) {
                params.setMarginStart(dp(node.depth * 13));
                holder.itemView.setLayoutParams(params);
            }
            holder.title.setText(node.name);
            holder.chevron.setVisibility(node.directory ? View.VISIBLE : View.INVISIBLE);
            holder.chevron.setImageResource(node.expanded ? R.drawable.ic_mtrl_arrow_down : R.drawable.ic_mtrl_arrow_right);
            int tint = ThemeUtils.getColor(ViewCodeEditorActivity.this,
                    node.directory ? R.attr.colorPrimary : R.attr.colorOnSurfaceVariant);
            holder.chevron.setColorFilter(ThemeUtils.getColor(ViewCodeEditorActivity.this, R.attr.colorOnSurfaceVariant));
            if (node.directory) holder.icon.setImageResource(node.expanded ? R.drawable.ic_mtrl_folder_open : R.drawable.ic_mtrl_folder);
            else if (node.name.toLowerCase(java.util.Locale.ROOT).endsWith(".java")) holder.icon.setImageResource(R.drawable.ic_mtrl_java);
            else if (node.name.toLowerCase(java.util.Locale.ROOT).endsWith(".gradle")) holder.icon.setImageResource(R.drawable.ic_mtrl_gradle);
            else if (node.name.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) holder.icon.setImageResource(R.drawable.ic_mtrl_code);
            else holder.icon.setImageResource(R.drawable.ic_mtrl_file);
            holder.icon.setColorFilter(tint);
            holder.itemView.setBackgroundResource(android.R.drawable.list_selector_background);
            holder.itemView.setOnClickListener(v -> {
                if (node.directory) {
                    node.expanded = !node.expanded;
                    rebuild();
                } else {
                    onExplorerFileClick(node);
                }
            });
        }

        @Override public int getItemCount() { return visibleNodes.size(); }

        class NodeHolder extends RecyclerView.ViewHolder {
            final ImageView chevron, icon;
            final TextView title;
            NodeHolder(View view, ImageView chevron, ImageView icon, TextView title) {
                super(view); this.chevron = chevron; this.icon = icon; this.title = title;
            }
        }
    }

	private final java.util.concurrent.atomic.AtomicInteger diagGeneration = new java.util.concurrent.atomic.AtomicInteger(0);
	
	private void triggerRealtimeDiagnostics() {
		if (diagRunnable != null) diagHandler.removeCallbacks(diagRunnable);
		final int myGen = diagGeneration.incrementAndGet();
		diagRunnable = () -> {
			String text = editor.getText().toString();
			if (filename != null && filename.endsWith(".xml")) {
				new Thread(() -> {
					List<Diagnostic> diags = new ArrayList<>();
					try {
						DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
						DocumentBuilder builder = factory.newDocumentBuilder();
						builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
							@Override public void warning(org.xml.sax.SAXParseException e) {
								diags.add(new Diagnostic(Diagnostic.Severity.WARNING, filename, e.getLineNumber(), e.getColumnNumber(), humanizeXmlError(e)));
							}
							@Override public void error(org.xml.sax.SAXParseException e) {
								diags.add(new Diagnostic(Diagnostic.Severity.ERROR, filename, e.getLineNumber(), e.getColumnNumber(), humanizeXmlError(e)));
							}
							@Override public void fatalError(org.xml.sax.SAXParseException e) {
								diags.add(new Diagnostic(Diagnostic.Severity.ERROR, filename, e.getLineNumber(), e.getColumnNumber(), humanizeXmlError(e)));
							}
						});
						builder.parse(new org.xml.sax.InputSource(new StringReader(text)));
					} catch (Exception e) {
						if (e instanceof org.xml.sax.SAXParseException) {
							org.xml.sax.SAXParseException sax = (org.xml.sax.SAXParseException) e;
							diags.add(new Diagnostic(Diagnostic.Severity.ERROR, filename, sax.getLineNumber(), sax.getColumnNumber(), humanizeXmlError(sax)));
						} else if (diags.isEmpty()) {
							diags.add(new Diagnostic(Diagnostic.Severity.ERROR, filename, 1, 0, e.getMessage()));
						}
					}
					runOnUiThread(() -> {
						if (myGen != diagGeneration.get()) return;
                        updateIssueStatus(diags);
						if (diagnosticsBehavior != null && !diags.isEmpty()) {
							showDiagnostics(diags);
						} else if (diagnosticsBehavior != null && diags.isEmpty()) {
							diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
						}
					});
				}).start();
			}
		};
		diagHandler.postDelayed(diagRunnable, 800);
	}
	
	private String humanizeXmlError(org.xml.sax.SAXParseException e) {
		String raw = e.getMessage() == null ? "" : e.getMessage();
		int line = e.getLineNumber();
		String lower = raw.toLowerCase();
		
		if (lower.contains("must be terminated by the matching end-tag")) {
			String tag = extractQuoted(raw, 0);
			return "Line " + line + ": <" + tag + "> tag is not closed. Add a matching </" + tag + "> tag.";
		}
		if (lower.contains("element type") && lower.contains("must be followed by either attribute specifications")) {
			return "Line " + line + ": Tag is not written correctly — check for a missing space, '>' or '/>' after the tag name.";
		}
		if (lower.contains("the markup in the document following the root element must be well-formed")) {
			return "Line " + line + ": Extra content found after the closing tag of the root element. Remove anything after the last closing tag.";
		}
		if (lower.contains("attribute") && lower.contains("was already specified")) {
			String attr = extractQuoted(raw, 0);
			return "Line " + line + ": Attribute \"" + attr + "\" is duplicated on this tag. Remove the repeated one.";
		}
		if (lower.contains("not allowed to start with the '?' character") || lower.contains("content is not allowed in prolog")) {
			return "Line " + line + ": Unexpected text before the XML declaration. Make sure nothing is written before <?xml ...?>.";
		}
		if (lower.contains("referenced entity") || lower.contains("entityref")) {
			return "Line " + line + ": Invalid character like \"&\" used directly. Use \"&amp;\" instead.";
		}
		if (lower.contains("must be well-formed")) {
			return "Line " + line + ": XML structure is broken here — a tag is likely missing, extra, or not closed properly.";
		}
		return "Line " + line + ": " + raw;
	}
	
	private String extractQuoted(String text, int occurrence) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(text);
		int count = 0;
		while (m.find()) {
			if (count == occurrence) return m.group(1);
			count++;
		}
		return "tag";
	}
	
	private void setupEditor() {
		editor.setTypefaceText(EditorUtils.getTypeface(this));
		float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
		editor.setTextSize(prefs.getInt("act_ts", 14));
		editor.setText(content == null ? "" : content);
		editor.setWordwrap(prefs.getBoolean("act_ww", false));
		editor.getComponent(EditorAutoCompletion.class).setEnabled(prefs.getBoolean("act_ac", true));
		editor.getProps().symbolPairAutoCompletion = prefs.getBoolean("act_acsp", true);
		editor.getComponent(Magnifier.class).setEnabled(true);
		editor.setHighlightCurrentLine(true);
		editor.setLineSpacing(2f, 1.1f);
		
		editor.getColorScheme().setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, 0x66FFEB3B);
		
		editorContentListener = new ContentListener() {
			@Override public void beforeReplace(Content content) { }
			@Override public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) {
                if (isLoadingTab) return;
				runOnUiThread(() -> updateMenuState());
				triggerRealtimeDiagnostics();
			}
			
			@Override
			public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
                if (isLoadingTab) return;
				runOnUiThread(() -> updateMenuState());
				triggerRealtimeDiagnostics();
				
				if (insertedContent != null && insertedContent.toString().equals(">")) {
					try {
						String lineText = content.getLineString(endLine);
						int tagStartIndex = lineText.lastIndexOf('<', endColumn - 1);
						if (tagStartIndex != -1) {
							String tagStr = lineText.substring(tagStartIndex + 1, endColumn - 1);
							if (!tagStr.startsWith("/") && !tagStr.endsWith("/") && !tagStr.contains(" ") && tagStr.matches("[a-zA-Z0-9_.]+")) {
								String closeTag = "</" + tagStr + ">";
								editor.post(() -> {
									try { editor.getText().insert(endLine, endColumn, closeTag); } catch (Exception ignored) {}
								});
							}
						}
					} catch (Exception ignored) {}
				}
				
				checkAndApplySnippets(startLine, startColumn, endLine, endColumn, insertedContent);
			}
		};
		registerEditorContentListener();
	}
	
	private void setupDiagnosticsPanel() {
		LinearLayout bottomSheet = findViewById(R.id.bottom_sheet_diagnostics);
		if(bottomSheet != null) {
			diagnosticsBehavior = BottomSheetBehavior.from(bottomSheet);
			diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
			View closeBtn = findViewById(R.id.btn_close_diagnostics);
			if (closeBtn != null) {
				closeBtn.setOnClickListener(v -> diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));
			}
		}
        updateIssueStatus(java.util.Collections.emptyList());
	}

    private void updateIssueStatus(List<Diagnostic> diagnostics) {
        if (tvIssueStatus == null || imgIssueStatus == null) return;
        int count = diagnostics == null ? 0 : diagnostics.size();
        if (count == 0) {
            tvIssueStatus.setText("No issues");
            tvIssueStatus.setTextColor(0xFF2EA866);
            imgIssueStatus.setImageResource(R.drawable.ic_mtrl_check);
            imgIssueStatus.setColorFilter(0xFF2EA866);
        } else {
            tvIssueStatus.setText(count == 1 ? "1 issue" : count + " issues");
            int errorColor = ThemeUtils.getColor(this, R.attr.colorError);
            tvIssueStatus.setTextColor(errorColor);
            imgIssueStatus.setImageResource(R.drawable.ic_mtrl_warning);
            imgIssueStatus.setColorFilter(errorColor);
        }
        if (issueStatusCard != null) {
            issueStatusCard.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerLow));
            issueStatusCard.setContentDescription(count == 0 ? "No issues. Open diagnostics." : count + (count == 1 ? " issue. Open diagnostics." : " issues. Open diagnostics."));
        }
    }
	
	public void showDiagnostics(List<Diagnostic> diagnostics) {
        updateIssueStatus(diagnostics);
		if(diagnosticsBehavior == null) return;
		androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.rv_diagnostics);
		if (rv != null) {
			DiagnosticsAdapter adapter = new DiagnosticsAdapter(diagnostics, diagnostic -> {
				if(diagnostic.fileName.equals(filename)) {
					editor.jumpToLine(diagnostic.line - 1); 
				}
				diagnosticsBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
			});
			rv.setAdapter(adapter);
		}
		TextView title = findViewById(R.id.tv_diagnostic_title);
		if (title != null) title.setText("Issues (" + diagnostics.size() + ")");
		diagnosticsBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
	}
	
	private void setupSearchPanel() {
        searchCard = new MaterialCardView(this);
        searchCard.setCardElevation(dp(12));
        searchCard.setRadius(dp(22));
        searchCard.setStrokeWidth(dp(1));
        searchCard.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        searchCard.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerHigh));
        searchCard.setVisibility(View.GONE);

        RelativeLayout.LayoutParams cardParams = new RelativeLayout.LayoutParams(
                Math.min(dp(328), getResources().getDisplayMetrics().widthPixels - dp(24)),
                Math.min((int) (getResources().getDisplayMetrics().heightPixels * 0.72f), dp(720)));
        cardParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        cardParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        cardParams.setMargins(0, dp(8), dp(8), dp(8));
        searchCard.setLayoutParams(cardParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        scrollView.addView(panel, new ScrollView.LayoutParams(-1, -2));
        searchCard.addView(scrollView);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_mtrl_search);
        searchIcon.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        header.addView(searchIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView panelTitle = new TextView(this);
        panelTitle.setText("Find & Replace");
        panelTitle.setTextSize(17);
        panelTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        panelTitle.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMarginStart(dp(8));
        header.addView(panelTitle, titleParams);
        ImageView closeBtn = new ImageView(this);
        closeBtn.setImageResource(R.drawable.ic_mtrl_close);
        closeBtn.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        closeBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        header.addView(closeBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        closeBtn.setOnClickListener(v -> hideSearchPanel());
        panel.addView(header);

        MaterialButtonToggleGroup modeGroup = new MaterialButtonToggleGroup(this);
        modeGroup.setSingleSelection(true);
        modeGroup.setSelectionRequired(true);
        findTabButton = createSearchModeButton("Find", true);
        replaceTabButton = createSearchModeButton("Replace", true);
        modeGroup.addView(findTabButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        modeGroup.addView(replaceTabButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        modeGroup.check(findTabButton.getId());
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(-1, -2);
        modeParams.topMargin = dp(8);
        modeParams.bottomMargin = dp(10);
        panel.addView(modeGroup, modeParams);
        findTabButton.setOnClickListener(v -> {
            modeGroup.check(findTabButton.getId());
            showReplaceControls = false;
            updateReplaceVisibility();
        });
        replaceTabButton.setOnClickListener(v -> {
            modeGroup.check(replaceTabButton.getId());
            showReplaceControls = true;
            updateReplaceVisibility();
        });

        findEdit = createSearchInput("Find what", "Search in current file...");
        panel.addView(wrapSearchInput(findEdit));

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(2), dp(6), dp(2), dp(6));
        matchCaseCheck = createSearchCheckBox("Match case");
        wholeWordCheck = createSearchCheckBox("Whole word");
        regexCheck = createSearchCheckBox("Regex (regular expression)");
        options.addView(matchCaseCheck);
        options.addView(wholeWordCheck);
        options.addView(regexCheck);
        panel.addView(options);

        LinearLayout scopeRow = new LinearLayout(this);
        scopeRow.setOrientation(LinearLayout.VERTICAL);
        TextView scopeLabel = new TextView(this);
        scopeLabel.setText("Search in");
        scopeLabel.setTextSize(12);
        scopeLabel.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        scopeRow.addView(scopeLabel);
        MaterialCardView scopeCard = new MaterialCardView(this);
        scopeCard.setCardElevation(0);
        scopeCard.setRadius(dp(14));
        scopeCard.setStrokeWidth(dp(1));
        scopeCard.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        scopeCard.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainer));
        LinearLayout scopeInner = new LinearLayout(this);
        scopeInner.setGravity(Gravity.CENTER_VERTICAL);
        scopeInner.setPadding(dp(12), dp(10), dp(12), dp(10));
        ImageView scopeIcon = new ImageView(this);
        scopeIcon.setImageResource(R.drawable.ic_mtrl_file);
        scopeIcon.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        scopeInner.addView(scopeIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView scopeText = new TextView(this);
        scopeText.setText("Current file");
        scopeText.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        scopeText.setTextSize(14);
        LinearLayout.LayoutParams scopeTextParams = new LinearLayout.LayoutParams(0, -2, 1f);
        scopeTextParams.setMarginStart(dp(8));
        scopeInner.addView(scopeText, scopeTextParams);
        ImageView scopeChevron = new ImageView(this);
        scopeChevron.setImageResource(R.drawable.ic_mtrl_arrow_down);
        scopeChevron.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        scopeInner.addView(scopeChevron, new LinearLayout.LayoutParams(dp(20), dp(20)));
        scopeCard.addView(scopeInner);
        LinearLayout.LayoutParams scopeCardParams = new LinearLayout.LayoutParams(-1, -2);
        scopeCardParams.topMargin = dp(4);
        scopeRow.addView(scopeCard, scopeCardParams);
        scopeCard.setOnClickListener(v -> SketchwareUtil.toast("Search scope: current file"));
        LinearLayout.LayoutParams scopeRowParams = new LinearLayout.LayoutParams(-1, -2);
        scopeRowParams.bottomMargin = dp(10);
        panel.addView(scopeRow, scopeRowParams);

        MaterialButton findNext = new MaterialButton(this);
        findNext.setText("Find next");
        findNext.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_search));
        findNext.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        findNext.setOnClickListener(v -> moveToSearchMatch(true));
        panel.addView(findNext, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        prevBtn = createSearchIconButton(R.drawable.ic_mtrl_arrow_up, "Previous match");
        nextBtn = createSearchIconButton(R.drawable.ic_mtrl_arrow_down, "Next match");
        navigation.addView(prevBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        nextParams.setMarginStart(dp(6));
        navigation.addView(nextBtn, nextParams);
        searchCountView = new TextView(this);
        searchCountView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        searchCountView.setText("0 matches");
        searchCountView.setTextSize(12);
        searchCountView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        navigation.addView(searchCountView, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(navigation);
        prevBtn.setOnClickListener(v -> moveToSearchMatch(false));
        nextBtn.setOnClickListener(v -> moveToSearchMatch(true));

        View replaceDivider = new View(this);
        replaceDivider.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(8);
        dividerParams.bottomMargin = dp(10);
        panel.addView(replaceDivider, dividerParams);

        LinearLayout replaceSection = new LinearLayout(this);
        replaceSection.setOrientation(LinearLayout.VERTICAL);
        replaceEdit = createSearchInput("Replace with", "Enter replacement text");
        replaceSection.addView(wrapSearchInput(replaceEdit));
        LinearLayout replaceActions = new LinearLayout(this);
        replaceActions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton replaceOne = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        replaceOne.setText("Replace");
        replaceOne.setOnClickListener(v -> replaceNextSearchMatch());
        MaterialButton replaceAll = new MaterialButton(this);
        replaceAll.setText("Replace all");
        replaceAll.setOnClickListener(v -> replaceAllSearchMatches());
        replaceActions.addView(replaceOne, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams replaceAllParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        replaceAllParams.setMarginStart(dp(8));
        replaceActions.addView(replaceAll, replaceAllParams);
        LinearLayout.LayoutParams replaceActionsParams = new LinearLayout.LayoutParams(-1, -2);
        replaceActionsParams.topMargin = dp(8);
        replaceSection.addView(replaceActions, replaceActionsParams);
        panel.addView(replaceSection);

        TextView resultsLabel = new TextView(this);
        resultsLabel.setText("Results");
        resultsLabel.setTextSize(13);
        resultsLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        resultsLabel.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        LinearLayout.LayoutParams resultsLabelParams = new LinearLayout.LayoutParams(-1, -2);
        resultsLabelParams.topMargin = dp(12);
        resultsLabelParams.bottomMargin = dp(4);
        panel.addView(resultsLabel, resultsLabelParams);
        searchResultsContainer = new LinearLayout(this);
        searchResultsContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(searchResultsContainer, new LinearLayout.LayoutParams(-1, -2));

        RelativeLayout rootView = findViewById(R.id.editor_content_root);
        if (rootView != null) {
            rootView.addView(searchCard);
            searchCard.bringToFront();
        }
        TextWatcher searchWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateFindResults(); }
        };
        findEdit.addTextChangedListener(searchWatcher);
        matchCaseCheck.setOnCheckedChangeListener((button, checked) -> updateFindResults());
        wholeWordCheck.setOnCheckedChangeListener((button, checked) -> updateFindResults());
        regexCheck.setOnCheckedChangeListener((button, checked) -> updateFindResults());
        updateReplaceVisibility();
    }

    private MaterialButton createSearchModeButton(String label, boolean checkable) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setId(View.generateViewId());
        button.setText(label);
        button.setCheckable(checkable);
        button.setTextSize(13);
        return button;
    }

    private EditText createSearchInput(String label, String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(14);
        edit.setHint(hint);
        edit.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        edit.setHintTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        edit.setPadding(dp(12), dp(4), dp(12), dp(4));
        edit.setBackgroundColor(Color.TRANSPARENT);
        edit.setContentDescription(label);
        return edit;
    }

    private View wrapSearchInput(EditText edit) {
        MaterialCardView field = new MaterialCardView(this);
        field.setCardElevation(0);
        field.setRadius(dp(14));
        field.setStrokeWidth(dp(1));
        field.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        field.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainer));
        field.addView(edit, new ViewGroup.LayoutParams(-1, dp(48)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        field.setLayoutParams(params);
        return field;
    }

    private CheckBox createSearchCheckBox(String label) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(label);
        checkBox.setTextSize(13);
        checkBox.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        checkBox.setMinHeight(dp(32));
        return checkBox;
    }

    private ImageView createSearchIconButton(int iconRes, String description) {
        ImageView button = new ImageView(this);
        button.setImageResource(iconRes);
        button.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackgroundResource(android.R.drawable.list_selector_background);
        button.setContentDescription(description);
        return button;
    }

    private void updateReplaceVisibility() {
        if (replaceEdit == null) return;
        // Keep the replacement controls available in Replace mode, as in the IDE panel.
        View parent = replaceEdit.getParent() == null ? null : (View) replaceEdit.getParent().getParent();
        if (parent != null) parent.setVisibility(showReplaceControls ? View.VISIBLE : View.GONE);
    }

    private void hideSearchPanel() {
        try { editor.getSearcher().stopSearch(); } catch (Exception ignored) { }
        searchCard.setVisibility(View.GONE);
        if (findEdit != null) findEdit.setText("");
    }

    private String getSearchExpression() {
        String query = findEdit == null || findEdit.getText() == null ? "" : findEdit.getText().toString();
        if (query.isEmpty()) return "";
        boolean regex = regexCheck != null && regexCheck.isChecked();
        boolean wholeWord = wholeWordCheck != null && wholeWordCheck.isChecked();
        if (!regex) query = Pattern.quote(query);
        if (wholeWord) query = "\\b(?:" + query + ")\\b";
        return query;
    }

    private Pattern buildSearchPattern() {
        String expression = getSearchExpression();
        int flags = Pattern.MULTILINE;
        if (matchCaseCheck == null || !matchCaseCheck.isChecked()) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(expression, flags);
    }

    private void runEditorSearch(boolean focusNext) {
        if (findEdit == null || findEdit.getText().length() == 0) return;
        try {
            String rawQuery = findEdit.getText().toString();
            boolean regexMode = regexCheck != null && regexCheck.isChecked();
            boolean wholeWord = wholeWordCheck != null && wholeWordCheck.isChecked();
            String expression = regexMode || wholeWord ? getSearchExpression() : rawQuery;
            int type = regexMode || wholeWord
                    ? EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                    : EditorSearcher.SearchOptions.TYPE_NORMAL;
            boolean ignoreCase = matchCaseCheck == null || !matchCaseCheck.isChecked();
            editor.getSearcher().search(expression, new EditorSearcher.SearchOptions(type, ignoreCase));
            if (focusNext) editor.getSearcher().gotoNext();
        } catch (Exception e) {
            if (searchCountView != null) searchCountView.setText("Invalid pattern");
        }
    }

    private static final class SearchMatch {
        final int start;
        final int end;

        SearchMatch(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private ArrayList<SearchMatch> findSearchMatches() {
        ArrayList<SearchMatch> matches = new ArrayList<>();
        if (findEdit == null || findEdit.getText() == null || findEdit.getText().length() == 0) return matches;
        String source = editor.getText().toString();
        Matcher matcher = buildSearchPattern().matcher(source);
        while (matcher.find()) {
            // Zero-length regex matches are not useful for editor navigation and can
            // otherwise make bulk replacement unexpectedly insert text everywhere.
            if (matcher.start() == matcher.end()) continue;
            matches.add(new SearchMatch(matcher.start(), matcher.end()));
        }
        return matches;
    }

    private void moveToSearchMatch(boolean forward) {
        if (findEdit == null || findEdit.getText() == null || findEdit.getText().length() == 0) return;
        try {
            // Keep Sora's in-editor search highlighting in sync with the results panel.
            runEditorSearch(false);
            ArrayList<SearchMatch> matches = findSearchMatches();
            if (matches.isEmpty()) {
                if (searchCountView != null) searchCountView.setText("0 matches");
                return;
            }
            int cursor = forward ? editor.getCursor().getRight() : editor.getCursor().getLeft();
            SearchMatch selected = null;
            if (forward) {
                for (SearchMatch match : matches) {
                    if (match.start >= cursor) { selected = match; break; }
                }
                if (selected == null) selected = matches.get(0);
            } else {
                for (int i = matches.size() - 1; i >= 0; i--) {
                    SearchMatch match = matches.get(i);
                    if (match.start < cursor) { selected = match; break; }
                }
                if (selected == null) selected = matches.get(matches.size() - 1);
            }
            selectSearchMatch(selected);
            int matchIndex = matches.indexOf(selected);
            if (searchCountView != null && matchIndex >= 0) {
                searchCountView.setText((matchIndex + 1) + "/" + matches.size() + " matches");
            }
        } catch (Exception error) {
            if (searchCountView != null) searchCountView.setText("Invalid pattern");
        }
    }

    private void selectSearchMatch(SearchMatch match) {
        if (match == null) return;
        Content text = editor.getText();
        io.github.rosemoe.sora.text.CharPosition start = text.getIndexer().getCharPosition(match.start);
        io.github.rosemoe.sora.text.CharPosition end = text.getIndexer().getCharPosition(match.end);
        editor.setSelectionRegion(start.line, start.column, end.line, end.column, SelectionChangeEvent.CAUSE_SEARCH);
    }

    private String expandSearchReplacement(SearchMatch match, String replacement, String source) {
        if (replacement == null) return "";
        if (regexCheck == null || !regexCheck.isChecked()) return replacement;
        Matcher matcher = buildSearchPattern().matcher(source);
        while (matcher.find()) {
            if (matcher.start() != match.start || matcher.end() != match.end) continue;
            StringBuffer expanded = new StringBuffer();
            matcher.appendReplacement(expanded, replacement);
            // appendReplacement includes the original prefix before the match.
            // Strip that prefix so callers get only the inserted replacement.
            return expanded.substring(match.start);
        }
        return replacement;
    }

    private ArrayList<String> expandAllSearchReplacements(List<SearchMatch> matches, String replacement, String source) {
        ArrayList<String> expandedReplacements = new ArrayList<>(matches.size());
        if (regexCheck == null || !regexCheck.isChecked()) {
            for (int i = 0; i < matches.size(); i++) expandedReplacements.add(replacement == null ? "" : replacement);
            return expandedReplacements;
        }
        Matcher matcher = buildSearchPattern().matcher(source);
        StringBuffer output = new StringBuffer();
        int previousMatchEnd = 0;
        int matchIndex = 0;
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) continue;
            if (matchIndex >= matches.size()) break;
            SearchMatch expected = matches.get(matchIndex);
            if (matcher.start() != expected.start || matcher.end() != expected.end) continue;
            int replacementStart = output.length() + matcher.start() - previousMatchEnd;
            matcher.appendReplacement(output, replacement == null ? "" : replacement);
            expandedReplacements.add(output.substring(replacementStart));
            previousMatchEnd = matcher.end();
            matchIndex++;
        }
        if (expandedReplacements.size() != matches.size()) {
            throw new IllegalStateException("Search results changed before replacement completed.");
        }
        return expandedReplacements;
    }

    private void replaceNextSearchMatch() {
        if (findEdit == null || findEdit.getText() == null || findEdit.getText().length() == 0) return;
        try {
            runEditorSearch(false);
            ArrayList<SearchMatch> matches = findSearchMatches();
            if (matches.isEmpty()) {
                SketchwareUtil.toast("No matches to replace");
                return;
            }
            int left = editor.getCursor().getLeft();
            int right = editor.getCursor().getRight();
            SearchMatch target = null;
            for (SearchMatch match : matches) {
                if (match.start == left && match.end == right) { target = match; break; }
            }
            if (target == null) {
                for (SearchMatch match : matches) {
                    if (match.start >= right) { target = match; break; }
                }
                if (target == null) target = matches.get(0);
            }
            String sourceSnapshot = editor.getText().toString();
            String replacement = expandSearchReplacement(target, replaceEdit == null ? "" : replaceEdit.getText().toString(), sourceSnapshot);
            Content text = editor.getText();
            boolean wasLoadingTab = isLoadingTab;
            isLoadingTab = true;
            try {
                text.replace(target.start, target.end, replacement);
            } finally {
                isLoadingTab = wasLoadingTab;
            }
            int caret = Math.min(target.start + replacement.length(), text.length());
            io.github.rosemoe.sora.text.CharPosition pos = text.getIndexer().getCharPosition(caret);
            editor.setSelection(pos.line, pos.column);
            updateMenuState();
            updateFindResults();
            if (filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) triggerRealtimeDiagnostics();
        } catch (Exception error) {
            SketchwareUtil.toastError("Could not replace match: " + error.getMessage());
        }
    }

    private void replaceAllSearchMatches() {
        if (findEdit == null || findEdit.getText() == null || findEdit.getText().length() == 0) return;
        try {
            String sourceSnapshot = editor.getText().toString();
            ArrayList<SearchMatch> matches = findSearchMatches();
            if (matches.isEmpty()) {
                SketchwareUtil.toast("No matches to replace");
                return;
            }
            Content text = editor.getText();
            String replacementText = replaceEdit == null ? "" : replaceEdit.getText().toString();
            ArrayList<String> expandedReplacements = expandAllSearchReplacements(matches, replacementText, sourceSnapshot);
            // Work backwards so earlier document offsets remain valid. Batch the
            // edits so Undo treats the replace-all operation as a single action.
            boolean wasLoadingTab = isLoadingTab;
            isLoadingTab = true;
            try {
                text.beginBatchEdit();
                try {
                    for (int i = matches.size() - 1; i >= 0; i--) {
                        SearchMatch match = matches.get(i);
                        text.replace(match.start, match.end, expandedReplacements.get(i));
                    }
                } finally {
                    text.endBatchEdit();
                }
            } finally {
                isLoadingTab = wasLoadingTab;
            }
            updateMenuState();
            updateFindResults();
            if (filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) triggerRealtimeDiagnostics();
            SketchwareUtil.toast("Replaced " + matches.size() + (matches.size() == 1 ? " occurrence" : " occurrences"));
        } catch (Exception error) {
            SketchwareUtil.toastError("Could not replace matches: " + error.getMessage());
        }
    }

    private void updateFindResults() {
        if (findEdit == null || searchCountView == null) return;
        String query = findEdit.getText() == null ? "" : findEdit.getText().toString();
        if (query.isEmpty()) {
            try { editor.getSearcher().stopSearch(); } catch (Exception ignored) { }
            searchCountView.setText("0 matches");
            if (searchResultsContainer != null) searchResultsContainer.removeAllViews();
            return;
        }
        try {
            runEditorSearch(false);
            Pattern pattern = buildSearchPattern();
            String text = editor.getText().toString();
            Matcher matcher = pattern.matcher(text);
            int count = 0;
            ArrayList<Integer> lines = new ArrayList<>();
            ArrayList<String> snippets = new ArrayList<>();
            while (matcher.find()) {
                if (matcher.start() == matcher.end()) continue;
                count++;
                if (lines.size() < 8) {
                    int line = 0;
                    for (int i = 0; i < matcher.start() && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
                    String[] allLines = text.split("\n", -1);
                    String lineText = line < allLines.length ? allLines[line].trim() : "";
                    lines.add(line);
                    snippets.add(lineText.length() > 76 ? lineText.substring(0, 73) + "..." : lineText);
                }
            }
            searchCountView.setText(count + (count == 1 ? " match" : " matches"));
            if (searchResultsContainer != null) {
                searchResultsContainer.removeAllViews();
                for (int i = 0; i < lines.size(); i++) {
                    final int line = lines.get(i);
                    LinearLayout resultRow = new LinearLayout(this);
                    resultRow.setOrientation(LinearLayout.HORIZONTAL);
                    resultRow.setGravity(Gravity.CENTER_VERTICAL);
                    resultRow.setPadding(dp(6), dp(8), dp(6), dp(8));
                    TextView lineNumber = new TextView(this);
                    lineNumber.setText(String.valueOf(line + 1));
                    lineNumber.setTextSize(12);
                    lineNumber.setGravity(Gravity.CENTER);
                    lineNumber.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
                    lineNumber.setBackgroundResource(R.drawable.bg_circle_surface_variant);
                    resultRow.addView(lineNumber, new LinearLayout.LayoutParams(dp(28), dp(28)));
                    TextView resultText = new TextView(this);
                    resultText.setText(snippets.get(i).isEmpty() ? "(empty line)" : snippets.get(i));
                    resultText.setSingleLine(true);
                    resultText.setEllipsize(TextUtils.TruncateAt.END);
                    resultText.setTextSize(12);
                    resultText.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
                    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
                    textParams.setMarginStart(dp(8));
                    resultRow.addView(resultText, textParams);
                    resultRow.setBackgroundResource(android.R.drawable.list_selector_background);
                    resultRow.setOnClickListener(v -> editor.jumpToLine(line));
                    searchResultsContainer.addView(resultRow);
                }
            }
        } catch (Exception e) {
            searchCountView.setText("Invalid pattern");
            if (searchResultsContainer != null) searchResultsContainer.removeAllViews();
        }
    }

	@Override
	public void onStart() {
		super.onStart();
		IntentFilter filter = new IntentFilter(ProjectBuilder.ACTION_BUILD_DIAGNOSTICS);
		registerReceiver(buildDiagnosticsReceiver, filter, Context.RECEIVER_NOT_EXPORTED); 
	}
	
    @Override
    public void onDestroy() {
        if (selectionSubscription != null) {
            try { selectionSubscription.unsubscribe(); } catch (Throwable ignored) { }
            selectionSubscription = null;
        }
        super.onDestroy();
    }

	@Override
	public void onStop() {
		super.onStop();
		try { unregisterReceiver(buildDiagnosticsReceiver); } catch (Exception ignored){}
		if (diagRunnable != null) diagHandler.removeCallbacks(diagRunnable);
		float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
		prefs.edit().putInt("act_ts", (int) (editor.getTextSizePx() / scaledDensity)).apply();
	}
	
    private MenuItem addOverflowItem(Menu menu, int id, String title, int iconResource) {
        MenuItem item = menu.add(Menu.NONE, id, Menu.NONE, title);
        if (iconResource != 0) item.setIcon(AppCompatResources.getDrawable(this, iconResource));
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return item;
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem undoItem = menu.add(Menu.NONE, 0, Menu.NONE, "Undo");
        undoItem.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_undo));
        undoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        MenuItem redoItem = menu.add(Menu.NONE, 1, Menu.NONE, "Redo");
        redoItem.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_redo));
        redoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        MenuItem saveItem = menu.add(Menu.NONE, 2, Menu.NONE, "Save");
        saveItem.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save));
        saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        addOverflowItem(menu, 12, "Project Explorer", R.drawable.ic_mtrl_folder_open);
        addOverflowItem(menu, 4, "Find & Replace", R.drawable.ic_mtrl_find_replace);
        addOverflowItem(menu, 5, "Format Code", R.drawable.ic_mtrl_formattext);
        addOverflowItem(menu, 6, "Layout Preview", R.drawable.ic_mtrl_preview);
        addOverflowItem(menu, 13, "Quick Layout", R.drawable.ic_mtrl_grid);
        addOverflowItem(menu, 7, "Select Language", R.drawable.ic_mtrl_code);
        addOverflowItem(menu, 8, "Theme", R.drawable.ic_mtrl_palette);
        addOverflowItem(menu, 14, "Editor Settings", R.drawable.ic_mtrl_settings);
        addOverflowItem(menu, 15, "About", R.drawable.ic_mtrl_info);

        MenuItem wrapItem = addOverflowItem(menu, 9, "Word wrap", R.drawable.ic_mtrl_formattext);
        wrapItem.setCheckable(true).setChecked(prefs.getBoolean("act_ww", false));
        MenuItem acItem = addOverflowItem(menu, 10, "Auto complete", R.drawable.ic_mtrl_code);
        acItem.setCheckable(true).setChecked(prefs.getBoolean("act_ac", true));
        MenuItem acspItem = addOverflowItem(menu, 11, "Auto complete symbol pair", R.drawable.ic_mtrl_code);
        acspItem.setCheckable(true).setChecked(prefs.getBoolean("act_acsp", true));
        // AppCompat hides optional menu icons by default; keep them visible so the
        // overflow menu matches the icon-led Sketchware Neo editor design.
        if (menu instanceof androidx.appcompat.view.menu.MenuBuilder) {
            ((androidx.appcompat.view.menu.MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return true;
	}

    private Menu optionsMenu;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        optionsMenu = menu;
        updateMenuState();
        boolean canPreviewLayout = activeTab != null && activeTab.databaseLayout
                && filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xml");
        MenuItem previewItem = menu.findItem(6);
        if (previewItem != null) previewItem.setVisible(canPreviewLayout);
        MenuItem quickLayoutItem = menu.findItem(13);
        if (quickLayoutItem != null) quickLayoutItem.setVisible(canPreviewLayout);
        return super.onPrepareOptionsMenu(menu);
    }

    private void updateMenuState() {
        if (editor != null && activeTab != null) {
            activeTab.workingContent = editor.getText().toString();
            updateTabTitle(activeTab);
        }
        if (optionsMenu == null || editor == null) return;
        MenuItem undoItem = optionsMenu.findItem(0);
        if (undoItem != null) {
            try { undoItem.setEnabled(editor.canUndo()); } catch (Exception ignored) { }
        }
        MenuItem redoItem = optionsMenu.findItem(1);
        if (redoItem != null) {
            try { redoItem.setEnabled(editor.canRedo()); } catch (Exception ignored) { }
        }
        MenuItem saveItem = optionsMenu.findItem(2);
        if (saveItem != null) saveItem.setEnabled(isContentModified());
        boolean canPreviewLayout = activeTab != null && activeTab.databaseLayout
                && filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xml");
        MenuItem previewItem = optionsMenu.findItem(6);
        if (previewItem != null) previewItem.setVisible(canPreviewLayout);
        MenuItem quickLayoutItem = optionsMenu.findItem(13);
        if (quickLayoutItem != null) quickLayoutItem.setVisible(canPreviewLayout);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 0 -> { try { editor.undo(); } catch (Exception ignored) { } return true; }
            case 1 -> { try { editor.redo(); } catch (Exception ignored) { } return true; }
            case 2 -> {
                if (isContentModified()) saveCurrentTab();
                else SketchwareUtil.toast("No changes to save");
                return true;
            }
            case 12 -> {
                openProjectExplorer();
                return true;
            }
            case 4 -> {
                if (searchCard != null) {
                    searchCard.setVisibility(View.VISIBLE);
                    searchCard.bringToFront();
                    updateFindResults();
                    if (findEdit != null) findEdit.requestFocus();
                }
                return true;
            }
            case 5 -> {
                if (filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".java")) {
                    StringBuilder formatted = new StringBuilder();
                    for (String line : editor.getText().toString().split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) formatted.append(trimmed).append("\n");
                    }
                    try { editor.setText(Lx.j(formatted.toString(), true)); SketchwareUtil.toast("Code formatted"); }
                    catch (Exception e) { SketchwareUtil.toastError("Could not format code. Check the syntax first."); }
                } else if (filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
                    String format = SrcCodeEditor.prettifyXml(editor.getText().toString(), 4, getIntent());
                    if (format != null) { editor.setText(format); SketchwareUtil.toast("XML formatted"); }
                    else SketchwareUtil.toastError("Could not format XML. Check the syntax first.");
                } else SketchwareUtil.toast("Formatting is available for Java and XML files.");
                return true;
            }
            case 6 -> {
                if (activeTab == null || !activeTab.databaseLayout) return true;
                Intent intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
                intent.putExtras(getIntent());
                intent.putExtra("xml", editor.getText().toString());
                startActivity(intent);
                return true;
            }
            case 7 -> { showLanguageSelector(); return true; }
            case 13 -> { if (activeTab != null && activeTab.databaseLayout) showGenerateLayoutWithAiDialog(); return true; }
            case 8 -> {
                SrcCodeEditor.showSwitchThemeDialog(this, editor, (dialog, which) -> {
                    SrcCodeEditor.selectTheme(editor, which);
                    prefs.edit().putInt("act_theme", which).apply();
                    dialog.dismiss();
                });
                return true;
            }
            case 9 -> {
                item.setChecked(!item.isChecked());
                editor.setWordwrap(item.isChecked());
                prefs.edit().putBoolean("act_ww", item.isChecked()).apply();
                return true;
            }
            case 10 -> {
                item.setChecked(!item.isChecked());
                editor.getComponent(EditorAutoCompletion.class).setEnabled(item.isChecked());
                prefs.edit().putBoolean("act_ac", item.isChecked()).apply();
                return true;
            }
            case 11 -> {
                item.setChecked(!item.isChecked());
                editor.getProps().symbolPairAutoCompletion = item.isChecked();
                prefs.edit().putBoolean("act_acsp", item.isChecked()).apply();
                return true;
            }
            case 14 -> { showEditorSettingsDialog(); return true; }
            case 15 -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Direct XML Editor")
                        .setMessage("Sketchware Neo\nProject: " + projectName + "\nProject ID: " + sc_id + "\nFile: " + filename)
                        .setPositiveButton("OK", null)
                        .show();
                return true;
            }
            default -> { return super.onOptionsItemSelected(item); }
        }
    }

    private void showEditorSettingsDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView label = new TextView(this);
        int initialSize = prefs.getInt("act_ts", 14);
        label.setText("Font size: " + initialSize + " sp");
        label.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        root.addView(label);
        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(14);
        sizeSeek.setProgress(Math.max(0, Math.min(14, initialSize - 10)));
        root.addView(sizeSeek, new LinearLayout.LayoutParams(-1, dp(44)));
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText("Font size: " + (progress + 10) + " sp");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        new MaterialAlertDialogBuilder(this)
                .setTitle("Editor Settings")
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    int size = sizeSeek.getProgress() + 10;
                    prefs.edit().putInt("act_ts", size).apply();
                    editor.setTextSize(size);
                })
                .show();
    }

	private void toAppCompat() {
		var intent = new Intent(getApplicationContext(), ManageAppCompatActivity.class);
		intent.putExtra("sc_id", sc_id);
		intent.putExtra("file_name", filename);
		startActivity(intent);
	}
	
	private void toLayoutPreview() {
		var intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
		intent.putExtras(getIntent());
		intent.putExtra("xml", editor.getText().toString());
		startActivity(intent);
	}
	
	private void setNote(String note) {
		if (prefs.getInt("note_" + sc_id, 0) < 1 && (note != null && !note.isEmpty())) {
			if(binding.noteCard != null) binding.noteCard.setVisibility(View.VISIBLE);
			if(binding.note != null) binding.note.setText(note);
		} else {
			if(binding.noteCard != null) binding.noteCard.setVisibility(View.GONE);
		}
	}
	
	private void showGenerateLayoutWithAiDialog() {
		int dp24 = (int) (24 * getResources().getDisplayMetrics().density);
		int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
		int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp24, dp24, dp24, dp8);

		TextView title = new TextView(this);
		title.setText("Generate Layout with AI");
		title.setTextSize(20);
		title.setTypeface(null, android.graphics.Typeface.BOLD);
		title.setTextColor(pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurface));
		root.addView(title);

		TextView subtitle = new TextView(this);
		subtitle.setText("Describe what this layout should look like or how it should change. AI will rewrite the XML.");
		subtitle.setTextSize(14);
		subtitle.setTextColor(pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
		LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
		subParams.setMargins(0, dp8, 0, dp16);
		subtitle.setLayoutParams(subParams);
		root.addView(subtitle);

		com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
		card.setCardElevation(0);
		card.setRadius(dp8);
		card.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
		card.setStrokeColor(pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
		card.setCardBackgroundColor(pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant));

		EditText promptInput = new EditText(this);
		promptInput.setHint("e.g. add a button below the title that says Submit");
		promptInput.setBackground(null);
		promptInput.setPadding(dp16, dp16, dp16, dp16);
		promptInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
				| android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		promptInput.setMinLines(4);
		promptInput.setMaxLines(10);
		promptInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
		promptInput.setVerticalScrollBarEnabled(true);
		promptInput.setTextColor(pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurface));
		promptInput.setHintTextColor(pro.sketchware.utility.ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOutline));

		card.addView(promptInput, new ViewGroup.LayoutParams(-1, -2));
		root.addView(card, new LinearLayout.LayoutParams(-1, -2));

		new MaterialAlertDialogBuilder(this)
				.setView(root)
				.setNegativeButton("Cancel", null)
				.setPositiveButton("Generate", (dialog, which) -> {
					String prompt = promptInput.getText() == null ? "" : promptInput.getText().toString().trim();
					if (prompt.isEmpty()) {
						SketchwareUtil.toastError("Describe the layout change first.");
						return;
					}
					runAiLayoutGeneration(prompt);
				})
				.show();
	}

	private void runAiLayoutGeneration(String prompt) {
		if (activeTab == null || !activeTab.databaseLayout) {
			SketchwareUtil.toastError("AI layout generation is only available for Sketchware layout files.");
			return;
		}
		k();
		final EditorTab targetTab = activeTab;
		final String targetXmlName = targetTab.title;
		String currentXml = editor.getText().toString();

		neo.sketchware.ai.LayoutGenTask.generate(this, currentXml, prompt, new neo.sketchware.ai.AiResponseCallback() {
			@Override
			public void onSuccess(String generatedXml) {
				boolean wasActive = activeTab == targetTab;
				String previousXml = targetTab.workingContent;
				boolean saved = applyXmlChanges(targetXmlName, generatedXml);
				if (saved) {
                    targetTab.savedContent = generatedXml;
                    targetTab.workingContent = generatedXml;
                    if (wasActive) {
                        isLoadingTab = true;
                        editor.setText(generatedXml);
                        isLoadingTab = false;
                        beforeContent = generatedXml;
                        content = generatedXml;
                        updateMenuState();
                    }
                    Intent result = new Intent();
                    result.putExtra("saved_file", targetXmlName);
                    result.putExtra("sc_id", sc_id);
                    setResult(RESULT_OK, result);
					invalidateOptionsMenu();
					updateTabTitle(targetTab);
					SketchwareUtil.toast("Layout updated by AI");
				} else if (wasActive && previousXml != null) {
                        isLoadingTab = true;
                        editor.setText(previousXml);
                        isLoadingTab = false;
                    }
				h();
			}

			@Override
			public void onFailure(String errorMessage) {
				h();
				SketchwareUtil.toastError("AI generation failed: " + errorMessage);
			}
		});
	}

    private boolean saveCurrentTab() {
        if (activeTab == null || editor == null) return false;
        boolean saved = saveTab(activeTab, editor.getText().toString());
        if (saved) {
            beforeContent = activeTab.savedContent;
            content = activeTab.savedContent;
            invalidateOptionsMenu();
            updateMenuState();
            SketchwareUtil.toast(activeTab.databaseLayout ? "Saved XML" : "Saved " + activeTab.title);
        }
        return saved;
    }

    private boolean saveTab(EditorTab tab, String text) {
        if (tab == null) return false;
        String value = text == null ? "" : text;
        if (Objects.equals(tab.savedContent, value)) return true;
        if (tab.databaseLayout) {
            if (!applyXmlChanges(tab.title, value)) return false;
        } else {
            if (TextUtils.isEmpty(tab.path)) {
                SketchwareUtil.toastError("This tab does not have a writable file path.");
                return false;
            }
            File destination = new File(tab.path);
            File parent = destination.getParentFile();
            try {
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                    SketchwareUtil.toastError("Could not create the file directory.");
                    return false;
                }
                try (FileOutputStream output = new FileOutputStream(destination, false)) {
                    output.write(value.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                String persisted = FileUtil.readFile(destination.getAbsolutePath());
                if (!Objects.equals(value, persisted)) {
                    SketchwareUtil.toastError("File save could not be verified.");
                    return false;
                }
            } catch (IOException e) {
                SketchwareUtil.toastError("Could not save file: " + e.getMessage());
                return false;
            }
            isEdited = true;
        }
        tab.savedContent = value;
        tab.workingContent = value;
        if (tab == activeTab) {
            beforeContent = value;
            content = value;
        }
        Intent result = new Intent();
        result.putExtra("saved_file", tab.title);
        result.putExtra("sc_id", sc_id);
        setResult(RESULT_OK, result);
        updateTabTitle(tab);
        return true;
    }

    private boolean isTabModified(EditorTab tab) {
        if (tab == null) return false;
        String current = tab == activeTab && editor != null ? editor.getText().toString() : tab.workingContent;
        return !Objects.equals(tab.savedContent, current);
    }

    private boolean hasUnsavedTabs() {
        for (EditorTab tab : openTabs) if (isTabModified(tab)) return true;
        return false;
    }

    private boolean saveAllTabs() {
        for (EditorTab tab : new ArrayList<>(openTabs)) {
            String value = tab == activeTab && editor != null ? editor.getText().toString() : tab.workingContent;
            if (!Objects.equals(tab.savedContent, value) && !saveTab(tab, value)) return false;
        }
        return true;
    }

    private void finishWithResultIfNeeded() {
        finish();
    }

    private boolean applyXmlChanges() {
        return activeTab != null && activeTab.databaseLayout
                && applyXmlChanges(activeTab.title, editor.getText().toString());
    }

	private boolean applyXmlChanges(String targetFilename, String xmlToParse) {
		try {
			ArrayList<ViewBean> oldLayout = jC.a(sc_id).d(targetFilename);
			
			var parser = new ViewBeanParser(xmlToParse, oldLayout);
			parser.setSkipRoot(true);
			ArrayList<ViewBean> parsedLayout = parser.parse();
			
			for (ViewBean bean : parsedLayout) {
				if (bean.convert != null && bean.convert.contains("ConstraintLayout")) {
					bean.type = ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT;
					bean.isCustomWidget = false;
					bean.convert = "androidx.constraintlayout.widget.ConstraintLayout";
				}
			}
			
			if (oldLayout != null) {
				for (ViewBean newBean : parsedLayout) {
					for (ViewBean oldBean : oldLayout) {
						if (newBean.id.equals(oldBean.id)) {
							if (oldBean.type == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT ||
							(oldBean.convert != null && oldBean.convert.contains("ConstraintLayout"))) {
								newBean.type = ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT;
								newBean.convert = "androidx.constraintlayout.widget.ConstraintLayout";
								newBean.isCustomWidget = false;
								newBean.customView = oldBean.customView;
							} else if (newBean.type == 0 || newBean.type == 14) {
								newBean.type = oldBean.type;
								newBean.clearClassInfo();
							}
							break;
						}
					}
				}
			}
			
			for (ViewBean child : parsedLayout) {
				if (!"root".equals(child.parent)) {
					for (ViewBean parent : parsedLayout) {
						if (child.parent.equals(parent.id)) {
							child.parentType = parent.type;
							break;
						}
					}
				}
				child.parentClassInfo = null;
			}
			
			for (ViewBean viewBean : parsedLayout) {
				CircularDependencyDetector detector = new CircularDependencyDetector(parsedLayout, viewBean);
				if (viewBean.parentAttributes != null) {
					for (String attr : viewBean.parentAttributes.keySet()) {
						String targetId = viewBean.parentAttributes.get(attr);
						if (!detector.isLegalAttribute(targetId, attr)) {
							SketchwareUtil.toastError("Circular dependency found in \"" + viewBean.name + "\"\nPlease resolve the issue before saving.");
							return false;
						}
					}
				}
			}
			
			if (activeTab == null || targetFilename.equals(filename)) content = xmlToParse;
			if (!isEdited) isEdited = true;
			
			var root = parser.getRootAttributes();
			rootLayoutManager.set(targetFilename, InjectRootLayoutManager.toRoot(root));
			
			HistoryViewBean bean = new HistoryViewBean();
			bean.actionOverride(parsedLayout, oldLayout);
			
			var cc = cC.c(sc_id);
			if (!cc.c.containsKey(targetFilename)) cc.e(targetFilename);
			
			cc.a(targetFilename);
			cc.a(targetFilename, bean);
			
			jC.a(sc_id).c.put(targetFilename, parsedLayout);
			return true;
			
		} catch (Exception e) {
			String friendlyMsg;
			int line = 1;
			if (e instanceof org.xml.sax.SAXParseException) {
				org.xml.sax.SAXParseException sax = (org.xml.sax.SAXParseException) e;
				line = sax.getLineNumber();
				friendlyMsg = humanizeXmlError(sax);
			} else {
				friendlyMsg = e.getMessage();
			}
			SketchwareUtil.toastError("XML Syntax Error: " + friendlyMsg);
			List<Diagnostic> errors = new ArrayList<>();
			errors.add(new Diagnostic(Diagnostic.Severity.ERROR, targetFilename, line, 0, friendlyMsg));
			showDiagnostics(errors);
			return false;
		}
	}
	
	private boolean isContentModified() {
        return editor != null && !Objects.equals(beforeContent, editor.getText().toString());
	}
}
