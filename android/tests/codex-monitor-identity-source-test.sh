#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_GRADLE="$ROOT/app/build.gradle.kts"
STRINGS="$ROOT/app/src/main/res/values/strings.xml"
BUILD="$ROOT/build.sh"
WORKFLOW="$ROOT/../.github/workflows/build-apk.yml"
BRANDING="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/Branding.java"
HOME_VERSION="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/HomeVersionLabel.java"
APP_CLASS="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/CodexMonitorApplication.java"
ONBOARDING="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/OnboardingActivity.java"
USAGE_API="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/UsageApi.java"
USAGE_JOB="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/UsageRefreshJobService.java"
SUB_API="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/SubscriptionApi.java"
SUB_STORE="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/SubscriptionStore.java"
CALENDAR="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/CalendarProcess.java"
IDLE_STATE="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/IdleProcessState.java"
PROCESS_NOTIF="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/ProcessNotificationManager.java"
SURFACE_CONTRACT="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/NotificationSurfaceContract.java"
ICON_SAFE="$ROOT/app/src/main/res/drawable/codex_monitor_focus_fg_safe.xml"
MONO_SAFE="$ROOT/app/src/main/res/drawable/codex_monitor_monochrome_safe.xml"
LAUNCHER="$ROOT/app/src/main/res/mipmap-anydpi/ic_launcher.xml"
LAUNCHER_V33="$ROOT/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"

# Canonical supported Android identity is the phone app. Wear source is historical/unsupported.
grep -Fq 'applicationId = "dev.kopandazavr.codexmonitor"' "$APP_GRADLE"
grep -Fq 'namespace = "dev.kopandazavr.codexmonitor"' "$APP_GRADLE"
! grep -R -Fq 'dev.bennett.codexmeter' "$ROOT/app/src" "$ROOT/shared/src"
! grep -R -Fq 'dev.kopandazavr.codexwatch' "$ROOT/app/src" "$ROOT/shared/src"
grep -Fq '<string name="app_name">Codex Monitor</string>' "$STRINGS"
grep -Fq 'private static final String PRODUCT_NAME = "Codex Monitor"' "$BRANDING"
grep -Fq 'Branding.apply(activity);' "$APP_CLASS"
grep -Fq 'private static final String HOME_TITLE = "Codex Monitor"' "$HOME_VERSION"

# Current supported phone candidate identity stays synchronized with the newest bounded scope.
APP_VERSION_NAME="$(awk -F'"' '/versionName = "/ { print $2; exit }' "$APP_GRADLE")"
APP_VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$APP_GRADLE")"
grep -Fq "VERSION_NAME = \"$APP_VERSION_NAME\"" "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/AppConstants.java"
grep -Fq "VERSION_CODE = $APP_VERSION_CODE" "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/AppConstants.java"
grep -Fq 'rootProject.name = "Codex-Monitor"' "$ROOT/settings.gradle.kts"
grep -Fq 'play-services-auth:22.0.0' "$APP_GRADLE"
grep -Fq 'codex-monitor-local.p12' "$APP_GRADLE"
grep -Fq 'keyAlias = "codexmonitor"' "$APP_GRADLE"
grep -Fq 'android:scheme="codexmonitor"' "$MANIFEST"
grep -Fq 'codex_monitor_watchdog' "$CALENDAR"
grep -Fq 'codex_meter_watchdog' "$CALENDAR" # legacy parser compatibility only

# Release artifacts keep the Codex Monitor identity, but the personal-use app no longer contains an
# in-app update client, installer, release browser, install permission, or update API config.
grep -Fq 'OUT="$DIST/CodexMonitor-$VERSION_NAME.apk"' "$BUILD"
! grep -Fq 'CodexMonitor-Wear' "$BUILD"
grep -Fq 'name: Build Codex Monitor APK' "$WORKFLOW"
grep -Fq 'name: codex-monitor-${{ steps.version.outputs.name }}-ci' "$WORKFLOW"
! grep -Fq 'CodexMonitor-Wear' "$WORKFLOW"
grep -Fq -- '--title "Codex Monitor $VERSION_NAME"' "$WORKFLOW"
! grep -Fq 'UPDATE_API_URL' "$APP_GRADLE"
! grep -Fq 'REQUEST_INSTALL_PACKAGES' "$MANIFEST"
for file in GitHubRelease.java GitHubReleaseParser.java GitHubReleaseSource.java \
  ReleaseHistoryActivity.java ReleaseIntegrity.java ReleaseNotesMarkdown.java ReleaseNotesUi.java \
  ReleaseUpdateClient.java ReleaseUpdateJobService.java ReleaseUpdatePolicy.java \
  ReleaseUpdateScheduler.java ReleaseVersion.java UpdateActivity.java UpdateChannel.java \
  UpdateCheckFrequency.java UpdateInstallReceiver.java UpdateInstaller.java \
  UpdateNotificationManager.java UpdatePreferences.java; do
  ! test -e "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/$file"
done

# Target-Samsung Quick Setup contract: static one-screen layout, no collapsing/scrolling surface,
# and the primary CTA lives outside the flexible content area at the bottom.
grep -Fq 'installStaticLayout()' "$ONBOARDING"
grep -Fq 'LinearLayout.LayoutParams(-1, 0, 1.0f)' "$ONBOARDING"
grep -Fq 'this.doneButton = Ui.nativePrimaryButton(this,' "$ONBOARDING"
grep -Fq 'this.settingsEntry ? "Done" : "Open Codex Monitor"' "$ONBOARDING"
! grep -Fq 'Ui.installPage(this, "Quick setup"' "$ONBOARDING"
! grep -Fq 'NestedScrollView' "$ONBOARDING"
grep -Fq 'Settings.ACTION_APP_NOTIFICATION_SETTINGS' "$ONBOARDING"

# Target-Samsung launcher contract: preserve the selected Focus artwork but inset it by ~10% of
# the 108dp adaptive-icon canvas on every side, exposing graphite background as a real safe area.
for edge in left top right bottom; do
  grep -Fq "android:${edge}=\"11dp\"" "$ICON_SAFE"
  grep -Fq "android:${edge}=\"11dp\"" "$MONO_SAFE"
done
grep -Fq 'android:drawable="@drawable/codex_monitor_focus_fg_safe"' "$LAUNCHER"
grep -Fq 'android:drawable="@drawable/codex_monitor_focus_fg_safe"' "$LAUNCHER_V33"
grep -Fq 'android:drawable="@drawable/codex_monitor_monochrome_safe"' "$LAUNCHER_V33"

# Critical refresh contract: explicit refresh is a real network cycle, subscription TTL can be
# bypassed, stale stored expiry is not surfaced, persistence emits ACTION_USAGE_UPDATED, and the
# notification rebuild is explicitly logged from the newly persisted snapshot.
grep -Fq 'refreshAndCacheInternal(context, true, "manual_direct")' "$USAGE_API"
grep -Fq 'refreshAndCacheScheduled' "$USAGE_API"
grep -Fq 'boolean forceSubscription = "immediate".equals(this.reason);' "$USAGE_JOB"
grep -Fq 'SubscriptionApi.refreshAndCacheLocked(context, authTokens,' "$USAGE_API"
grep -Fq 'forceSubscription, safeTrigger' "$USAGE_API"
grep -Fq 'subscription_refresh_fetching' "$SUB_API"
grep -Fq 'backend_stale' "$SUB_API"
grep -Fq 'storedActiveUntilMillis' "$SUB_STORE"
grep -Fq 'storedUntil > 0L && storedUntil <= System.currentTimeMillis()' "$SUB_STORE"
grep -Fq 'usage_snapshot_replaced' "$USAGE_API"
grep -Fq 'ACTION_USAGE_UPDATED' "$USAGE_API"
grep -Fq 'usage_update_broadcast_sent' "$USAGE_API"
grep -Fq 'usage_notification_rebuilt' "$USAGE_API"

# Role/display contract: reuse the canonical parser, normalize Calendar HTML, persist optional
# project_short through completion, and render compact project — role with legacy full-project fallback.
grep -Fq 'replaceAll("(?is)<br\\s*/?>", " ")' "$CALENDAR"
grep -Fq 'static String displayIdentity' "$CALENDAR"
grep -Fq 'static boolean hasCanonicalRole' "$CALENDAR"
grep -Fq 'CalendarProcess.displayIdentity(role, project, projectShort, topic)' "$IDLE_STATE"
grep -Fq 'json.put("project_short", projectShort)' "$IDLE_STATE"
grep -Fq 'json.optString("project_short", "")' "$IDLE_STATE"
grep -Fq 'CalendarProcess.isCanonicalIdentity(process.project, process.role)' "$IDLE_STATE"
grep -Fq 'process.displayLabel()' "$PROCESS_NOTIF"

# Two-card ordering remains structural: common group + stable keys, with both surfaces in STATUS
# ranking class so Samsung cannot promote Processes just because it was CATEGORY_PROGRESS.
grep -Fq 'SORT_USAGE = "00_usage"' "$SURFACE_CONTRACT"
grep -Fq 'SORT_PROCESSES = "10_processes"' "$SURFACE_CONTRACT"
grep -Fq '.setCategory(Notification.CATEGORY_STATUS)' "$PROCESS_NOTIF"
! grep -Fq '.setCategory(Notification.CATEGORY_PROGRESS)' "$PROCESS_NOTIF"

echo 'Codex Monitor critical correction source contract PASS'
