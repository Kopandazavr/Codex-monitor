package dev.kopandazavr.codexmonitor;

import android.content.Context;

/** Serializes destructive invalidation with account-scoped async state commits. */
final class AccountContainerLifecycleGuard {
    private static final Object LOCK = new Object();

    private AccountContainerLifecycleGuard() {}

    static Object lock() { return LOCK; }

    static boolean isAlive(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return false;
        synchronized (LOCK) {
            return AccountContainerStore.find(context, containerId.trim()) != null;
        }
    }

    static boolean invalidate(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return false;
        synchronized (LOCK) {
            return AccountContainerStore.remove(context, containerId.trim());
        }
    }
}