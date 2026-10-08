package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;

/** Only rich combined One Card is supported; old stored modes migrate safely. */
final class ProcessNotificationMode {
    static final String COMBINED = "combined";
    static final String PREFERENCE_KEY = "process_notification_mode_ui";
    private static final String SETTINGS_PREFS = "codex_monitor_settings_v1";
    private ProcessNotificationMode() {}

    static String current(Context context) {
        return current(context, AccountContainerStore.selectedId(context));
    }

    static String current(Context context, String containerId) {
        if (context == null) return COMBINED;
        SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        String scoped = scopedKey(containerId);
        if (prefs.contains(scoped) || (AccountContainerStore.isLegacyOwner(context, containerId)
                && prefs.contains(PREFERENCE_KEY))) {
            prefs.edit().remove(scoped).remove(PREFERENCE_KEY).apply();
        }
        return COMBINED;
    }

    static void clearContainer(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return;
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit()
                .remove(scopedKey(containerId)).apply();
    }

    private static String scopedKey(String containerId) {
        return PREFERENCE_KEY + "::" + AccountNotificationNamespace.safe(containerId);
    }

    static String normalize(String ignored) {
        return COMBINED;
    }
}
