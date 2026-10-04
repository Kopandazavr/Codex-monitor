package dev.kopandazavr.codexmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/* JADX INFO: loaded from: classes.dex */
public final class ResetAlertReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !AppConstants.ACTION_RESET_ALERT.equals(intent.getAction())) {
            return;
        }
        String containerId = intent.getStringExtra(ResetAlertScheduler.EXTRA_CONTAINER_ID);
        if (containerId != null && !containerId.trim().isEmpty()) {
            containerId = containerId.trim();
            if (AccountContainerStore.find(context, containerId) == null) return;
        } else {
            containerId = AccountContainerStore.selectedId(context);
        }
        if (!SecureTokenStore.isSignedIn(context, containerId)
                || !ResetAlertPreferences.enabled(context)) {
            return;
        }
        String metric = intent.getStringExtra(ResetAlertScheduler.EXTRA_METRIC);
        if (!"weekly".equals(metric) && !"monthly".equals(metric)) metric = "five_hour";
        long resetAt = intent.getLongExtra(ResetAlertScheduler.EXTRA_RESET_AT, 0L);
        if (!stillRelevant(context, containerId, metric, resetAt)) return;

        ResetNotificationManager.showResetNotification(context, containerId, metric);
        RefreshScheduler.scheduleImmediate(context);
        WidgetRenderer.updateAll(context);
    }

    private static boolean stillRelevant(Context context, String containerId,
            String metric, long resetAt) {
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(context, containerId);
        if (resetAt <= 0L || snapshot == null) return true;
        UsageWindow window = "weekly".equals(metric) ? snapshot.weekly
                : "monthly".equals(metric) ? snapshot.monthly : snapshot.fiveHour;
        return window == null || window.resetAtMillis() <= 0L
                || Math.abs(window.resetAtMillis() - resetAt) < 60_000L;
    }

}
