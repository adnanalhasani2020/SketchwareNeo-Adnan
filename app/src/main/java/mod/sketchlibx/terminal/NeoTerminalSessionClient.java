package mod.sketchlibx.terminal;

import android.graphics.Color;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.ref.WeakReference;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

public class NeoTerminalSessionClient implements TerminalSessionClient, TerminalViewClient {

    private static final String TAG = "NeoTerminal";

    private static final int TERMINAL_BACKGROUND = Color.parseColor("#121212");
    private static final int TERMINAL_FOREGROUND = Color.parseColor("#E0E0E0");
    private static final int TERMINAL_CURSOR = Color.parseColor("#4CAF50"); // green cursor

    private final WeakReference<TerminalView> terminalViewRef;
    private final WeakReference<NeoTerminalView> terminalOwnerRef;
    private boolean ctrlDown = false;
    private boolean altDown = false;

    public NeoTerminalSessionClient(TerminalView terminalView, NeoTerminalView owner) {
        this.terminalViewRef = new WeakReference<>(terminalView);
        this.terminalOwnerRef = new WeakReference<>(owner);
    }

    private TerminalView view() {
        return terminalViewRef.get();
    }

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        TerminalView v = view();
        if (v != null && v.getCurrentSession() == changedSession) {
            v.onScreenUpdated();
        }
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
    }

    @Override
    public void onSessionFinished(TerminalSession finishedSession) {
        TerminalView v = view();
        if (v != null && v.getCurrentSession() == finishedSession) v.onScreenUpdated();
        NeoTerminalView owner = terminalOwnerRef.get();
        if (owner != null) owner.onSessionFinished(finishedSession);
    }

    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {
        TerminalView v = view();
        if (v == null) return;
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) v.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text));
        }
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {
        TerminalView v = view();
        if (v == null) return;
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) v.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        android.content.ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0 || session == null) return;
        CharSequence paste = clip.getItemAt(0).coerceToText(v.getContext());
        if (paste != null) session.write(paste.toString());
    }

    @Override
    public void onBell(TerminalSession session) {

    }

    @Override
    public void onColorsChanged(TerminalSession changedSession) {
        TerminalView v = view();
        if (v != null) v.onScreenUpdated();
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {

    }

    @Override
    public Integer getTerminalCursorStyle() {
        return null; // library default (block cursor)
    }

    @Override
    public void onEmulatorSet() {
        TerminalView v = view();
        TerminalSession session = v != null ? v.getCurrentSession() : null;
        if (session == null || session.getEmulator() == null) return;

        try {
            session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = TERMINAL_BACKGROUND;
            session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = TERMINAL_FOREGROUND;
            session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = TERMINAL_CURSOR;
        } catch (Throwable t) {
            Log.w(TAG, "Could not apply custom color scheme, falling back to library default", t);
        }
        if (v != null) v.onScreenUpdated();
    }


    @Override
    public float onScale(float scale) {
        return 1.0f; // pinch-to-zoom text size disabled; app controls text size explicitly
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        TerminalView v = view();
        if (v != null) {
            v.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) v.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return false; // let system back button
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return true;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return true;
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return false;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    @Override
    public boolean readControlKey() {
        return ctrlDown;
    }

    @Override
    public boolean readAltKey() {
        return altDown;
    }

    @Override
    public boolean readShiftKey() {
        return false;
    }

    @Override
    public boolean readFnKey() {
        return false;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        return false;
    }

    @Override
    public void logError(String tag, String message) {
        Log.e(tag, message);
    }

    @Override
    public void logWarn(String tag, String message) {
        Log.w(tag, message);
    }

    @Override
    public void logInfo(String tag, String message) {
        Log.i(tag, message);
    }

    @Override
    public void logDebug(String tag, String message) {
        Log.d(tag, message);
    }

    @Override
    public void logVerbose(String tag, String message) {
        Log.v(tag, message);
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
        Log.e(tag, message, e);
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
        Log.e(tag, "", e);
    }

    public void setCtrlDown(boolean down) {
        this.ctrlDown = down;
    }

    public void setAltDown(boolean down) {
        this.altDown = down;
    }
}
