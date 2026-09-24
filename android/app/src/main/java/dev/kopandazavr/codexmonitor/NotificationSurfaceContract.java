package dev.kopandazavr.codexmonitor;

/** Stable ownership/order contract for persistent Codex Monitor notification surfaces. */
final class NotificationSurfaceContract {
    static final String GROUP_KEY = "codex_watch_persistent_v1";
    static final String SORT_USAGE = "00_usage";
    static final String SORT_PROCESSES = "10_processes";

    private NotificationSurfaceContract() {
    }

    static String sortRole(String key) {
        String stable = key == null ? "" : key;
        return "20_role_" + Integer.toHexString(stable.hashCode());
    }
}