package mod.sketchlibx.terminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Sketchware Neo's interactive project terminal. It keeps a real PTY shell, independent
 * sessions per project, a command-entry shortcut for touch devices, and terminal-output tools.
 */
public class NeoTerminalView extends LinearLayout {

    private static final int COLOR_BG = Color.parseColor("#121212");
    private static final int COLOR_SURFACE = Color.parseColor("#1E1E1E");
    private static final int COLOR_SURFACE_HIGH = Color.parseColor("#292929");
    private static final int COLOR_TEXT = Color.parseColor("#E6EAF0");
    private static final int COLOR_ACCENT = Color.parseColor("#4285F4");
    private static final int COLOR_MUTED = Color.parseColor("#9AA4B2");
    private static final int COLOR_OUTLINE = Color.parseColor("#394150");

    private final TerminalView terminalView;
    private final NeoTerminalSessionClient sessionClient;
    private final String scId;
    private final List<String> commandHistory = new ArrayList<>();

    private LinearLayout tabStrip;
    private TerminalSessionManager.Entry activeEntry;
    private boolean initialSessionAttached = false;
    private EditText commandInput;
    private TextView ctrlKeyView;
    private TextView altKeyView;

    public NeoTerminalView(Context context, String scId) {
        this(context, scId, true);
    }

    public NeoTerminalView(Context context, String scId, boolean showSessionStrip) {
        super(context);
        this.scId = scId;

        setOrientation(VERTICAL);
        setBackgroundColor(COLOR_BG);
        setClipChildren(false);
        setClipToPadding(false);

        if (showSessionStrip) {
            addView(buildTabStrip(context));
        }
        addView(buildOutputToolbar(context));

        terminalView = new TerminalView(context, null);
        terminalView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        // A slightly smaller monospace size exposes more columns on narrow phone screens. The
        // Termux TerminalView still owns real terminal-cell wrapping and PTY resize handling.
        terminalView.setTextSize(spToPx(context, 12));
        terminalView.setTypeface(Typeface.MONOSPACE);
        terminalView.setBackgroundColor(COLOR_BG);
        terminalView.setFocusable(true);
        terminalView.setFocusableInTouchMode(true);
        terminalView.setPadding(0, 0, 0, 0);

        sessionClient = new NeoTerminalSessionClient(terminalView, this);
        terminalView.setTerminalViewClient(sessionClient);

        addView(terminalView);
        addView(buildCommandInputRow(context));
        addView(buildExtraKeysRow(context));

        applyImeAwarePadding();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (initialSessionAttached) return;

        if (getWidth() > 0 && getHeight() > 0) {
            initialSessionAttached = true;
            attachInitialSession(getContext());
        } else {
            getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (getWidth() > 0 && getHeight() > 0 && !initialSessionAttached) {
                        initialSessionAttached = true;
                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        attachInitialSession(getContext());
                    }
                }
            });
        }
    }

    private void attachInitialSession(Context context) {
        TerminalSessionManager.Entry entry = TerminalSessionManager.getOrCreateFirst(context, scId, sessionClient);
        switchToSession(entry);
        refreshTabStrip(context);
    }

    private void switchToSession(TerminalSessionManager.Entry entry) {
        if (entry == null) return;
        activeEntry = entry;
        entry.session.updateTerminalSessionClient(sessionClient);
        terminalView.attachSession(entry.session);
        resetModifiers();
        terminalView.requestFocus();
    }

    private View buildTabStrip(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(COLOR_SURFACE);
        bar.setPadding(dp(4), 0, dp(4), 0);
        bar.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        tabStrip = new LinearLayout(context);
        tabStrip.setOrientation(HORIZONTAL);
        tabStrip.setGravity(Gravity.CENTER_VERTICAL);
        tabStrip.setPadding(dp(3), 0, dp(3), 0);
        scroller.addView(tabStrip);
        bar.addView(scroller);

        bar.addView(buildIconButton(context, R.drawable.ic_mtrl_add, "New terminal session", v -> {
            TerminalSessionManager.Entry fresh = TerminalSessionManager.createSession(getContext(), scId, sessionClient);
            switchToSession(fresh);
            refreshTabStrip(context);
        }));
        bar.addView(buildIconButton(context, R.drawable.ic_mtrl_delete, "Close all terminal sessions", v -> confirmKillAllSessions(context)));
        bar.addView(buildIconButton(context, R.drawable.ic_mtrl_more_vertical, "Session options", this::showSessionMenu));
        return bar;
    }

    private View buildOutputToolbar(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(COLOR_BG);
        bar.setPadding(dp(10), dp(2), dp(6), dp(2));
        bar.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        TextView title = new TextView(context);
        title.setText("TERMINAL OUTPUT");
        title.setTextColor(COLOR_MUTED);
        title.setTextSize(10f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(0.04f);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        bar.addView(buildIconButton(context, R.drawable.ic_mtrl_copy, "Copy terminal output", v -> copyTerminalOutput()));
        bar.addView(buildIconButton(context, R.drawable.ic_mtrl_search, "Find in terminal output", v -> showSearchDialog(context)));
        bar.addView(buildIconButton(context, R.drawable.ic_mtrl_more_vertical, "Terminal output options", this::showOutputMenu));
        return bar;
    }

    private void refreshTabStrip(Context context) {
        if (tabStrip == null) return;
        tabStrip.removeAllViews();
        List<TerminalSessionManager.Entry> sessions = TerminalSessionManager.getSessions(scId);
        for (TerminalSessionManager.Entry entry : sessions) {
            tabStrip.addView(buildTab(context, entry));
        }
    }

    private View buildTab(Context context, TerminalSessionManager.Entry entry) {
        boolean selected = entry == activeEntry;
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), 0, dp(4), 0);
        pill.setBackground(pillBackground(selected ? COLOR_ACCENT : COLOR_SURFACE_HIGH));
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        pillParams.rightMargin = dp(5);
        pill.setLayoutParams(pillParams);

        TextView label = new TextView(context);
        label.setText(entry.label + (entry.session.isRunning() ? "" : " · exited"));
        label.setTextColor(selected ? Color.WHITE : COLOR_TEXT);
        label.setTypeface(Typeface.MONOSPACE, selected ? Typeface.BOLD : Typeface.NORMAL);
        label.setTextSize(12f);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setOnClickListener(v -> {
            switchToSession(entry);
            refreshTabStrip(context);
        });
        pill.addView(label);

        ImageButton close = buildSmallTabCloseButton(context, selected);
        close.setOnClickListener(v -> closeSession(context, entry));
        pill.addView(close);
        pill.setContentDescription(entry.label + (selected ? ", active session" : ", switch session"));
        return pill;
    }

    private ImageButton buildSmallTabCloseButton(Context context, boolean selected) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(R.drawable.ic_mtrl_close);
        button.setColorFilter(selected ? Color.WHITE : COLOR_MUTED);
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
        button.setBackground(circleRipple());
        button.setContentDescription("Close session tab");
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(26), dp(26)));
        return button;
    }

    private ImageButton buildIconButton(Context context, int drawableRes, String contentDescription, OnClickListener onClick) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawableRes);
        button.setColorFilter(COLOR_MUTED);
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setContentDescription(contentDescription);
        button.setBackground(circleRipple());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        button.setOnClickListener(onClick);
        return button;
    }

    private RippleDrawable circleRipple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), null, mask);
    }

    private void showSessionMenu(View anchor) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add("Rename active session");
        menu.getMenu().add("Restart active session");
        menu.setOnMenuItemClickListener(item -> {
            if ("Rename active session".contentEquals(item.getTitle())) {
                renameActiveSession();
                return true;
            }
            if ("Restart active session".contentEquals(item.getTitle())) {
                restartActiveSession();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showOutputMenu(View anchor) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add("Clear screen");
        menu.getMenu().add("Command help");
        menu.getMenu().add("Run terminal diagnostics");
        menu.getMenu().add("Recent commands");
        menu.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if ("Clear screen".contentEquals(title)) {
                sendShellCommand("clear");
                return true;
            }
            if ("Command help".contentEquals(title)) {
                sendShellCommand("sw_help");
                return true;
            }
            if ("Run terminal diagnostics".contentEquals(title)) {
                sendShellCommand("swdoctor");
                return true;
            }
            if ("Recent commands".contentEquals(title)) {
                showCommandHistory();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void renameActiveSession() {
        if (activeEntry == null) {
            toast("No active session");
            return;
        }
        EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setText(activeEntry.label);
        input.setSelection(input.length());
        input.setHint("Session name");
        int padding = dp(20);
        LinearLayout container = new LinearLayout(getContext());
        container.setPadding(padding, dp(4), padding, 0);
        container.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Rename session")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("Session name cannot be empty");
                        return;
                    }
                    activeEntry.label = name.length() > 32 ? name.substring(0, 32) : name;
                    refreshTabStrip(getContext());
                })
                .show();
    }

    private void restartActiveSession() {
        if (activeEntry == null) {
            toast("No active session");
            return;
        }
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Restart active session?")
                .setMessage("Any process running in this session will be stopped. Other sessions will remain open.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restart", (dialog, which) -> {
                    String oldId = activeEntry.sessionId;
                    TerminalSessionManager.killSession(scId, oldId);
                    TerminalSessionManager.Entry fresh = TerminalSessionManager.createSession(getContext(), scId, sessionClient);
                    switchToSession(fresh);
                    refreshTabStrip(getContext());
                })
                .show();
    }

    private void closeSession(Context context, TerminalSessionManager.Entry entry) {
        if (entry == null) return;
        if (!entry.session.isRunning()) {
            removeSessionAndSelectNext(context, entry);
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle("Close " + entry.label + "?")
                .setMessage("Any command currently running in this session will be stopped.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close", (dialog, which) -> removeSessionAndSelectNext(context, entry))
                .show();
    }

    private void removeSessionAndSelectNext(Context context, TerminalSessionManager.Entry entry) {
        boolean closingActive = entry == activeEntry;
        TerminalSessionManager.killSession(scId, entry.sessionId);
        if (closingActive) {
            activeEntry = null;
            TerminalSessionManager.Entry next = TerminalSessionManager.getOrCreateFirst(context, scId, sessionClient);
            switchToSession(next);
        }
        refreshTabStrip(context);
    }

    private void confirmKillAllSessions(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle("Close all sessions?")
                .setMessage("Every terminal process for this project will be stopped. A fresh session will open afterward.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close all", (dialog, which) -> {
                    TerminalSessionManager.killAllSessions(scId);
                    TerminalSessionManager.Entry fresh = TerminalSessionManager.createSession(context, scId, sessionClient);
                    switchToSession(fresh);
                    refreshTabStrip(context);
                })
                .show();
    }

    private View buildCommandInputRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(COLOR_SURFACE);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView prompt = new TextView(context);
        prompt.setText("$");
        prompt.setTextColor(COLOR_MUTED);
        prompt.setTextSize(18f);
        prompt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(0, 0, dp(10), 0);
        row.addView(prompt);

        commandInput = new EditText(context);
        commandInput.setSingleLine(true);
        commandInput.setTextSize(14f);
        commandInput.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        commandInput.setTextColor(COLOR_TEXT);
        commandInput.setHintTextColor(COLOR_MUTED);
        commandInput.setHint("Type command...");
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commandInput.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        commandInput.setPadding(dp(12), 0, dp(12), 0);
        commandInput.setBackground(outlinedBackground());
        commandInput.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1f));
        commandInput.setOnEditorActionListener((textView, actionId, event) -> {
            boolean enterPressed = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                submitCommand();
                return true;
            }
            return false;
        });
        row.addView(commandInput);

        TextView run = new TextView(context);
        run.setText("▶ Run");
        run.setTextColor(Color.WHITE);
        run.setTextSize(14f);
        run.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        run.setGravity(Gravity.CENTER);
        run.setPadding(dp(14), 0, dp(14), 0);
        run.setContentDescription("Run command in terminal");
        run.setBackground(pillBackground(COLOR_ACCENT));
        LinearLayout.LayoutParams runParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        runParams.leftMargin = dp(8);
        run.setLayoutParams(runParams);
        run.setClickable(true);
        run.setFocusable(true);
        run.setOnClickListener(v -> submitCommand());
        row.addView(run);
        return row;
    }

    private GradientDrawable outlinedBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_BG);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), COLOR_OUTLINE);
        return drawable;
    }

    private void submitCommand() {
        if (commandInput == null) return;
        String command = commandInput.getText().toString();
        if (command.trim().isEmpty()) return;
        TerminalSession session = activeEntry == null ? null : activeEntry.session;
        if (session == null || !session.isRunning()) {
            toast("This session has ended. Close or restart it first.");
            return;
        }

        // Keep one persistent PTY: cd, environment changes, shell functions and foreground jobs
        // therefore behave like a normal terminal instead of running in a fresh process per tap.
        session.write(command + "\n");
        if (commandHistory.isEmpty() || !command.equals(commandHistory.get(commandHistory.size() - 1))) {
            commandHistory.add(command);
            if (commandHistory.size() > 50) commandHistory.remove(0);
        }
        commandInput.setText("");
        resetModifiers();
    }

    private void showCommandHistory() {
        if (commandHistory.isEmpty()) {
            toast("No commands in this view yet");
            return;
        }
        String[] items = commandHistory.toArray(new String[0]);
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Recent commands")
                .setItems(items, (dialog, which) -> {
                    commandInput.setText(items[which]);
                    commandInput.setSelection(commandInput.length());
                    commandInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void copyTerminalOutput() {
        String output = getTranscriptText();
        if (output.trim().isEmpty()) {
            toast("There is no terminal output to copy");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            toast("Clipboard is unavailable");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal output", output));
        toast("Terminal output copied");
    }

    private void showSearchDialog(Context context) {
        EditText queryInput = new EditText(context);
        queryInput.setSingleLine(true);
        queryInput.setHint("Find text in output");
        queryInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        TextView results = new TextView(context);
        results.setTextColor(COLOR_TEXT);
        results.setTextSize(12f);
        results.setTypeface(Typeface.MONOSPACE);
        results.setText("Enter a term, then tap Find.");
        results.setTextIsSelectable(true);
        results.setPadding(dp(4), dp(8), dp(4), dp(8));

        ScrollView resultScroller = new ScrollView(context);
        resultScroller.setFillViewport(false);
        resultScroller.addView(results);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), dp(4));
        content.addView(queryInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        content.addView(resultScroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Find in terminal output")
                .setView(content)
                .setNegativeButton("Close", null)
                .setPositiveButton("Find", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String query = queryInput.getText().toString();
            if (query.trim().isEmpty()) {
                results.setText("Enter text to search for.");
                return;
            }
            results.setText(searchTranscript(query));
        }));
        dialog.show();
    }

    private String searchTranscript(String query) {
        String output = getTranscriptText();
        if (output.isEmpty()) return "No terminal output is available.";
        String needle = query.toLowerCase(Locale.ROOT);
        String[] lines = output.split("\\n");
        StringBuilder matches = new StringBuilder();
        int count = 0;
        int shown = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase(Locale.ROOT).contains(needle)) {
                count++;
                if (shown < 40) {
                    matches.append(i + 1).append(": ").append(lines[i]).append('\n');
                    shown++;
                }
            }
        }
        if (count == 0) return "No matches found for: " + query;
        String header = count + (count == 1 ? " match found" : " matches found")
                + (count > shown ? " (first " + shown + " shown)" : "") + "\n\n";
        return header + matches;
    }

    private String getTranscriptText() {
        TerminalSession session = activeEntry == null ? null : activeEntry.session;
        if (session == null || session.getEmulator() == null || session.getEmulator().getScreen() == null) return "";
        return session.getEmulator().getScreen().getTranscriptText();
    }

    private View buildExtraKeysRow(Context context) {
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        scroller.setBackgroundColor(COLOR_SURFACE);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(6);
        row.setPadding(pad, pad, pad, pad);

        ctrlKeyView = (TextView) addExtraKey(row, "CTRL", null);
        ctrlKeyView.setOnClickListener(v -> toggleModifier(ctrlKeyView, sessionClient::setCtrlDown));

        altKeyView = (TextView) addExtraKey(row, "ALT", null);
        altKeyView.setOnClickListener(v -> toggleModifier(altKeyView, sessionClient::setAltDown));

        addExtraKey(row, "ESC", () -> writeToSession("\u001b"));
        addExtraKey(row, "TAB", () -> writeToSession("\t"));
        addExtraKey(row, "/", () -> writeToSession("/"));
        addExtraKey(row, "-", () -> writeToSession("-"));
        addExtraKey(row, "|", () -> writeToSession("|"));
        addExtraKey(row, "~", () -> writeToSession("~"));
        addExtraKey(row, "HOME", () -> writeToSession("\u001b[H"));
        addExtraKey(row, "END", () -> writeToSession("\u001b[F"));
        addExtraKey(row, "PGUP", () -> writeToSession("\u001b[5~"));
        addExtraKey(row, "PGDN", () -> writeToSession("\u001b[6~"));
        addExtraKey(row, "↑", () -> writeToSession("\u001b[A"));
        addExtraKey(row, "↓", () -> writeToSession("\u001b[B"));
        addExtraKey(row, "←", () -> writeToSession("\u001b[D"));
        addExtraKey(row, "→", () -> writeToSession("\u001b[C"));
        addExtraKey(row, "⌨", () -> {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.toggleSoftInputFromWindow(terminalView.getWindowToken(), 0, 0);
        });

        scroller.addView(row);
        return scroller;
    }

    private void toggleModifier(TextView keyView, Consumer<Boolean> setter) {
        boolean now = !keyView.isSelected();
        keyView.setSelected(now);
        keyView.setBackground(pillBackground(now ? COLOR_ACCENT : COLOR_SURFACE_HIGH));
        keyView.setTextColor(now ? Color.WHITE : COLOR_TEXT);
        setter.accept(now);
    }

    private View addExtraKey(LinearLayout row, String label, Runnable onClick) {
        Context context = row.getContext();
        TextView key = new TextView(context);
        key.setText(label);
        key.setTextColor(COLOR_TEXT);
        key.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        key.setTextSize(12f);
        key.setGravity(Gravity.CENTER);
        int hPad = dp(12);
        key.setPadding(hPad, 0, hPad, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.rightMargin = dp(6);
        key.setLayoutParams(params);
        key.setBackground(pillBackground(COLOR_SURFACE_HIGH));
        key.setClickable(true);
        key.setFocusable(true);
        if (onClick != null) key.setOnClickListener(v -> onClick.run());
        row.addView(key);
        return key;
    }

    private GradientDrawable pillBackground(int fillColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(10));
        return drawable;
    }

    private void applyImeAwarePadding() {
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int pad = Math.max(0, imeBottom - navBottom);
            v.setPadding(0, 0, 0, pad);
            return insets;
        });
    }

    private void sendShellCommand(String command) {
        TerminalSession session = activeEntry == null ? null : activeEntry.session;
        if (session == null || !session.isRunning()) {
            toast("This session has ended. Restart it before running a command.");
            return;
        }
        session.write(command + "\n");
        if (commandInput != null) commandInput.clearFocus();
    }

    private void writeToSession(String data) {
        TerminalSession session = activeEntry == null ? null : activeEntry.session;
        if (session != null && session.isRunning()) session.write(data);
        resetModifiers();
    }

    private void resetModifiers() {
        if (ctrlKeyView != null && ctrlKeyView.isSelected()) {
            ctrlKeyView.setSelected(false);
            ctrlKeyView.setTextColor(COLOR_TEXT);
            ctrlKeyView.setBackground(pillBackground(COLOR_SURFACE_HIGH));
        }
        if (altKeyView != null && altKeyView.isSelected()) {
            altKeyView.setSelected(false);
            altKeyView.setTextColor(COLOR_TEXT);
            altKeyView.setBackground(pillBackground(COLOR_SURFACE_HIGH));
        }
        sessionClient.setCtrlDown(false);
        sessionClient.setAltDown(false);
    }

    private void toast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return SketchwareUtil.dpToPx((int) value);
    }

    private static int spToPx(Context context, float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }

    public TerminalView getTerminalView() {
        return terminalView;
    }

    public String getScId() {
        return scId;
    }

    /** Called by the session client when a shell exits while its tab is still visible. */
    void onSessionFinished(TerminalSession finishedSession) {
        post(() -> {
            if (activeEntry != null && activeEntry.session == finishedSession) {
                resetModifiers();
            }
            refreshTabStrip(getContext());
        });
    }

    public void destroy() {
        resetModifiers();
    }
}
