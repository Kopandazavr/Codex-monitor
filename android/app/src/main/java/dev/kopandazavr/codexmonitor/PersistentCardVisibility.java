package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;

/** Independent, durable One Card visibility keyed by stable account-container identity. */
final class PersistentCardVisibility {
    private static final String PREFS = "codex_persistent_card_visibility_v1";
    private static final String KEY_PREFIX = "shown:";

    private PersistentCardVisibility() {}

    static boolean isShown(Context context, String containerId) {
        if (context == null || AccountContainerStore.find(context, containerId) == null) {
            return false;
        }
        return prefs(context).getBoolean(KEY_PREFIX + containerId, true);
    }

    static boolean setShown(Context context, String containerId, boolean shown) {
        if (context == null || AccountContainerStore.find(context, containerId) == null) {
            return false;
        }
        return PersistentCardCoordinator.changeVisibility(context, containerId, shown);
    }

    static void clear(Context context, String containerId) {
        if (context == null || containerId == null) return;
        prefs(context).edit().remove(KEY_PREFIX + containerId).apply();
    }

    static boolean store(Context context, String containerId, boolean shown) {
        return prefs(context).edit().putBoolean(KEY_PREFIX + containerId, shown).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
