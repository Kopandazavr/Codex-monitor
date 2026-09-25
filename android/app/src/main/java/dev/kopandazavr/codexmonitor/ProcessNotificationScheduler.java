package dev.kopandazavr.codexmonitor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.TimeUnit;

/**
 * Keeps calendar-backed process state fresh without coupling it to the remote usage API.
 *
 * The one-second experiment is an app-owned, non-overlapping loop. AlarmManager is used only as
 * a coarse recovery wake-up if the process disappears; exact alarms are deliberately not used for
 * polling.
 */
final class ProcessNotificationScheduler {
    static final String ACTION_REFRESH =
            "dev.kopandazavr.codexmonitor.action.PROCESS_NOTIFICATION_REFRESH";
    private static final int REQUEST_REFRESH = 8631;

    static final boolean DIAGNOSTIC_FIVE_SECOND_REPAINT = false;
    static final long POLL_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long RECOVERY_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);

    private static final Object LOOP_LOCK = new Object();
    private static final Handler LOOP_HANDLER = new Handler(Looper.getMainLooper());
    private static Context loopContext;
    private static boolean loopRunning;
    private static final Runnable POLL_TICK = ProcessNotificationScheduler::pollOnce;

    private ProcessNotificationScheduler() {
    }

    static void schedule(Context context) {
        if (context == null || !NowBarManager.isActive(context)) {
            cancel(context);
            return;
        }
        Context app = context.getApplicationContext();
        synchronized (LOOP_LOCK) {
            loopContext = app;
            if (!loopRunning) {
                loopRunning = true;
                LOOP_HANDLER.removeCallbacks(POLL_TICK);
                LOOP_HANDLER.post(POLL_TICK);
                DiagnosticLog.info(app, "calendar_process", "calendar_poll_loop_started",
                        "interval_ms", POLL_INTERVAL_MS);
            }
        }
        armRecovery(app);
    }

    static void recover(Context context) {
        if (context == null || !NowBarManager.isActive(context)) {
            cancel(context);
            return;
        }
        DiagnosticLog.info(context, "calendar_process", "calendar_poll_recovery_wakeup",
                "interval_ms", POLL_INTERVAL_MS);
        synchronized (LOOP_LOCK) {
            loopRunning = false;
            LOOP_HANDLER.removeCallbacks(POLL_TICK);
        }
        schedule(context);
    }

    static void cancel(Context context) {
        synchronized (LOOP_LOCK) {
            loopRunning = false;
            loopContext = null;
            LOOP_HANDLER.removeCallbacks(POLL_TICK);
        }
        if (context == null) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        try {
            alarms.cancel(pendingIntent(context));
        } catch (RuntimeException ignored) {
        }
    }

    private static void pollOnce() {
        Context app;
        synchronized (LOOP_LOCK) {
            if (!loopRunning) return;
            app = loopContext;
        }
        if (app == null || !NowBarManager.isActive(app)) {
            cancel(app);
            return;
        }

        final long started = SystemClock.elapsedRealtime();
        final String correlationId = "calendar-poll-" + System.currentTimeMillis();
        DiagnosticLog.info(app, "calendar_process", "calendar_poll_requested",
                "correlation_id", correlationId,
                "interval_ms", POLL_INTERVAL_MS,
                "calendar_api_connected", GoogleCalendarAuthorization.isConnected(app));
        GoogleCalendarProcessSource.forceRefresh(app, () -> {
            long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - started);
            boolean posted = DualUsageNotificationManager.repostForProcessChange(app);
            DiagnosticLog.info(app, "calendar_process", "calendar_poll_completed",
                    "correlation_id", correlationId,
                    "elapsed_ms", elapsed,
                    "posted", posted,
                    "remote_usage_fetch", false);
            synchronized (LOOP_LOCK) {
                if (!loopRunning || loopContext == null || !NowBarManager.isActive(app)) {
                    loopRunning = false;
                    loopContext = null;
                    LOOP_HANDLER.removeCallbacks(POLL_TICK);
                    return;
                }
                long delay = Math.max(0L, POLL_INTERVAL_MS - elapsed);
                LOOP_HANDLER.removeCallbacks(POLL_TICK);
                LOOP_HANDLER.postDelayed(POLL_TICK, delay);
            }
        });
    }

    private static void armRecovery(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        long triggerAt = SystemClock.elapsedRealtime() + RECOVERY_INTERVAL_MS;
        try {
            // Recovery only: never spend exact-alarm access on the one-second Calendar cadence.
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt, pendingIntent(context));
            DiagnosticLog.info(context, "calendar_process", "calendar_poll_recovery_scheduled",
                    "delay_ms", RECOVERY_INTERVAL_MS);
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "calendar_process", "refresh_schedule_failed",
                    "error", exception.getClass().getSimpleName());
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, NowBarActionReceiver.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, REQUEST_REFRESH, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
