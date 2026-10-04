package dev.kopandazavr.codexmonitor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/* JADX INFO: loaded from: classes.dex */
public final class ResetAlertScheduler {
    private static final long DELIVERY_GRACE_MS = 3000;
    static final String EXTRA_CONTAINER_ID = OAuthService.EXTRA_CONTAINER_ID;
    static final String EXTRA_METRIC = "metric";
    static final String EXTRA_RESET_AT = "reset_at";
    private static final int REQUEST_FIVE_HOUR = 74205;
    private static final int REQUEST_WEEKLY = 74207;
    private static final int REQUEST_MONTHLY = 74208;

    private ResetAlertScheduler() {
    }

    public static void scheduleFromSnapshot(Context context, UsageSnapshot usageSnapshot) {
        scheduleFromSnapshot(context, AccountContainerStore.selectedId(context), usageSnapshot);
    }

    static void scheduleFromSnapshot(Context context, String containerId,
            UsageSnapshot usageSnapshot) {
        Context app = appContext(context);
        if (app == null) return;
        cancelAll(app, containerId);
        if (usageSnapshot == null
                || !SecureTokenStore.isSignedIn(app, containerId)
                || !ResetAlertPreferences.enabled(app)) {
            return;
        }
        String metric = ResetAlertPreferences.getMetric(app);
        if (!ResetAlertPreferences.METRIC_WEEKLY.equals(metric)) {
            scheduleWindow(app, containerId, usageSnapshot.fiveHour,
                    "five_hour", REQUEST_FIVE_HOUR);
        }
        if (!ResetAlertPreferences.METRIC_FIVE_HOUR.equals(metric)) {
            scheduleWindow(app, containerId, usageSnapshot.weekly,
                    "weekly", REQUEST_WEEKLY);
            scheduleWindow(app, containerId, usageSnapshot.monthly,
                    "monthly", REQUEST_MONTHLY);
        }
    }

    public static void cancelAll(Context context) {
        Context app = appContext(context);
        if (app == null) return;
        for (AccountContainerStore.Account account : AccountContainerStore.all(app)) {
            cancelAll(app, account.id);
        }
    }

    static void cancelAll(Context context, String containerId) {
        Context app = appContext(context);
        if (app == null) return;
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        alarms.cancel(pending(app, containerId, "five_hour", 0L, REQUEST_FIVE_HOUR));
        alarms.cancel(pending(app, containerId, "weekly", 0L, REQUEST_WEEKLY));
        alarms.cancel(pending(app, containerId, "monthly", 0L, REQUEST_MONTHLY));
        if (AccountContainerStore.isLegacyOwner(app, containerId)) {
            cancelLegacy(alarms, legacyPending(app, REQUEST_FIVE_HOUR));
            cancelLegacy(alarms, legacyPending(app, REQUEST_WEEKLY));
            cancelLegacy(alarms, legacyPending(app, REQUEST_MONTHLY));
        }
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ResetAlertPreferences.STYLE_ALARM);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private static void scheduleWindow(Context context, String containerId,
            UsageWindow usageWindow, String metric, int legacyRequestCode) {
        if (usageWindow == null || usageWindow.resetAtMillis() <= System.currentTimeMillis()) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        long deliveryAt = usageWindow.resetAtMillis() + DELIVERY_GRACE_MS;
        PendingIntent pending = pending(context, containerId, metric,
                usageWindow.resetAtMillis(), legacyRequestCode);
        try {
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, deliveryAt, pending);
            } else {
                alarms.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, deliveryAt, pending);
            }
        } catch (SecurityException exception) {
            alarms.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, deliveryAt, pending);
        }
    }

    private static PendingIntent pending(Context context, String containerId,
            String metric, long resetAt, int legacyRequestCode) {
        Intent intent = new Intent(context, ResetAlertReceiver.class)
                .setAction(AppConstants.ACTION_RESET_ALERT)
                .putExtra(EXTRA_CONTAINER_ID, containerId)
                .putExtra(EXTRA_METRIC, metric)
                .putExtra(EXTRA_RESET_AT, resetAt);
        int requestCode = AccountNotificationNamespace.requestCode(
                containerId, "reset_alert_" + metric + "_" + legacyRequestCode);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent legacyPending(Context context, int requestCode) {
        Intent intent = new Intent(context, ResetAlertReceiver.class)
                .setAction(AppConstants.ACTION_RESET_ALERT);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelLegacy(AlarmManager alarms, PendingIntent pending) {
        if (alarms == null || pending == null) return;
        alarms.cancel(pending);
        pending.cancel();
    }

    private static Context appContext(Context context) {
        if (context == null) {
            return null;
        }
        Context applicationContext = context.getApplicationContext();
        return applicationContext != null ? applicationContext : context;
    }
}
