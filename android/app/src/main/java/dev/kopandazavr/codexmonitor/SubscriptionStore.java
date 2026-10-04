package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;

/** Cached ChatGPT subscription metadata isolated by account container. */
final class SubscriptionStore {
    private static final String PREFS = "codex_monitor_subscription_v1";
    private static final String KEY_PLAN = "plan_type";
    private static final String KEY_ACTIVE_UNTIL = "active_until";
    private static final String KEY_WILL_RENEW = "will_renew";
    private static final String KEY_HAS_WILL_RENEW = "has_will_renew";
    private static final String KEY_FETCHED_AT = "fetched_at";
    private static final String KEY_LAST_ATTEMPT = "last_attempt";

    private SubscriptionStore() {
    }

    static SubscriptionInfo load(Context context) {
        return load(context, AccountContainerStore.selectedId(context));
    }

    static SubscriptionInfo load(Context context, String containerId) {
        SharedPreferences prefs = prefs(context);
        long storedUntil = longValue(context, containerId, KEY_ACTIVE_UNTIL, 0L);
        long visibleUntil = storedUntil > 0L && storedUntil <= System.currentTimeMillis()
                ? 0L : storedUntil;
        SubscriptionInfo info = new SubscriptionInfo(
                stringValue(context, containerId, KEY_PLAN, ""),
                visibleUntil,
                boolValue(context, containerId, KEY_WILL_RENEW, false),
                boolValue(context, containerId, KEY_HAS_WILL_RENEW, false),
                longValue(context, containerId, KEY_FETCHED_AT, 0L));
        return info.hasDisplayableData() ? info : null;
    }

    static void save(Context context, SubscriptionInfo info) {
        save(context, AccountContainerStore.selectedId(context), info);
    }

    static void save(Context context, String containerId, SubscriptionInfo info) {
        if (context == null || info == null || !info.hasDisplayableData()) return;
        prefs(context).edit()
                .putString(key(containerId, KEY_PLAN), info.planType)
                .putLong(key(containerId, KEY_ACTIVE_UNTIL), info.activeUntilMillis)
                .putBoolean(key(containerId, KEY_WILL_RENEW), info.willRenew)
                .putBoolean(key(containerId, KEY_HAS_WILL_RENEW), info.hasWillRenew)
                .putLong(key(containerId, KEY_FETCHED_AT), info.fetchedAtMillis)
                .apply();
    }

    static void seedFromJwt(Context context, AuthTokens tokens, long now) {
        seedFromJwt(context, AccountContainerStore.selectedId(context), tokens, now);
    }

    static void seedFromJwt(Context context, String containerId, AuthTokens tokens, long now) {
        SubscriptionInfo jwt = SubscriptionInfo.fromJwt(tokens, now);
        if (jwt == null) return;
        SubscriptionInfo cached = load(context, containerId);
        if (cached == null) {
            save(context, containerId, jwt);
            return;
        }
        String plan = cached.planType.isEmpty() ? jwt.planType : cached.planType;
        long until = cached.activeUntilMillis > 0L
                ? cached.activeUntilMillis : jwt.activeUntilMillis;
        save(context, containerId, new SubscriptionInfo(plan, until, cached.willRenew,
                cached.hasWillRenew, Math.max(cached.fetchedAtMillis, jwt.fetchedAtMillis)));
    }

    static long storedActiveUntilMillis(Context context) {
        return storedActiveUntilMillis(context, AccountContainerStore.selectedId(context));
    }

    static long storedActiveUntilMillis(Context context, String containerId) {
        return longValue(context, containerId, KEY_ACTIVE_UNTIL, 0L);
    }

    static long lastAttemptMillis(Context context) {
        return lastAttemptMillis(context, AccountContainerStore.selectedId(context));
    }

    static long lastAttemptMillis(Context context, String containerId) {
        return longValue(context, containerId, KEY_LAST_ATTEMPT, 0L);
    }

    static void markAttempt(Context context, long now) {
        markAttempt(context, AccountContainerStore.selectedId(context), now);
    }

    static void markAttempt(Context context, String containerId, long now) {
        prefs(context).edit().putLong(key(containerId, KEY_LAST_ATTEMPT), now).apply();
    }

    static void clear(Context context, String containerId) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String base : new String[]{KEY_PLAN, KEY_ACTIVE_UNTIL, KEY_WILL_RENEW,
                KEY_HAS_WILL_RENEW, KEY_FETCHED_AT, KEY_LAST_ATTEMPT}) {
            editor.remove(key(containerId, base));
            if (AccountContainerStore.isLegacyOwner(context, containerId)) editor.remove(base);
        }
        editor.apply();
    }

    private static String stringValue(Context context, String containerId,
            String base, String fallback) {
        SharedPreferences prefs = prefs(context);
        String key = key(containerId, base);
        if (prefs.contains(key)) return prefs.getString(key, fallback);
        return AccountContainerStore.isLegacyOwner(context, containerId)
                ? prefs.getString(base, fallback) : fallback;
    }

    private static long longValue(Context context, String containerId,
            String base, long fallback) {
        SharedPreferences prefs = prefs(context);
        String key = key(containerId, base);
        if (prefs.contains(key)) return prefs.getLong(key, fallback);
        return AccountContainerStore.isLegacyOwner(context, containerId)
                ? prefs.getLong(base, fallback) : fallback;
    }

    private static boolean boolValue(Context context, String containerId,
            String base, boolean fallback) {
        SharedPreferences prefs = prefs(context);
        String key = key(containerId, base);
        if (prefs.contains(key)) return prefs.getBoolean(key, fallback);
        return AccountContainerStore.isLegacyOwner(context, containerId)
                ? prefs.getBoolean(base, fallback) : fallback;
    }

    private static String key(String containerId, String base) {
        String id = containerId == null ? "" : containerId.trim();
        return base + "::" + id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
