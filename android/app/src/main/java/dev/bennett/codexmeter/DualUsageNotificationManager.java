package dev.bennett.codexmeter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Notification-shade presentation for the live monitor. */
final class DualUsageNotificationManager {
    private static final String CHANNEL_ID = "codex_live_monitor_v2";
    private static final int NOTIFICATION_ID = 8610;
    private static final int REQUEST_CONTENT = 9760;
    private static final int REQUEST_STOP = 9761;
    private static final int REQUEST_REFRESH = 9762;
    private static final int REQUEST_DISMISSED = 9763;

    private DualUsageNotificationManager() {
    }

    static boolean postFromSnapshot(Context context, UsageSnapshot snapshot) {
        if (context == null || snapshot == null || !NowBarManager.isActive(context)
                || !NowBarManager.canPostNotifications(context)) {
            return false;
        }
        SurfaceState state = surfaceState(context, snapshot);
        if (state == null) return false;
        Notification notification = buildSurface(context, CHANNEL_ID, state,
                "Codex usage", state.fallbackText, true);
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || notification == null) return false;
        try {
            manager.notify(NOTIFICATION_ID, notification);
            ProcessNotificationManager.sync(context, state.processes, state.idleRoles,
                    state.processMode, state.now);
            // Keep local timer presentation independent from the remote refresh scheduler.
            ProcessNotificationScheduler.schedule(context);
            DiagnosticLog.info(context, "notification", "persistent_surface_posted",
                    "surface", "usage",
                    "notification_id", NOTIFICATION_ID,
                    "mode", state.processMode,
                    "fingerprint", semanticFingerprint(state),
                    "five_hour", state.fiveHour != null,
                    "long_window", state.longWindow != null,
                    "process_count", state.processes.size(),
                    "idle_count", state.idleRoles.size());
            return true;
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "now_bar", "dual_notification_post_failed", exception);
            return false;
        }
    }

    /**
     * Produces an attention event by updating the existing usage notification ID on an alerting
     * channel. The caller restores the normal silent/live channel shortly afterwards, so no extra
     * persistent alert card is left behind.
     */
    static boolean realertUsageSurface(Context context, String alertChannelId,
            String alertTitle, String alertText) {
        if (context == null || alertChannelId == null || !NowBarManager.isActive(context)) {
            return false;
        }
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(context);
        if (snapshot == null) return false;
        SurfaceState state = surfaceState(context, snapshot);
        if (state == null) return false;
        Notification notification = buildSurface(context, alertChannelId, state,
                alertTitle == null ? "Codex usage" : alertTitle,
                alertText == null ? state.fallbackText : alertText, false);
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || notification == null) return false;
        try {
            manager.notify(NOTIFICATION_ID, notification);
            DiagnosticLog.info(context, "notification", "persistent_surface_realerted",
                    "surface", "usage",
                    "notification_id", NOTIFICATION_ID,
                    "channel", alertChannelId,
                    "mode", state.processMode,
                    "fingerprint", semanticFingerprint(state));
            return true;
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "now_bar", "usage_surface_realert_failed", exception);
            return false;
        }
    }

    static boolean repostFromCache(Context context) {
        if (context == null) return false;
        if (!NowBarManager.isActive(context)) {
            ProcessNotificationManager.clearAll(context);
            return false;
        }
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(context);
        return snapshot != null && postFromSnapshot(context, snapshot);
    }

    static void repostDelayed(Context context, long delayMillis) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> repostFromCache(app), Math.max(0L, delayMillis));
    }

    private static SurfaceState surfaceState(Context context, UsageSnapshot snapshot) {
        long now = System.currentTimeMillis();
        long observedAt = snapshot.fetchedAtMillis;
        UsageWindow fiveHour = UsageSnapshot.currentWindow(snapshot.fiveHour, observedAt, now);
        UsageWindow longWindow = UsageSnapshot.currentWindow(snapshot.longWindow(), observedAt, now);
        if (fiveHour == null && longWindow == null) return null;

        String longLabel = snapshot.longWindowIsMonthly() ? "Monthly" : "Weekly";
        String focus = NowBarManager.activeFocusMetric(context);
        if (focus == null) focus = NowBarPercentMode.lowerRemainingFocus(fiveHour, longWindow);
        UsageWindow paceWindow = NowBarPercentMode.selectWindow(focus, fiveHour, longWindow);
        String fiveResetTime = formatResetTime(fiveHour, observedAt);
        String longResetTime = formatResetTime(longWindow, observedAt);
        String processMode = ProcessNotificationMode.current(context);
        List<CalendarProcess> observed = CalendarProcessReader.observed(context, now);
        List<CalendarProcess> processes = CalendarProcessReader.active(observed, now);
        List<CalendarProcess> finished = CalendarProcessReader.recentlyFinished(observed, now);
        List<IdleProcessState.IdleRole> idleRoles =
                IdleProcessState.synchronize(context, processes, finished, observed, now);
        IdleReminderManager.sync(context, processes, idleRoles, now);

        try {
            SubscriptionStore.seedFromJwt(context, SecureTokenStore.load(context), now);
        } catch (RuntimeException ignored) {
        }
        SubscriptionInfo subscription = SubscriptionStore.load(context);
        String planText = formatSubscription(subscription);
        String fiveText = NowBarCopy.limitText("5-hour", fiveHour, observedAt, now);
        String longText = NowBarCopy.limitText(longLabel, longWindow, observedAt, now);
        String fallbackText = fiveText + " · " + longText
                + (longResetTime.isEmpty() ? "" : " · " + longLabel + " reset: " + longResetTime);
        return new SurfaceState(now, observedAt, fiveHour, longWindow, longLabel, focus,
                paceWindow, fiveResetTime, longResetTime, processMode, processes, idleRoles,
                subscription, planText, fallbackText, snapshot.longWindowIsMonthly());
    }

    private static Notification buildSurface(Context context, String channelId, SurfaceState state,
            String title, String text, boolean onlyAlertOnce) {
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, REQUEST_CONTENT, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopIntent = PendingIntent.getBroadcast(context, REQUEST_STOP,
                new Intent(context, NowBarActionReceiver.class).setAction(NowBarManager.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent refreshIntent = PendingIntent.getBroadcast(context, REQUEST_REFRESH,
                new Intent(context, NowBarActionReceiver.class).setAction(NowBarManager.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent dismissedIntent = PendingIntent.getBroadcast(context, REQUEST_DISMISSED,
                new Intent(context, NowBarActionReceiver.class).setAction(NowBarManager.ACTION_DISMISSED),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Icon stopIcon = Icon.createWithResource(context, R.drawable.ic_notification);
        Icon refreshIcon = Icon.createWithResource(context, R.drawable.ic_refresh);
        RemoteViews compact = buildViews(context, R.layout.notification_usage_dual_bars,
                state.fiveHour, state.longWindow, state.longLabel, state.observedAt, state.now,
                state.planText, state.fiveResetTime, state.longResetTime, state.processes,
                state.idleRoles, state.processMode);
        RemoteViews expanded = buildViews(context, R.layout.notification_usage_dual_bars_expanded,
                state.fiveHour, state.longWindow, state.longLabel, state.observedAt, state.now,
                state.planText, state.fiveResetTime, state.longResetTime, state.processes,
                state.idleRoles, state.processMode);

        // Reset controls now live directly beside both visible limit rows, so the old focused-metric
        // action is deliberately absent. Stop + manual Refresh remain notification actions.
        Notification.Builder builder = new Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setDeleteIntent(dismissedIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(onlyAlertOnce)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(Color.rgb(3, 129, 254))
                .setShowWhen(false)
                .setGroup(NotificationSurfaceContract.GROUP_KEY)
                .setSortKey(NotificationSurfaceContract.SORT_USAGE)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(compact)
                .setCustomBigContentView(expanded)
                .addAction(new Notification.Action.Builder(stopIcon, "Stop", stopIntent).build())
                .addAction(new Notification.Action.Builder(refreshIcon, "Refresh", refreshIntent).build());
        try {
            return builder.build();
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "now_bar", "dual_notification_build_failed", exception,
                    "channel", channelId);
            return null;
        }
    }

    private static RemoteViews buildViews(Context context, int layoutId,
            UsageWindow fiveHour, UsageWindow longWindow, String longLabel,
            long observedAt, long now, String planText, String fiveResetTime,
            String longResetTime, List<CalendarProcess> processes,
            List<IdleProcessState.IdleRole> idleRoles, String processMode) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);
        int textColor = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                ? Color.WHITE : Color.rgb(32, 33, 36);

        if (planText.isEmpty()) {
            views.setViewVisibility(R.id.notification_plan_text, View.GONE);
        } else {
            views.setViewVisibility(R.id.notification_plan_text, View.VISIBLE);
            views.setTextViewText(R.id.notification_plan_text, planText);
            views.setTextColor(R.id.notification_plan_text, textColor);
        }

        if (layoutId == R.layout.notification_usage_dual_bars) {
            String processSummary = ProcessNotificationMode.COMBINED.equals(processMode)
                    ? ProcessNotificationManager.collapsedSummary(processes, idleRoles, now)
                    : "";
            views.setViewVisibility(R.id.notification_process_summary,
                    processSummary.isEmpty() ? View.GONE : View.VISIBLE);
            views.setTextViewText(R.id.notification_process_summary, processSummary);
            views.setTextColor(R.id.notification_process_summary, textColor);
        }

        if (fiveHour == null) {
            views.setViewVisibility(R.id.notification_five_row, View.GONE);
        } else {
            views.setViewVisibility(R.id.notification_five_row, View.VISIBLE);
            String fiveText = NowBarCopy.limitText("5-hour", fiveHour, observedAt, now);
            if (!fiveResetTime.isEmpty()) fiveText += " · reset " + fiveResetTime;
            views.setTextViewText(R.id.notification_five_text, fiveText);
            views.setTextColor(R.id.notification_five_text, textColor);
            views.setProgressBar(R.id.notification_five_progress, 100,
                    fiveHour.remainingPercent(), false);
            bindResetBell(context, views, R.id.notification_five_bell, "five_hour",
                    fiveHour, observedAt, textColor);
        }

        if (longWindow == null) {
            views.setViewVisibility(R.id.notification_long_row, View.GONE);
        } else {
            views.setViewVisibility(R.id.notification_long_row, View.VISIBLE);
            String longText = NowBarCopy.limitText(longLabel, longWindow, observedAt, now);
            if (!longResetTime.isEmpty()) longText += " · reset " + longResetTime;
            views.setTextViewText(R.id.notification_long_text, longText);
            views.setTextColor(R.id.notification_long_text, textColor);
            views.setProgressBar(R.id.notification_long_progress, 100,
                    longWindow.remainingPercent(), false);
            bindResetBell(context, views, R.id.notification_long_bell,
                    "Monthly".equals(longLabel) ? "monthly" : "weekly",
                    longWindow, observedAt, textColor);
        }

        if (layoutId == R.layout.notification_usage_dual_bars_expanded) {
            boolean showProcesses = ProcessNotificationMode.COMBINED.equals(processMode)
                    && ((processes != null && !processes.isEmpty())
                    || (idleRoles != null && !idleRoles.isEmpty()));
            views.setViewVisibility(R.id.notification_process_section,
                    showProcesses ? View.VISIBLE : View.GONE);
            if (showProcesses) {
                views.setTextColor(R.id.notification_process_section_title, textColor);
                ProcessNotificationManager.addRows(context, views,
                        R.id.notification_process_container, processes, idleRoles, now);
            }
        }
        return views;
    }

    private static void bindResetBell(Context context, RemoteViews views, int viewId,
            String metric, UsageWindow window, long observedAt, int textColor) {
        long resetAt = window == null ? 0L : window.effectiveResetAtMillis(observedAt);
        long windowSeconds = window == null ? 0L : window.windowSeconds;
        boolean armed = resetAt > 0L && NowBarResetReminder.isArmedFor(
                context, metric, resetAt, windowSeconds);
        views.setViewVisibility(viewId, View.VISIBLE);
        views.setImageViewResource(viewId, armed ? R.drawable.ic_bell_on : R.drawable.ic_bell_off);
        views.setInt(viewId, "setColorFilter", armed ? 0xFFFFC107 : textColor);
        PendingIntent toggle = NowBarResetReminder.toggleIntent(context, metric, window, observedAt);
        if (toggle != null) {
            views.setOnClickPendingIntent(viewId, toggle);
        }
    }

    private static String semanticFingerprint(SurfaceState state) {
        StringBuilder value = new StringBuilder();
        value.append(state.processMode).append('|')
                .append(state.fiveHour == null ? "-" : state.fiveHour.remainingPercent())
                .append('|')
                .append(state.longWindow == null ? "-" : state.longWindow.remainingPercent())
                .append('|').append(state.fiveResetTime)
                .append('|').append(state.longResetTime);
        for (CalendarProcess process : state.processes) {
            value.append('|').append(process.identity()).append(':')
                    .append(process.elapsedPercent(state.now));
        }
        for (IdleProcessState.IdleRole idle : state.idleRoles) {
            value.append('|').append(idle.key).append(':').append(idle.lastFinishedMillis);
        }
        return Integer.toHexString(value.toString().hashCode());
    }

    static String formatResetTime(UsageWindow window, long observedAtMillis) {
        if (window == null) return "";
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        if (resetAt <= 0L) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm",
                Locale.getDefault()).withZone(ZoneId.systemDefault());
        return formatter.format(Instant.ofEpochMilli(resetAt));
    }

    static String formatSubscription(SubscriptionInfo info) {
        if (info == null || !info.hasDisplayableData()) return "";
        String plan = info.displayPlanName();
        if (info.activeUntilMillis <= 0L) return plan;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm",
                Locale.getDefault()).withZone(ZoneId.systemDefault());
        return plan + " · до " + formatter.format(Instant.ofEpochMilli(info.activeUntilMillis));
    }

    private static final class SurfaceState {
        final long now;
        final long observedAt;
        final UsageWindow fiveHour;
        final UsageWindow longWindow;
        final String longLabel;
        final String focus;
        final UsageWindow paceWindow;
        final String fiveResetTime;
        final String longResetTime;
        final String processMode;
        final List<CalendarProcess> processes;
        final List<IdleProcessState.IdleRole> idleRoles;
        final SubscriptionInfo subscription;
        final String planText;
        final String fallbackText;
        final boolean longWindowIsMonthly;

        SurfaceState(long now, long observedAt, UsageWindow fiveHour, UsageWindow longWindow,
                String longLabel, String focus, UsageWindow paceWindow, String fiveResetTime,
                String longResetTime, String processMode, List<CalendarProcess> processes,
                List<IdleProcessState.IdleRole> idleRoles, SubscriptionInfo subscription,
                String planText, String fallbackText, boolean longWindowIsMonthly) {
            this.now = now;
            this.observedAt = observedAt;
            this.fiveHour = fiveHour;
            this.longWindow = longWindow;
            this.longLabel = longLabel;
            this.focus = focus;
            this.paceWindow = paceWindow;
            this.fiveResetTime = fiveResetTime;
            this.longResetTime = longResetTime;
            this.processMode = processMode;
            this.processes = processes;
            this.idleRoles = idleRoles;
            this.subscription = subscription;
            this.planText = planText;
            this.fallbackText = fallbackText;
            this.longWindowIsMonthly = longWindowIsMonthly;
        }
    }
}
