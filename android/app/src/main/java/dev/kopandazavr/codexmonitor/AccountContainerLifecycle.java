package dev.kopandazavr.codexmonitor;

import android.content.Context;

/** Destructive lifecycle operations for one local Codex Monitor account container. */
final class AccountContainerLifecycle {
    private AccountContainerLifecycle() {
    }

    static AuthTokens disconnectChatGPT(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return null;
        AuthTokens tokens = SecureTokenStore.load(context, containerId);

        DualUsageNotificationManager.clearUsageSurface(context, containerId);
        ResetAlertScheduler.cancelAll(context, containerId);
        ResetCreditExpiryScheduler.cancelAll(context, containerId);
        NowBarResetReminder.clearContainer(context, containerId);
        ResetNotificationManager.clearContainerState(context, containerId);
        SecureTokenStore.clear(context, containerId);
        SubscriptionStore.clear(context, containerId);
        AppPreferences.clearSnapshot(context, containerId);
        AppPreferences.setOAuthPending(context, containerId, false, "");

        RefreshScheduler.schedulePeriodic(context);
        if (hasAnySignedInAccount(context)) {
            RefreshScheduler.scheduleImmediate(context);
        }
        ProcessNotificationScheduler.schedule(context);
        WidgetRenderer.updateAll(context);
        DiagnosticLog.info(context, "account", "chatgpt_account_disconnected",
                "container_id", containerId);
        return tokens;
    }

    private static boolean hasAnySignedInAccount(Context context) {
        for (AccountContainerStore.Account account : AccountContainerStore.all(context)) {
            if (SecureTokenStore.isSignedIn(context, account.id)) return true;
        }
        return false;
    }

    static boolean remove(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return false;
        AccountContainerStore.Account account = AccountContainerStore.find(context, containerId);
        if (account == null || AccountContainerStore.all(context).size() <= 1) return false;

        // Remove credentials and app-owned surfaces before dropping the registry row so legacy
        // fallback cleanup can still identify the original migration owner.
        DualUsageNotificationManager.clearAccountSurface(context, containerId);
        ResetAlertScheduler.cancelAll(context, containerId);
        ResetCreditExpiryScheduler.cancelAll(context, containerId);
        NowBarResetReminder.clearContainer(context, containerId);
        ResetNotificationManager.clearContainerState(context, containerId);
        SecureTokenStore.clear(context, containerId);
        SubscriptionStore.clear(context, containerId);
        GoogleCalendarAuthorization.clearContainerState(context, containerId);
        GoogleCalendarProcessSource.clearContainer(context, containerId);
        AppPreferences.clearAccountData(context, containerId);
        IdleProcessState.clearContainer(context, containerId);
        WatchdogInstanceState.clearContainer(context, containerId);

        boolean removed = AccountContainerStore.remove(context, containerId);
        if (!removed) return false;

        LocalCalendarFallbackOwner.onContainerRemoved(context, containerId);
        WidgetRenderer.updateAll(context);
        String selectedId = AccountContainerStore.selectedId(context);
        UsageSnapshot selected = AppPreferences.loadSnapshot(context, selectedId);
        if (selected != null) {
            DualUsageNotificationManager.postFromSnapshot(context, selectedId, selected);
        }
        RefreshScheduler.scheduleImmediate(context);
        DiagnosticLog.info(context, "account", "account_container_removed",
                "container_id", containerId);
        return true;
    }
}
