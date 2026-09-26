#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"

# Finished watchdogs become durable per-role idle state instead of disappearing.
grep -q 'recentlyFinished' "$SRC/CalendarProcessReader.java"
grep -q 'IdleProcessState.synchronize' "$SRC/DualUsageNotificationManager.java"
grep -q 'dismissedThroughMillis' "$SRC/IdleProcessState.java"
grep -q 'row.reminderEnabled = !row.reminderEnabled' "$SRC/IdleProcessState.java"

# DESCRIPTION parsing accepts both canonical newline-separated metadata and provider-flattened
# whitespace-separated key=value pairs so explicit role/topic survive local Calendar sync.
grep -q 'METADATA_PAIR' "$SRC/CalendarProcess.java"
grep -q 'Matcher matcher = METADATA_PAIR.matcher' "$SRC/CalendarProcess.java"
grep -q 'metadata.get("project_short")' "$SRC/CalendarProcess.java"
grep -q 'compactProject + " — " + cleanRole' "$SRC/CalendarProcess.java"
test -f "$ROOT/tests/CalendarProcessSelfTest.java"

# Future watchdogs are observed before BEGIN and persisted into idle lifecycle state. Once a known
# event disappears, deletion is an early completion even if the event never reached BEGIN.
grep -q 'static List<CalendarProcess> observed' "$SRC/CalendarProcessReader.java"
grep -q 'CalendarProcessReader.observed(context, now)' "$SRC/DualUsageNotificationManager.java"
grep -q 'CalendarProcessReader.active(observed, now)' "$SRC/DualUsageNotificationManager.java"
grep -q 'CalendarProcessReader.recentlyFinished(observed, now)' "$SRC/DualUsageNotificationManager.java"
grep -q 'synchronize(context, processes, finished, observed, now)' "$SRC/DualUsageNotificationManager.java"
grep -q 'List<CalendarProcess> recentlyFinished, List<CalendarProcess> observed' "$SRC/IdleProcessState.java"
grep -q 'rememberObserved(row, process)' "$SRC/IdleProcessState.java"
grep -q 'row.projectShort = clean(process.projectShort)' "$SRC/IdleProcessState.java"
grep -q 'json.put("project_short", projectShort)' "$SRC/IdleProcessState.java"
grep -q 'json.optString("project_short", "")' "$SRC/IdleProcessState.java"

# Canonical watchdog BEGIN is the 27-minute deadline, not the start of the work. Future observed
# events are active from BEGIN-27m and progress/remaining time targets BEGIN during that interval.
grep -q 'ACTIVE_WORK_WINDOW_MS = 27L \* 60_000L' "$SRC/CalendarProcess.java"
grep -q 'long workStartMillis()' "$SRC/CalendarProcess.java"
grep -q 'boolean isVisibleActive' "$SRC/CalendarProcess.java"
grep -q 'long remainingMillis' "$SRC/CalendarProcess.java"
grep -q 'process.isVisibleActive(nowMillis)' "$SRC/CalendarProcessReader.java"
grep -q 'process.remainingMillis(nowMillis)' "$SRC/ProcessNotificationManager.java"

# Once an event has been observed, deleting it is an early completion rather than waiting for the
# stale scheduled END. Fresh direct-API absence is authoritative; stale/error states stay UNKNOWN.
grep -q 'static boolean eventExists' "$SRC/CalendarProcessReader.java"
grep -q 'GoogleCalendarProcessSource.hasFreshCache(context, now)' "$SRC/CalendarProcessReader.java"
grep -q 'GoogleCalendarProcessSource.cachedEventExists' "$SRC/CalendarProcessReader.java"
grep -q '"watchdog_presence_checked"' "$SRC/CalendarProcessReader.java"
grep -q '"authoritative", true' "$SRC/CalendarProcessReader.java"
grep -q 'CalendarContract.Events.CONTENT_URI' "$SRC/CalendarProcessReader.java"
grep -q 'if (!hasFreshCache(context, nowMillis)) return true' "$SRC/GoogleCalendarProcessSource.java"
grep -q 'fromDirectEvent' "$SRC/GoogleCalendarProcessSource.java"
grep -q 'boolean directSource' "$SRC/CalendarProcess.java"
grep -q 'row.pendingDirectSource = process.directSource' "$SRC/IdleProcessState.java"
grep -q 'row.pendingEventId, row.pendingDirectSource' "$SRC/IdleProcessState.java"
grep -q '"watchdog_presence_unknown"' "$SRC/CalendarProcessReader.java"
grep -q 'direct_cache_not_fresh' "$SRC/CalendarProcessReader.java"
grep -q 'long finishedAt = watchedEventDeleted ? nowMillis : row.pendingEndMillis' "$SRC/IdleProcessState.java"
grep -q 'row.pendingEndMillis = 0L' "$SRC/IdleProcessState.java"
grep -q 'watchdog_deleted_early' "$SRC/IdleProcessState.java"
grep -q 'deliverFreshCompletion(context, idle, nowMillis)' "$SRC/IdleReminderManager.java"
grep -q 'COMPLETION_FRESH_MS' "$SRC/IdleReminderManager.java"
grep -q '"completion_delivered"' "$SRC/IdleReminderManager.java"
grep -q 'prefs.edit().putLong(key, idle.lastFinishedMillis).apply()' "$SRC/IdleReminderManager.java"
grep -q 'deliverCompletionAttention' "$SRC/IdleReminderManager.java"
grep -q 'COMPLETION_ATTENTION_DELAY_MS = 1_100L' "$SRC/IdleReminderManager.java"
grep -q 'codex_idle_reminders_v2' "$SRC/IdleReminderManager.java"
grep -q '"completion_attention_channel"' "$SRC/IdleReminderManager.java"
grep -q 'new Handler(Looper.getMainLooper()).postDelayed' "$SRC/IdleReminderManager.java"
grep -q 'Recurring idle alarms never create completion overlays' "$SRC/IdleReminderManager.java"

# Every process-notification mode has a useful collapsed summary. Combined mode surfaces it inside
# the usage card; grouped / one-each use a compact summary view plus the full expanded row layout.
grep -q 'static String collapsedSummary' "$SRC/ProcessNotificationManager.java"
grep -q 'process.remainingPercent(nowMillis)' "$SRC/ProcessNotificationManager.java"
grep -q 'R.layout.notification_processes_expanded' "$SRC/ProcessNotificationManager.java"
grep -q 'setCustomContentView(compact)' "$SRC/ProcessNotificationManager.java"
grep -q 'setCustomBigContentView(expanded)' "$SRC/ProcessNotificationManager.java"
grep -q 'ProcessNotificationManager.collapsedSummary' "$SRC/DualUsageNotificationManager.java"
grep -q 'notification_process_summary' "$ROOT/app/src/main/res/layout/notification_processes.xml"
grep -q 'notification_process_summary' "$ROOT/app/src/main/res/layout/notification_processes_expanded.xml"
grep -q 'notification_process_summary' "$ROOT/app/src/main/res/layout/notification_usage_dual_bars.xml"

# 2.10: every active process row exposes the same per-role reminder bell in combined, grouped,
# and one-each modes; idle rows retain their bell as before.
grep -q 'addRows(context, parent, containerId, processes, idleRoles, nowMillis, true)' "$SRC/ProcessNotificationManager.java"
grep -q 'processes, idleRoles, nowMillis, true);' "$SRC/ProcessNotificationManager.java"
! grep -q 'processes, idleRoles, nowMillis, false)' "$SRC/ProcessNotificationManager.java"
grep -q 'IdleProcessState.isReminderEnabled(context, key)' "$SRC/ProcessNotificationManager.java"
grep -q 'IdleReminderManager.toggleIntent(context, key)' "$SRC/ProcessNotificationManager.java"
grep -q 'static boolean isReminderEnabled' "$SRC/IdleProcessState.java"
grep -q 'static PendingIntent toggleIntent(Context context, String key)' "$SRC/IdleReminderManager.java"

# Persistent notification ownership is mode-stable: usage first, process surfaces second, and
# reminder/usage attention re-alerts the owning ID instead of adding independent persistent cards.
test -f "$SRC/NotificationSurfaceContract.java"
grep -q 'GROUP_KEY = "codex_monitor_persistent_v1"' "$SRC/NotificationSurfaceContract.java"
grep -q 'SORT_USAGE = "00_usage"' "$SRC/NotificationSurfaceContract.java"
grep -q 'SORT_PROCESSES = "10_processes"' "$SRC/NotificationSurfaceContract.java"
grep -q 'setGroup(NotificationSurfaceContract.GROUP_KEY)' "$SRC/DualUsageNotificationManager.java"
grep -q 'setSortKey(NotificationSurfaceContract.SORT_USAGE)' "$SRC/DualUsageNotificationManager.java"
grep -q 'setGroup(NotificationSurfaceContract.GROUP_KEY)' "$SRC/ProcessNotificationManager.java"
grep -q 'reAlertIdleReminder' "$SRC/ProcessNotificationManager.java"
grep -q 'realertUsageSurface' "$SRC/ResetNotificationManager.java"
! grep -q 'showFallbackNotification' "$SRC/IdleReminderManager.java"

# Install/update first reconciliation removes stale app-owned SystemUI surfaces, then the existing
# state-driven restore/repost path reconstructs only current usage/process/idle surfaces.
grep -q 'lastUpdateTime' "$SRC/CodexMonitorApplication.java"
grep -q 'manager.cancelAll()' "$SRC/CodexMonitorApplication.java"
grep -q 'IdleReminderManager.restore(this)' "$SRC/CodexMonitorApplication.java"
grep -q 'DualUsageNotificationManager.repostDelayed(this, 350L)' "$SRC/CodexMonitorApplication.java"

# Cadence stays intentionally bounded to the approved 5/10 minute choices.
grep -q 'idle_reminder_cadence_entries' "$ROOT/app/src/main/res/values/settings_arrays.xml"
grep -q '<item>5</item>' "$ROOT/app/src/main/res/values/settings_arrays.xml"
grep -q '<item>10</item>' "$ROOT/app/src/main/res/values/settings_arrays.xml"
grep -q 'DEFAULT_CADENCE_MINUTES = 5' "$SRC/IdleProcessState.java"

# Reminder alarms are keyed per role and stop when the same role becomes active.
grep -q 'ACTION_FIRE' "$SRC/IdleReminderManager.java"
grep -q 'IdleProcessState.isRoleActive' "$SRC/IdleReminderManager.java"
grep -q 'cancelAlarm(context, key)' "$SRC/IdleReminderManager.java"

# Idle rows expose dismiss + bell actions, while overlay taps have explicit strong haptics.
grep -q 'notification_process_dismiss' "$ROOT/app/src/main/res/layout/notification_process_row.xml"
grep -q 'notification_process_reminder' "$ROOT/app/src/main/res/layout/notification_process_row.xml"
grep -q 'DiagonalStripeDrawable' "$SRC/IdleReminderOverlayService.java"
grep -q 'vibrate(160L)' "$SRC/IdleReminderOverlayService.java"
grep -q 'vibrate(330L)' "$SRC/IdleReminderOverlayService.java"
grep -q 'GoogleCalendarProcessSource.refreshIfDue' "$SRC/CalendarProcessReader.java"
grep -q 'calendar.events.readonly' "$SRC/GoogleCalendarAuthorization.java"
grep -q 'overlay_bell_toggled' "$SRC/IdleReminderOverlayService.java"
grep -q 'idle.lastStartedMillis' "$SRC/IdleReminderOverlayService.java"
grep -q 'formatDuration' "$SRC/IdleReminderOverlayService.java"
grep -q 'IdleReminderOverlayService' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'SYSTEM_ALERT_WINDOW' "$ROOT/app/src/main/AndroidManifest.xml"

# Reboot/package replacement restores local reminder scheduling.
grep -q 'IdleReminderManager.restore(context)' "$SRC/BootReceiver.java"

# 2.14 Quick Setup is a fixed, non-scrolling one-screen layout. The content owns the flexible
# middle area while the primary CTA remains outside it at the bottom.
grep -q 'installStaticLayout()' "$SRC/OnboardingActivity.java"
grep -q 'new LinearLayout.LayoutParams(-1, 0, 1.0f)' "$SRC/OnboardingActivity.java"
grep -q 'this.doneButton = Ui.nativePrimaryButton(this,' "$SRC/OnboardingActivity.java"
grep -q 'this.settingsEntry ? "Done" : "Open Codex Monitor"' "$SRC/OnboardingActivity.java"
! grep -q 'Ui.installPage(this, "Quick setup"' "$SRC/OnboardingActivity.java"
! grep -q 'NestedScrollView' "$SRC/OnboardingActivity.java"
grep -q 'STATUS_YELLOW = 0xFFFFC107' "$SRC/OnboardingActivity.java"
grep -q 'addAccountRow(account, false)' "$SRC/OnboardingActivity.java"
grep -q 'SetupReadiness.overallSummary(this)' "$SRC/OnboardingActivity.java"
grep -q 'hasExplicitStyle' "$SRC/ResetAlertPreferences.java"
grep -q 'ensureNotificationFeatureDefault' "$SRC/OnboardingActivity.java"
test -f "$SRC/HomeVersionLabel.java"
grep -q 'BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"' "$SRC/HomeVersionLabel.java"
grep -q 'RelativeSizeSpan' "$SRC/HomeVersionLabel.java"
grep -q 'HomeVersionLabel.apply(activity)' "$SRC/CodexMonitorApplication.java"
grep -q 'normalizeAutomaticDefaults' "$SRC/CodexMonitorApplication.java"
! grep -q 'dashboard_reorder_root' "$ROOT/app/src/main/res/xml/preferences_settings.xml"
grep -q 'android:key="settings_diagnostics"' "$ROOT/app/src/main/res/xml/preferences_settings.xml"

# Selected Focus launcher/adaptive assets keep the original artwork but now place it behind an
# explicit 11dp (~10%) safe inset on each side so Samsung launcher masking cannot clip the arcs.
test -f "$ROOT/app/src/main/res/drawable/codex_monitor_focus_bg.xml"
test -f "$ROOT/app/src/main/res/drawable/codex_monitor_focus_fg.xml"
test -f "$ROOT/app/src/main/res/drawable/codex_monitor_focus_fg_safe.xml"
test -f "$ROOT/app/src/main/res/drawable/codex_monitor_monochrome_safe.xml"
grep -q 'strokeColor="#FFD400"' "$ROOT/app/src/main/res/drawable/codex_monitor_focus_fg.xml"
grep -q 'strokeColor="#12B6FF"' "$ROOT/app/src/main/res/drawable/codex_monitor_focus_fg.xml"
grep -q 'android:left="11dp"' "$ROOT/app/src/main/res/drawable/codex_monitor_focus_fg_safe.xml"
grep -q '@drawable/codex_monitor_focus_bg' "$ROOT/app/src/main/res/mipmap-anydpi/ic_launcher.xml"
grep -q '@drawable/codex_monitor_focus_fg_safe' "$ROOT/app/src/main/res/mipmap-anydpi/ic_launcher.xml"
grep -q '@drawable/codex_monitor_focus_fg_safe' "$ROOT/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml"
grep -q '@drawable/codex_monitor_monochrome_safe' "$ROOT/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml"

# OneUI HorizontalRadioPreference needs an explicit title and view type at runtime; missing these
# caused the Settings -> Now Bar page to fail during preference inflation on the target Samsung.
NOW_BAR_XML="$ROOT/app/src/main/res/xml/preferences_settings_now_bar.xml"
grep -q 'android:title="Process notifications"' "$NOW_BAR_XML"
grep -q 'app:viewType="noImage"' "$NOW_BAR_XML"

# Run focused pure-Java metadata + 27-minute timing regression coverage without Android stubs.
META_OUT="$ROOT/build/calendar-process-metadata-tests"
rm -rf "$META_OUT" && mkdir -p "$META_OUT"
javac -encoding UTF-8 -d "$META_OUT" \
  "$SRC/CalendarProcess.java" \
  "$ROOT/tests/CalendarProcessSelfTest.java"
java -ea -cp "$META_OUT" dev.kopandazavr.codexmonitor.CalendarProcessSelfTest


# Android 15+/targetSdk 36 completion overlay starts through an exact-alarm receiver exemption,
# not directly from the background completion synchronizer.
grep -q 'ACTION_COMPLETION_OVERLAY' "$SRC/IdleReminderManager.java"
grep -q 'setExactAndAllowWhileIdle' "$SRC/IdleReminderManager.java"
grep -q 'completion_overlay_alarm_scheduled' "$SRC/IdleReminderManager.java"
grep -q 'completionOverlayFromIntent' "$SRC/NowBarActionReceiver.java"
grep -q 'overlay_window_added' "$SRC/IdleReminderOverlayService.java"
! grep -q 'boolean overlayShown = IdleReminderOverlayService.show(context, idle);' "$SRC/IdleReminderManager.java"
echo 'Idle reminder + phone follow-up source regression contract passed.'
