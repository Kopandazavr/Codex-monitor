package dev.kopandazavr.codexmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Renders active and idle calendar-backed process roles. */
final class ProcessNotificationManager {
    private static final String CHANNEL_ID = AlertSoundManager.OPERATIONAL_CHANNEL_ID;
    private static final int GROUPED_NOTIFICATION_ID = 8620;
    private static final int PROCESS_NOTIFICATION_BASE = 12000;
    private static final int PROCESS_NOTIFICATION_RANGE = 12000;
    private static final int IDLE_NOTIFICATION_BASE = 24000;
    private static final int REQUEST_CONTENT = 9780;
    private static final int COMPLETION_NOTIFICATION_BASE = 52000;
    private static final int COMPLETION_NOTIFICATION_RANGE = 10000;
    private static final String PREFS = "codex_process_notification_state_v1";
    private static final String KEY_ACTIVE_IDS = "active_ids";
    private static final String KEY_COMPLETION_POSTED_PREFIX = "completion_posted:";
    private static final String KEY_COMPLETION_RECONCILED_VERSION = "completion_reconciled_version";

    private ProcessNotificationManager() {
    }

    /** Retired per-process/grouped surfaces are cleaned but never republished. */
    static void sync(Context context, List<CalendarProcess> processes,
            List<IdleProcessState.IdleRole> idleRoles, String mode, long nowMillis) {
        sync(context, AccountContainerStore.selectedId(context),
                processes, idleRoles, mode, nowMillis);
    }

    static void sync(Context context, String containerId, List<CalendarProcess> processes,
            List<IdleProcessState.IdleRole> idleRoles, String mode, long nowMillis) {
        if (context == null) return;
        reconcileStaleCompletionAlerts(context, containerId);
        clearAll(context, containerId);
    }

    /** An idle-role attention event is independent from visibility of the persistent card. */
    static boolean reAlertIdleReminder(Context context, IdleProcessState.IdleRole idle,
            String alertChannelId, long nowMillis) {
        return reAlertIdleReminder(context, AccountContainerStore.selectedId(context),
                idle, alertChannelId, nowMillis);
    }

    static boolean reAlertIdleReminder(Context context, String containerId,
            IdleProcessState.IdleRole idle, String alertChannelId, long nowMillis) {
        if (context == null || idle == null || alertChannelId == null) return false;
        return DualUsageNotificationManager.realertUsageSurface(
                context, containerId, alertChannelId,
                notificationIdentity(context, idle.project, idle.role, "", false) + " is idle",
                "Idle reminder · " + formatIdle(nowMillis - idle.lastFinishedMillis));
    }

    /**
     * Posts one fresh completion notification on the user-controlled completion channel.
     *
     * <p>Do not recycle an already-posted operational notification ID for this attention edge:
     * Samsung can treat that as a silent update even when the replacement names an audible
     * channel. The durable idle/process surface stays on the operational channel; this event
     * notification is the single completion alert and is deduped by IdleReminderManager.</p>
     */
    static synchronized boolean postCompletionAlert(Context context,
            IdleProcessState.IdleRole idle, long nowMillis) {
        return postCompletionAlert(context, AccountContainerStore.selectedId(context),
                idle, nowMillis);
    }

    static synchronized boolean postCompletionAlert(Context context, String containerId,
            IdleProcessState.IdleRole idle, long nowMillis) {
        if (context == null || idle == null) return false;
        SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String postedKey = scopedKey(containerId, KEY_COMPLETION_POSTED_PREFIX
                + idle.key + ":" + idle.completionIdentity());
        if (idle.lastFinishedMillis > 0L
                && state.getLong(postedKey, 0L) == idle.lastFinishedMillis) {
            DiagnosticLog.info(context, "notification", "completion_notification_deduped",
                    "role", idle.displayLabel(),
                    "finished_at", idle.lastFinishedMillis);
            return true;
        }
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
        AlertSoundManager.ensureChannels(context);

        Intent open = new Intent(context, MainActivity.class)
                .putExtra(OAuthService.EXTRA_CONTAINER_ID, containerId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context,
                AccountNotificationNamespace.requestCode(
                        containerId, "completion_content"), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String identity = notificationIdentity(
                context, idle.project, idle.role, idle.topic, false);
        boolean speakerRequested = AlertSoundManager.playOnPhoneSpeaker(context);
        boolean silentSpeakerDelivery =
                CompletionAudioRoute.useSilentNotificationDelivery(speakerRequested);
        String completionChannelId = AlertSoundManager.completionNotificationChannelId(
                context, silentSpeakerDelivery);
        Notification notification = new Notification.Builder(context, completionChannelId)
                .setSmallIcon(R.drawable.ic_notification_codex_monitor)
                .setContentTitle(accountTitle(
                        context, containerId, identity + " finished"))
                .setContentText("Watched process completed")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(Color.rgb(3, 129, 254))
                .setWhen(idle.lastFinishedMillis > 0L ? idle.lastFinishedMillis : nowMillis)
                .setShowWhen(true)
                .build();
        try {
            int notificationId = completionNotificationId(idle.key);
            manager.notify(AccountNotificationNamespace.tag(containerId),
                    notificationId, notification);
            if (idle.lastFinishedMillis > 0L) {
                state.edit().putLong(postedKey, idle.lastFinishedMillis).apply();
            }
            boolean speakerSoundPlayed = false;
            if (CompletionAudioRoute.playDirectSound(speakerRequested)) {
                speakerSoundPlayed = AlertSoundManager.playProcessCompletion(context);
            }
            DiagnosticLog.info(context, "notification", "completion_notification_posted",
                    "role", idle.displayLabel(),
                    "notification_id", notificationId,
                    "channel", completionChannelId,
                    "speaker_sound_played", speakerSoundPlayed);
            return true;
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "notification", "completion_notification_failed",
                    exception, "role", idle.displayLabel());
            return false;
        }
    }

    private static int completionNotificationId(String key) {
        int hash = key == null ? 0 : key.hashCode();
        return COMPLETION_NOTIFICATION_BASE
                + Math.floorMod(hash, COMPLETION_NOTIFICATION_RANGE);
    }

    static void clearCompletionAlert(Context context, String key) {
        clearCompletionAlert(context, AccountContainerStore.selectedId(context), key);
    }

    static void clearCompletionAlert(Context context, String containerId, String key) {
        if (context == null || key == null || key.trim().isEmpty()) return;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        int notificationId = completionNotificationId(key);
        manager.cancel(AccountNotificationNamespace.tag(containerId), notificationId);
        DiagnosticLog.info(context, "notification", "completion_notification_cleared",
                "container_id", containerId,
                "notification_id", notificationId,
                "role_key", key);
    }

    static void clearCompletionAlerts(Context context, Iterable<String> keys) {
        clearCompletionAlerts(context, AccountContainerStore.selectedId(context), keys);
    }

    static void clearCompletionAlerts(Context context, String containerId, Iterable<String> keys) {
        if (keys == null) return;
        for (String key : keys) clearCompletionAlert(context, containerId, key);
    }

    private static void reconcileStaleCompletionAlerts(Context context) {
        reconcileStaleCompletionAlerts(context, AccountContainerStore.selectedId(context));
    }

    private static void reconcileStaleCompletionAlerts(Context context, String containerId) {
        SharedPreferences state = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String versionKey = scopedKey(containerId, KEY_COMPLETION_RECONCILED_VERSION);
        if (state.getInt(versionKey, 0) >= AppConstants.VERSION_CODE) {
            return;
        }
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        int cleared = 0;
        String tag = AccountNotificationNamespace.tag(containerId);
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                StatusBarNotification[] active = manager.getActiveNotifications();
                if (active != null) {
                    for (StatusBarNotification item : active) {
                        if (item == null) continue;
                        int id = item.getId();
                        if (id < COMPLETION_NOTIFICATION_BASE
                                || id >= COMPLETION_NOTIFICATION_BASE
                                        + COMPLETION_NOTIFICATION_RANGE) {
                            continue;
                        }
                        String itemTag = item.getTag();
                        boolean accountMatch = tag.equals(itemTag);
                        boolean legacyMatch = itemTag == null
                                && AccountContainerStore.isLegacyOwner(context, containerId);
                        if (!accountMatch && !legacyMatch) continue;
                        if (accountMatch) {
                            manager.cancel(tag, id);
                        } else {
                            manager.cancel(id);
                        }
                        cleared++;
                    }
                }
            }
            SharedPreferences.Editor editor = state.edit()
                    .putInt(versionKey, AppConstants.VERSION_CODE);
            if (AccountContainerStore.isLegacyOwner(context, containerId)) {
                editor.remove(KEY_COMPLETION_RECONCILED_VERSION);
            }
            editor.apply();
            DiagnosticLog.info(context, "notification",
                    "completion_notification_reconciled",
                    "container_id", containerId,
                    "version_code", AppConstants.VERSION_CODE,
                    "cleared", cleared);
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "notification",
                    "completion_notification_reconcile_failed",
                    "container_id", containerId,
                    "error", exception.getClass().getSimpleName());
        }
    }

    static void clearAll(Context context) {
        clearAll(context, AccountContainerStore.selectedId(context));
    }

    static void clearAll(Context context, String containerId) {
        if (context == null) return;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.cancel(AccountNotificationNamespace.tag(containerId), GROUPED_NOTIFICATION_ID);
        clearPerProcess(context, containerId, manager);
    }

    static void addRows(Context context, RemoteViews parent, int viewContainerId,
            List<CalendarProcess> processes, List<IdleProcessState.IdleRole> idleRoles,
            long nowMillis) {
        addRows(context, AccountContainerStore.selectedId(context), parent, viewContainerId,
                processes, idleRoles, nowMillis, true);
    }

    static void addRows(Context context, RemoteViews parent, int viewContainerId,
            List<CalendarProcess> processes, List<IdleProcessState.IdleRole> idleRoles,
            long nowMillis, boolean showActiveReminder) {
        addRows(context, AccountContainerStore.selectedId(context), parent, viewContainerId,
                processes, idleRoles, nowMillis, showActiveReminder);
    }

    static void addRows(Context context, String accountId, RemoteViews parent, int viewContainerId,
            List<CalendarProcess> processes, List<IdleProcessState.IdleRole> idleRoles,
            long nowMillis, boolean showActiveReminder) {
        parent.removeAllViews(viewContainerId);
        for (ProcessRoleGroup group : ProcessRoleGroup.group(context, processes)) {
            parent.addView(viewContainerId,
                    buildActiveGroupRow(context, accountId, group, nowMillis,
                            showActiveReminder));
        }
        if (idleRoles != null) {
            for (IdleProcessState.IdleRole idle : idleRoles) {
                parent.addView(viewContainerId,
                        buildIdleRow(context, accountId, idle, nowMillis));
            }
        }
    }

    /** One-line collapsed process summary shared by combined, grouped, and one-each modes. */
    static String collapsedSummary(Context context, List<CalendarProcess> processes,
            List<IdleProcessState.IdleRole> idleRoles, long nowMillis) {
        int activeCount = processes == null ? 0 : processes.size();
        int idleCount = idleRoles == null ? 0 : idleRoles.size();
        int total = activeCount + idleCount;
        if (activeCount > 0) {
            ProcessRoleGroup group = ProcessRoleGroup.group(context, processes).get(0);
            CalendarProcess process = group.representative();
            boolean single = group.processes.size() == 1;
            StringBuilder summary = new StringBuilder(notificationIdentity(
                    context, process.project, process.role, process.topic, single));
            if (single) {
                appendSummaryPart(summary, process.remainingPercent(nowMillis) + "%");
                appendSummaryPart(summary, formatRemaining(process.remainingMillis(nowMillis)));
            } else {
                appendSummaryPart(summary, group.processes.size() + " sessions");
            }
            appendMore(summary, total - group.processes.size());
            return summary.toString();
        }
        if (idleCount > 0) {
            IdleProcessState.IdleRole idle = idleRoles.get(0);
            StringBuilder summary = new StringBuilder(notificationIdentity(
                    context, idle.project, idle.role, "", false));
            appendSummaryPart(summary, "idle " + formatIdle(nowMillis - idle.lastFinishedMillis));
            appendMore(summary, total - 1);
            return summary.toString();
        }
        return "";
    }

    private static void clearPerProcess(Context context, String containerId,
            NotificationManager manager) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String activeKey = scopedKey(containerId, KEY_ACTIVE_IDS);
        Set<String> activeIds = new HashSet<>(preferences.getStringSet(activeKey,
                AccountContainerStore.isLegacyOwner(context, containerId)
                        ? preferences.getStringSet(KEY_ACTIVE_IDS, Collections.emptySet())
                        : Collections.emptySet()));
        for (String id : activeIds) {
            try {
                manager.cancel(AccountNotificationNamespace.tag(containerId),
                        Integer.parseInt(id));
            } catch (NumberFormatException ignored) {
            }
        }
        SharedPreferences.Editor clear = preferences.edit().remove(activeKey);
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            clear.remove(KEY_ACTIVE_IDS);
        }
        clear.apply();
    }

    private static RemoteViews buildActiveGroupRow(Context context, String accountId,
            ProcessRoleGroup group, long nowMillis, boolean showReminder) {
        CalendarProcess process = group == null ? null : group.representative();
        if (process == null) {
            return new RemoteViews(context.getPackageName(), R.layout.notification_process_group_row);
        }
        RemoteViews row = new RemoteViews(
                context.getPackageName(), R.layout.notification_process_group_row);
        int textColor = textColor(context);
        ProjectProfileStore.Profile profile = ProjectProfileStore.resolve(context, process.project);
        String projectIdentity = ProjectProfileStore.effectiveShort(profile);
        row.setTextViewText(R.id.notification_process_project, projectIdentity);
        row.setTextColor(R.id.notification_process_project,
                ProjectProfileStore.accentColor(profile));
        row.setViewVisibility(R.id.notification_process_project,
                projectIdentity.isEmpty() ? View.GONE : View.VISIBLE);

        boolean single = group.processes.size() == 1;
        String roleAndTopic = RoleProfileStore.displayLabel(
                context, process.role, process.roleIcon);
        if (single && !clean(process.topic).isEmpty()) {
            roleAndTopic = roleAndTopic.isEmpty()
                    ? clean(process.topic) : roleAndTopic + " · " + clean(process.topic);
        }
        row.setTextViewText(R.id.notification_process_title, roleAndTopic);
        row.setTextColor(R.id.notification_process_title, textColor);
        row.setTextViewText(R.id.notification_process_remaining,
                single ? formatRemaining(process.remainingMillis(nowMillis))
                        : group.processes.size() + " active");
        row.setTextColor(R.id.notification_process_remaining, textColor);

        row.removeAllViews(R.id.notification_process_instance_bars);
        for (CalendarProcess instance : group.processes) {
            RemoteViews bar = new RemoteViews(
                    context.getPackageName(), R.layout.notification_process_instance_bar);
            bar.setProgressBar(R.id.notification_process_instance_progress, 100,
                    instance.elapsedPercent(nowMillis), false);
            row.addView(R.id.notification_process_instance_bars, bar);
        }

        if (showReminder) {
            boolean reminderEnabled = IdleProcessState.isReminderEnabled(context, accountId, group.roleKey);
            row.setViewVisibility(R.id.notification_process_reminder, View.VISIBLE);
            row.setImageViewResource(R.id.notification_process_reminder,
                    reminderEnabled ? R.drawable.ic_bell_on : R.drawable.ic_bell_off);
            row.setInt(R.id.notification_process_reminder, "setColorFilter",
                    reminderEnabled ? 0xFFFFC107 : textColor);
            row.setOnClickPendingIntent(R.id.notification_process_reminder,
                    IdleReminderManager.toggleIntent(context, accountId, group.roleKey));
        } else {
            row.setViewVisibility(R.id.notification_process_reminder, View.GONE);
        }
        return row;
    }

    private static RemoteViews buildIdleRow(Context context, String accountId,
            IdleProcessState.IdleRole idle, long nowMillis) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.notification_process_row);
        int textColor = textColor(context);
        ProjectProfileStore.Profile profile = ProjectProfileStore.resolve(context, idle.project);
        String projectIdentity = ProjectProfileStore.effectiveShort(profile);
        row.setTextViewText(R.id.notification_process_project, projectIdentity);
        row.setTextColor(R.id.notification_process_project,
                ProjectProfileStore.accentColor(profile));
        row.setViewVisibility(R.id.notification_process_project,
                projectIdentity.isEmpty() ? View.GONE : View.VISIBLE);
        row.setTextViewText(R.id.notification_process_title,
                RoleProfileStore.displayNameById(context, idle.key, idle.role));
        row.setTextColor(R.id.notification_process_title, textColor);
        row.setTextViewText(R.id.notification_process_remaining,
                "idle " + formatIdle(nowMillis - idle.lastFinishedMillis));
        row.setTextColor(R.id.notification_process_remaining, textColor);
        row.setViewVisibility(R.id.notification_process_progress, View.GONE);
        row.setViewVisibility(R.id.notification_process_reminder, View.VISIBLE);
        row.setImageViewResource(R.id.notification_process_reminder,
                idle.reminderEnabled ? R.drawable.ic_bell_on : R.drawable.ic_bell_off);
        row.setInt(R.id.notification_process_reminder, "setColorFilter",
                idle.reminderEnabled ? 0xFFFFC107 : textColor);
        row.setOnClickPendingIntent(R.id.notification_process_reminder,
                IdleReminderManager.toggleIntent(context, accountId, idle));
        return row;
    }

    private static String notificationIdentity(Context context, String project,
            String role, String topic, boolean includeTopic) {
        ProjectProfileStore.Profile profile = ProjectProfileStore.resolve(context, project);
        String compactProject = ProjectProfileStore.effectiveShort(profile);
        StringBuilder value = new StringBuilder(compactProject);
        String cleanRole = RoleProfileStore.displayLabel(context, role);
        if (!cleanRole.isEmpty()) appendSummaryPart(value, cleanRole);
        String cleanTopic = clean(topic);
        if (includeTopic && !cleanTopic.isEmpty()) appendSummaryPart(value, cleanTopic);
        return value.length() == 0 ? "Process" : value.toString();
    }

    private static int activeRoleNotificationId(String roleKey) {
        int hash = roleKey == null ? 0 : roleKey.hashCode() & 0x7fffffff;
        return PROCESS_NOTIFICATION_BASE + (hash % PROCESS_NOTIFICATION_RANGE);
    }

    private static int idleNotificationId(String key) {
        int hash = key == null ? 0 : key.hashCode() & 0x7fffffff;
        return IDLE_NOTIFICATION_BASE + (hash % PROCESS_NOTIFICATION_RANGE);
    }

    private static String scopedKey(String containerId, String base) {
        return base + "::" + AccountNotificationNamespace.safe(containerId);
    }

    private static String accountTitle(Context context, String containerId, String title) {
        if (AccountContainerStore.all(context).size() <= 1) return title;
        AccountContainerStore.Account account = AccountContainerStore.find(context, containerId);
        String name = account == null ? "" : account.name;
        return name.isEmpty() ? title : title + " · " + name;
    }

    private static int textColor(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                ? Color.WHITE : Color.rgb(32, 33, 36);
    }

    private static void appendSummaryPart(StringBuilder summary, String part) {
        String clean = clean(part);
        if (clean.isEmpty()) return;
        if (summary.length() > 0) summary.append(" · ");
        summary.append(clean);
    }

    private static void appendMore(StringBuilder summary, int more) {
        if (more <= 0) return;
        appendSummaryPart(summary, "+" + more + " more");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String countLabel(int active, int idle) {
        if (active > 0 && idle > 0) return active + " active · " + idle + " idle";
        if (active > 0) return active + " active";
        return idle + " idle";
    }

    private static String formatRemaining(long remainingMillis) {
        if (remainingMillis <= 0L) return "done";
        long minutes = Math.max(1L, (remainingMillis + 59_999L) / 60_000L);
        if (minutes < 60L) return minutes + "m left";
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        return rest == 0L ? hours + "h left" : hours + "h " + rest + "m";
    }

    private static String formatIdle(long idleMillis) {
        long minutes = Math.max(1L, idleMillis / 60_000L);
        if (minutes < 60L) return minutes + "m";
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        if (hours < 24L) return rest == 0L ? hours + "h" : hours + "h " + rest + "m";
        long days = hours / 24L;
        long hourRest = hours % 24L;
        return hourRest == 0L ? days + "d" : days + "d " + hourRest + "h";
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
