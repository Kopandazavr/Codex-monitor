package dev.kopandazavr.codexmonitor;

/** Pure retry/backoff rules. No Android or OAuth side effects live here. */
final class CalendarAuthorizationRecoveryPolicy {
    static final long NEVER = Long.MAX_VALUE;
    private static final long MINUTE = 60_000L;

    private CalendarAuthorizationRecoveryPolicy() {}

    static long delayMillis(String kind, int attempts) {
        if ("consent".equals(kind)) {
            // One later silent observation is allowed; never consume a PendingIntent silently.
            return attempts <= 1 ? 15L * MINUTE : NEVER;
        }
        int n = Math.max(1, attempts);
        if (n == 1) return MINUTE;
        if (n == 2) return 5L * MINUTE;
        if (n == 3) return 15L * MINUTE;
        return 60L * MINUTE; // Sustained failures cannot create request storms.
    }

    static boolean mayPoll(boolean bound, boolean connected, String kind,
            long dueAt, long now) {
        if (!bound || "cancelled".equals(kind)) return false;
        if (connected) return true;
        return dueAt != NEVER && now >= dueAt;
    }

    static boolean mayMonitor(boolean bound, boolean connected, String kind,
            long dueAt) {
        return bound && (connected
                || (!"cancelled".equals(kind) && dueAt != NEVER));
    }

    static boolean mayAccelerate(String kind, long lastSignalAt, long now) {
        return "transient".equals(kind) && now >= lastSignalAt
                && now - lastSignalAt >= 120_000L;
    }
}
