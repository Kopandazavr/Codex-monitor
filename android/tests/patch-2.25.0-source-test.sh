#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
RES="$ROOT/app/src/main/res"

grep -Fq 'versionCode = 51' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionName = "2.25.0"' "$ROOT/app/build.gradle.kts"
grep -Fq 'VERSION_CODE = 51' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = "2.25.0"' "$SRC/AppConstants.java"

# Limit-card palette: normal surface, blue 5-hour, orange Weekly, no liquid at zero.
grep -Fq 'FIVE_HOUR_BLUE = 0xFF4D81EF' "$SRC/UsageWaveView.java"
grep -Fq 'WEEKLY_ORANGE = 0xFFFF9800' "$SRC/UsageWaveView.java"
grep -Fq 'if (fillPercent > 0)' "$SRC/UsageWaveView.java"
! grep -Fq 'canvas.drawRect(0f, 0f, getWidth(), getHeight(), trackPaint)' "$SRC/UsageWaveView.java"
grep -Fq 'fillPaint.setColor(weekly ? WEEKLY_ORANGE : FIVE_HOUR_BLUE);' "$SRC/UsageWaveView.java"

# Process identity/action alignment.
grep -Fq 'disc.setStroke(Ui.dp(context, 1.5f), accent);' "$SRC/ProjectBadgeView.java"
! grep -Fq 'icon.setTranslationY(-Ui.dp(context, 2))' "$SRC/ProjectBadgeView.java"
grep -Fq 'bell.setTranslationY(-Ui.dp(this, 2));' "$SRC/MainActivity.java"
grep -Fq 'action.setTranslationY(-Ui.dp(this, 2));' "$SRC/MainActivity.java"

# Project Settings stays in one dialog and uses six-column compact icon grid.
grep -Fq 'ICON_COLUMNS = 6' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'render(true);' "$SRC/ProjectSettingsDialog.java"
! grep -Fq 'reopen(' "$SRC/ProjectSettingsDialog.java"
! grep -Fq '"Save short name", false' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'commit.setVisibility(focused ? View.VISIBLE : View.GONE);' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'commit.setEnabled(focused && !current.equals(persisted));' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'return automaticAcronym(primaryAlias);' "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/ProjectProfileRules.java"

# Exactly two user-meaningful sound channels + one silent operational channel.
grep -Fq 'PROCESS_COMPLETION_CHANNEL_ID = "codex_process_completion_v1"' "$SRC/AlertSoundManager.java"
grep -Fq 'LIMITS_RESET_CHANNEL_ID = "codex_limits_reset_v1"' "$SRC/AlertSoundManager.java"
grep -Fq 'OPERATIONAL_CHANNEL_ID = "codex_operational_v1"' "$SRC/AlertSoundManager.java"
grep -Fq '"Process completion"' "$SRC/AlertSoundManager.java"
grep -Fq '"Limits reset"' "$SRC/AlertSoundManager.java"
grep -Fq 'manager.deleteNotificationChannel(legacyId);' "$SRC/AlertSoundManager.java"
grep -Fq 'AlertSoundManager.playProcessCompletion(context)' "$SRC/IdleReminderManager.java"
grep -Fq 'AlertSoundManager.playLimitsReset(context)' "$SRC/NowBarResetReminder.java"
grep -Fq 'CHANNEL_ID = AlertSoundManager.OPERATIONAL_CHANNEL_ID' "$SRC/NowBarManager.java"
grep -Fq 'CHANNEL_ID = AlertSoundManager.OPERATIONAL_CHANNEL_ID' "$SRC/ProcessNotificationManager.java"
grep -Fq 'CHANNEL_ID = AlertSoundManager.OPERATIONAL_CHANNEL_ID' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'CHANNEL_ID = AlertSoundManager.OPERATIONAL_CHANNEL_ID' "$SRC/OAuthService.java"
! grep -Fq 'new NotificationChannel(CHANNEL_NOTIFY' "$SRC/ResetNotificationManager.java"
! grep -Fq 'new NotificationChannel(CHANNEL_ALARM' "$SRC/ResetNotificationManager.java"
! grep -Fq 'new NotificationChannel(CHANNEL_SILENT' "$SRC/ResetNotificationManager.java"

# Speaker-routing preference is global, defaults off, and has normal-route fallback.
grep -Fq 'android:key="alert_sounds_phone_speaker"' "$RES/xml/preferences_settings_now_bar.xml"
grep -Fq 'android:defaultValue="false"' "$RES/xml/preferences_settings_now_bar.xml"
grep -Fq 'android:title="Play alert sounds on phone speaker"' "$RES/xml/preferences_settings_now_bar.xml"
grep -Fq 'setPreferredDevice(speaker)' "$SRC/AlertSoundManager.java"
grep -Fq '"speaker_fallback"' "$SRC/AlertSoundManager.java"
grep -Fq 'AlertSoundManager.setPlayOnPhoneSpeaker' "$SRC/SettingsActivity.java"

echo "Codex Monitor 2.25 source contract PASS"
