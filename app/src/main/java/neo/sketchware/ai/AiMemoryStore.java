package neo.sketchware.ai;

import android.content.Context;
import android.content.SharedPreferences;

/** User-controlled reusable project/agent instructions stored in app-private preferences. */
public final class AiMemoryStore {
    private static final String PREFS = "ai_agent_memory";
    private static final String KEY_REUSABLE_CONTEXT = "reusable_context";
    public static final int MAX_CONTEXT_LENGTH = 8000;

    private AiMemoryStore() {}

    public static String getReusableContext(Context context) {
        if (context == null) return "";
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_REUSABLE_CONTEXT, "");
    }

    public static boolean saveReusableContext(Context context, String value) {
        if (context == null) return false;
        String clean = value == null ? "" : value.trim();
        if (clean.length() > MAX_CONTEXT_LENGTH) return false;
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return preferences.edit().putString(KEY_REUSABLE_CONTEXT, clean).commit();
    }
}
