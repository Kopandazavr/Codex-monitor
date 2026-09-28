#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"

VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")"
[[ "$VERSION_CODE" -ge 56 ]]
grep -Fq 'VERSION_CODE = ' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = ' "$SRC/AppConstants.java"

# Completion is a fresh notification edge on a versioned user-controlled channel.
grep -Fq 'PROCESS_COMPLETION_CHANNEL_ID = "codex_process_completion_v2"' "$SRC/AlertSoundManager.java"
grep -Fq 'KEY_CHANNEL_MIGRATED = "channel_migrated_v2"' "$SRC/AlertSoundManager.java"
grep -Fq '"codex_process_completion_v1"' "$SRC/AlertSoundManager.java"
grep -Fq 'legacy.getImportance()' "$SRC/AlertSoundManager.java"
grep -Fq 'legacy.getVibrationPattern()' "$SRC/AlertSoundManager.java"
grep -Fq 'ProcessNotificationManager.postCompletionAlert(context, idle, nowMillis)' "$SRC/IdleReminderManager.java"
grep -Fq 'new Notification.Builder(' "$SRC/ProcessNotificationManager.java"
grep -Fq 'AlertSoundManager.PROCESS_COMPLETION_CHANNEL_ID)' "$SRC/ProcessNotificationManager.java"
grep -Fq '.setCategory(Notification.CATEGORY_EVENT)' "$SRC/ProcessNotificationManager.java"
grep -Fq '"completion_notification_posted"' "$SRC/ProcessNotificationManager.java"
! grep -Fq '.setVibrate(' "$SRC/ProcessNotificationManager.java"
! grep -Fq '.setSound(' "$SRC/ProcessNotificationManager.java"
! grep -Fq 'AlertSoundManager.playProcessCompletion(context)' "$SRC/IdleReminderManager.java"
! grep -Eq 'Vibrator|VibrationEffect' "$SRC/IdleReminderManager.java"

# Direct Calendar health is source-driven, fallback-aware, actionable, and clears on recovery.
grep -Fq 'ACTION_CALENDAR_HEALTH_CHANGED' "$SRC/AppConstants.java"
grep -Fq 'static boolean isDirectHealthy(Context context)' "$SRC/MonitorHealthDiagnostics.java"
grep -Fq 'GoogleCalendarProcessSource.hasFreshCache' "$SRC/MonitorHealthDiagnostics.java"
grep -Fq '"calendar_provider_fallback"' "$SRC/MonitorHealthDiagnostics.java"
grep -Fq 'notifyHealthChanged(context);' "$SRC/MonitorHealthDiagnostics.java"
grep -Fq 'buildCalendarHealthActionView()' "$SRC/MainActivity.java"
grep -Fq 'calendarHealth.setVisible(!MonitorHealthDiagnostics.isDirectHealthy(this));' "$SRC/MainActivity.java"
grep -Fq '"Retry now"' "$SRC/MainActivity.java"
grep -Fq 'GoogleCalendarProcessSource.forceRefresh(this' "$SRC/MainActivity.java"
grep -Fq '"Reconnect"' "$SRC/MainActivity.java"
grep -Fq 'openPermissionsConnections()' "$SRC/MainActivity.java"
grep -Fq 'return canonicalize(context, queryProvider(context, nowMillis));' "$SRC/CalendarProcessReader.java"

# The redundant button is gone; chart-owned tap-toggle zoom and pan bounds remain.
! grep -Fq 'setContentDescription("Zoom Out")' "$SRC/MainActivity.java"
! grep -Fq 'chart.zoomOut()' "$SRC/MainActivity.java"
grep -Fq 'chart.setZoomEnabled(true)' "$SRC/MainActivity.java"
grep -Fq 'chart.setOnZoomChangedListener' "$SRC/MainActivity.java"
grep -Fq 'zoomOut();' "$SRC/UsageBurnChartView.java"
grep -Fq '"chart_tap_toggle"' "$SRC/UsageBurnChartView.java"
grep -Fq 'start = Math.max(measuredStart, Math.min(start, maxStart))' "$SRC/UsageBurnChartView.java"

echo "Codex Monitor 2.30 source contract PASS"
