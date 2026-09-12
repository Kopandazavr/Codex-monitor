package dev.bennett.codexmeter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles the explicit actions attached to the finite Now Bar Live Update. */
public final class NowBarActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (NowBarManager.ACTION_STOP.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "stop");
            NowBarManager.stop(context, true);
            ProcessNotificationManager.clearAll(context);
            ProcessNotificationScheduler.cancel(context);
        } else if (NowBarManager.ACTION_END.equals(action)) {
            DiagnosticLog.info(context, "notification", "notification_action",
                    "source", "scheduled_end");
            NowBarManager.onScheduledEnd(context);
            DualUsageNotificationManager.repostDelayed(context, 450L);
        } else if (NowBarManager.ACTION_REFRESH.equals(action)) {
            DiagnosticLog.info(context, "notification", "remote_refresh_requested",
                    "source", "manual_notification_refresh");
            RefreshScheduler.scheduleImmediate(context);
        } else if (ProcessNotificationScheduler.ACTION_REFRESH.equals(action)) {
            String correlationId = "local-repaint-" + System.currentTimeMillis();
            if (!NowBarManager.isActive(context)) {
                DiagnosticLog.info(context, "notification", "local_repaint_suppressed",
                        "correlation_id", correlationId,
                        "reason", "monitor_inactive");
                ProcessNotificationScheduler.cancel(context);
                ProcessNotificationManager.clearAll(context);
            } else {
                DiagnosticLog.info(context, "notification", "diagnostic_5s_repaint",
                        "correlation_id", correlationId,
                        "source", "process_notification_scheduler",
                        "diagnostic_5s", ProcessNotificationScheduler.DIAGNOSTIC_FIVE_SECOND_REPAINT);
                boolean posted = DualUsageNotificationManager.repostFromCache(context);
                DiagnosticLog.info(context, "notification", "local_repaint_completed",
                        "correlation_id", correlationId,
                        "posted", posted,
                        "remote_fetch", false);
                ProcessNotificationScheduler.schedule(context);
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
        }
    }
}
