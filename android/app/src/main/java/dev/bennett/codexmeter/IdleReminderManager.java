package dev.bennett.codexmeter;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owns recurring local reminder alarms for idle watchdog roles. */
final class IdleReminderManager {
    static final String ACTION_FIRE = "dev.bennett.codexmeter.action.IDLE_REMINDER_FIRE";
    static final String ACTION_TOGGLE = "dev.bennett.codexmeter.action.IDLE_REMINDER_TOGGLE";
    static final String ACTION_DISMISS_ROW = "dev.bennett.codexmeter.action.IDLE_ROW_DISMISS";
    static final String EXTRA_ROLE_KEY = "idle_role_key";
    static final String EXTRA_FINISHED_AT = "idle_finished_at";

    private static final String PREFS = "codex_idle_reminder_scheduler_v1";
    private static final String KEY_ENABLED_KEYS = "enabled_role_keys";
    private static final String CHANNEL_ID = "codex_idle_reminders_v1";
    // Legacy separate reminder IDs are retained only so old cards can be cleaned up.
    private static final int NOTIFICATION_BASE = 31000;
    private static final int REQUEST_BASE = 41000;

    private IdleReminderManager() {
    }

    static void sync(Context context, List<CalendarProcess> active,
            List<IdleProcessState.IdleRole> visibleIdle, long nowMillis) {
        if (context == null) return;
        Set<String> enabledKeys = enabledKeys(context);
        if (active != null) {
            for (CalendarProcess process : active) {
                String key = IdleProcessState.roleKey(process);
                cancelAlarm(context, key);
                dismissSurface(context, key);
            }
        }
        if (visibleIdle != null) {
            for (IdleProcessState.IdleRole idle : visibleIdle) {
                if (!idle.reminderEnabled) continue;
                enabledKeys.add(idle.key);
                schedule(context, idle, nowMillis);
            }
        }
        saveEnabledKeys(context, enabledKeys);
    }

    static void onReminderToggled(Context context, String key, boolean enabled, long nowMillis) {
        Set<String> keys = enabledKeys(context);
        if (enabled) {
            keys.add(key);
            IdleProcessState.IdleRole idle = IdleProcessState.find(context, key);
            if (idle != null) schedule(context, idle, nowMillis);
        } else {
            keys.remove(key);
            cancelAlarm(context, key);
            dismissSurface(context, key);
        }
        saveEnabledKeys(context, keys);
    }

    static void dismissRowFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String key = intent.getStringExtra(EXTRA_ROLE_KEY);
        long finished = intent.getLongExtra(EXTRA_FINISHED_AT, 0L);
        if (key == null || key.trim().isEmpty() || finished <= 0L) return;
        IdleProcessState.dismiss(context, key, finished);
        DualUsageNotificationManager.repostDelayed(context, 120L);
    }

    static void toggleFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String key = intent.getStringExtra(EXTRA_ROLE_KEY);
        if (key == null || key.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean enabled = IdleProcessState.toggleReminder(context, key, now);
        onReminderToggled(context, key, enabled, now);
        DualUsageNotificationManager.repostDelayed(context, 120L);
    }

    static void fireFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String key = intent.getStringExtra(EXTRA_ROLE_KEY);
        long expectedFinished = intent.getLongExtra(EXTRA_FINISHED_AT, 0L);
        IdleProcessState.IdleRole idle = IdleProcessState.find(context, key);
        if (idle == null || !idle.reminderEnabled
                || idle.lastFinishedMillis != expectedFinished) {
            if (key != null) cancelAlarm(context, key);
            return;
        }
        long now = System.currentTimeMillis();
        List<CalendarProcess> active = CalendarProcessReader.active(context, now);
        if (IdleProcessState.isRoleActive(active, key)) {
            cancelAlarm(context, key);
            dismissSurface(context, key);
            return;
        }

        boolean overlayShown = IdleReminderOverlayService.show(context, idle);
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean persistentSurfaceAlerted = false;
        if (manager != null) {
            ensureChannel(manager);
            persistentSurfaceAlerted = ProcessNotificationManager.reAlertIdleReminder(
                    context, idle, CHANNEL_ID, now);
            // Restore the canonical live/process channel after the attention event; the ID stays
            // the same throughout, so no second long-lived reminder card appears.
            if (persistentSurfaceAlerted) {
                DualUsageNotificationManager.repostDelayed(context, 5_000L);
            }
        }
        long next = now + IdleProcessState.cadenceMillis(context);
        IdleProcessState.setNextReminderAt(context, key, next);
        scheduleAt(context, idle, next);
        DiagnosticLog.info(context, "idle_process", "reminder_fired",
                "role", idle.displayLabel(),
                "overlay", overlayShown,
                "persistent_surface_alerted", persistentSurfaceAlerted);
    }

    static void restore(Context context) {
        if (context == null) return;
        long now = System.currentTimeMillis();
        List<CalendarProcess> observed = CalendarProcessReader.observed(context, now);
        List<CalendarProcess> active = CalendarProcessReader.active(observed, now);
        List<CalendarProcess> finished = CalendarProcessReader.recentlyFinished(observed, now);
        List<IdleProcessState.IdleRole> visible =
                IdleProcessState.synchronize(context, active, finished, observed, now);
        Set<String> keys = enabledKeys(context);
        for (String key : new HashSet<>(keys)) {
            IdleProcessState.IdleRole idle = IdleProcessState.find(context, key);
            if (idle == null || !idle.reminderEnabled) {
                keys.remove(key);
                cancelAlarm(context, key);
                continue;
            }
            if (IdleProcessState.isRoleActive(active, key)) {
                cancelAlarm(context, key);
            } else {
                schedule(context, idle, now);
            }
        }
        saveEnabledKeys(context, keys);
        sync(context, active, visible, now);
    }

    static PendingIntent toggleIntent(Context context, IdleProcessState.IdleRole idle) {
        return toggleIntent(context, idle == null ? null : idle.key);
    }

    static PendingIntent toggleIntent(Context context, String key) {
        Intent intent = new Intent(context, NowBarActionReceiver.class)
                .setAction(ACTION_TOGGLE)
                .putExtra(EXTRA_ROLE_KEY, key);
        return PendingIntent.getBroadcast(context, requestCode(key, 2), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent dismissRowIntent(Context context, IdleProcessState.IdleRole idle) {
        Intent intent = baseIntent(context, ACTION_DISMISS_ROW, idle);
        return PendingIntent.getBroadcast(context, requestCode(idle.key, 3), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context context, IdleProcessState.IdleRole idle, long nowMillis) {
        long when = idle.nextReminderAtMillis;
        if (when <= nowMillis) {
            when = Math.max(nowMillis + 1_000L,
                    idle.lastFinishedMillis + IdleProcessState.cadenceMillis(context));
            if (when <= nowMillis) when = nowMillis + IdleProcessState.cadenceMillis(context);
            IdleProcessState.setNextReminderAt(context, idle.key, when);
        }
        scheduleAt(context, idle, when);
    }

    private static void scheduleAt(Context context, IdleProcessState.IdleRole idle, long whenMillis) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pending = fireIntent(context, idle);
        try {
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending);
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending);
            }
        } catch (SecurityException exception) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending);
        }
    }

    private static PendingIntent fireIntent(Context context, IdleProcessState.IdleRole idle) {
        return PendingIntent.getBroadcast(context, requestCode(idle.key, 1),
                baseIntent(context, ACTION_FIRE, idle),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent baseIntent(Context context, String action, IdleProcessState.IdleRole idle) {
        return new Intent(context, NowBarActionReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_ROLE_KEY, idle.key)
                .putExtra(EXTRA_FINISHED_AT, idle.lastFinishedMillis);
    }

    private static void cancelAlarm(Context context, String key) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null || key == null) return;
        Intent intent = new Intent(context, NowBarActionReceiver.class).setAction(ACTION_FIRE);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode(key, 1), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            alarms.cancel(pending);
            pending.cancel();
        }
    }

    private static void dismissSurface(Context context, String key) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        // Clean any legacy separate reminder card left by an older build/runtime.
        if (manager != null) manager.cancel(notificationId(key));
        IdleReminderOverlayService.dismiss(context, key);
    }

    private static void ensureChannel(NotificationManager manager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Idle process reminders", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Reminders when a watched GPT role has become idle");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private static Set<String> enabledKeys(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new HashSet<>(preferences.getStringSet(KEY_ENABLED_KEYS, Collections.emptySet()));
    }

    private static void saveEnabledKeys(Context context, Set<String> keys) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_ENABLED_KEYS, new HashSet<>(keys)).apply();
    }

    private static int requestCode(String key, int kind) {
        int hash = key == null ? 0 : key.hashCode() & 0x7fffffff;
        return REQUEST_BASE + kind * 10000 + (hash % 9000);
    }

    private static int notificationId(String key) {
        int hash = key == null ? 0 : key.hashCode() & 0x7fffffff;
        return NOTIFICATION_BASE + (hash % 9000);
    }
}
