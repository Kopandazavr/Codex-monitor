#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/bennett/codexmeter"
SHARED="$ROOT/shared/src/main/java/dev/bennett/codexmeter"
RES="$ROOT/app/src/main/res/xml"

# Foreground lifecycle refresh is real network work, coalesced across dashboard and app-level
# transitions, while the previous cached snapshot remains authoritative for presentation.
test -f "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'UsageApi.refreshAndCacheScheduled(app, forceSubscription, trigger)' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'ForegroundUsageRefresh.request(context, "foreground_main");' "$SRC/RefreshEngagement.java"
grep -Fq 'ForegroundUsageRefresh.request(this, "foreground_transition");' "$SRC/CodexMeterApplication.java"
grep -Fq 'ForegroundUsageRefresh.isInFlight()' "$SRC/RefreshScheduler.java"
grep -Fq '"immediate_refresh_coalesced"' "$SRC/RefreshScheduler.java"

# Visible-app usage freshness uses one-minute remote polling; the 5-second experiment is local-only.
grep -Fq 'ACTIVE_POLL_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'request(app, "foreground_periodic", false)' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'RefreshScheduler.suspendPeriodic(app)' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'ForegroundUsageRefresh.startActivePolling(this);' "$SRC/CodexMeterApplication.java"
grep -Fq 'ForegroundUsageRefresh.stopActivePolling(this);' "$SRC/CodexMeterApplication.java"
grep -Fq 'DIAGNOSTIC_FIVE_SECOND_REPAINT = true' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'TimeUnit.SECONDS.toMillis(5)' "$SRC/ProcessNotificationScheduler.java"
grep -Fq '"diagnostic_5s_repaint"' "$SRC/NowBarActionReceiver.java"
grep -Fq 'DualUsageNotificationManager.repostFromCache(context)' "$SRC/NowBarActionReceiver.java"
grep -Fq '"remote_fetch", false' "$SRC/NowBarActionReceiver.java"
! grep -Fq 'UsageApi.' "$SRC/ProcessNotificationScheduler.java"

# Cached cards stay visible while refreshing and surface a small stale/in-flight title state.
grep -Fq 'ForegroundUsageRefresh.isInFlight()' "$SRC/UsageWaveView.java"
grep -Fq 'ForegroundUsageRefresh.isStale()' "$SRC/UsageWaveView.java"
grep -Fq '"Not refreshed"' "$SRC/UsageWaveView.java"
grep -Fq 'drawRefreshState' "$SRC/UsageWaveView.java"

# Diagnostics is now first-class and always on: no hidden unlock and no user-facing capture toggle.
grep -Fq 'android:key="settings_diagnostics"' "$RES/preferences_settings.xml"
grep -Fq 'android:title="Diagnostics"' "$RES/preferences_settings.xml"
! grep -Fq 'diagnostic_logging_enabled' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'Always on' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'public static boolean isEnabled(Context context)' "$SRC/DiagnosticLog.java"
grep -A2 -F 'public static boolean isEnabled(Context context)' "$SRC/DiagnosticLog.java" \
  | grep -Fq 'return appContext(context) != null;'
grep -Fq 'always_on_capture_started' "$SRC/DiagnosticLog.java"
grep -Fq 'MAX_ARCHIVES = 2' "$SRC/DiagnosticLog.java"
grep -Fq 'MAX_FILE_BYTES = 1024L * 1024L' "$SRC/DiagnosticLog.java"
grep -Fq 'DiagnosticSanitizer.redact' "$SRC/DiagnosticLog.java"
grep -Fq 'export_diagnostic_logs' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'clear_diagnostic_logs' "$RES/preferences_settings_diagnostics.xml"

# Personal-use Settings cleanup: removed root/page UI and dead About/transfer implementation.
! grep -Fq 'about_codex_meter' "$RES/preferences_settings.xml"
! grep -Fq 'settings_updates' "$RES/preferences_settings.xml"
! grep -Fq 'settings_transfer' "$RES/preferences_settings.xml"
! grep -Fq 'settings_privacy' "$RES/preferences_settings.xml"
! test -e "$RES/preferences_settings_updates.xml"
! test -e "$RES/preferences_settings_transfer.xml"
! test -e "$RES/preferences_settings_privacy.xml"
! test -e "$SRC/SettingsTransfer.java"
! test -e "$SRC/SettingsTransferStore.java"
! test -e "$SRC/AboutActivity.java"
! test -e "$ROOT/app/src/main/res/layout/activity_about.xml"
! test -e "$ROOT/app/src/main/res/drawable/about_gradient_bg.xml"
! grep -Fq 'PAGE_UPDATES' "$SRC/SettingsActivity.java"
! grep -Fq 'PAGE_TRANSFER' "$SRC/SettingsActivity.java"
! grep -Fq 'PAGE_PRIVACY' "$SRC/SettingsActivity.java"
! grep -Fq 'bindUpdates' "$SRC/SettingsActivity.java"
! grep -Fq 'bindTransfer' "$SRC/SettingsActivity.java"

# The updater has no user-facing Settings page and is functionally disabled for the personal build.
grep -A2 -F 'public static boolean automaticChecks(Context context)' "$SRC/UpdatePreferences.java" \
  | grep -Fq 'return false;'
grep -A2 -F 'public static GitHubRelease availableUpdate(Context context)' "$SRC/UpdatePreferences.java" \
  | grep -Fq 'return null;'

# Settings navigation must never hard-code the obsolete pre-migration applicationId.
! grep -R -F 'android:targetPackage="dev.bennett.codexmeter"' "$RES/preferences_settings"*.xml
! grep -R -F 'android:data="package:dev.bennett.codexmeter"' "$RES/preferences_settings"*.xml
grep -Fq 'Ui.startSecondaryActivity(requireActivity(), DashboardReorderActivity.class);' "$SRC/SettingsActivity.java"
grep -Fq 'Ui.startSecondaryActivity(requireActivity(), CalendarPermissionActivity.class);' "$SRC/SettingsActivity.java"
grep -Fq 'Uri.parse("package:" + requireContext().getPackageName())' "$SRC/SettingsActivity.java"

# Direct limit bells remain independent and reset-timed.
grep -Fq '"five_hour"' "$SRC/DualUsageNotificationManager.java"
grep -Fq '"monthly" : "weekly"' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'RESTORABLE_METRICS = {"five_hour", "weekly", "monthly"}' "$SRC/NowBarResetReminder.java"
grep -Fq 'setExactAndAllowWhileIdle' "$SRC/NowBarResetReminder.java"

# Active process progress remains elapsed 0->100 and every visible role owns its bell.
grep -Fq 'elapsedPercent' "$SRC/CalendarProcess.java"
grep -Fq 'process.elapsedPercent' "$SRC/ProcessNotificationManager.java"
grep -Fq 'processes, idleRoles, nowMillis, true);' "$SRC/ProcessNotificationManager.java"

# Notification/live-monitor IA remains direct and the old one/both selector stays hidden.
grep -Fq 'android:title="Notifications &amp; live monitor"' "$RES/preferences_settings.xml"
grep -Fq 'android:key="notification_live_monitor_settings"' "$RES/preferences_settings_notifications.xml"
grep -A6 -F 'android:key="notification_metric_ui"' "$RES/preferences_settings_notifications.xml" \
  | grep -Fq 'app:isPreferenceVisible="false"'

# Background usage cadence remains adaptive and separate from the local 5-second diagnostic repaint.
grep -Fq 'INTERVALS = {5, 10, 15, 30, 60, 120}' "$SHARED/AdaptiveRefreshPolicy.java"
! grep -Fq 'SECONDS.toMillis(5)' "$SHARED/AdaptiveRefreshPolicy.java"

# Still the same 2.10 diagnostic release line.
grep -Fq 'versionCode = 36' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionName = "2.10.0"' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionCode = 36' "$ROOT/wear/build.gradle.kts"
grep -Fq 'versionName = "2.10.0"' "$ROOT/wear/build.gradle.kts"
grep -Fq 'VERSION_CODE = 36' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = "2.10.0"' "$SRC/AppConstants.java"

echo 'Codex Monitor 2.10.0 personal-settings/direct-controls source contract PASS'
