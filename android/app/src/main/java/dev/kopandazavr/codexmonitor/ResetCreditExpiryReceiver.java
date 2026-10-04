package dev.kopandazavr.codexmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Revalidates a scheduled reset-credit reminder before showing it. */
public final class ResetCreditExpiryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !AppConstants.ACTION_RESET_CREDIT_EXPIRY_ALERT.equals(intent.getAction())
                || !ResetAlertPreferences.enabled(context)
                || !ResetAlertPreferences.resetCreditExpiryEnabled(context)) {
            return;
        }
        String containerId = intent.getStringExtra(
                ResetCreditExpiryScheduler.EXTRA_CONTAINER_ID);
        if (containerId != null && !containerId.trim().isEmpty()) {
            containerId = containerId.trim();
            if (AccountContainerStore.find(context, containerId) == null) return;
        } else {
            containerId = AccountContainerStore.selectedId(context);
        }
        if (!SecureTokenStore.isSignedIn(context, containerId)) return;

        String creditId = intent.getStringExtra(ResetCreditExpiryScheduler.EXTRA_CREDIT_ID);
        long expiresAt = intent.getLongExtra(
                ResetCreditExpiryScheduler.EXTRA_EXPIRES_AT, 0L);
        long leadTime = intent.getLongExtra(
                ResetCreditExpiryScheduler.EXTRA_LEAD_TIME, 0L);
        if (!ResetAlertPreferences.getResetCreditExpiryLeadTimes(context).contains(leadTime)
                || !isStillAvailable(context, containerId, creditId, expiresAt)) {
            return;
        }
        ResetNotificationManager.showResetCreditExpiryNotification(context,
                containerId, creditId, expiresAt, leadTime);
        RefreshScheduler.scheduleImmediate(context);
        WidgetRenderer.updateAll(context);
    }

    static boolean isStillAvailable(Context context, String creditId, long expiresAt) {
        return isStillAvailable(context, AccountContainerStore.selectedId(context),
                creditId, expiresAt);
    }

    static boolean isStillAvailable(Context context, String containerId,
            String creditId, long expiresAt) {
        if (expiresAt <= System.currentTimeMillis()) return false;
        ResetCreditsSnapshot snapshot = AppPreferences.loadResetCredits(context, containerId);
        if (snapshot == null) return false;
        for (RateLimitResetCredit credit : snapshot.credits) {
            if (credit == null || !credit.isAvailable()
                    || credit.expiresAtMillis != expiresAt) {
                continue;
            }
            if (creditId == null || creditId.isEmpty() || credit.id.equals(creditId)) {
                return true;
            }
        }
        return false;
    }
}
