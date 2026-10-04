package dev.kopandazavr.codexmonitor;

/** Stable notification/PendingIntent namespace derived from a Codex Monitor account container. */
final class AccountNotificationNamespace {
    private AccountNotificationNamespace() {
    }

    static String tag(String containerId) {
        return "codex_account:" + safe(containerId);
    }

    static String groupKey(String containerId) {
        return "codex_monitor_persistent_v2:" + safe(containerId);
    }

    static int requestCode(String containerId, String purpose) {
        String value = safe(containerId) + "|" + (purpose == null ? "" : purpose);
        int hash = value.hashCode();
        return hash == Integer.MIN_VALUE ? 0 : Math.abs(hash);
    }

    static String safe(String containerId) {
        String value = containerId == null ? "" : containerId.trim();
        if (value.isEmpty()) return "selected";
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
