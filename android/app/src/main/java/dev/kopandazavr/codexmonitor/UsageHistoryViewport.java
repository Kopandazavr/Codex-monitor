package dev.kopandazavr.codexmonitor;

/** Pure viewport geometry rules for measured Usage History zoom. */
final class UsageHistoryViewport {
    private UsageHistoryViewport() {}

    static long zoomSpan(long minimumViewportSpan, long measuredStart, long measuredEnd) {
        if (minimumViewportSpan <= 0L || measuredEnd <= measuredStart) return 0L;
        return minimumViewportSpan;
    }

    static long restoreSpan(long minimumViewportSpan, long measuredStart, long measuredEnd,
            long requestedSpan) {
        if (minimumViewportSpan <= 0L || measuredEnd <= measuredStart || requestedSpan <= 0L) {
            return 0L;
        }
        long measuredSpan = measuredEnd - measuredStart;
        if (measuredSpan <= minimumViewportSpan) return minimumViewportSpan;
        return Math.min(measuredSpan, Math.max(minimumViewportSpan, requestedSpan));
    }

    static boolean canPan(long measuredStart, long measuredEnd, long viewportSpan) {
        return viewportSpan > 0L && measuredEnd > measuredStart
                && measuredEnd - measuredStart > viewportSpan;
    }
}
