package dev.kopandazavr.codexmonitor;

/** Pure identity guard for local, latest-finished idle episode dismissal/restoration. */
final class IdleProcessRestorePolicy {
    private IdleProcessRestorePolicy() {}

    static boolean isDeleted(long dismissedThroughMillis, long lastFinishedMillis) {
        return lastFinishedMillis > 0L && dismissedThroughMillis == lastFinishedMillis;
    }

    static boolean sameEpisode(String currentInstance, long currentEvent,
            long currentStarted, long currentFinished, String expectedInstance,
            long expectedEvent, long expectedStarted, long expectedFinished) {
        return currentFinished > 0L && currentFinished == expectedFinished
                && currentStarted == expectedStarted && currentEvent == expectedEvent
                && clean(currentInstance).equals(clean(expectedInstance));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
