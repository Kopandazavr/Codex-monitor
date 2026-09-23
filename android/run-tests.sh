#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
JSON_JAR="${JSON_JAR:-}"

if [[ -z "$JSON_JAR" ]]; then
  for candidate in /mnt/data/toolcache/json-20250517.jar "$ROOT/build/test-libs/json-20250517.jar"; do
    if [[ -f "$candidate" ]]; then JSON_JAR="$candidate"; break; fi
  done
fi
if [[ -z "$JSON_JAR" ]]; then
  CACHE="$ROOT/build/test-libs"
  mkdir -p "$CACHE"
  JSON_JAR="$CACHE/json-20250517.jar"
  if [[ -n "${CAAS_ARTIFACTORY_MAVEN_REGISTRY:-}" && -n "${CAAS_ARTIFACTORY_READER_USERNAME:-}" ]]; then
    curl -fsSL -u "${CAAS_ARTIFACTORY_READER_USERNAME}:${CAAS_ARTIFACTORY_READER_PASSWORD}" \
      "https://${CAAS_ARTIFACTORY_MAVEN_REGISTRY}/org/json/json/20250517/json-20250517.jar" -o "$JSON_JAR"
  else
    curl -fsSL "https://repo1.maven.org/maven2/org/json/json/20250517/json-20250517.jar" -o "$JSON_JAR"
  fi
fi

OUT="$ROOT/build/tests"
rm -rf "$OUT" && mkdir -p "$OUT"

# The personal build intentionally has no updater or settings-transfer feature. Keep the broad
# historical parser suite by filtering only tests whose production implementation was removed.
FILTERED_TEST="$OUT/ParserSelfTest.java"
awk '
  /^[[:space:]]*testSettingsTransfer\(\);/ { next }
  /^[[:space:]]*testReleaseVersions\(\);/ { next }
  /^[[:space:]]*testGitHubReleases\(\);/ { next }
  /^[[:space:]]*testUpdateChannel\(\);/ { next }
  /^[[:space:]]*testReleaseChecksums\(\);/ { next }
  /^[[:space:]]*testReleaseNotesMarkdown\(\);/ { next }
  /^[[:space:]]*testReleaseUpdatePolicy\(\);/ { next }
  /^[[:space:]]*testUpdateCheckFrequency\(\);/ { next }
  /^[[:space:]]*private static void testSettingsTransfer\(\) throws Exception \{/ { transfer=1; next }
  transfer && /^[[:space:]]*private static void testWidgetOptions\(\) \{/ { transfer=0 }
  /^[[:space:]]*private static void testReleaseVersions\(\) \{/ { updater=1; next }
  updater && /^[[:space:]]*private static String jwt\(String payload\) \{/ { updater=0 }
  !transfer && !updater { print }
' "$ROOT/tests/ParserSelfTest.java" > "$FILTERED_TEST"

javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageWindow.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageCredits.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageLimit.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/DashboardSections.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/HistorySections.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageSnapshot.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageSample.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageHistory.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsagePace.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/PlanPricing.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageStats.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/AdaptiveRefreshPolicy.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarAutoStart.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarDisplayMode.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarPercentMode.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarCopy.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/wear/WearSyncPaths.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/wear/WearSyncStatus.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/wear/WearSettingsState.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/wear/WearUsageState.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/wear/WearMonitorState.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/wear/WearSurfaceMode.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WearGlanceFormat.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageParser.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/CelebrationDetector.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/RateLimitResetCredit.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/ResetCreditsSnapshot.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/ResetCreditExpiryReminder.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/Pkce.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/JwtClaims.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/WidgetOptions.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/OnboardingFlow.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/OAuthBrowserPage.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/DiagnosticSanitizer.java" \
  "$FILTERED_TEST"
java -ea -cp "$OUT:$JSON_JAR" dev.bennett.codexmeter.ParserSelfTest

APP_VERSION_NAME="$(awk -F'"' '/versionName = "/ { print $2; exit }' "$ROOT/app/build.gradle.kts")"
APP_VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")"
[[ -n "$APP_VERSION_NAME" && -n "$APP_VERSION_CODE" ]]
grep -q "VERSION_NAME = \"$APP_VERSION_NAME\"" "$ROOT/app/src/main/java/dev/bennett/codexmeter/AppConstants.java"
grep -q "VERSION_CODE = $APP_VERSION_CODE" "$ROOT/app/src/main/java/dev/bennett/codexmeter/AppConstants.java"
grep -q "versionName = \"$APP_VERSION_NAME\"" "$ROOT/wear/build.gradle.kts"
grep -q "versionCode = $APP_VERSION_CODE" "$ROOT/wear/build.gradle.kts"
grep -q 'return ORIGINATOR + "/" + VERSION_NAME' "$ROOT/app/src/main/java/dev/bennett/codexmeter/AppConstants.java"

MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
SRC="$ROOT/app/src/main/java/dev/bennett/codexmeter"
RES="$ROOT/app/src/main/res/xml"
WORKFLOW="$ROOT/../.github/workflows/build-apk.yml"

# Core dashboard/history behavior still has explicit source guards.
grep -q 'DashboardReorderActivity' "$MANIFEST"
grep -q 'DashboardSections.resolveOrder' "$SRC/MainActivity.java"
grep -q 'snapshot.usageCredits.shouldDisplay()' "$SRC/MainActivity.java"
grep -q 'shouldShowResetCreditsCard' "$SRC/MainActivity.java"
grep -q 'ItemTouchHelper' "$SRC/DashboardReorderActivity.java"
grep -q 'USAGE_HISTORY = "usage_history"' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/DashboardSections.java"
grep -q 'setScrubEnabled' "$SRC/UsageBurnChartView.java"
grep -q 'UsageStats.windowBreakdown' "$SRC/UsageHistoryActivity.java"
grep -q 'PlanPricing.forPlan' "$SRC/UsageHistoryActivity.java"
grep -q 'MONTHLY = "monthly"' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageHistory.java"
grep -q 'WINDOW_MONTHLY' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsagePace.java"

# Diagnostics is first-class, bounded, sanitized, and always on.
grep -Fq 'android:key="settings_diagnostics"' "$RES/preferences_settings.xml"
grep -Fq 'android:title="Diagnostics"' "$RES/preferences_settings.xml"
! grep -Fq 'diagnostic_logging_enabled' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'Always on' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'always_on_capture_started' "$SRC/DiagnosticLog.java"
grep -Fq 'MAX_ARCHIVES = 2' "$SRC/DiagnosticLog.java"
grep -Fq 'MAX_FILE_BYTES = 1024L * 1024L' "$SRC/DiagnosticLog.java"
grep -Fq 'DiagnosticSanitizer.redact' "$SRC/DiagnosticLog.java"

# Personal-use cleanup is physical, not merely hidden.
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
! test -e "$RES/preferences_settings_updates.xml"
! test -e "$RES/preferences_settings_transfer.xml"
! test -e "$RES/preferences_settings_privacy.xml"
! grep -Fq 'REQUEST_INSTALL_PACKAGES' "$MANIFEST"
! grep -Fq 'UpdateActivity' "$MANIFEST"
! grep -Fq 'ReleaseHistoryActivity' "$MANIFEST"
! grep -Fq 'ReleaseUpdateJobService' "$MANIFEST"
! grep -Fq 'UPDATE_API_URL' "$ROOT/app/build.gradle.kts"

# Navigation never hard-codes the obsolete pre-migration applicationId in Settings resources.
! grep -R -F 'android:targetPackage="dev.bennett.codexmeter"' "$RES/preferences_settings"*.xml
! grep -R -F 'android:data="package:dev.bennett.codexmeter"' "$RES/preferences_settings"*.xml
! grep -Fq 'DashboardReorderActivity.class' "$SRC/SettingsActivity.java"
grep -Fq 'Ui.startSecondaryActivity(requireActivity(), CalendarPermissionActivity.class);' "$SRC/SettingsActivity.java"
grep -Fq 'Uri.parse("package:" + requireContext().getPackageName())' "$SRC/SettingsActivity.java"

# Core permissions/routes and CI release branch support remain.
grep -q 'android.permission.ACCESS_NETWORK_STATE' "$MANIFEST"
grep -q 'android.permission.POST_NOTIFICATIONS' "$MANIFEST"
grep -q 'android.permission.SCHEDULE_EXACT_ALARM' "$MANIFEST"
grep -q 'android:scheme="codexmeter"' "$MANIFEST"
grep -q 'NowBarActionReceiver' "$MANIFEST"
grep -q 'WidgetRepairJobService' "$MANIFEST"
grep -Fq 'branches: [main, alpha]' "$WORKFLOW"

echo "Codex Monitor regression/source checks PASS"
