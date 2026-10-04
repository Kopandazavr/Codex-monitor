package dev.kopandazavr.codexmonitor;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;

/**
 * One-shot reset reminders controlled directly from visible limit rows.
 * Each account container owns independent bell state and alarms.
 */
public final class NowBarResetReminder {
    static final String ACTION_TOGGLE =
            "dev.kopandazavr.codexmonitor.action.NOW_BAR_RESET_REMINDER_TOGGLE";
    static final String ACTION_FIRE =
            "dev.kopandazavr.codexmonitor.action.NOW_BAR_RESET_REMINDER_FIRE";
    static final String EXTRA_CONTAINER_ID = OAuthService.EXTRA_CONTAINER_ID;
    static final String EXTRA_METRIC = "metric";
    static final String EXTRA_RESET_AT = "reset_at";
    static final String EXTRA_WINDOW_SECONDS = "window_seconds";

    private static final String PREFS = "codex_monitor_now_bar_reset_reminder_v1";
    private static final String KEY_ARMED = "armed";
    private static final String KEY_METRIC = "metric";
    private static final String KEY_RESET_AT = "reset_at";
    private static final String KEY_WINDOW_SECONDS = "window_seconds";
    private static final String KEY_PER_METRIC_MIGRATED = "per_metric_migrated_v2";
    private static final int REQUEST_TOGGLE_BASE = 8621;
    private static final int REQUEST_FIRE_BASE = 8630;
    private static final long DELIVERY_GRACE_MS = 3000L;
    private static final String[] RESTORABLE_METRICS = {"five_hour", "weekly", "monthly"};

    private NowBarResetReminder() {
    }

    static Notification.Action buildAction(Context context, String metric, UsageWindow window,
            long observedAtMillis) {
        return buildAction(context, AccountContainerStore.selectedId(context),
                metric, window, observedAtMillis);
    }

    static Notification.Action buildAction(Context context, String containerId, String metric,
            UsageWindow window, long observedAtMillis) {
        PendingIntent pending = toggleIntent(
                context, containerId, metric, window, observedAtMillis);
        String normalizedMetric = normalizeMetric(metric);
        if (pending == null || normalizedMetric == null || window == null) return null;
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        boolean armed = isArmedFor(context, containerId, normalizedMetric,
                resetAt, window.windowSeconds);
        Icon icon = Icon.createWithResource(context,
                armed ? R.drawable.ic_bell_on : R.drawable.ic_bell_off);
        return new Notification.Action.Builder(icon,
                armed ? "Reset alert on" : "Notify on reset", pending).build();
    }

    static PendingIntent toggleIntent(Context context, String metric, UsageWindow window,
            long observedAtMillis) {
        return toggleIntent(context, AccountContainerStore.selectedId(context),
                metric, window, observedAtMillis);
    }

    static PendingIntent toggleIntent(Context context, String containerId, String metric,
            UsageWindow window, long observedAtMillis) {
        if (context == null || window == null) return null;
        migrateLegacyState(context, containerId);
        String normalizedMetric = normalizeMetric(metric);
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        if (normalizedMetric == null || resetAt <= System.currentTimeMillis()) return null;
        long windowSeconds = Math.max(0L, window.windowSeconds);

        if (isArmedFor(context, containerId, normalizedMetric, resetAt, windowSeconds)) {
            SharedPreferences preferences = state(context);
            long storedResetAt = preferences.getLong(
                    key(containerId, keyResetAt(normalizedMetric)), 0L);
            if (storedResetAt != resetAt) {
                armInternal(context, containerId, normalizedMetric, resetAt, windowSeconds);
            }
        }

        Intent toggle = new Intent(context, NowBarActionReceiver.class)
                .setAction(ACTION_TOGGLE)
                .putExtra(EXTRA_CONTAINER_ID, containerId)
                .putExtra(EXTRA_METRIC, normalizedMetric)
                .putExtra(EXTRA_RESET_AT, resetAt)
                .putExtra(EXTRA_WINDOW_SECONDS, windowSeconds);
        return PendingIntent.getBroadcast(context,
                requestToggle(containerId, normalizedMetric), toggle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void toggleFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String containerId = containerFromIntent(context, intent);
        migrateLegacyState(context, containerId);
        String metric = normalizeMetric(intent.getStringExtra(EXTRA_METRIC));
        long resetAt = intent.getLongExtra(EXTRA_RESET_AT, 0L);
        long windowSeconds = Math.max(0L, intent.getLongExtra(EXTRA_WINDOW_SECONDS, 0L));
        if (metric == null || resetAt <= System.currentTimeMillis()) return;

        boolean enabled;
        if (isArmedFor(context, containerId, metric, resetAt, windowSeconds)) {
            disarm(context, containerId, metric);
            enabled = false;
        } else {
            armInternal(context, containerId, metric, resetAt, windowSeconds);
            enabled = true;
        }
        DiagnosticLog.info(context, "notification", "limit_reset_bell_toggled",
                "container_id", containerId,
                "metric", metric,
                "enabled", enabled,
                "reset_at", resetAt);
        repost(context, containerId);
    }

    static void fireFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String containerId = containerFromIntent(context, intent);
        migrateLegacyState(context, containerId);
        String metric = normalizeMetric(intent.getStringExtra(EXTRA_METRIC));
        long resetAt = intent.getLongExtra(EXTRA_RESET_AT, 0L);
        long windowSeconds = Math.max(0L, intent.getLongExtra(EXTRA_WINDOW_SECONDS, 0L));
        if (metric == null) return;

        SharedPreferences preferences = state(context);
        if (!preferences.getBoolean(key(containerId, keyArmed(metric)), false)) return;
        if (preferences.getLong(key(containerId, keyResetAt(metric)), 0L) != resetAt
                || preferences.getLong(key(containerId, keyWindowSeconds(metric)), 0L)
                        != windowSeconds) {
            return;
        }

        clearMetricState(context, containerId, metric);
        if (!SecureTokenStore.isSignedIn(context, containerId)) return;
        playResetSound(context, metric);
        DiagnosticLog.info(context, "notification", "limit_reset_alert_fired",
                "container_id", containerId,
                "metric", metric,
                "reset_at", resetAt);
        repost(context, containerId);
        RefreshScheduler.scheduleImmediate(context);
        WidgetRenderer.updateAll(context);
    }

    public static void restore(Context context) {
        if (context == null) return;
        for (AccountContainerStore.Account account : AccountContainerStore.all(context)) {
            restore(context, account.id);
        }
    }

    private static void restore(Context context, String containerId) {
        migrateLegacyState(context, containerId);
        if (!SecureTokenStore.isSignedIn(context, containerId)) {
            for (String metric : RESTORABLE_METRICS) disarm(context, containerId, metric);
            return;
        }
        long now = System.currentTimeMillis();
        SharedPreferences preferences = state(context);
        for (String metric : RESTORABLE_METRICS) {
            if (!preferences.getBoolean(key(containerId, keyArmed(metric)), false)) continue;
            long resetAt = preferences.getLong(key(containerId, keyResetAt(metric)), 0L);
            long windowSeconds = Math.max(0L, preferences.getLong(
                    key(containerId, keyWindowSeconds(metric)), 0L));
            if (resetAt <= 0L) {
                disarm(context, containerId, metric);
            } else if (resetAt <= now) {
                clearMetricState(context, containerId, metric);
                playResetSound(context, metric);
                DiagnosticLog.info(context, "notification",
                        "limit_reset_alert_restored_late",
                        "container_id", containerId,
                        "metric", metric,
                        "reset_at", resetAt);
                repost(context, containerId);
                RefreshScheduler.scheduleImmediate(context);
                WidgetRenderer.updateAll(context);
            } else {
                schedule(context, containerId, metric, resetAt, windowSeconds);
            }
        }
    }

    static boolean isArmedFor(Context context, String metric, long resetAt, long windowSeconds) {
        return isArmedFor(context, AccountContainerStore.selectedId(context),
                metric, resetAt, windowSeconds);
    }

    static boolean isArmedFor(Context context, String containerId, String metric,
            long resetAt, long windowSeconds) {
        if (context == null || metric == null || resetAt <= 0L) return false;
        migrateLegacyState(context, containerId);
        String normalizedMetric = normalizeMetric(metric);
        if (normalizedMetric == null) return false;
        SharedPreferences preferences = state(context);
        if (!preferences.getBoolean(key(containerId, keyArmed(normalizedMetric)), false)) {
            return false;
        }
        long storedResetAt = preferences.getLong(
                key(containerId, keyResetAt(normalizedMetric)), 0L);
        long storedWindowSeconds = preferences.getLong(
                key(containerId, keyWindowSeconds(normalizedMetric)), 0L);
        if (storedResetAt <= 0L) return false;
        if (storedWindowSeconds > 0L && windowSeconds > 0L) {
            return UsageWindow.sameResetWindow(storedResetAt, storedWindowSeconds,
                    resetAt, windowSeconds);
        }
        return Math.abs(storedResetAt - resetAt) < 60_000L;
    }

    static boolean isArmed(Context context, String metric) {
        return isArmed(context, AccountContainerStore.selectedId(context), metric);
    }

    static boolean isArmed(Context context, String containerId, String metric) {
        String normalized = normalizeMetric(metric);
        return context != null && normalized != null
                && state(context).getBoolean(
                        key(containerId, keyArmed(normalized)), false);
    }

    static void clearContainer(Context context, String containerId) {
        if (context == null) return;
        for (String metric : RESTORABLE_METRICS) {
            cancelAlarm(context, containerId, metric);
        }
        SharedPreferences.Editor editor = state(context).edit();
        for (String metric : RESTORABLE_METRICS) {
            editor.remove(key(containerId, keyArmed(metric)))
                    .remove(key(containerId, keyResetAt(metric)))
                    .remove(key(containerId, keyWindowSeconds(metric)));
        }
        editor.remove(key(containerId, KEY_PER_METRIC_MIGRATED)).apply();
    }

    private static void armInternal(Context context, String containerId, String metric,
            long resetAt, long windowSeconds) {
        cancelAlarm(context, containerId, metric);
        state(context).edit()
                .putBoolean(key(containerId, keyArmed(metric)), true)
                .putLong(key(containerId, keyResetAt(metric)), resetAt)
                .putLong(key(containerId, keyWindowSeconds(metric)),
                        Math.max(0L, windowSeconds))
                .apply();
        AlertSoundManager.ensureChannels(context);
        schedule(context, containerId, metric, resetAt, windowSeconds);
    }

    private static void disarm(Context context, String containerId, String metric) {
        cancelAlarm(context, containerId, metric);
        clearMetricState(context, containerId, metric);
    }

    private static void clearMetricState(Context context, String containerId, String metric) {
        state(context).edit()
                .remove(key(containerId, keyArmed(metric)))
                .remove(key(containerId, keyResetAt(metric)))
                .remove(key(containerId, keyWindowSeconds(metric)))
                .apply();
    }

    private static void schedule(Context context, String containerId, String metric,
            long resetAt, long windowSeconds) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null || resetAt <= System.currentTimeMillis()) return;
        PendingIntent pending = fireIntent(context, containerId, metric, resetAt, windowSeconds);
        long when = resetAt + DELIVERY_GRACE_MS;
        try {
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            }
        } catch (SecurityException exception) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        }
        DiagnosticLog.info(context, "notification", "limit_reset_alarm_scheduled",
                "container_id", containerId,
                "metric", metric,
                "reset_at", resetAt,
                "delivery_at", when);
    }

    private static void cancelAlarm(Context context, String containerId, String metric) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, NowBarActionReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_CONTAINER_ID, containerId);
        PendingIntent pending = PendingIntent.getBroadcast(context,
                requestFire(containerId, metric), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            alarms.cancel(pending);
            pending.cancel();
        }
    }

    private static PendingIntent fireIntent(Context context, String containerId,
            String metric, long resetAt, long windowSeconds) {
        Intent fire = new Intent(context, NowBarActionReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_CONTAINER_ID, containerId)
                .putExtra(EXTRA_METRIC, metric)
                .putExtra(EXTRA_RESET_AT, resetAt)
                .putExtra(EXTRA_WINDOW_SECONDS, Math.max(0L, windowSeconds));
        return PendingIntent.getBroadcast(context,
                requestFire(containerId, metric), fire,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void migrateLegacyState(Context context, String containerId) {
        SharedPreferences preferences = state(context);
        String migratedKey = key(containerId, KEY_PER_METRIC_MIGRATED);
        if (preferences.getBoolean(migratedKey, false)) return;

        SharedPreferences.Editor edit = preferences.edit();
        if (AccountContainerStore.isLegacyOwner(context, containerId)
                && preferences.getBoolean(KEY_ARMED, false)) {
            String metric = normalizeMetric(preferences.getString(KEY_METRIC, null));
            long resetAt = preferences.getLong(KEY_RESET_AT, 0L);
            long windowSeconds = Math.max(0L,
                    preferences.getLong(KEY_WINDOW_SECONDS, 0L));
            if (metric != null && resetAt > 0L) {
                edit.putBoolean(key(containerId, keyArmed(metric)), true)
                        .putLong(key(containerId, keyResetAt(metric)), resetAt)
                        .putLong(key(containerId, keyWindowSeconds(metric)), windowSeconds);
            }
        }
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            edit.remove(KEY_ARMED)
                    .remove(KEY_METRIC)
                    .remove(KEY_RESET_AT)
                    .remove(KEY_WINDOW_SECONDS);
        }
        edit.putBoolean(migratedKey, true).apply();
    }

    private static void repost(Context context, String containerId) {
        if (containerId.equals(AccountContainerStore.selectedId(context))) {
            NowBarManager.repostActive(context);
        }
        DualUsageNotificationManager.repostFromCache(context, containerId);
    }

    private static String containerFromIntent(Context context, Intent intent) {
        String requested = intent == null ? "" : intent.getStringExtra(EXTRA_CONTAINER_ID);
        if (requested != null) requested = requested.trim();
        return requested != null && !requested.isEmpty()
                && AccountContainerStore.find(context, requested) != null
                ? requested : AccountContainerStore.selectedId(context);
    }

    private static void playResetSound(Context context, String metric) {
        boolean played = AlertSoundManager.playLimitsReset(context);
        DiagnosticLog.info(context, "now_bar", "reset_sound_played",
                "metric", metric == null ? "" : metric,
                "played", played);
    }

    private static int requestToggle(String containerId, String metric) {
        return AccountNotificationNamespace.requestCode(
                containerId, "reset_toggle_" + metric + "_" + REQUEST_TOGGLE_BASE);
    }

    private static int requestFire(String containerId, String metric) {
        return AccountNotificationNamespace.requestCode(
                containerId, "reset_fire_" + metric + "_" + REQUEST_FIRE_BASE);
    }

    private static String key(String containerId, String base) {
        return base + "::" + AccountNotificationNamespace.safe(containerId);
    }

    private static String keyArmed(String metric) {
        return "armed_" + metric;
    }

    private static String keyResetAt(String metric) {
        return "reset_at_" + metric;
    }

    private static String keyWindowSeconds(String metric) {
        return "window_seconds_" + metric;
    }

    private static String normalizeMetric(String metric) {
        if ("five_hour".equals(metric) || "weekly".equals(metric) || "monthly".equals(metric)) {
            return metric;
        }
        return null;
    }

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
