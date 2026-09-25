#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
SHARED="$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor"
RES="$ROOT/app/src/main/res/xml"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"

# Foreground lifecycle refresh is real network work, coalesced across app/dashboard transitions.
test -f "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'UsageApi.refreshAndCacheScheduled(app, forceSubscription, trigger)' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'ForegroundUsageRefresh.request(context, "foreground_main");' "$SRC/RefreshEngagement.java"
grep -Fq 'ForegroundUsageRefresh.request(this, "foreground_transition");' "$SRC/CodexMonitorApplication.java"
grep -Fq 'ForegroundUsageRefresh.isInFlight()' "$SRC/RefreshScheduler.java"
grep -Fq '"immediate_refresh_coalesced"' "$SRC/RefreshScheduler.java"

# Visible-app usage freshness remains one-minute remote usage polling. Calendar process freshness
# is a separate non-overlapping one-second direct loop; its AlarmManager path is recovery only.
grep -Fq 'ACTIVE_POLL_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'request(app, "foreground_periodic", false)' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'RefreshScheduler.suspendPeriodic(app)' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'ForegroundUsageRefresh.startActivePolling(this);' "$SRC/CodexMonitorApplication.java"
grep -Fq 'ForegroundUsageRefresh.stopActivePolling(this);' "$SRC/CodexMonitorApplication.java"
grep -Fq 'DIAGNOSTIC_FIVE_SECOND_REPAINT = false' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'POLL_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1)' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'RECOVERY_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'GoogleCalendarProcessSource.forceRefresh(app' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'GoogleCalendarProcessSource.forceRefresh(app' "$SRC/NowBarActionReceiver.java"
grep -Fq '"remote_usage_fetch", false' "$SRC/ProcessNotificationScheduler.java"
! grep -Fq 'setExactAndAllowWhileIdle' "$SRC/ProcessNotificationScheduler.java"
! grep -Fq 'UsageApi.' "$SRC/ProcessNotificationScheduler.java"

# Cached cards stay visible while refreshing and surface restrained freshness state.
grep -Fq 'ForegroundUsageRefresh.isInFlight()' "$SRC/UsageWaveView.java"
grep -Fq 'ForegroundUsageRefresh.isStale()' "$SRC/UsageWaveView.java"
grep -Fq '"Not refreshed"' "$SRC/UsageWaveView.java"
grep -Fq 'drawRefreshState' "$SRC/UsageWaveView.java"

# Diagnostics is first-class, always on, bounded and sanitized.
grep -Fq 'android:key="settings_diagnostics"' "$RES/preferences_settings.xml"
grep -Fq 'android:title="Diagnostics"' "$RES/preferences_settings.xml"
! grep -Fq 'diagnostic_logging_enabled' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'Always on' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'Sanitization' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'always_on_capture_started' "$SRC/DiagnosticLog.java"
grep -A2 -F 'public static boolean isEnabled(Context context)' "$SRC/DiagnosticLog.java" | grep -Fq 'return appContext(context) != null;'
grep -Fq 'MAX_ARCHIVES = 2' "$SRC/DiagnosticLog.java"
grep -Fq 'MAX_FILE_BYTES = 1024L * 1024L' "$SRC/DiagnosticLog.java"
grep -Fq 'DiagnosticSanitizer.redact' "$SRC/DiagnosticLog.java"
grep -Fq 'export_diagnostic_logs' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'clear_diagnostic_logs' "$RES/preferences_settings_diagnostics.xml"

# Personal-use Settings cleanup: removed sections and implementation are physically absent.
! grep -Fq 'about_codex_monitor' "$RES/preferences_settings.xml"
! grep -Fq 'settings_updates' "$RES/preferences_settings.xml"
! grep -Fq 'settings_transfer' "$RES/preferences_settings.xml"
! grep -Fq 'settings_privacy' "$RES/preferences_settings.xml"
! test -e "$RES/preferences_settings_updates.xml"
! test -e "$RES/preferences_settings_transfer.xml"
! test -e "$RES/preferences_settings_privacy.xml"
for file in AboutActivity.java SettingsTransfer.java SettingsTransferStore.java \
  GitHubRelease.java GitHubReleaseParser.java GitHubReleaseSource.java \
  ReleaseHistoryActivity.java ReleaseIntegrity.java ReleaseNotesMarkdown.java ReleaseNotesUi.java \
  ReleaseUpdateClient.java ReleaseUpdateJobService.java ReleaseUpdatePolicy.java \
  ReleaseUpdateScheduler.java ReleaseVersion.java UpdateActivity.java UpdateChannel.java \
  UpdateCheckFrequency.java UpdateInstallReceiver.java UpdateInstaller.java \
  UpdateNotificationManager.java UpdatePreferences.java; do
  ! test -e "$SRC/$file"
done
! test -e "$ROOT/app/src/main/res/layout/activity_about.xml"
! test -e "$ROOT/app/src/main/res/drawable/about_gradient_bg.xml"
! grep -Fq 'REQUEST_INSTALL_PACKAGES' "$MANIFEST"
! grep -Fq 'UpdateActivity' "$MANIFEST"
! grep -Fq 'ReleaseHistoryActivity' "$MANIFEST"
! grep -Fq 'UpdateInstallReceiver' "$MANIFEST"
! grep -Fq 'ReleaseUpdateJobService' "$MANIFEST"
! grep -Fq 'UPDATE_API_URL' "$ROOT/app/build.gradle.kts"

# Settings navigation uses runtime/class-based routing, never the obsolete applicationId.
! grep -R -F 'android:targetPackage="dev.kopandazavr.codexmonitor"' "$RES/preferences_settings"*.xml
! grep -R -F 'android:data="package:dev.kopandazavr.codexmonitor"' "$RES/preferences_settings"*.xml
! grep -Fq 'DashboardReorderActivity.class' "$SRC/SettingsActivity.java"
grep -Fq 'Ui.startSecondaryActivity(this, CalendarPermissionActivity.class);' "$SRC/OnboardingActivity.java"
grep -Fq 'Uri.parse("package:" + getPackageName())' "$SRC/OnboardingActivity.java"

# Direct limit bells remain independent and reset-timed.
grep -Fq '"five_hour"' "$SRC/DualUsageNotificationManager.java"
grep -Fq '"monthly" : "weekly"' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'RESTORABLE_METRICS = {"five_hour", "weekly", "monthly"}' "$SRC/NowBarResetReminder.java"
grep -Fq 'setExactAndAllowWhileIdle' "$SRC/NowBarResetReminder.java"

# Active process progress remains elapsed 0->100 and visible roles keep direct bells.
grep -Fq 'elapsedPercent' "$SRC/CalendarProcess.java"
grep -Fq 'process.elapsedPercent' "$SRC/ProcessNotificationManager.java"
grep -Fq 'processes, idleRoles, nowMillis, true);' "$SRC/ProcessNotificationManager.java"

# Notification/live-monitor IA remains direct and the old one/both selector stays hidden.
grep -Fq 'android:title="Notifications"' "$RES/preferences_settings.xml"
grep -Fq 'android:key="settings_now_bar"' "$RES/preferences_settings.xml"
! grep -Fq 'notification_live_monitor_settings' "$RES/preferences_settings_notifications.xml"
! grep -Fq 'notification_style_ui' "$RES/preferences_settings_notifications.xml"
grep -A6 -F 'android:key="notification_metric_ui"' "$RES/preferences_settings_notifications.xml" | grep -Fq 'app:isPreferenceVisible="false"'

# Background usage cadence remains separate from the local 5-second repaint.
grep -Fq 'INTERVALS = {5, 10, 15, 30, 60, 120}' "$SHARED/AdaptiveRefreshPolicy.java"
! grep -Fq 'SECONDS.toMillis(5)' "$SHARED/AdaptiveRefreshPolicy.java"

# Current code-bearing candidate identity.
grep -Fq 'versionCode = 44' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionName = "2.18.0"' "$ROOT/app/build.gradle.kts"
grep -Fq 'VERSION_CODE = 44' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = "2.18.0"' "$SRC/AppConstants.java"

grep -Fq 'MediaStore.Downloads.EXTERNAL_CONTENT_URI' "$SRC/SettingsActivity.java"
grep -Fq 'diagnostic_build_identity' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'GoogleCalendarAuthorizationActivity' "$MANIFEST"
grep -Fq 'DiagonalStripeDrawable' "$SRC/IdleReminderOverlayService.java"
echo 'Codex Monitor 2.18.0 scoped source contract PASS'
