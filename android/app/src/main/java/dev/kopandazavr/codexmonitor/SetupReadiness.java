package dev.kopandazavr.codexmonitor;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

/** Cheap local/shared setup readiness used by first-run setup, Settings, and Dashboard. */
final class SetupReadiness {
    static final int REQUIRED_TOTAL = 5;
    static final int RECOMMENDED_TOTAL = 1;

    static final int STATUS_REQUIRED_MISSING = 0;
    static final int STATUS_RECOMMENDED_MISSING = 1;
    static final int STATUS_READY = 2;

    private static final String PREFS = "codex_monitor_setup_readiness_v1";
    private static final String KEY_BATTERY_UNRESTRICTED_ACK =
            "battery_unrestricted_ack";

    private SetupReadiness() {
    }

    static int requiredReadyCount(Context context) {
        if (context == null) return 0;
        int ready = 0;
        if (SecureTokenStore.isSignedIn(context)) ready++;
        if (notificationsAllowed(context)) ready++;
        if (GoogleCalendarAuthorization.isConnected(context)) ready++;
        if (IdleReminderOverlayService.canDraw(context)) ready++;
        if (exactAlarmAllowed(context)) ready++;
        return ready;
    }

    static int missingRequiredCount(Context context) {
        return Math.max(0, REQUIRED_TOTAL - requiredReadyCount(context));
    }

    static int overallReadyCount(Context context) {
        return requiredReadyCount(context) + recommendedReadyCount(context);
    }

    static int overallTotal() {
        return REQUIRED_TOTAL + RECOMMENDED_TOTAL;
    }

    static String overallSummary(Context context) {
        return overallReadyCount(context) + " of " + overallTotal() + " ready";
    }

    static int recommendedReadyCount(Context context) {
        if (context == null) return 0;
        return batteryUnrestrictedAcknowledged(context) ? 1 : 0;
    }

    static int overallStatus(Context context) {
        if (missingRequiredCount(context) > 0) return STATUS_REQUIRED_MISSING;
        return recommendedReadyCount(context) >= RECOMMENDED_TOTAL
                ? STATUS_READY : STATUS_RECOMMENDED_MISSING;
    }

    static boolean batteryUnrestrictedAcknowledged(Context context) {
        return context != null && prefs(context).getBoolean(
                KEY_BATTERY_UNRESTRICTED_ACK, false);
    }

    static void setBatteryUnrestrictedAcknowledged(Context context, boolean acknowledged) {
        if (context == null) return;
        prefs(context).edit().putBoolean(KEY_BATTERY_UNRESTRICTED_ACK, acknowledged).apply();
    }

    static boolean notificationsAllowed(Context context) {
        if (context == null) return false;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean exactAlarmAllowed(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 31) return true;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarms != null && alarms.canScheduleExactAlarms();
    }

    static boolean localCalendarAllowed(Context context) {
        return context != null && context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
