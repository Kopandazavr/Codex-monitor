package dev.kopandazavr.codexmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if ("android.intent.action.BOOT_COMPLETED".equals(action)
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            DiagnosticLog.info(context, "process", "boot_receiver", "action", action);
            RefreshScheduler.schedulePeriodic(context);
            ResetAlertScheduler.scheduleFromSnapshot(
                    context, AppPreferences.loadSnapshot(context));
            ResetCreditExpiryScheduler.scheduleFromSnapshot(
                    context, AppPreferences.loadResetCredits(context));
            NowBarResetReminder.restore(context);
            IdleReminderManager.restore(context);
            NowBarManager.restore(context);
            DualUsageNotificationManager.repostDelayed(context, 500L);
            if ("android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
                WidgetUpgradeRepair.afterPackageReplaced(context);
            } else {
                WidgetUpgradeRepair.runIfNeeded(context);
            }
        }
    }
}
