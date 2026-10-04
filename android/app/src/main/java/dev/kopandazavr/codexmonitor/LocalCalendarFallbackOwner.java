package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;

/** Device-global Local Calendar Provider fallback ownership. */
final class LocalCalendarFallbackOwner {
    private static final String PREFS = "codex_local_calendar_fallback_owner_v1";
    private static final String KEY_OWNER_ID = "owner_id";

    private LocalCalendarFallbackOwner() {
    }

    static synchronized void ensureInitialized(Context context) {
        if (context == null) return;
        AccountContainerStore.ensureInitialized(context);
        SharedPreferences prefs = prefs(context);
        String owner = clean(prefs.getString(KEY_OWNER_ID, ""));
        if (AccountContainerStore.find(context, owner) != null) return;
        AccountContainerStore.Account selected = AccountContainerStore.selected(context);
        if (selected != null) prefs.edit().putString(KEY_OWNER_ID, selected.id).commit();
    }

    static synchronized String ownerId(Context context) {
        ensureInitialized(context);
        return clean(prefs(context).getString(KEY_OWNER_ID, ""));
    }

    static synchronized boolean isOwner(Context context, String containerId) {
        return clean(containerId).equals(ownerId(context));
    }

    static synchronized AccountContainerStore.Account owner(Context context) {
        return AccountContainerStore.find(context, ownerId(context));
    }

    static synchronized boolean transfer(Context context, String containerId) {
        AccountContainerStore.Account target = AccountContainerStore.find(context, containerId);
        if (target == null) return false;
        return prefs(context).edit().putString(KEY_OWNER_ID, target.id).commit();
    }

    static synchronized void onContainerRemoved(Context context, String removedId) {
        if (!clean(removedId).equals(ownerId(context))) return;
        AccountContainerStore.Account selected = AccountContainerStore.selected(context);
        if (selected != null) {
            prefs(context).edit().putString(KEY_OWNER_ID, selected.id).commit();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
