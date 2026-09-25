package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;

/** Placement policy for long-running calendar-backed process notifications. */
final class ProcessNotificationMode {
    static final String COMBINED = "combined";
    static final String PER_PROCESS = "per_process";
    static final String GROUPED = "grouped";

    static final String PREFERENCE_KEY = "process_notification_mode_ui";
    private static final String SETTINGS_PREFS = "codex_monitor_settings_v1";

    private ProcessNotificationMode() {
    }

    static String current(Context context) {
        if (context == null) return COMBINED;
        SharedPreferences preferences = context.getSharedPreferences(
                SETTINGS_PREFS, Context.MODE_PRIVATE);
        return normalize(preferences.getString(PREFERENCE_KEY, COMBINED));
    }

    static void set(Context context, String value) {
        if (context == null) return;
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREFERENCE_KEY, normalize(value)).apply();
    }

    static String normalize(String value) {
        if (PER_PROCESS.equals(value)) return PER_PROCESS;
        if (GROUPED.equals(value)) return GROUPED;
        return COMBINED;
    }
}
