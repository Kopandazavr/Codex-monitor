package dev.bennett.codexmeter;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Keeps UI-driven notification rebuilds bounded and unable to crash a foreground activity. */
final class NotificationRepostGuard {
    private static final long ACTIVITY_RESUME_DELAY_MS = 200L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Runnable pendingActivityResume;

    private NotificationRepostGuard() {
    }

    /**
     * Activity transitions can produce several resume callbacks in quick succession (for example
     * Settings -> Refresh & usage -> Edit dashboard). Only the final visible activity needs the
     * lifecycle maintenance repost, so collapse that burst to one guarded rebuild.
     */
    static void repostAfterActivityResume(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        synchronized (NotificationRepostGuard.class) {
            if (pendingActivityResume != null) {
                MAIN.removeCallbacks(pendingActivityResume);
            }
            pendingActivityResume = () -> {
                synchronized (NotificationRepostGuard.class) {
                    pendingActivityResume = null;
                }
                repostSafely(app, "activity_resume");
            };
            MAIN.postDelayed(pendingActivityResume, ACTIVITY_RESUME_DELAY_MS);
        }
    }

    /** Toggle an idle-role bell and rebuild its existing notification surface immediately. */
    static void toggleIdleReminder(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String key = intent.getStringExtra(IdleReminderManager.EXTRA_ROLE_KEY);
        if (key == null || key.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean enabled = IdleProcessState.toggleReminder(context, key, now);
        IdleReminderManager.onReminderToggled(context, key, enabled, now);
        repostSafely(context.getApplicationContext(), "idle_reminder_toggle");
    }

    private static void repostSafely(Context context, String reason) {
        try {
            DualUsageNotificationManager.repostFromCache(context);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "notification_surface", "guarded_repost_failed",
                    exception, "reason", reason);
        }
    }
}
