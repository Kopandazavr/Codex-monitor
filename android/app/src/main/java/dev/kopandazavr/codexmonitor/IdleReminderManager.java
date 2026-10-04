package dev.kopandazavr.codexmonitor;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owns recurring local reminder alarms for idle watchdog roles. */
final class IdleReminderManager {
    static final String ACTION_FIRE = "dev.kopandazavr.codexmonitor.action.IDLE_REMINDER_FIRE";
    static final String ACTION_TOGGLE = "dev.kopandazavr.codexmonitor.action.IDLE_REMINDER_TOGGLE";
    static final String ACTION_DISMISS_ROW = "dev.kopandazavr.codexmonitor.action.IDLE_ROW_DISMISS";
    static final String ACTION_COMPLETION_OVERLAY =
            "dev.kopandazavr.codexmonitor.action.IDLE_COMPLETION_OVERLAY";
    static final String EXTRA_CONTAINER_ID = "idle_container_id";
    static final String EXTRA_ROLE_KEY = "idle_role_key";
    static final String EXTRA_INSTANCE_ID = "idle_instance_id";
    static final String EXTRA_EVENT_ID = "idle_event_id";
    static final String EXTRA_FINISHED_AT = "idle_finished_at";

    private static final String PREFS = "codex_idle_reminder_scheduler_v1";
    private static final String KEY_ENABLED_KEYS = "enabled_role_keys";
    // Preserve the existing preference key so upgrades retain completion dedupe state.
    private static final String KEY_COMPLETION_DELIVERED_PREFIX = "overlay_finished:";
    private static final long COMPLETION_FRESH_MS = 3L * 60_000L;
    private static final long COMPLETION_OVERLAY_ALARM_DELAY_MS = 1L;
    private static final String CHANNEL_ID = AlertSoundManager.OPERATIONAL_CHANNEL_ID;
    // Legacy separate reminder IDs are retained only so old cards can be cleaned up.
    private static final int NOTIFICATION_BASE = 31000;
    private static final int REQUEST_BASE = 41000;

    private IdleReminderManager() {
    }

    static void sync(Context context, List<CalendarProcess> active,
            List<IdleProcessState.IdleRole> visibleIdle, long nowMillis) {
        sync(context, AccountContainerStore.selectedId(context),
                active, visibleIdle, nowMillis);
    }

    static void sync(Context context, String containerId, List<CalendarProcess> active,
            List<IdleProcessState.IdleRole> visibleIdle, long nowMillis) {
        if (context == null) return;
        Set<String> enabledKeys = enabledKeys(context, containerId);
        if (active != null) {
            for (CalendarProcess process : active) {
                String key = IdleProcessState.roleKey(context, process);
                cancelAlarm(context, containerId, key);
                clearLegacyReminderCard(context, containerId, key);
            }
        }
        for (IdleProcessState.IdleRole completion :
                IdleProcessState.recentCompletions(
                        context, containerId, nowMillis, COMPLETION_FRESH_MS)) {
            if (!completion.reminderEnabled) continue;
            enabledKeys.add(completion.key);
            deliverFreshCompletion(context, containerId, completion, nowMillis);
        }
        if (visibleIdle != null) {
            for (IdleProcessState.IdleRole idle : visibleIdle) {
                if (!idle.reminderEnabled) continue;
                enabledKeys.add(idle.key);
                schedule(context, containerId, idle, nowMillis);
            }
        }
        saveEnabledKeys(context, containerId, enabledKeys);
    }

    static void onReminderToggled(Context context, String key, boolean enabled, long nowMillis) {
        onReminderToggled(context, AccountContainerStore.selectedId(context),
                key, enabled, nowMillis);
    }

    static void onReminderToggled(Context context, String containerId, String key,
            boolean enabled, long nowMillis) {
        Set<String> keys = enabledKeys(context, containerId);
        if (enabled) {
            keys.add(key);
            IdleProcessState.IdleRole idle = IdleProcessState.find(context, containerId, key);
            if (idle != null) {
                markCompletionBaseline(context, containerId, idle);
                schedule(context, containerId, idle, nowMillis);
            }
        } else {
            keys.remove(key);
            cancelAlarm(context, containerId, key);
            cancelCompletionOverlayAlarm(context, containerId, key);
            clearLegacyReminderCard(context, containerId, key);
        }
        saveEnabledKeys(context, containerId, keys);
    }

    static void dismissRowFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String containerId = containerFromIntent(context, intent);
        String key = intent.getStringExtra(EXTRA_ROLE_KEY);
        long finished = intent.getLongExtra(EXTRA_FINISHED_AT, 0L);
        if (key == null || key.trim().isEmpty() || finished <= 0L) return;
        IdleProcessState.dismiss(context, containerId, key, finished);
        DualUsageNotificationManager.repostForProcessChangeDelayed(
                context, containerId, 120L);
    }

    static void toggleFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String containerId = containerFromIntent(context, intent);
        String key = intent.getStringExtra(EXTRA_ROLE_KEY);
        if (key == null || key.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean enabled = IdleProcessState.toggleReminder(
                context, containerId, key, now);
        onReminderToggled(context, containerId, key, enabled, now);
        DualUsageNotificationManager.repostForProcessChangeDelayed(
                context, containerId, 120L);
    }

    static void fireFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String containerId = containerFromIntent(context, intent);
        String key = intent.getStringExtra(EXTRA_ROLE_KEY);
        String instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID);
        long eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L);
        long expectedFinished = intent.getLongExtra(EXTRA_FINISHED_AT, 0L);
        IdleProcessState.IdleRole idle = IdleProcessState.findCompletion(
                context, containerId, key, instanceId, eventId, expectedFinished);
        if (idle == null || !idle.reminderEnabled) {
            if (key != null) cancelAlarm(context, containerId, key);
            return;
        }
        long now = System.currentTimeMillis();
        List<CalendarProcess> active = CalendarProcessReader.active(context, containerId, now);
        if (IdleProcessState.isRoleActive(context, active, key)) {
            cancelAlarm(context, containerId, key);
            clearLegacyReminderCard(context, containerId, key);
            return;
        }

        // Recurring idle alarms never create completion overlays. Completion delivery remains
        // a one-shot path owned by sync()/deliverFreshCompletion().
        boolean overlayShown = false;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean persistentSurfaceAlerted = false;
        if (manager != null) {
            AlertSoundManager.ensureChannels(context);
            persistentSurfaceAlerted = ProcessNotificationManager.reAlertIdleReminder(
                    context, containerId, idle, CHANNEL_ID, now);
            if (persistentSurfaceAlerted) {
                DualUsageNotificationManager.repostForProcessChangeDelayed(
                        context, containerId, 5_000L);
            }
        }
        long next = now + IdleProcessState.cadenceMillis(context);
        IdleProcessState.setNextReminderAt(context, containerId, key, next);
        scheduleAt(context, containerId, idle, next);
        DiagnosticLog.info(context, "idle_process", "reminder_fired",
                "container_id", containerId,
                "role", idle.displayLabel(),
                "overlay", overlayShown,
                "persistent_surface_alerted", persistentSurfaceAlerted);
    }

    private static void deliverFreshCompletion(Context context,
            IdleProcessState.IdleRole idle, long nowMillis) {
        deliverFreshCompletion(context, AccountContainerStore.selectedId(context),
                idle, nowMillis);
    }

    private static void deliverFreshCompletion(Context context, String containerId,
            IdleProcessState.IdleRole idle, long nowMillis) {
        if (context == null || idle == null || !idle.reminderEnabled
                || idle.lastFinishedMillis <= 0L || nowMillis < idle.lastFinishedMillis) {
            return;
        }
        long age = nowMillis - idle.lastFinishedMillis;
        if (age > COMPLETION_FRESH_MS) return;

        SharedPreferences prefs = preferences(context);
        String key = completionPreferenceKey(containerId, idle);
        if (prefs.getLong(key, 0L) == idle.lastFinishedMillis) return;

        // Mark before side effects so the same logical completion cannot recursively re-enter.
        prefs.edit().putLong(key, idle.lastFinishedMillis).apply();

        // Android 15+ requires a background-FGS exemption here. The exact alarm owns
        // that exemption, but it must also own the ordering: posting the audible notification
        // immediately can let Samsung/SystemUI finish the alert tone before this alarm is
        // delivered. When scheduling succeeds, defer the notification to the overlay service,
        // which posts it synchronously after WindowManager.addView(). If scheduling is
        // unavailable, preserve notification delivery as a direct fallback.
        boolean overlayScheduled = scheduleCompletionOverlay(context, containerId, idle);
        boolean completionNotificationPosted = false;
        if (!overlayScheduled) {
            completionNotificationPosted =
                    ProcessNotificationManager.postCompletionAlert(
                            context, containerId, idle, nowMillis);
        }
        DiagnosticLog.info(context, "idle_process", "completion_dispatched",
                "role", idle.displayLabel(), "finished_at", idle.lastFinishedMillis,
                "overlay_scheduled", overlayScheduled,
                "completion_notification_delegated_to_overlay", overlayScheduled,
                "completion_notification_posted_fallback", completionNotificationPosted);
    }

    private static void markCompletionBaseline(Context context, IdleProcessState.IdleRole idle) {
        markCompletionBaseline(context, AccountContainerStore.selectedId(context), idle);
    }

    private static void markCompletionBaseline(Context context, String containerId,
            IdleProcessState.IdleRole idle) {
        if (context == null || idle == null || idle.lastFinishedMillis <= 0L) return;
        preferences(context).edit()
                .putLong(completionPreferenceKey(containerId, idle), idle.lastFinishedMillis)
                .apply();
    }


    static void completionOverlayFromIntent(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String containerId = containerFromIntent(context, intent);
        String key = intent.getStringExtra(EXTRA_ROLE_KEY);
        String instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID);
        long eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L);
        long expectedFinished = intent.getLongExtra(EXTRA_FINISHED_AT, 0L);
        IdleProcessState.IdleRole idle = IdleProcessState.findCompletion(
                context, containerId, key, instanceId, eventId, expectedFinished);
        if (idle == null || !idle.reminderEnabled) {
            DiagnosticLog.info(context, "idle_process", "completion_overlay_alarm_ignored",
                    "container_id", containerId,
                    "reason", "stale_or_disabled");
            return;
        }
        boolean requested = IdleReminderOverlayService.showCompletion(
                context, containerId, idle);
        if (!requested) {
            ProcessNotificationManager.postCompletionAlert(
                    context, containerId, idle, System.currentTimeMillis());
        } else {
            Context app = context.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> ProcessNotificationManager.postCompletionAlert(
                            app, containerId, idle, System.currentTimeMillis()),
                    2_000L);
        }
        DiagnosticLog.info(context, "idle_process", "completion_overlay_alarm_received",
                "container_id", containerId,
                "role", idle.displayLabel(),
                "overlay_start_requested", requested,
                "notification_owned_by_overlay_service", requested);
    }

    private static boolean scheduleCompletionOverlay(Context context,
            IdleProcessState.IdleRole idle) {
        return scheduleCompletionOverlay(context, AccountContainerStore.selectedId(context), idle);
    }

    private static boolean scheduleCompletionOverlay(Context context, String containerId,
            IdleProcessState.IdleRole idle) {
        if (!IdleReminderOverlayService.canDraw(context)) {
            DiagnosticLog.warn(context, "idle_process", "completion_overlay_not_scheduled",
                    "reason", "overlay_permission_missing");
            return false;
        }
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            DiagnosticLog.warn(context, "idle_process", "completion_overlay_not_scheduled",
                    "reason", "alarm_manager_missing");
            return false;
        }
        if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
            DiagnosticLog.warn(context, "idle_process", "completion_overlay_not_scheduled",
                    "reason", "exact_alarm_access_missing");
            return false;
        }
        long triggerAt = System.currentTimeMillis() + COMPLETION_OVERLAY_ALARM_DELAY_MS;
        PendingIntent pending = completionOverlayIntent(context, containerId, idle);
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            DiagnosticLog.info(context, "idle_process", "completion_overlay_alarm_scheduled",
                    "delay_ms", COMPLETION_OVERLAY_ALARM_DELAY_MS,
                    "role", idle.displayLabel());
            return true;
        } catch (SecurityException exception) {
            DiagnosticLog.warn(context, "idle_process", "completion_overlay_not_scheduled",
                    "reason", "exact_alarm_security_exception");
            return false;
        }
    }

    private static PendingIntent completionOverlayIntent(Context context,
            IdleProcessState.IdleRole idle) {
        return completionOverlayIntent(context, AccountContainerStore.selectedId(context), idle);
    }

    private static PendingIntent completionOverlayIntent(Context context, String containerId,
            IdleProcessState.IdleRole idle) {
        return PendingIntent.getBroadcast(context,
                requestCode(containerId, completionIntentKey(idle), 4),
                baseIntent(context, containerId, ACTION_COMPLETION_OVERLAY, idle),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelCompletionOverlayAlarm(Context context, String key) {
        cancelCompletionOverlayAlarm(context, AccountContainerStore.selectedId(context), key);
    }

    private static void cancelCompletionOverlayAlarm(Context context, String containerId,
            String key) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null || key == null) return;
        long now = System.currentTimeMillis();
        for (IdleProcessState.IdleRole completion :
                IdleProcessState.recentCompletions(
                        context, containerId, now, COMPLETION_FRESH_MS)) {
            if (!key.equals(completion.key)) continue;
            Intent intent = new Intent(context, NowBarActionReceiver.class)
                    .setAction(ACTION_COMPLETION_OVERLAY)
                    .putExtra(EXTRA_CONTAINER_ID, containerId);
            PendingIntent pending = PendingIntent.getBroadcast(context,
                    requestCode(containerId, completionIntentKey(completion), 4), intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pending != null) {
                alarms.cancel(pending);
                pending.cancel();
            }
        }
    }

    static void restore(Context context) {
        if (context == null) return;
        for (AccountContainerStore.Account account : AccountContainerStore.all(context)) {
            restore(context, account.id);
        }
    }

    private static void restore(Context context, String containerId) {
        long now = System.currentTimeMillis();
        List<CalendarProcess> observed =
                CalendarProcessReader.observed(context, containerId, now);
        List<CalendarProcess> active = CalendarProcessReader.active(observed, now);
        List<CalendarProcess> finished =
                CalendarProcessReader.recentlyFinished(observed, now);
        List<IdleProcessState.IdleRole> visible =
                IdleProcessState.synchronize(
                        context, containerId, active, finished, observed, now);
        Set<String> keys = enabledKeys(context, containerId);
        for (String key : new HashSet<>(keys)) {
            IdleProcessState.IdleRole idle =
                    IdleProcessState.find(context, containerId, key);
            if (idle == null || !idle.reminderEnabled) {
                keys.remove(key);
                cancelAlarm(context, containerId, key);
                continue;
            }
            if (IdleProcessState.isRoleActive(context, active, key)) {
                cancelAlarm(context, containerId, key);
            } else {
                schedule(context, containerId, idle, now);
            }
        }
        saveEnabledKeys(context, containerId, keys);
        sync(context, containerId, active, visible, now);
    }

    static PendingIntent toggleIntent(Context context, IdleProcessState.IdleRole idle) {
        return toggleIntent(context, AccountContainerStore.selectedId(context), idle);
    }

    static PendingIntent toggleIntent(Context context, String containerId,
            IdleProcessState.IdleRole idle) {
        return toggleIntent(context, containerId, idle == null ? null : idle.key);
    }

    static PendingIntent toggleIntent(Context context, String key) {
        return toggleIntent(context, AccountContainerStore.selectedId(context), key);
    }

    static PendingIntent toggleIntent(Context context, String containerId, String key) {
        Intent intent = new Intent(context, NowBarActionReceiver.class)
                .setAction(ACTION_TOGGLE)
                .putExtra(EXTRA_CONTAINER_ID, containerId)
                .putExtra(EXTRA_ROLE_KEY, key);
        return PendingIntent.getBroadcast(context, requestCode(containerId, key, 2), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent dismissRowIntent(Context context, IdleProcessState.IdleRole idle) {
        Intent intent = baseIntent(context, ACTION_DISMISS_ROW, idle);
        return PendingIntent.getBroadcast(context, requestCode(idle.key, 3), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context context, IdleProcessState.IdleRole idle,
            long nowMillis) {
        schedule(context, AccountContainerStore.selectedId(context), idle, nowMillis);
    }

    private static void schedule(Context context, String containerId,
            IdleProcessState.IdleRole idle, long nowMillis) {
        long when = idle.nextReminderAtMillis;
        if (when <= nowMillis) {
            when = Math.max(nowMillis + 1_000L,
                    idle.lastFinishedMillis + IdleProcessState.cadenceMillis(context));
            if (when <= nowMillis) when = nowMillis + IdleProcessState.cadenceMillis(context);
            IdleProcessState.setNextReminderAt(context, containerId, idle.key, when);
        }
        scheduleAt(context, containerId, idle, when);
    }

    private static void scheduleAt(Context context, IdleProcessState.IdleRole idle,
            long whenMillis) {
        scheduleAt(context, AccountContainerStore.selectedId(context), idle, whenMillis);
    }

    private static void scheduleAt(Context context, String containerId,
            IdleProcessState.IdleRole idle, long whenMillis) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pending = fireIntent(context, containerId, idle);
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
        return fireIntent(context, AccountContainerStore.selectedId(context), idle);
    }

    private static PendingIntent fireIntent(Context context, String containerId,
            IdleProcessState.IdleRole idle) {
        return PendingIntent.getBroadcast(context,
                requestCode(containerId, idle.key, 1),
                baseIntent(context, containerId, ACTION_FIRE, idle),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent baseIntent(Context context, String action, IdleProcessState.IdleRole idle) {
        return baseIntent(context, AccountContainerStore.selectedId(context), action, idle);
    }

    private static Intent baseIntent(Context context, String containerId, String action,
            IdleProcessState.IdleRole idle) {
        return new Intent(context, NowBarActionReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_CONTAINER_ID, containerId)
                .putExtra(EXTRA_ROLE_KEY, idle.key)
                .putExtra(EXTRA_INSTANCE_ID, idle.instanceId)
                .putExtra(EXTRA_EVENT_ID, idle.eventId)
                .putExtra(EXTRA_FINISHED_AT, idle.lastFinishedMillis);
    }

    private static void cancelAlarm(Context context, String key) {
        cancelAlarm(context, AccountContainerStore.selectedId(context), key);
    }

    private static void cancelAlarm(Context context, String containerId, String key) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null || key == null) return;
        Intent intent = new Intent(context, NowBarActionReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_CONTAINER_ID, containerId);
        PendingIntent pending = PendingIntent.getBroadcast(context,
                requestCode(containerId, key, 1), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            alarms.cancel(pending);
            pending.cancel();
        }
    }

    private static void clearLegacyReminderCard(Context context, String key) {
        clearLegacyReminderCard(context, AccountContainerStore.selectedId(context), key);
    }

    private static void clearLegacyReminderCard(Context context, String containerId, String key) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.cancel(AccountNotificationNamespace.tag(containerId), notificationId(key));
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            manager.cancel(notificationId(key));
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> enabledKeys(Context context) {
        return enabledKeys(context, AccountContainerStore.selectedId(context));
    }

    private static Set<String> enabledKeys(Context context, String containerId) {
        SharedPreferences prefs = preferences(context);
        String key = scopedKey(containerId, KEY_ENABLED_KEYS);
        Set<String> fallback = AccountContainerStore.isLegacyOwner(context, containerId)
                ? prefs.getStringSet(KEY_ENABLED_KEYS, Collections.emptySet())
                : Collections.emptySet();
        return new HashSet<>(prefs.getStringSet(key, fallback));
    }

    private static void saveEnabledKeys(Context context, Set<String> keys) {
        saveEnabledKeys(context, AccountContainerStore.selectedId(context), keys);
    }

    private static void saveEnabledKeys(Context context, String containerId, Set<String> keys) {
        preferences(context).edit()
                .putStringSet(scopedKey(containerId, KEY_ENABLED_KEYS), new HashSet<>(keys))
                .apply();
    }

    private static String containerFromIntent(Context context, Intent intent) {
        String requested = intent == null ? "" : intent.getStringExtra(EXTRA_CONTAINER_ID);
        if (requested != null) requested = requested.trim();
        if (requested != null && !requested.isEmpty()
                && AccountContainerStore.find(context, requested) != null) {
            return requested;
        }
        return AccountContainerStore.selectedId(context);
    }

    private static String scopedKey(String containerId, String base) {
        return base + "::" + AccountNotificationNamespace.safe(containerId);
    }

    private static String completionIntentKey(IdleProcessState.IdleRole idle) {
        if (idle == null) return "";
        return idle.key + ":" + idle.completionIdentity() + ":" + idle.lastFinishedMillis;
    }

    private static String completionPreferenceKey(IdleProcessState.IdleRole idle) {
        return completionPreferenceKey("", idle);
    }

    private static String completionPreferenceKey(String containerId,
            IdleProcessState.IdleRole idle) {
        String base = KEY_COMPLETION_DELIVERED_PREFIX + completionIntentKey(idle);
        return containerId == null || containerId.trim().isEmpty()
                ? base : scopedKey(containerId, base);
    }

    private static int requestCode(String containerId, String key, int kind) {
        String stable = AccountNotificationNamespace.safe(containerId) + "|"
                + (key == null ? "" : key);
        int hash = stable.hashCode() & 0x7fffffff;
        return REQUEST_BASE + kind * 10000 + (hash % 9000);
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
