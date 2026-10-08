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
    private static final Map<String, String> ordinaryFingerprints = new HashMap<>();
    private static final Map<String, Boolean> needsQuietRestore = new HashMap<>();
    private static final Map<String, Long> lastDiagnosticAt = new HashMap<>();
    static final String FINGERPRINT_EXTRA = "codex_monitor_card_fingerprint";

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
            ordinaryFingerprints.remove(containerId);
            needsQuietRestore.remove(containerId);
            return false;
        }

        Long previousVersion = snapshotVersions.get(containerId);
        if (!attention && previousVersion != null && previousVersion > fetchedAtMillis) {
            // A late older network callback cannot downgrade the current persistent card.
            return false;
        }
        String fingerprint = card.extras == null ? null
                : card.extras.getString(FINGERPRINT_EXTRA);
        if (!attention && fingerprint != null
                && fingerprint.equals(ordinaryFingerprints.get(containerId))
                && !Boolean.TRUE.equals(needsQuietRestore.get(containerId))) {
            // Semantic no-op: a timestamp-only refresh must not move an account card in OneUI.
            snapshotVersions.put(containerId, Math.max(fetchedAtMillis,
                    previousVersion == null ? 0L : previousVersion));
            trace(context, containerId, "unchanged_suppressed");
            return true;
        }
        try {
            manager.notify(AccountNotificationNamespace.tag(containerId), CARD_ID, card);
            if (attention) {
                // Next ordinary post must return from the alerting channel even with identical data.
                needsQuietRestore.put(containerId, true);
            } else {
                ordinaryCards.put(containerId, card);
                snapshotVersions.put(containerId, fetchedAtMillis);
                if (fingerprint == null) ordinaryFingerprints.remove(containerId);
                else ordinaryFingerprints.put(containerId, fingerprint);
                needsQuietRestore.remove(containerId);
            }
            // Do not repost predecessors: that caused a visible second reorder after each refresh.
            // Android/OneUI owns cross-group ranking, so a zero-jank guarantee still needs PHONE.
            trace(context, containerId, attention ? "attention_post" : "content_changed");
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
        // A single post on Shown is enough; the old subsequent full reorder bounced cards.
        DualUsageNotificationManager.repostFromCache(context, containerId);
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
            // Rehydrate only genuinely absent cards after process death/reboot; do not
            // refresh or re-rank already displayed cards on ordinary Activity resumes.
            if (!ordinaryCards.containsKey(id)) {
                DualUsageNotificationManager.repostFromCache(context, id);
            }
        }
    }

    static synchronized void clear(Context context, String containerId) {
        ordinaryCards.remove(containerId);
        snapshotVersions.remove(containerId);
        ordinaryFingerprints.remove(containerId);
        needsQuietRestore.remove(containerId);
        lastDiagnosticAt.remove(containerId);
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

    private static void trace(Context context, String containerId, String decision) {
        long now = android.os.SystemClock.elapsedRealtime();
        String key = containerId + ":" + decision;
        Long previous = lastDiagnosticAt.get(key);
        if (previous != null && now - previous < 30_000L) return;
        lastDiagnosticAt.put(key, now);
        List<AccountContainerStore.Account> accounts = AccountContainerStore.all(context);
        int rank = -1;
        int shown = 0;
        for (int i = 0; i < accounts.size(); i++) {
            if (containerId.equals(accounts.get(i).id)) rank = i;
            if (PersistentCardVisibility.isShown(context, accounts.get(i).id)) shown++;
        }
        DiagnosticLog.info(context, "notification", "persistent_card_publish_decision",
                "account_rank", rank, "shown_count", shown, "decision", decision);
    }

    private static NotificationManager manager(Context context) {
        return context == null ? null : (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
