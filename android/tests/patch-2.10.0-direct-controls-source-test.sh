#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/bennett/codexmeter"
SHARED="$ROOT/shared/src/main/java/dev/bennett/codexmeter"

# Foreground lifecycle refresh is real network work, coalesced across dashboard and app-level
# transitions, while the previous cached snapshot remains authoritative for presentation.
test -f "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'UsageApi.refreshAndCacheScheduled(app, true, trigger)' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'ForegroundUsageRefresh.request(context, "foreground_main");' "$SRC/RefreshEngagement.java"
grep -Fq 'ForegroundUsageRefresh.request(this, "foreground_transition");' "$SRC/CodexMeterApplication.java"
grep -Fq 'ForegroundUsageRefresh.isInFlight()' "$SRC/RefreshScheduler.java"
grep -Fq '"immediate_refresh_coalesced"' "$SRC/RefreshScheduler.java"

# Cached usage cards stay visible while refreshing; title-area state distinguishes in-flight and
# failed refresh without blanking or replacing the whole card.
grep -Fq 'ForegroundUsageRefresh.isInFlight()' "$SRC/UsageWaveView.java"
grep -Fq 'ForegroundUsageRefresh.isStale()' "$SRC/UsageWaveView.java"
grep -Fq '"Not refreshed"' "$SRC/UsageWaveView.java"
grep -Fq 'drawRefreshState' "$SRC/UsageWaveView.java"

# TEMPORARY 2.10 Samsung diagnostic: presentation repaints every five seconds through the cached
# notification path only. It must never imply a five-second remote usage refresh.
grep -Fq 'DIAGNOSTIC_FIVE_SECOND_REPAINT = true' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'TimeUnit.SECONDS.toMillis(5)' "$SRC/ProcessNotificationScheduler.java"
grep -Fq '"diagnostic_5s_repaint"' "$SRC/NowBarActionReceiver.java"
grep -Fq 'DualUsageNotificationManager.repostFromCache(context)' "$SRC/NowBarActionReceiver.java"
grep -Fq '"remote_fetch", false' "$SRC/NowBarActionReceiver.java"
! grep -Fq 'UsageApi.' "$SRC/ProcessNotificationScheduler.java"
grep -Fq '"fingerprint", semanticFingerprint(state)' "$SRC/DualUsageNotificationManager.java"

# Direct limit bells are independent per visible window and use local AlarmManager reset timing,
# not presentation cadence or foreground polling, for audible delivery.
grep -Fq '"five_hour"' "$SRC/DualUsageNotificationManager.java"
grep -Fq '"monthly" : "weekly"' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'RESTORABLE_METRICS = {"five_hour", "weekly", "monthly"}' "$SRC/NowBarResetReminder.java"
grep -Fq 'return "armed_" + metric;' "$SRC/NowBarResetReminder.java"
grep -Fq 'setExactAndAllowWhileIdle' "$SRC/NowBarResetReminder.java"
grep -Fq '"limit_reset_bell_toggled"' "$SRC/NowBarResetReminder.java"
grep -Fq '"limit_reset_alert_fired"' "$SRC/NowBarResetReminder.java"

# Active agent progress is elapsed 0->100 and every active/idle row owns its bell in all modes.
grep -Fq 'elapsedPercent' "$SRC/CalendarProcess.java"
grep -Fq 'process.elapsedPercent' "$SRC/ProcessNotificationManager.java"
grep -Fq 'processes, idleRoles, nowMillis, true);' "$SRC/ProcessNotificationManager.java"
! grep -Fq 'processes, idleRoles, nowMillis, false)' "$SRC/ProcessNotificationManager.java"

# Settings expose one top-level notification area; the old Now Bar page remains only as a
# compatibility subpage and the legacy one/both metric selector is no longer user-facing.
grep -Fq 'android:title="Notifications &amp; live monitor"' "$ROOT/app/src/main/res/xml/preferences_settings.xml"
grep -A4 -F 'android:key="settings_now_bar"' "$ROOT/app/src/main/res/xml/preferences_settings.xml" \
  | grep -Fq 'app:isPreferenceVisible="false"'
grep -Fq 'android:key="notification_live_monitor_settings"' "$ROOT/app/src/main/res/xml/preferences_settings_notifications.xml"
grep -A6 -F 'android:key="notification_metric_ui"' "$ROOT/app/src/main/res/xml/preferences_settings_notifications.xml" \
  | grep -Fq 'app:isPreferenceVisible="false"'

# Remote usage polling remains minute-based adaptive scheduling; five-second work is local-only.
grep -Fq 'INTERVALS = {5, 10, 15, 30, 60, 120}' "$SHARED/AdaptiveRefreshPolicy.java"
! grep -Fq 'SECONDS.toMillis(5)' "$SHARED/AdaptiveRefreshPolicy.java"

# The test build is a real 2.10 upgrade on phone and Wear, not a separate 2.9.5 release.
grep -Fq 'versionCode = 36' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionName = "2.10.0"' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionCode = 36' "$ROOT/wear/build.gradle.kts"
grep -Fq 'versionName = "2.10.0"' "$ROOT/wear/build.gradle.kts"
grep -Fq 'VERSION_CODE = 36' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = "2.10.0"' "$SRC/AppConstants.java"

echo 'Codex Monitor 2.10.0 direct-controls/live-freshness source contract PASS'
