package dev.kopandazavr.codexmonitor;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Cheap local/shared setup readiness used by first-run setup and Settings. */
final class SetupReadiness {
    static final int REQUIRED_TOTAL = 5;

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

    static String requiredSummary(Context context) {
        return requiredReadyCount(context) + " of " + REQUIRED_TOTAL + " ready";
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
}
