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
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ResetAlertPreferences.STYLE_ALARM);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private static void scheduleWindow(Context context, UsageWindow usageWindow, String str, int i) {
        AlarmManager alarmManager;
        if (usageWindow != null && usageWindow.resetAtMillis() > System.currentTimeMillis()) {
            if ((alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE)) != null) {
                long jResetAtMillis = usageWindow.resetAtMillis() + DELIVERY_GRACE_MS;
                PendingIntent pendingIntentPending = pending(context, str, usageWindow.resetAtMillis(), i);
                try {
                    if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(0, jResetAtMillis, pendingIntentPending);
                    } else {
                        alarmManager.setAndAllowWhileIdle(0, jResetAtMillis, pendingIntentPending);
                    }
                } catch (SecurityException e) {
                    alarmManager.setAndAllowWhileIdle(0, jResetAtMillis, pendingIntentPending);
                }
            }
        }
    }

    private static PendingIntent pending(Context context, String str, long j, int i) {
        return PendingIntent.getBroadcast(context, i, new Intent(context, (Class<?>) ResetAlertReceiver.class).setAction(AppConstants.ACTION_RESET_ALERT).putExtra(EXTRA_METRIC, str).putExtra(EXTRA_RESET_AT, j), 201326592);
    }

    private static Context appContext(Context context) {
        if (context == null) {
            return null;
        }
        Context applicationContext = context.getApplicationContext();
        return applicationContext != null ? applicationContext : context;
    }
}
