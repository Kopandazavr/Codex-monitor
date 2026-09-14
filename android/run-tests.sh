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
      "https://${CAAS_ARTIFACTORY_MAVEN_REGISTRY}/org/json/json/20250517/json-20250517.jar" \
      -o "$JSON_JAR"
  else
    curl -fsSL "https://repo1.maven.org/maven2/org/json/json/20250517/json-20250517.jar" -o "$JSON_JAR"
  fi
fi

OUT="$ROOT/build/tests"
rm -rf "$OUT" && mkdir -p "$OUT"

# Backup/transfer was intentionally removed from the personal-use app. Keep the broad parser suite,
# but filter its now-obsolete transfer-only self-test until the historical test file is compacted.
FILTERED_TEST="$OUT/ParserSelfTest.java"
awk '
  /^[[:space:]]*testSettingsTransfer\(\);/ { next }
  /^[[:space:]]*private static void testSettingsTransfer\(\) throws Exception \{/ { skip=1; next }
  skip && /^[[:space:]]*private static void testWidgetOptions\(\) \{/ { skip=0 }
  !skip { print }
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
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/ReleaseVersion.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/GitHubReleaseSource.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/GitHubRelease.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/GitHubReleaseParser.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/UpdateChannel.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/ReleaseIntegrity.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/ReleaseNotesMarkdown.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/ReleaseUpdatePolicy.java" \
  "$ROOT/app/src/main/java/dev/bennett/codexmeter/UpdateCheckFrequency.java" \
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
grep -q 'VERSION_NAME=.*app/build.gradle.kts' "$ROOT/build.sh"
WORKFLOW="$ROOT/../.github/workflows/build-apk.yml"
grep -Fq 'release-dist/CodexMeter-Wear-$VERSION_NAME.apk' "$WORKFLOW"
grep -Fq '"platforms;android-37.0"' "$WORKFLOW"
grep -q 'Kopandazavr/Codex-Meter/releases?per_page=30' "$ROOT/app/build.gradle.kts" # pragma: allowlist secret
! grep -R -q 'thatjoshguy67/Codex-Meter' "$ROOT/app/src" "$ROOT/app/build.gradle.kts"

# Dashboard reorder + usage-credit / reset-credit auto-hide wiring.
grep -q 'testUsageCreditsAutoHide' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testResetCreditsAutoHide' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testDashboardSectionOrder' "$ROOT/tests/ParserSelfTest.java"
grep -q 'DashboardReorderActivity' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'snapshot.usageCredits.shouldDisplay()' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'shouldShowResetCreditsCard' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'public boolean shouldDisplay()' "$ROOT/app/src/main/java/dev/bennett/codexmeter/ResetCreditsSnapshot.java"
grep -q 'DashboardSections.resolveOrder' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'ItemTouchHelper' "$ROOT/app/src/main/java/dev/bennett/codexmeter/DashboardReorderActivity.java"
grep -q 'ic_oui_reorder' "$ROOT/app/src/main/java/dev/bennett/codexmeter/DashboardReorderActivity.java"

# Edit-dashboard visibility switches + sortable/hideable usage-history section.
grep -q 'USAGE_HISTORY = "usage_history"' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/DashboardSections.java"
grep -q 'SwitchCompat' "$ROOT/app/src/main/java/dev/bennett/codexmeter/DashboardReorderActivity.java"
grep -q 'setSectionVisible' "$ROOT/app/src/main/java/dev/bennett/codexmeter/DashboardReorderActivity.java"
grep -q 'DashboardSections.USAGE_HISTORY.equals(key)' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'setDashboardSectionHidden' "$ROOT/app/src/main/java/dev/bennett/codexmeter/AppPreferences.java"
grep -q 'dashboard_usage_history' "$ROOT/app/src/main/res/xml/preferences_settings_refresh_usage.xml"

# Usage-history analytics and declutter remain covered independently of removed transfer serialization.
grep -q 'testPlanPricing' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testUsageStats' "$ROOT/tests/ParserSelfTest.java"
grep -q 'setScrubEnabled' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageBurnChartView.java"
grep -q 'requestDisallowInterceptTouchEvent' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageBurnChartView.java"
grep -q 'setOnScrubListener' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageHistoryActivity.java"
grep -q 'PlanPricing.forPlan' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageHistoryActivity.java"
grep -q 'UsageStats.windowBreakdown' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageHistoryActivity.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/PlanPricing.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageStats.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/HistorySections.java"
grep -q 'testHistorySections' "$ROOT/tests/ParserSelfTest.java"
grep -q 'MENU_CUSTOMIZE' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageHistoryActivity.java"
grep -q 'HistorySections.GUIDE' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageHistoryActivity.java"
grep -q 'isHistorySectionVisible' "$ROOT/app/src/main/java/dev/bennett/codexmeter/AppPreferences.java"
! grep -q 'Burn trends' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageHistoryActivity.java"
! grep -q 'completed window count' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageHistoryActivity.java"

grep -q 'fiveWindow != null && snapshot.fetchedAtMillis > 0L' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'weeklyWindow != null && snapshot.fetchedAtMillis > 0L' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'fiveWindow != null && snapshot.fetchedAtMillis > 0L' "$ROOT/app/src/main/java/dev/bennett/codexmeter/UsageHistoryActivity.java"
grep -q 'snapshot.fiveHour != null || snapshot.weekly != null' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"

# Free-tier monthly window and long-window fallbacks.
grep -q 'testMonthlyWindow' "$ROOT/tests/ParserSelfTest.java"
grep -q 'MONTHLY = "monthly"' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/DashboardSections.java"
grep -q 'MONTHLY = "monthly"' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageHistory.java"
grep -q 'public UsageWindow longWindow()' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageSnapshot.java"
grep -q 'monthlyWindow != null && snapshot.fetchedAtMillis > 0L' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'DashboardSections.MONTHLY.equals(key)' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'DashboardSections.MONTHLY.equals(key)' "$ROOT/app/src/main/java/dev/bennett/codexmeter/DashboardReorderActivity.java"
grep -q 'usage_history_monthly' "$ROOT/app/src/main/java/dev/bennett/codexmeter/AppPreferences.java"
grep -q 'WINDOW_MONTHLY' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsagePace.java"
grep -q 'longWindowIsMonthly' "$ROOT/app/src/main/java/dev/bennett/codexmeter/NowBarManager.java"
grep -q 'currentLongWindow' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WearGlanceFormat.java"
grep -q 'meterWindow' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java"
grep -q 'Hidden automatically when no resets are available' "$ROOT/app/src/main/java/dev/bennett/codexmeter/DashboardReorderActivity.java"

# Widget meter catalog/config regression guards.
grep -q 'Model-specific additional limits' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java"
grep -q 'available meters exclude model-specific Spark limits' "$ROOT/tests/ParserSelfTest.java"
grep -q 'resolveVisibleForWidget' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java"
grep -q 'resolvedSingleUsageMetric' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java"
grep -q 'ItemTouchHelper' "$ROOT/app/src/main/java/dev/bennett/codexmeter/WidgetConfigActivity.java"
grep -q 'orderedSelectedMeters' "$ROOT/app/src/main/java/dev/bennett/codexmeter/WidgetConfigActivity.java"
grep -q 'ic_oui_reorder' "$ROOT/app/src/main/java/dev/bennett/codexmeter/WidgetConfigActivity.java"

# Dashboard card presentation/order guards.
grep -Fq 'Ui.text(this, "Reset credits", 18' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -Fq 'Ui.text(this, "Usage credits", 18' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'buildIconDetailRow' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'ic_oui_battery' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'ic_oui_credit_card_outline' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
! grep -q 'Ui.separator' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -Fq 'Ui.separator(this, "Available credits")' "$ROOT/app/src/main/java/dev/bennett/codexmeter/ResetCreditActivity.java"
grep -Fq 'Ui.separator(this, "Credit expirations")' "$ROOT/app/src/main/java/dev/bennett/codexmeter/ResetCreditActivity.java"
! grep -q 'ic_reset_credit_details' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'RESET_CREDITS = "reset_credits"' "$ROOT/shared/src/main/java/dev/bennett/codexmeter/DashboardSections.java"
grep -q 'DashboardSections.RESET_CREDITS.equals(key)' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"
grep -q 'DashboardSections.RESET_CREDITS.equals(key)' "$ROOT/app/src/main/java/dev/bennett/codexmeter/DashboardReorderActivity.java"
! grep -q 'this.content.addView(buildResetCreditsCard())' "$ROOT/app/src/main/java/dev/bennett/codexmeter/MainActivity.java"

# Core permissions/routes still required after removing the in-app updater UI.
grep -q 'android.permission.ACCESS_NETWORK_STATE' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'android.permission.POST_NOTIFICATIONS' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'android.permission.SCHEDULE_EXACT_ALARM' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'android:scheme="codexmeter"' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'OnboardingActivity' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'ResetAlertReceiver' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'android.permission.POST_PROMOTED_NOTIFICATIONS' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'NowBarActionReceiver' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'MY_PACKAGE_REPLACED' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'WidgetRepairJobService' "$ROOT/app/src/main/AndroidManifest.xml"

# Pure release parsing/integrity helpers remain regression-tested even though the personal app no
# longer exposes or schedules in-app updates.
grep -q 'testUpdateChannel' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testReleaseChecksums' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testReleaseNotesMarkdown' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testReleaseUpdatePolicy' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testUpdateCheckFrequency' "$ROOT/tests/ParserSelfTest.java"
test -f "$ROOT/app/src/main/java/dev/bennett/codexmeter/UpdateCheckFrequency.java"
grep -Fq 'branches: [main, alpha]' "$WORKFLOW"

echo "Codex Monitor regression/source checks PASS"
