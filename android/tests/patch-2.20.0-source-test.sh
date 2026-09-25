#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
RES="$ROOT/app/src/main/res"

# PHONE release identity.
grep -Fq 'versionCode = 46' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionName = "2.20.0"' "$ROOT/app/build.gradle.kts"
grep -Fq 'VERSION_CODE = 46' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = "2.20.0"' "$SRC/AppConstants.java"

# Strict watchdog metadata: marker + project + role required; no project-only fallback state.
grep -Fq 'missing_or_invalid_marker' "$SRC/CalendarProcess.java"
grep -Fq 'missing_or_invalid_project' "$SRC/CalendarProcess.java"
grep -Fq 'missing_or_invalid_role' "$SRC/CalendarProcess.java"
grep -Fq 'isCanonicalIdentity' "$SRC/CalendarProcess.java"
grep -Fq '"watchdog_rejected_metadata"' "$SRC/CalendarProcessReader.java"
grep -Fq '"source", "calendar_provider"' "$SRC/CalendarProcessReader.java"
grep -Fq '"watchdog_rejected_metadata"' "$SRC/GoogleCalendarProcessSource.java"
grep -Fq '"source", "direct_api"' "$SRC/GoogleCalendarProcessSource.java"
grep -Fq '"source", "direct_cache"' "$SRC/GoogleCalendarProcessSource.java"
grep -Fq '"watchdog_idle_state_rejected"' "$SRC/IdleProcessState.java"
! grep -Fq 'return "project:" + project' "$SRC/IdleProcessState.java"

# Readiness: 5 Required + 1 Recommended; Optional excluded.
grep -Fq 'REQUIRED_TOTAL = 5' "$SRC/SetupReadiness.java"
grep -Fq 'RECOMMENDED_TOTAL = 1' "$SRC/SetupReadiness.java"
grep -Fq 'return REQUIRED_TOTAL + RECOMMENDED_TOTAL' "$SRC/SetupReadiness.java"
grep -Fq 'overallSummary' "$SRC/SetupReadiness.java"
grep -Fq 'SetupReadiness.overallSummary(requireContext())' "$SRC/SettingsActivity.java"
grep -Fq 'SetupReadiness.overallSummary(this)' "$SRC/OnboardingActivity.java"
grep -Fq '"Battery usage"' "$SRC/OnboardingActivity.java"
! grep -Fq '"Samsung background limits"' "$SRC/OnboardingActivity.java"
! grep -Fq 'Never sleeping apps' "$SRC/OnboardingActivity.java"

# Required -> Recommended -> Optional source order.
required_line="$(grep -n 'addSectionHeader("Required")' "$SRC/OnboardingActivity.java" | head -n1 | cut -d: -f1)"
recommended_line="$(grep -n 'addSectionHeader("Recommended")' "$SRC/OnboardingActivity.java" | head -n1 | cut -d: -f1)"
optional_line="$(grep -n 'addSectionHeader("Optional")' "$SRC/OnboardingActivity.java" | head -n1 | cut -d: -f1)"
[[ "$required_line" -lt "$recommended_line" && "$recommended_line" -lt "$optional_line" ]]

# Dashboard glyph stays white/gear-sized; only a smaller lower-right badge carries readiness color.
grep -Fq 'ColorStateList.valueOf(0xFFFFFFFF)' "$SRC/MainActivity.java"
grep -Fq 'Ui.dp(this, 24), Ui.dp(this, 24), Gravity.CENTER' "$SRC/MainActivity.java"
grep -Fq 'Ui.dp(this, 13), Ui.dp(this, 13), Gravity.BOTTOM | Gravity.END' "$SRC/MainActivity.java"
grep -Fq 'badgeBackground.setColor(color)' "$SRC/MainActivity.java"

# Completion stripes use a full rotated plane and darker light band.
grep -Fq '0xE61B1B1F, 0xE6222226' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'canvas.rotate(-45.0f' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'stripeWidth * 2.0f' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'Math.hypot(bounds.width(), bounds.height())' "$SRC/IdleReminderOverlayService.java"

# Custom RemoteViews keep their own usage bars; no duplicate framework progress line.
! grep -Fq 'setProgress(100, systemProgress, false)' "$SRC/DualUsageNotificationManager.java"
! grep -Fq 'systemProgressPercent' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'setProgressBar(R.id.notification_five_progress' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'setProgressBar(R.id.notification_long_progress' "$SRC/DualUsageNotificationManager.java"

# Root-level Settings rows all carry icons.
grep -Fq 'android:icon="@drawable/ic_permissions_checklist"' "$RES/xml/preferences_settings.xml"
grep -Fq 'android:icon="@drawable/ic_settings_live_monitor"' "$RES/xml/preferences_settings.xml"
grep -Fq 'android:icon="@drawable/ic_settings_diagnostics"' "$RES/xml/preferences_settings.xml"
grep -Fq 'app:iconSpaceReserved="true"' "$RES/xml/preferences_settings.xml"
test -f "$RES/drawable/ic_settings_live_monitor.xml"
test -f "$RES/drawable/ic_settings_diagnostics.xml"

# Accepted large circular small icon is preserved.
grep -Fq 'M54,7 A47,47' "$RES/drawable/ic_notification_codex_monitor.xml"

echo "Codex Monitor 2.20 source contract PASS"
