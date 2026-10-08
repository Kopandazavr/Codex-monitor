package dev.kopandazavr.codexmonitor;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sole serialization point for account-scoped persistent One Cards.
 * Google/OneUI sorting by recency is device-dependent; Samsung PHONE acceptance is required.
 * No network or event-delivery operations are permitted during predecessor reposts.
 */
final class PersistentCardCoordinator {
    private static final int CARD_ID = 8610;
    private static final Map<String, Notification> ordinaryCards = new HashMap<>();
    private static final Map<String, Long> snapshotVersions = new HashMap<>();

    private PersistentCardCoordinator() {}

    static synchronized boolean publish(Context context, String containerId,
            Notification card, long fetchedAtMillis, boolean attention) {
        if (context == null || card == null || containerId == null
                || AccountContainerStore.find(context, containerId) == null) return false;
        NotificationManager manager = manager(context);
        if (manager == null) return false;
        if (!PersistentCardVisibility.isShown(context, containerId)) {
            manager.cancel(AccountNotificationNamespace.tag(containerId), CARD_ID);
            ordinaryCards.remove(containerId);
            snapshotVersions.remove(containerId);
            return false;
        }

        Long previousVersion = snapshotVersions.get(containerId);
        if (!attention && previousVersion != null && previousVersion > fetchedAtMillis) {
            // A late older network callback cannot downgrade the current persistent card.
            return false;
        }
        try {
            manager.notify(AccountNotificationNamespace.tag(containerId), CARD_ID, card);
            if (!attention) {
                ordinaryCards.put(containerId, card);
                snapshotVersions.put(containerId, fetchedAtMillis);
            }
            // Publish nearest predecessor first, Main last. No upstream refreshes and no alerts.
            List<AccountContainerStore.Account> accounts = AccountContainerStore.all(context);
            int index = -1;
            for (int i = 0; i < accounts.size(); i++) {
                if (containerId.equals(accounts.get(i).id)) {
                    index = i;
                    break;
                }
            }
            for (int i = index - 1; i >= 0; i--) {
                String predecessor = accounts.get(i).id;
                if (!PersistentCardVisibility.isShown(context, predecessor)) continue;
                Notification cached = ordinaryCards.get(predecessor);
                if (cached == null) continue;
                // Reposts only: preserve existing content/intent, but force silent presentation.
                Notification quiet = Notification.Builder.recoverBuilder(context, cached)
                        .setSilent(true).setOnlyAlertOnce(true).build();
                manager.notify(AccountNotificationNamespace.tag(predecessor), CARD_ID, quiet);
            }
            return true;
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "notification", "ordered_card_post_failed",
                    exception, "container_id", containerId, "attention", attention);
            return false;
        }
    }

    static synchronized boolean changeVisibility(Context context, String containerId,
            boolean shown) {
        if (context == null || AccountContainerStore.find(context, containerId) == null) {
            return false;
        }
        if (!PersistentCardVisibility.store(context, containerId, shown)) return false;
        if (!shown) {
            clear(context, containerId);
            return true;
        }
        DualUsageNotificationManager.repostFromCache(context, containerId);
        restoreOrder(context);
        return true;
    }

    static synchronized void restoreOrder(Context context) {
        if (context == null) return;
        NotificationManager manager = manager(context);
        if (manager == null) return;
        List<AccountContainerStore.Account> accounts = AccountContainerStore.all(context);
        for (int i = accounts.size() - 1; i >= 0; i--) {
            String id = accounts.get(i).id;
            if (!PersistentCardVisibility.isShown(context, id)) {
                manager.cancel(AccountNotificationNamespace.tag(id), CARD_ID);
                continue;
            }
            Notification cached = ordinaryCards.get(id);
            if (cached == null) {
                DualUsageNotificationManager.repostFromCache(context, id);
                cached = ordinaryCards.get(id);
            }
            if (cached == null) continue;
            try {
                Notification quiet = Notification.Builder.recoverBuilder(context, cached)
                        .setSilent(true).setOnlyAlertOnce(true).build();
                manager.notify(AccountNotificationNamespace.tag(id), CARD_ID, quiet);
            } catch (RuntimeException exception) {
                DiagnosticLog.warn(context, "notification", "ordered_card_restore_failed",
                        "container_id", id);
            }
        }
    }

    static synchronized void clear(Context context, String containerId) {
        ordinaryCards.remove(containerId);
        snapshotVersions.remove(containerId);
        NotificationManager manager = manager(context);
        if (manager != null) {
            manager.cancel(AccountNotificationNamespace.tag(containerId), CARD_ID);
        }
    }

    /** Role/reset attention is never suppressed merely because a persistent card is hidden. */
    static synchronized boolean attentionWhenHidden(Context context, String containerId,
            Notification attention) {
        NotificationManager manager = manager(context);
        if (manager == null || attention == null) return false;
        int eventId = 63000 + (int) (android.os.SystemClock.elapsedRealtime() % 12000);
        try {
            Notification transientAlert = Notification.Builder.recoverBuilder(context, attention)
                    .setOngoing(false).setAutoCancel(true).setOnlyAlertOnce(false).build();
            manager.notify(AccountNotificationNamespace.tag(containerId), eventId, transientAlert);
            return true;
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "notification", "hidden_account_alert_failed",
                    exception, "container_id", containerId);
            return false;
        }
    }

    private static NotificationManager manager(Context context) {
        return context == null ? null : (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
