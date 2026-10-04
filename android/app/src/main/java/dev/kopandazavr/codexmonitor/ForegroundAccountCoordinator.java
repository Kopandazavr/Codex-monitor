package dev.kopandazavr.codexmonitor;

import android.content.Context;

/** One authoritative foreground-account selection/reconciliation path. */
final class ForegroundAccountCoordinator {
    private ForegroundAccountCoordinator() {
    }

    static boolean select(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return false;
        String target = containerId.trim();
        if (AccountContainerStore.find(context, target) == null) return false;
        String previous = AccountContainerStore.selectedId(context);
        if (!AccountContainerStore.select(context, target)) return false;
        reconcileAfterSelection(context, previous);
        return true;
    }

    static void afterImplicitSelection(Context context, String previousContainerId) {
        if (context == null) return;
        reconcileAfterSelection(context, previousContainerId);
    }

    static void reconcile(Context context) {
        if (context == null) return;
        WidgetRenderer.updateAll(context);
        NowBarManager.restore(context);
        ProcessNotificationScheduler.schedule(context);
    }

    private static void reconcileAfterSelection(Context context, String previousContainerId) {
        String selected = AccountContainerStore.selectedId(context);
        String previous = previousContainerId == null ? "" : previousContainerId.trim();
        if (!previous.isEmpty() && !previous.equals(selected)) {
            NowBarManager.onSelectedContainerChanged(context, previous);
        } else {
            NowBarManager.restore(context);
        }
        WidgetRenderer.updateAll(context);
        ProcessNotificationScheduler.schedule(context);
        DiagnosticLog.info(context, "account", "foreground_account_reconciled",
                "previous_container_id", previous,
                "selected_container_id", selected);
    }
}