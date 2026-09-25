package dev.kopandazavr.codexmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the explicit actions attached to the finite Now Bar Live Update. */
public final class NowBarActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (NowBarManager.ACTION_STOP.equals(action)) {
            // A stale pre-2.19 notification can still carry this PendingIntent briefly across an
            // in-place update. The user-facing off-state no longer exists, so treat it as a
            // request to reconcile the always-on surface rather than disabling monitoring.
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "obsolete_stop_ignored");
            NowBarManager.ensureAlwaysOn(context);
            DualUsageNotificationManager.repostDelayed(context, 150L);
        } else if (NowBarManager.ACTION_END.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "scheduled_end");
            NowBarManager.onScheduledEnd(context);
            DualUsageNotificationManager.repostDelayed(context, 450L);
        } else if (NowBarManager.ACTION_REFRESH.equals(action)) {
            String correlationId = "manual-calendar-" + System.currentTimeMillis();
            DiagnosticLog.info(context, "notification", "remote_refresh_requested",
                    "source", "manual_notification_refresh",
                    "correlation_id", correlationId);
            PendingResult pending = goAsync();
            Context app = context.getApplicationContext();
            GoogleCalendarProcessSource.forceRefresh(app, () -> {
                try {
                    boolean posted = DualUsageNotificationManager.repostForProcessChange(app);
                    DiagnosticLog.info(app, "notification", "manual_calendar_refresh_completed",
                            "correlation_id", correlationId,
                            "posted", posted,
                            "calendar_api_connected",
                            GoogleCalendarAuthorization.isConnected(app));
                    // Usage refresh is independent and may complete later; Calendar never repaints
                    // stale cache first.
                    RefreshScheduler.scheduleImmediate(app);
                } finally {
                    pending.finish();
                }
            });
        } else if (ProcessNotificationScheduler.ACTION_REFRESH.equals(action)) {
            if (!NowBarManager.isActive(context)) {
                DiagnosticLog.info(context, "notification", "local_repaint_suppressed",
                        "reason", "monitor_inactive");
                ProcessNotificationScheduler.cancel(context);
                ProcessNotificationManager.clearAll(context);
            } else {
                ProcessNotificationScheduler.recover(context.getApplicationContext());
            }
        } else if (NowBarManager.ACTION_DISMISSED.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "dismissed");
            NowBarManager.onUserDismissed(context);
            DualUsageNotificationManager.repostDelayed(context, 500L);
        } else if (NowBarResetReminder.ACTION_TOGGLE.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "limit_bell_toggle",
                    "metric", intent == null ? "" : intent.getStringExtra(NowBarResetReminder.EXTRA_METRIC));
            NowBarResetReminder.toggleFromIntent(context, intent);
            DualUsageNotificationManager.repostDelayed(context, 150L);
        } else if (NowBarResetReminder.ACTION_FIRE.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "limit_reset_fire",
                    "metric", intent == null ? "" : intent.getStringExtra(NowBarResetReminder.EXTRA_METRIC));
            NowBarResetReminder.fireFromIntent(context, intent);
            DualUsageNotificationManager.repostDelayed(context, 500L);
        } else if (IdleReminderManager.ACTION_TOGGLE.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "role_bell_toggle");
            NotificationRepostGuard.toggleIdleReminder(context, intent);
        } else if (IdleReminderManager.ACTION_DISMISS_ROW.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "idle_row_dismiss");
            IdleReminderManager.dismissRowFromIntent(context, intent);
        } else if (IdleReminderManager.ACTION_FIRE.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "idle_reminder_fire");
            IdleReminderManager.fireFromIntent(context, intent);
        } else if (IdleReminderManager.ACTION_COMPLETION_OVERLAY.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "completion_overlay_exact_alarm");
            IdleReminderManager.completionOverlayFromIntent(context, intent);
        }
    }
}
