package dev.kopandazavr.codexmonitor;

import android.content.Context;

/** Serializes samples through the same refresh lock used by UsageApi. */
final class UsageHistoryRecorder {
    private UsageHistoryRecorder() {
    }

    static void record(Context context, UsageSnapshot snapshot) {
        record(context, AccountContainerStore.selectedId(context), snapshot);
    }

    static void record(Context context, String containerId, UsageSnapshot snapshot) {
        if (context == null || snapshot == null) return;
        recordWindow(context, containerId, UsageHistory.FIVE_HOUR, snapshot.fiveHour,
                snapshot.fetchedAtMillis);
        recordWindow(context, containerId, UsageHistory.WEEKLY, snapshot.weekly,
                snapshot.fetchedAtMillis);
        recordWindow(context, containerId, UsageHistory.MONTHLY, snapshot.monthly,
                snapshot.fetchedAtMillis);
    }

    private static void recordWindow(Context context, String containerId, String kind,
            UsageWindow window, long observedAtMillis) {
        if (window == null) return;
        UsageHistory current = AppPreferences.loadUsageHistory(context, containerId, kind);
        AppPreferences.saveUsageHistory(context, containerId,
                current.append(window, observedAtMillis));
    }
}
