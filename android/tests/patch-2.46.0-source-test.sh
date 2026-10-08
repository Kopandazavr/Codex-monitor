#!/usr/bin/env bash
set -euo pipefail
trap 'echo "2.46 source contract failed at line $LINENO" >&2' ERR
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
RES="$ROOT/app/src/main/res"

VERSION_NAME="$(awk -F'"' '/versionName = "/ { print $2; exit }' "$ROOT/app/build.gradle.kts")"
VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")"
[[ "$VERSION_NAME" == "2.46.0" ]]
[[ "$VERSION_CODE" -eq 72 ]]
grep -Fq 'VERSION_NAME = "2.46.0"' "$SRC/AppConstants.java"
grep -Fq 'VERSION_CODE = 72' "$SRC/AppConstants.java"

# Header is a single fixed measured view; only its sibling body scrolls.
grep -Fq '@+id/main_fixed_header' "$RES/layout/activity_oneui_dashboard.xml"
grep -Fq '@+id/dashboard_scroll' "$RES/layout/activity_oneui_dashboard.xml"
grep -Fq 'android:layout_weight="1"' "$RES/layout/activity_oneui_dashboard.xml"
grep -Fq 'setContentView(R.layout.activity_oneui_dashboard)' "$SRC/MainActivity.java"
grep -Fq 'Ui.configureSystemBars(this, findViewById(R.id.main_dashboard_root)' "$SRC/MainActivity.java"
! grep -Fq 'seslSetCustomHeight' "$SRC/MainActivity.java"
! grep -Fq 'toolbarLayout.setSubtitle' "$SRC/MainActivity.java"

# Only One Card: no user-selectable legacy modes and no legacy notification publishers.
! grep -Fq 'process_notification_mode_ui' "$RES/xml/preferences_settings_now_bar.xml"
! grep -Fq 'process_notification_mode_entries' "$RES/values/arrays.xml"
! grep -Fq 'PER_PROCESS = ' "$SRC/ProcessNotificationMode.java"
! grep -Fq 'GROUPED = ' "$SRC/ProcessNotificationMode.java"
! grep -Fq 'syncPerProcess(' "$SRC/ProcessNotificationManager.java"
! grep -Fq 'manager.notify(tag, GROUPED_NOTIFICATION_ID' "$SRC/ProcessNotificationManager.java"
grep -Fq 'prefs.edit().remove(scoped).remove(PREFERENCE_KEY).apply()' "$SRC/ProcessNotificationMode.java"
grep -Fq 'clearAll(context, containerId)' "$SRC/ProcessNotificationManager.java"

# One durable Shown/Hidden switch per account; hidden cannot resurrect on later callbacks.
grep -Fq 'getBoolean(KEY_PREFIX + containerId, true)' "$SRC/PersistentCardVisibility.java"
grep -Fq 'setWidgetLayoutResource(R.layout.preference_account_visibility_widget)' "$SRC/SettingsActivity.java"
grep -Fq 'pill.setText(shown ? "Shown" : "Hidden")' "$SRC/SettingsActivity.java"
grep -Fq 'PersistentCardCoordinator.changeVisibility' "$SRC/PersistentCardVisibility.java"
grep -Fq 'PersistentCardVisibility.clear(context, target)' "$SRC/AccountContainerLifecycle.java"
grep -Fq 'PersistentCardVisibility.isShown(context, containerId)' "$SRC/PersistentCardCoordinator.java"

# Every persistent publisher routes through one serialized, non-network reorder path.
grep -Fq 'static synchronized boolean publish(' "$SRC/PersistentCardCoordinator.java"
grep -Fq 'for (int i = index - 1; i >= 0; i--)' "$SRC/PersistentCardCoordinator.java"
grep -Fq 'for (int i = accounts.size() - 1; i >= 0; i--)' "$SRC/PersistentCardCoordinator.java"
grep -Fq '.setSilent(true).setOnlyAlertOnce(true).build()' "$SRC/PersistentCardCoordinator.java"
grep -Fq 'PersistentCardCoordinator.publish(context, containerId' "$SRC/NowBarManager.java"
grep -Fq 'PersistentCardCoordinator.restoreOrder(this)' "$SRC/CodexMonitorApplication.java"
grep -Fq 'PersistentCardCoordinator.publish(context, containerId' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'PersistentCardCoordinator.attentionWhenHidden' "$SRC/DualUsageNotificationManager.java"
! grep -Fq 'UsageApi.' "$SRC/PersistentCardCoordinator.java"
! grep -Fq 'GoogleCalendarProcessSource.' "$SRC/PersistentCardCoordinator.java"

echo "Codex Monitor 2.46 source contract PASS"
