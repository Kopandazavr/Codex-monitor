#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
RES="$ROOT/app/src/main/res"

# Version and PHONE-only release identity.
grep -Fq 'versionCode = 45' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionName = "2.19.0"' "$ROOT/app/build.gradle.kts"
grep -Fq 'VERSION_CODE = 45' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = "2.19.0"' "$SRC/AppConstants.java"

# Permissions & connections: Required N/5, Recommended acknowledgements, dashboard shortcut.
grep -Fq 'REQUIRED_TOTAL = 5' "$SRC/SetupReadiness.java"
grep -Fq 'RECOMMENDED_TOTAL = 2' "$SRC/SetupReadiness.java"
grep -Fq 'STATUS_REQUIRED_MISSING' "$SRC/SetupReadiness.java"
grep -Fq 'STATUS_RECOMMENDED_MISSING' "$SRC/SetupReadiness.java"
grep -Fq 'missingRequiredCount' "$SRC/MainActivity.java"
grep -Fq 'ic_permissions_checklist' "$SRC/MainActivity.java"
grep -Fq '"Recommended"' "$SRC/OnboardingActivity.java"
grep -Fq '"Battery usage"' "$SRC/OnboardingActivity.java"
grep -Fq '"Samsung background limits"' "$SRC/OnboardingActivity.java"
! grep -Fq 'Ui.actionRow(this, "Live monitor"' "$SRC/OnboardingActivity.java"
grep -Fq 'PermissionsConnectionsPreference' "$RES/xml/preferences_settings.xml"

# Live Monitor is internal always-on behavior; old user switches/modes are gone.
grep -Fq 'ensureAlwaysOn' "$SRC/NowBarManager.java"
! grep -Fq 'android:key="now_bar_monitor_ui"' "$RES/xml/preferences_settings_now_bar.xml"
! grep -Fq 'android:key="now_bar_display_mode_ui"' "$RES/xml/preferences_settings_now_bar.xml"
! grep -Fq 'android:key="now_bar_percent_mode_ui"' "$RES/xml/preferences_settings_now_bar.xml"
! grep -Fq 'android:key="now_bar_auto_start_ui"' "$RES/xml/preferences_settings_now_bar.xml"
! grep -Fq '"Stop", stopIntent' "$SRC/NowBarManager.java"
! grep -Fq '"Stop", stopIntent' "$SRC/DualUsageNotificationManager.java"

# Production Calendar cadence is 10 seconds with non-exact recovery and connectivity recovery.
grep -Fq 'POLL_INTERVAL_MS = TimeUnit.SECONDS.toMillis(10)' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'registerDefaultNetworkCallback' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'setAndAllowWhileIdle' "$SRC/ProcessNotificationScheduler.java"
! grep -Fq 'setExactAndAllowWhileIdle' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'GoogleCalendarProcessSource.forceRefresh(app' "$SRC/ProcessNotificationScheduler.java"
grep -Fq '"manual_calendar_refresh_completed"' "$SRC/NowBarActionReceiver.java"

# History interaction is bounded on both sides by real measured samples.
grep -Fq 'measuredStartMillis()' "$SRC/UsageBurnChartView.java"
grep -Fq 'measuredEndMillis()' "$SRC/UsageBurnChartView.java"
grep -Fq 'touchX >= measuredLeft && touchX <= measuredRight' "$SRC/UsageBurnChartView.java"
grep -Fq 'start = Math.max(measuredStart, Math.min(start, maxStart))' "$SRC/UsageBurnChartView.java"

# Overlay and status-icon visual deltas.
grep -Fq '0xE61B1B1F, 0xE62D2D33' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'stripeWidth * 2.828427f' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'M54,7 A47,47' "$RES/drawable/ic_notification_codex_monitor.xml"
! grep -Fq 'M32,11 C18,11 11,18 11,32' "$RES/drawable/ic_notification_codex_monitor.xml"

# Retired alert page/features are no longer reachable; test notification moved to Diagnostics.
! grep -Fq 'android:key="settings_notifications"' "$RES/xml/preferences_settings.xml"
grep -Fq 'android:key="notification_test"' "$RES/xml/preferences_settings_diagnostics.xml"
grep -Fq 'ResetAlertPreferences.STYLE_OFF' "$SRC/CodexMonitorApplication.java"

# Collapsed/system progress: five-hour by default; Weekly exhaustion uses mint reset progress.
grep -Fq 'weeklyResetProgress' "$SRC/NowBarManager.java"
grep -Fq '0xFFA8E6CF' "$SRC/NowBarManager.java"
grep -Fq 'resetCycleProgressPercent' "$SRC/NowBarManager.java"

echo "Codex Monitor 2.19 source contract PASS"
