#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_GRADLE="$ROOT/app/build.gradle.kts"
WEAR_GRADLE="$ROOT/wear/build.gradle.kts"
STRINGS="$ROOT/app/src/main/res/values/strings.xml"
WEAR_STRINGS="$ROOT/wear/src/main/res/values/strings.xml"
BUILD="$ROOT/build.sh"
WORKFLOW="$ROOT/../.github/workflows/build-apk.yml"
PARSER="$ROOT/app/src/main/java/dev/bennett/codexmeter/GitHubReleaseParser.java"
BRANDING="$ROOT/app/src/main/java/dev/bennett/codexmeter/Branding.java"
HOME_VERSION="$ROOT/app/src/main/java/dev/bennett/codexmeter/HomeVersionLabel.java"
APP_CLASS="$ROOT/app/src/main/java/dev/bennett/codexmeter/CodexMeterApplication.java"
USAGE_API="$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageApi.java"
USAGE_JOB="$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageRefreshJobService.java"
SUB_API="$ROOT/app/src/main/java/dev/bennett/codexmeter/SubscriptionApi.java"
SUB_STORE="$ROOT/app/src/main/java/dev/bennett/codexmeter/SubscriptionStore.java"
CALENDAR="$ROOT/app/src/main/java/dev/bennett/codexmeter/CalendarProcess.java"
IDLE_STATE="$ROOT/app/src/main/java/dev/bennett/codexmeter/IdleProcessState.java"
PROCESS_NOTIF="$ROOT/app/src/main/java/dev/bennett/codexmeter/ProcessNotificationManager.java"
SURFACE_CONTRACT="$ROOT/app/src/main/java/dev/bennett/codexmeter/NotificationSurfaceContract.java"

# Installed-app identity remains unchanged while live product branding advances to Codex Monitor.
grep -Fq 'applicationId = "dev.kopandazavr.codexwatch"' "$APP_GRADLE"
grep -Fq 'applicationId = "dev.kopandazavr.codexwatch"' "$WEAR_GRADLE"
! grep -Fq 'applicationId = "dev.bennett.codexmeter"' "$APP_GRADLE"
! grep -Fq 'applicationId = "dev.bennett.codexmeter"' "$WEAR_GRADLE"
grep -Fq '<string name="app_name">Codex Monitor</string>' "$STRINGS"
grep -Fq '<string name="app_name">Codex Monitor</string>' "$WEAR_STRINGS"
grep -Fq 'Codex Monitor contains no analytics SDK' "$STRINGS"
grep -Fq 'private static final String PRODUCT_NAME = "Codex Monitor"' "$BRANDING"
grep -Fq 'Branding.apply(activity);' "$APP_CLASS"
grep -Fq 'private static final String HOME_TITLE = "Codex Monitor"' "$HOME_VERSION"

# Patch release stays on the established 2.9.x line and remains monotonic for phone updates.
grep -Fq 'versionName = "2.9.1"' "$APP_GRADLE"
grep -Fq 'versionCode = 32' "$APP_GRADLE"
grep -Fq 'versionName = "2.9.1"' "$WEAR_GRADLE"
grep -Fq 'versionCode = 32' "$WEAR_GRADLE"

# Live updater/release/artifact identity follows Codex Monitor while parser stays compatible with
# historical Codex Watch / Codex Meter release assets.
grep -Fq 'https://api.github.com/repos/Kopandazavr/Codex-monitor/releases?per_page=30' "$APP_GRADLE"
grep -Fq 'String expectedApk = "CodexMonitor-"' "$PARSER"
grep -Fq 'String legacyWatchApk = "CodexWatch-"' "$PARSER"
grep -Fq 'String legacyMeterApk = "CodexMeter-"' "$PARSER"
grep -Fq 'releaseName = "Codex Monitor " + version.normalized();' "$PARSER"
grep -Fq 'OUT="$DIST/CodexMonitor-$VERSION_NAME.apk"' "$BUILD"
grep -Fq 'WEAR_OUT="$DIST/CodexMonitor-Wear-$VERSION_NAME.apk"' "$BUILD"
grep -Fq 'name: Build Codex Monitor APK' "$WORKFLOW"
grep -Fq 'name: codex-monitor-${{ steps.version.outputs.name }}-ci' "$WORKFLOW"
grep -Fq 'release-dist/CodexMonitor-Wear-$VERSION_NAME.apk#Codex Monitor Wear OS $VERSION_NAME APK' "$WORKFLOW"
grep -Fq -- '--title "Codex Monitor $VERSION_NAME"' "$WORKFLOW"

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

# Role/display contract: reuse the canonical parser, normalize Calendar HTML, prefer role across
# active/idle/one-each presentation, and retain project fallback only when role is unavailable.
grep -Fq 'replaceAll("(?is)<br\\s*/?>", " ")' "$CALENDAR"
grep -Fq 'static String displayIdentity' "$CALENDAR"
grep -Fq 'static boolean hasCanonicalRole' "$CALENDAR"
grep -Fq 'CalendarProcess.displayIdentity(role, project, topic)' "$IDLE_STATE"
grep -Fq 'CalendarProcess.hasCanonicalRole(role)' "$IDLE_STATE"
grep -Fq 'process.displayLabel()' "$PROCESS_NOTIF"

# Two-card ordering remains structural: common group + stable keys, with both surfaces in STATUS
# ranking class so Samsung cannot promote Processes just because it was CATEGORY_PROGRESS.
grep -Fq 'SORT_USAGE = "00_usage"' "$SURFACE_CONTRACT"
grep -Fq 'SORT_PROCESSES = "10_processes"' "$SURFACE_CONTRACT"
grep -Fq '.setCategory(Notification.CATEGORY_STATUS)' "$PROCESS_NOTIF"
! grep -Fq '.setCategory(Notification.CATEGORY_PROGRESS)' "$PROCESS_NOTIF"

echo 'Codex Monitor critical correction source contract PASS'
