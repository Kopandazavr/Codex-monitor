package dev.bennett.codexmeter;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/**
 * One-shot reset reminders controlled directly from the visible limit rows.
 *
 * <p>5-hour and long-window reminders are independent. Each uses the known reset timestamp and an
 * AlarmManager wakeup, so sound delivery is not coupled to notification repaint or remote polling.
 * Firing never creates another persistent notification card.</p>
 */
public final class NowBarResetReminder {
    static final String ACTION_TOGGLE =
            "dev.bennett.codexmeter.action.NOW_BAR_RESET_REMINDER_TOGGLE";
    static final String ACTION_FIRE =
            "dev.bennett.codexmeter.action.NOW_BAR_RESET_REMINDER_FIRE";
    static final String EXTRA_METRIC = "metric";
    static final String EXTRA_RESET_AT = "reset_at";
    static final String EXTRA_WINDOW_SECONDS = "window_seconds";

    private static final String PREFS = "codex_meter_now_bar_reset_reminder_v1";
    // Legacy singleton keys retained only for one-time migration from pre-2.10 builds.
    private static final String KEY_ARMED = "armed";
    private static final String KEY_METRIC = "metric";
    private static final String KEY_RESET_AT = "reset_at";
    private static final String KEY_WINDOW_SECONDS = "window_seconds";
    private static final String KEY_PER_METRIC_MIGRATED = "per_metric_migrated_v2";
    private static final String CHANNEL_NOTIFY = "codex_reset_notify";
    private static final String CHANNEL_ALARM = "codex_reset_alarm";
    private static final String CHANNEL_SILENT = "codex_reset_silent";
    private static final int REQUEST_TOGGLE_BASE = 8621;
    private static final int REQUEST_FIRE_BASE = 8630;
    private static final long DELIVERY_GRACE_MS = 3000L;
    private static final String[] RESTORABLE_METRICS = {"five_hour", "weekly", "monthly"};

    private NowBarResetReminder() {
    }

    /** Compatibility action used by any legacy surface that still presents a notification action. */
    static Notification.Action buildAction(Context context, String metric, UsageWindow window,
            long observedAtMillis) {
        PendingIntent pending = toggleIntent(context, metric, window, observedAtMillis);
        String normalizedMetric = normalizeMetric(metric);
        if (pending == null || normalizedMetric == null || window == null) return null;
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        boolean armed = isArmedFor(context, normalizedMetric, resetAt, window.windowSeconds);
        Icon icon = Icon.createWithResource(context,
                armed ? R.drawable.ic_bell_on : R.drawable.ic_bell_off);
        return new Notification.Action.Builder(icon,
                armed ? "Reset alert on" : "Notify on reset", pending).build();
    }

    /** PendingIntent for the inline bell attached to one specific visible limit row. */
    static PendingIntent toggleIntent(Context context, String metric, UsageWindow window,
            long observedAtMillis) {
        if (context == null || window == null) return null;
        migrateLegacyState(context);
        String normalizedMetric = normalizeMetric(metric);
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        if (normalizedMetric == null || resetAt <= System.currentTimeMillis()) return null;
        long windowSeconds = Math.max(0L, window.windowSeconds);

        // The backend can refine the timestamp within the same logical reset window. Keep the
        // armed bell attached to that window and move its alarm to the fresher timestamp.
        if (isArmedFor(context, normalizedMetric, resetAt, windowSeconds)) {
            SharedPreferences preferences = state(context);
            long storedResetAt = preferences.getLong(keyResetAt(normalizedMetric), 0L);
            if (storedResetAt != resetAt) {
                armInternal(context, normalizedMetric, resetAt, windowSeconds);
            }
        }

        Intent toggle = new Intent(context, NowBarActionReceiver.class)
                .setAction(ACTION_TOGGLE)
                .putExtra(EXTRA_METRIC, normalizedMetric)
                .putExtra(EXTRA_RESET_AT, resetAt)
                .putExtra(EXTRA_WINDOW_SECONDS, windowSeconds);
        return PendingIntent.getBroadcast(context, requestToggle(normalizedMetric), toggle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void toggleFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        migrateLegacyState(context);
        String metric = normalizeMetric(intent.getStringExtra(EXTRA_METRIC));
        long resetAt = intent.getLongExtra(EXTRA_RESET_AT, 0L);
        long windowSeconds = Math.max(0L, intent.getLongExtra(EXTRA_WINDOW_SECONDS, 0L));
        if (metric == null || resetAt <= System.currentTimeMillis()) return;

        boolean enabled;
        if (isArmedFor(context, metric, resetAt, windowSeconds)) {
            disarm(context, metric);
            enabled = false;
        } else {
            armInternal(context, metric, resetAt, windowSeconds);
            enabled = true;
        }
        DiagnosticLog.info(context, "notification", "limit_reset_bell_toggled",
                "metric", metric,
                "enabled", enabled,
                "reset_at", resetAt);
        NowBarManager.repostActive(context);
    }

    static void fireFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        migrateLegacyState(context);
        String metric = normalizeMetric(intent.getStringExtra(EXTRA_METRIC));
        long resetAt = intent.getLongExtra(EXTRA_RESET_AT, 0L);
        long windowSeconds = Math.max(0L, intent.getLongExtra(EXTRA_WINDOW_SECONDS, 0L));
        if (metric == null) return;
        SharedPreferences preferences = state(context);
        if (!preferences.getBoolean(keyArmed(metric), false)) return;
        if (preferences.getLong(keyResetAt(metric), 0L) != resetAt
                || preferences.getLong(keyWindowSeconds(metric), 0L) != windowSeconds) {
            return;
        }

        clearMetricState(context, metric);
        if (!SecureTokenStore.isSignedIn(context)) return;
        playResetSound(context, metric);
        DiagnosticLog.info(context, "notification", "limit_reset_alert_fired",
                "metric", metric,
                "reset_at", resetAt);
        // Keep exactly the existing persistent card(s), then fetch the new usage state.
        NowBarManager.repostActive(context);
        RefreshScheduler.scheduleImmediate(context);
        WidgetRenderer.updateAll(context);
    }

    /** Re-arms every stored per-limit reminder after reboot or package replacement. */
    public static void restore(Context context) {
        if (context == null) return;
        migrateLegacyState(context);
        if (!SecureTokenStore.isSignedIn(context)) {
            for (String metric : RESTORABLE_METRICS) disarm(context, metric);
            return;
        }
        long now = System.currentTimeMillis();
        for (String metric : RESTORABLE_METRICS) {
            SharedPreferences preferences = state(context);
            if (!preferences.getBoolean(keyArmed(metric), false)) continue;
            long resetAt = preferences.getLong(keyResetAt(metric), 0L);
            long windowSeconds = Math.max(0L,
                    preferences.getLong(keyWindowSeconds(metric), 0L));
            if (resetAt <= 0L) {
                disarm(context, metric);
            } else if (resetAt <= now) {
                clearMetricState(context, metric);
                playResetSound(context, metric);
                DiagnosticLog.info(context, "notification", "limit_reset_alert_restored_late",
                        "metric", metric,
                        "reset_at", resetAt);
                NowBarManager.repostActive(context);
                RefreshScheduler.scheduleImmediate(context);
                WidgetRenderer.updateAll(context);
            } else {
                schedule(context, metric, resetAt, windowSeconds);
            }
        }
    }

    static boolean isArmedFor(Context context, String metric, long resetAt, long windowSeconds) {
        if (context == null || metric == null || resetAt <= 0L) return false;
        migrateLegacyState(context);
        String normalizedMetric = normalizeMetric(metric);
        if (normalizedMetric == null) return false;
        SharedPreferences preferences = state(context);
        if (!preferences.getBoolean(keyArmed(normalizedMetric), false)) return false;
        long storedResetAt = preferences.getLong(keyResetAt(normalizedMetric), 0L);
        long storedWindowSeconds = preferences.getLong(keyWindowSeconds(normalizedMetric), 0L);
        if (storedResetAt <= 0L) return false;
        if (storedWindowSeconds > 0L && windowSeconds > 0L) {
            return UsageWindow.sameResetWindow(storedResetAt, storedWindowSeconds,
                    resetAt, windowSeconds);
        }
        return Math.abs(storedResetAt - resetAt) < 60_000L;
    }

    static boolean isArmed(Context context, String metric) {
        String normalized = normalizeMetric(metric);
        return context != null && normalized != null
                && state(context).getBoolean(keyArmed(normalized), false);
    }

    private static void armInternal(Context context, String metric, long resetAt,
            long windowSeconds) {
        cancelAlarm(context, metric);
        state(context).edit()
                .putBoolean(keyArmed(metric), true)
                .putLong(keyResetAt(metric), resetAt)
                .putLong(keyWindowSeconds(metric), Math.max(0L, windowSeconds))
                .apply();
        try {
            ResetNotificationManager.ensureChannel(context);
        } catch (RuntimeException ignored) {
        }
        schedule(context, metric, resetAt, windowSeconds);
    }

    private static void disarm(Context context, String metric) {
        cancelAlarm(context, metric);
        clearMetricState(context, metric);
    }

    private static void clearMetricState(Context context, String metric) {
        state(context).edit()
                .remove(keyArmed(metric))
                .remove(keyResetAt(metric))
                .remove(keyWindowSeconds(metric))
                .apply();
    }

    private static void schedule(Context context, String metric, long resetAt,
            long windowSeconds) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null || resetAt <= System.currentTimeMillis()) return;
        PendingIntent pending = fireIntent(context, metric, resetAt, windowSeconds);
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
                "metric", metric,
                "reset_at", resetAt,
                "delivery_at", when);
    }

    private static void cancelAlarm(Context context, String metric) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pending = PendingIntent.getBroadcast(context, requestFire(metric),
                new Intent(context, NowBarActionReceiver.class).setAction(ACTION_FIRE),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            alarms.cancel(pending);
            pending.cancel();
        }
    }

    private static PendingIntent fireIntent(Context context, String metric, long resetAt,
            long windowSeconds) {
        Intent fire = new Intent(context, NowBarActionReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_METRIC, metric)
                .putExtra(EXTRA_RESET_AT, resetAt)
                .putExtra(EXTRA_WINDOW_SECONDS, Math.max(0L, windowSeconds));
        return PendingIntent.getBroadcast(context, requestFire(metric), fire,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void migrateLegacyState(Context context) {
        SharedPreferences preferences = state(context);
        if (preferences.getBoolean(KEY_PER_METRIC_MIGRATED, false)) return;
        SharedPreferences.Editor edit = preferences.edit();
        if (preferences.getBoolean(KEY_ARMED, false)) {
            String metric = normalizeMetric(preferences.getString(KEY_METRIC, null));
            long resetAt = preferences.getLong(KEY_RESET_AT, 0L);
            long windowSeconds = Math.max(0L, preferences.getLong(KEY_WINDOW_SECONDS, 0L));
            if (metric != null && resetAt > 0L) {
                edit.putBoolean(keyArmed(metric), true)
                        .putLong(keyResetAt(metric), resetAt)
                        .putLong(keyWindowSeconds(metric), windowSeconds);
            }
        }
        edit.remove(KEY_ARMED)
                .remove(KEY_METRIC)
                .remove(KEY_RESET_AT)
                .remove(KEY_WINDOW_SECONDS)
                .putBoolean(KEY_PER_METRIC_MIGRATED, true)
                .apply();
    }

    private static void playResetSound(Context context, String metric) {
        try {
            Uri sound = configuredUsageAlertSound(context);
            if (sound == null) {
                sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (sound == null) {
                DiagnosticLog.warn(context, "now_bar", "reset_sound_unavailable",
                        "metric", metric == null ? "" : metric);
                return;
            }
            Ringtone ringtone = RingtoneManager.getRingtone(context.getApplicationContext(), sound);
            if (ringtone == null) {
                DiagnosticLog.warn(context, "now_bar", "reset_sound_unavailable",
                        "metric", metric == null ? "" : metric);
                return;
            }
            ringtone.play();
            DiagnosticLog.info(context, "now_bar", "reset_sound_played",
                    "metric", metric == null ? "" : metric);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "now_bar", "reset_sound_failed", exception,
                    "metric", metric == null ? "" : metric);
        }
    }

    private static Uri configuredUsageAlertSound(Context context) {
        try {
            ResetNotificationManager.ensureChannel(context);
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return null;
            String style = ResetAlertPreferences.getStyle(context);
            String channelId = ResetAlertPreferences.STYLE_ALARM.equals(style)
                    ? CHANNEL_ALARM
                    : ResetAlertPreferences.STYLE_SILENT.equals(style)
                    ? CHANNEL_SILENT : CHANNEL_NOTIFY;
            NotificationChannel channel = manager.getNotificationChannel(channelId);
            return channel == null ? null : channel.getSound();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int requestToggle(String metric) {
        return REQUEST_TOGGLE_BASE + metricOffset(metric);
    }

    private static int requestFire(String metric) {
        return REQUEST_FIRE_BASE + metricOffset(metric);
    }

    private static int metricOffset(String metric) {
        if ("five_hour".equals(metric)) return 1;
        if ("weekly".equals(metric)) return 2;
        if ("monthly".equals(metric)) return 3;
        return 0;
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
