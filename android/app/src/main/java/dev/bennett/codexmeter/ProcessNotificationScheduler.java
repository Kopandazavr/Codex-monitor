package dev.bennett.codexmeter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import java.util.concurrent.TimeUnit;

/** Keeps calendar-backed process progress fresh without invoking the remote usage API. */
final class ProcessNotificationScheduler {
    static final String ACTION_REFRESH = "dev.bennett.codexmeter.action.PROCESS_NOTIFICATION_REFRESH";
    private static final int REQUEST_REFRESH = 8631;

    // TEMPORARY 2.10 PHONE DIAGNOSTIC: deliberately stress the exact same local notification
    // rebuild/repost path every five seconds so Samsung expanded-view flicker can be correlated.
    // This must be returned to the normal product cadence after PHONE evidence is collected.
    static final boolean DIAGNOSTIC_FIVE_SECOND_REPAINT = true;
    private static final long INTERVAL_MS = DIAGNOSTIC_FIVE_SECOND_REPAINT
            ? TimeUnit.SECONDS.toMillis(5) : TimeUnit.MINUTES.toMillis(1);

    private ProcessNotificationScheduler() {
    }

    static void schedule(Context context) {
        if (context == null || !NowBarManager.isActive(context)) {
            cancel(context);
            return;
        }
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        long triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS;
        PendingIntent refresh = pendingIntent(context);
        try {
            if (Build.VERSION.SDK_INT >= 31 && alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt, refresh);
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt, refresh);
            }
            DiagnosticLog.info(context, "notification", "local_repaint_scheduled",
                    "diagnostic_5s", DIAGNOSTIC_FIVE_SECOND_REPAINT,
                    "delay_ms", INTERVAL_MS);
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "calendar_process", "refresh_schedule_failed",
                    "error", exception.getClass().getSimpleName());
        }
    }

    static void cancel(Context context) {
        if (context == null) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        try {
            alarms.cancel(pendingIntent(context));
        } catch (RuntimeException ignored) {
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, NowBarActionReceiver.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, REQUEST_REFRESH, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
