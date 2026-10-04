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
        if (containerId.equals(AccountContainerStore.selectedId(context))) {
            ForegroundAccountCoordinator.reconcile(context);
        } else {
            WidgetRenderer.updateAll(context);
        }
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
        String target = containerId.trim();
        AccountContainerStore.Account account = AccountContainerStore.find(context, target);
        if (account == null || AccountContainerStore.all(context).size() <= 1) return false;
        boolean removedSelected = target.equals(AccountContainerStore.selectedId(context));

        if (!AccountContainerLifecycleGuard.invalidate(context, target)) return false;

        try {
            context.startService(new android.content.Intent(context, OAuthService.class)
                    .setAction(OAuthService.ACTION_CANCEL_SILENT)
                    .putExtra(OAuthService.EXTRA_CONTAINER_ID, target));
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "account", "oauth_cancel_on_remove_failed",
                    "container_id", target,
                    "error", exception.getClass().getSimpleName());
        }

        IdleReminderManager.cancelAllScheduled(context, target);
        DualUsageNotificationManager.clearAccountSurface(context, target);
        ResetAlertScheduler.cancelAll(context, target);
        ResetCreditExpiryScheduler.cancelAll(context, target);
        NowBarResetReminder.clearContainer(context, target);
        ResetNotificationManager.clearContainerState(context, target);
        SecureTokenStore.clear(context, target);
        SubscriptionStore.clear(context, target);
        GoogleCalendarAuthorization.clearContainerState(context, target);
        GoogleCalendarProcessSource.clearContainer(context, target);
        AppPreferences.clearAccountData(context, target);
        IdleProcessState.clearContainer(context, target);
        WatchdogInstanceState.clearContainer(context, target);
        MonitorHealthDiagnostics.clearContainer(context, target);
        ProcessNotificationMode.clearContainer(context, target);

        LocalCalendarFallbackOwner.onContainerRemoved(context, target);
        if (removedSelected) {
            NowBarManager.onSelectedContainerChanged(context, target);
        }
        ForegroundAccountCoordinator.reconcile(context);
        RefreshScheduler.scheduleImmediate(context);
        DiagnosticLog.info(context, "account", "account_container_removed",
                "container_id", target,
                "selected_container_id", AccountContainerStore.selectedId(context));
        return true;
    }

}
