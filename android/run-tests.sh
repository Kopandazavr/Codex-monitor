#!/usr/bin/env bash
set -euo pipefail
trap 'echo "run-tests failed at line $LINENO" >&2' ERR
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
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsageWindow.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsageCredits.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsageLimit.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/DashboardSections.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/HistorySections.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/WidgetMeters.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsageSnapshot.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsageSample.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsageHistory.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsagePace.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/PlanPricing.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsageStats.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/AdaptiveRefreshPolicy.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/NowBarAutoStart.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/NowBarDisplayMode.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/NowBarPercentMode.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/NowBarCopy.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/ProjectProfileRules.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/ProjectProfileEditState.java" \
  "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/RoleProfileEditState.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/CalendarProcess.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/WatchdogCanonicalizer.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/UsageParser.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/CelebrationDetector.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/RateLimitResetCredit.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/ResetCreditsSnapshot.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/ResetCreditExpiryReminder.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/Pkce.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/JwtClaims.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/WidgetOptions.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/OnboardingFlow.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/OAuthBrowserPage.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/DiagnosticSanitizer.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/RoleSessionHistory.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/UsageHistoryViewport.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/UsageHistoryTickGrid.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/UsageHistoryStripeCadence.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/CompletionAudioRoute.java" \
  "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/IdleProcessRestorePolicy.java" \
  "$FILTERED_TEST" \
  "$ROOT/tests/ProjectProfileRulesSelfTest.java" \
  "$ROOT/tests/CalendarProcessSelfTest.java" \
  "$ROOT/tests/NowBarCopySelfTest.java" \
  "$ROOT/tests/WatchdogCanonicalizerSelfTest.java" \
  "$ROOT/tests/RoleProfileEditStateSelfTest.java" \
  "$ROOT/tests/RoleSessionHistorySelfTest.java" \
  "$ROOT/tests/UsageHistoryViewportSelfTest.java" \
  "$ROOT/tests/UsageHistoryTickGridSelfTest.java" \
  "$ROOT/tests/UsageHistoryStripeCadenceSelfTest.java" \
  "$ROOT/tests/CompletionAudioRouteSelfTest.java" \
  "$ROOT/tests/IdleProcessRestorePolicySelfTest.java"
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.ParserSelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.ProjectProfileRulesSelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.CalendarProcessSelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.NowBarCopySelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.WatchdogCanonicalizerSelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.RoleProfileEditStateSelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.RoleSessionHistorySelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.UsageHistoryViewportSelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.UsageHistoryTickGridSelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.UsageHistoryStripeCadenceSelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.CompletionAudioRouteSelfTest
java -ea -cp "$OUT:$JSON_JAR" dev.kopandazavr.codexmonitor.IdleProcessRestorePolicySelfTest

APP_VERSION_NAME="$(awk -F'"' '/versionName = "/ { print $2; exit }' "$ROOT/app/build.gradle.kts")"
APP_VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")"
[[ -n "$APP_VERSION_NAME" && -n "$APP_VERSION_CODE" ]]
grep -q "VERSION_NAME = \"$APP_VERSION_NAME\"" "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/AppConstants.java"
grep -q "VERSION_CODE = $APP_VERSION_CODE" "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/AppConstants.java"
grep -q 'return ORIGINATOR + "/" + VERSION_NAME' "$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor/AppConstants.java"

MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
RES="$ROOT/app/src/main/res/xml"
WORKFLOW="$ROOT/../.github/workflows/build-apk.yml"

# Core dashboard/history behavior still has explicit source guards.
grep -q 'DashboardReorderActivity' "$MANIFEST"
grep -q 'DashboardSections.resolveOrder' "$SRC/MainActivity.java"
grep -q 'snapshot.usageCredits.shouldDisplay()' "$SRC/MainActivity.java"
grep -q 'shouldShowResetCreditsCard' "$SRC/MainActivity.java"
grep -q 'ItemTouchHelper' "$SRC/DashboardReorderActivity.java"
grep -q 'USAGE_HISTORY = "usage_history"' "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/DashboardSections.java"
! grep -q 'setScrubEnabled' "$SRC/UsageBurnChartView.java"
! grep -q 'OnScrubListener' "$SRC/UsageBurnChartView.java"
! grep -q 'drawScrub' "$SRC/UsageBurnChartView.java"
grep -q 'FIVE_HOUR_ZOOM_MS = TimeUnit.HOURS.toMillis(1)' "$SRC/UsageBurnChartView.java"
grep -q 'WEEKLY_ZOOM_MS = TimeUnit.DAYS.toMillis(1)' "$SRC/UsageBurnChartView.java"
grep -q 'FIVE_HOUR_ZOOM_TICK_MS = TimeUnit.MINUTES.toMillis(10)' "$SRC/UsageBurnChartView.java"
grep -q 'UsageHistoryTickGrid.firstZoomTick' "$SRC/UsageBurnChartView.java"
grep -q 'UsageHistoryViewport.zoomSpan' "$SRC/UsageBurnChartView.java"
grep -q 'UsageHistoryViewport.restoreSpan' "$SRC/UsageBurnChartView.java"
grep -q 'UsageHistoryViewport.canPan' "$SRC/UsageBurnChartView.java"
grep -q 'defaultExpandedDay' "$SRC/RoleSettingsDialog.java"
grep -q 'expandedDays' "$SRC/RoleSettingsDialog.java"
grep -q 'row.sessionCount' "$SRC/RoleSettingsDialog.java"
grep -q 'row.totalDurationMillis' "$SRC/RoleSettingsDialog.java"
grep -q 'lockDialogHeightAfterFirstLayout' "$SRC/RoleSettingsDialog.java"
grep -q 'params.height=measuredHeight' "$SRC/RoleSettingsDialog.java"
[[ "$(grep -Fc 'body.setOnClickListener(roleSettingsAction)' "$SRC/MainActivity.java" || true)" == "2" ]]
[[ "$(grep -Fc 'copy.addView(projectBadge, new LinearLayout.LayoutParams(-2, -2))' "$SRC/MainActivity.java" || true)" == "2" ]]
grep -q 'role.setOnClickListener(roleSettingsAction)' "$SRC/MainActivity.java"
grep -q 'state.setOnClickListener(roleSettingsAction)' "$SRC/MainActivity.java"
grep -q 'topic.setOnClickListener(roleSettingsAction)' "$SRC/MainActivity.java"
grep -q 'UsageHistoryTickGrid.nextZoomTick' "$SRC/UsageBurnChartView.java"
grep -q 'reset > 0L ? reset : System.currentTimeMillis()' "$SRC/UsageBurnChartView.java"
grep -q 'WEEKLY_ORANGE = 0xFFFF9800' "$SRC/UsageBurnChartView.java"
grep -q 'return 100d - used;' "$SRC/UsageBurnChartView.java"
grep -q 'drawStripedFill' "$SRC/UsageBurnChartView.java"
grep -q 'UsageHistoryStripeCadence.spacingPx' "$SRC/UsageBurnChartView.java"
grep -q 'ZOOMED_SPACING_MULTIPLIER = 3f' "$SRC/UsageHistoryStripeCadence.java"
grep -q 'WEEKLY_ORANGE = 0xFFFF9800' "$SRC/UsageWaveView.java"
grep -q 'currentWindowSamples()' "$SRC/UsageBurnChartView.java"
grep -q 'resetLabel(System.currentTimeMillis())' "$SRC/UsageBurnChartView.java"
! grep -Fq 'samples.size() + " samples"' "$SRC/UsageBurnChartView.java"
! grep -q 'recentWindows' "$SRC/UsageBurnChartView.java"
! grep -q 'projectionDash' "$SRC/UsageBurnChartView.java"
! test -e "$SRC/UsageHistoryActivity.java"
! grep -q 'UsageHistoryActivity' "$MANIFEST"
! grep -Fq '"View history"' "$SRC/MainActivity.java"
grep -q 'addInteractiveHistoryChart' "$SRC/MainActivity.java"
grep -q 'chart.setZoomEnabled(true)' "$SRC/MainActivity.java"
! grep -Fq 'setContentDescription("Zoom Out")' "$SRC/MainActivity.java"
! grep -Fq 'chart.zoomOut()' "$SRC/MainActivity.java"
grep -q 'zoomOut();' "$SRC/UsageBurnChartView.java"
grep -q 'historyZoomViewports' "$SRC/MainActivity.java"
grep -q 'restoreZoomViewport' "$SRC/MainActivity.java"
grep -q 'TAP_TOGGLE_GUARD_MS = 250L' "$SRC/UsageBurnChartView.java"
grep -q 'requestDisallowInterceptTouchEvent(true)' "$SRC/UsageBurnChartView.java"
grep -q 'requestDisallowInterceptTouchEvent(false)' "$SRC/UsageBurnChartView.java"
grep -q '"chart_tap_duplicate_suppressed"' "$SRC/UsageBurnChartView.java"
grep -q '"chart_tap_outside_domain"' "$SRC/UsageBurnChartView.java"
grep -q '"chart_tap_toggle"' "$SRC/UsageBurnChartView.java"
grep -q 'zoomToMeasuredEndpoint' "$SRC/UsageBurnChartView.java"
grep -q '"chart_tap_endpoint_snap"' "$SRC/UsageBurnChartView.java"
grep -q 'MONTHLY = "monthly"' "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsageHistory.java"
grep -q 'WINDOW_MONTHLY' "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/UsagePace.java"

# 2.14 bounded phone follow-ups and notification-stability contracts.
grep -q 'isMeasuredInteractionX' "$SRC/UsageBurnChartView.java"
grep -q 'measuredEndMillis()' "$SRC/UsageBurnChartView.java"
grep -q 'measuredStartMillis()' "$SRC/UsageBurnChartView.java"
grep -q 'start = Math.max(measuredStart, Math.min(start, maxStart))' "$SRC/UsageBurnChartView.java"
grep -q 'if (zoomed)' "$SRC/UsageBurnChartView.java"
grep -q 'repostForProcessChange(Context context)' "$SRC/DualUsageNotificationManager.java"
grep -q 'repostForProcessChange(' "$SRC/NowBarActionReceiver.java"
grep -q 'app, containerId' "$SRC/NowBarActionReceiver.java"
grep -q 'setGroup(NotificationSurfaceContract.groupKey(containerId))' "$SRC/NowBarManager.java"
grep -q 'setSortKey(NotificationSurfaceContract.SORT_USAGE)' "$SRC/NowBarManager.java"
grep -q 'SORT_USAGE = "00_usage"' "$SRC/NotificationSurfaceContract.java"
grep -q 'SORT_PROCESSES = "10_processes"' "$SRC/NotificationSurfaceContract.java"
! grep -q 'M54,20 A34,34' "$ROOT/app/src/main/res/drawable/ic_notification_codex_monitor.xml"
! grep -q 'M54,32 A22,22' "$ROOT/app/src/main/res/drawable/ic_notification_codex_monitor.xml"
grep -q 'M59.5,31 A24,24' "$ROOT/app/src/main/res/drawable/ic_notification_codex_monitor.xml"
grep -q 'M74.5,22.5 A37.5,37.5' "$ROOT/app/src/main/res/drawable/ic_notification_codex_monitor.xml"
grep -q 'M54,7 A47,47' "$ROOT/app/src/main/res/drawable/ic_notification_codex_monitor.xml"
grep -q 'new DiagonalStripeDrawable(' "$SRC/IdleReminderOverlayService.java"
grep -q '0xE61B1B1F, 0xE6222226' "$SRC/IdleReminderOverlayService.java"
grep -q 'canvas.rotate(-45.0f' "$SRC/IdleReminderOverlayService.java"
grep -q 'stripeWidth \* 2.0f' "$SRC/IdleReminderOverlayService.java"
# 2.36: no global app/session labels above completion cards.
! grep -Fq 'TextView header = text("Codex Monitor"' "$SRC/IdleReminderOverlayService.java"
! grep -Fq 'TextView title = text("Session finished"' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'entries = new LinearLayout(this);' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'SpannableStringBuilder projectIdentity = new SpannableStringBuilder();' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'ProjectProfileStore.effectiveShort(' "$SRC/IdleReminderOverlayService.java"
! grep -Fq 'ProjectProfileStore.badgeText(' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'new ForegroundColorSpan(ProjectProfileStore.accentColor(projectProfile))' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'TextView roleView = text(role, 16f, Color.WHITE);' "$SRC/IdleReminderOverlayService.java"
! grep -Fq 'TextView project = text(idle.project, 14f' "$SRC/IdleReminderOverlayService.java"
! grep -Fq 'TextView roleView = text(role, 20f' "$SRC/IdleReminderOverlayService.java"
# 2.38 parallel same-role watchdog instances.
grep -Fq 'metadata.get("instance_id")' "$SRC/CalendarProcess.java"
grep -Fq 'instanceIdentity()' "$SRC/CalendarProcess.java"
grep -Fq 'instance:' "$SRC/WatchdogCanonicalizer.java"
grep -Fq 'WatchdogInstanceState.load(context, containerId)' "$SRC/IdleProcessState.java"
grep -Fq 'recentCompletions(Context context' "$SRC/IdleProcessState.java"
grep -Fq 'findCompletion(Context context' "$SRC/IdleProcessState.java"
grep -Fq 'ProcessRoleGroup.group(this, active)' "$SRC/MainActivity.java"
grep -Fq 'toggleProcessReminder(group.roleKey)' "$SRC/MainActivity.java"
grep -Fq 'ProcessRoleGroup.group(context, processes)' "$SRC/ProcessNotificationManager.java"
grep -Fq 'notification_process_instance_bars' "$SRC/ProcessNotificationManager.java"
grep -Fq 'idle.completionIdentity()' "$SRC/ProcessNotificationManager.java"
grep -Fq 'EXTRA_INSTANCE_ID' "$SRC/IdleReminderManager.java"
grep -Fq 'findCompletion(' "$SRC/IdleReminderManager.java"
grep -Fq 'roles.put(entryKey, idle)' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'roleAccounts.put(entryKey, containerId)' "$SRC/IdleReminderOverlayService.java"
grep -Fq '@+id/notification_process_instance_bars' "$ROOT/app/src/main/res/layout/notification_process_group_row.xml"
grep -Fq '@+id/notification_process_instance_progress' "$ROOT/app/src/main/res/layout/notification_process_instance_bar.xml"
grep -q 'COMPLETION_OVERLAY_ALARM_DELAY_MS = 1L' "$SRC/IdleReminderManager.java"
grep -q 'if (!overlayScheduled)' "$SRC/IdleReminderManager.java"
grep -q 'IdleReminderOverlayService.showCompletion(' "$SRC/IdleReminderManager.java"
grep -q 'context, containerId, idle' "$SRC/IdleReminderManager.java"
grep -q 'COMPLETION_NOTIFICATION_AFTER_OVERLAY_MS = 500L' "$SRC/IdleReminderOverlayService.java"
grep -q 'completion_notification_after_overlay_draw_gap' "$SRC/IdleReminderOverlayService.java"
grep -q 'KEY_COMPLETION_POSTED_PREFIX' "$SRC/ProcessNotificationManager.java"
grep -q 'KEY_COMPLETION_RECONCILED_VERSION' "$SRC/ProcessNotificationManager.java"
grep -q 'getActiveNotifications' "$SRC/ProcessNotificationManager.java"
grep -q 'clearCompletionAlerts' "$SRC/IdleReminderOverlayService.java"
grep -q 'completion_notification_skipped_after_overlay_dismiss' "$SRC/IdleReminderOverlayService.java"
grep -q 'PROCESS_COMPLETION_CHANNEL_ID' "$SRC/AlertSoundManager.java"
grep -q 'PROCESS_COMPLETION_SPEAKER_CHANNEL_ID' "$SRC/AlertSoundManager.java"
grep -q 'ensureSpeakerCompletionDelivery' "$SRC/AlertSoundManager.java"
grep -q 'completionNotificationChannelId' "$SRC/ProcessNotificationManager.java"
grep -q 'CompletionAudioRoute.useSilentNotificationDelivery' "$SRC/ProcessNotificationManager.java"
grep -q 'CompletionAudioRoute.playDirectSound' "$SRC/ProcessNotificationManager.java"
[[ "$(grep -Fc 'AlertSoundManager.playProcessCompletion(context)' "$SRC/ProcessNotificationManager.java" || true)" == "1" ]]
grep -q '"speaker_sound_played"' "$SRC/ProcessNotificationManager.java"
! grep -q 'AlertSoundManager.playProcessCompletion(context)' "$SRC/IdleReminderManager.java"
grep -q 'installStaticLayout()' "$SRC/OnboardingActivity.java"
grep -q 'doneButton' "$SRC/OnboardingActivity.java"
! grep -q 'Ui.installPage(this, "Quick setup"' "$SRC/OnboardingActivity.java"
! grep -q 'NestedScrollView' "$SRC/OnboardingActivity.java"
grep -q 'shouldRetryTransient' "$SRC/GoogleCalendarAuthorization.java"
grep -q 'invalidateCachedToken' "$SRC/GoogleCalendarAuthorization.java"
grep -q 'exception.status == 401 && !retriedUnauthorized' "$SRC/GoogleCalendarProcessSource.java"
grep -q 'calendar_http_401_retrying_with_fresh_token' "$SRC/GoogleCalendarProcessSource.java"
grep -q '"authorization_retry_scheduled"' "$SRC/GoogleCalendarAuthorizationActivity.java"
grep -q 'eventExists(' "$SRC/IdleProcessState.java"
grep -q 'IdleProcessState.history(activity,profile.id)' "$SRC/RoleSettingsDialog.java"
grep -q 'new ListView(activity)' "$SRC/RoleSettingsDialog.java"
grep -q 'RoleSessionHistory.buildRows' "$SRC/RoleSettingsDialog.java"
! grep -q 'maybeLoadMoreHistory' "$SRC/RoleSettingsDialog.java"
! grep -Fq 'Scroll for 10 more' "$SRC/RoleSettingsDialog.java"
! grep -q 'history.clear()' "$SRC/IdleProcessState.java"
grep -q '"direct_cache_not_fresh"' "$SRC/CalendarProcessReader.java"

# Permissions & connections supersedes the old Restart onboarding/account-card setup.
grep -Fq 'android:key="permissions_connections"' "$RES/preferences_settings.xml"
! grep -Fq 'account_card' "$RES/preferences_settings.xml"
! grep -Fq 'restart_onboarding' "$RES/preferences_settings_diagnostics.xml"
! grep -Fq 'EXTRA_RESTART_ONBOARDING' "$SRC/OnboardingActivity.java"
grep -Fq 'EXTRA_PERMISSIONS_CONNECTIONS = "permissions_connections"' "$SRC/OnboardingActivity.java"
grep -Fq 'SetupReadiness.overallSummary' "$SRC/SettingsActivity.java"
grep -Fq '"Required"' "$SRC/OnboardingActivity.java"
grep -Fq '"Optional"' "$SRC/OnboardingActivity.java"
grep -Fq '"Alarms & reminders"' "$SRC/OnboardingActivity.java"
grep -Fq '"Local Calendar fallback"' "$SRC/OnboardingActivity.java"
grep -Fq '"Recommended"' "$SRC/OnboardingActivity.java"
grep -Fq '"Battery usage"' "$SRC/OnboardingActivity.java"
! grep -Fq '"Samsung background limits"' "$SRC/OnboardingActivity.java"
! grep -Fq 'Ui.actionRow(this, "Live monitor"' "$SRC/OnboardingActivity.java"
grep -Fq 'missingRequiredCount' "$SRC/MainActivity.java"
grep -Fq 'ic_permissions_checklist' "$SRC/MainActivity.java"
grep -Fq 'STATUS_RECOMMENDED_MISSING' "$SRC/SetupReadiness.java"
grep -Fq 'RECOMMENDED_TOTAL = 1' "$SRC/SetupReadiness.java"
grep -Fq 'return REQUIRED_TOTAL + RECOMMENDED_TOTAL' "$SRC/SetupReadiness.java"
grep -Fq 'SetupReadiness.overallSummary(this)' "$SRC/OnboardingActivity.java"
grep -Fq 'ColorStateList.valueOf(0xFFFFFFFF)' "$SRC/MainActivity.java"
grep -Fq 'Gravity.BOTTOM | Gravity.END' "$SRC/MainActivity.java"
grep -q 'CommonStatusCodes.DEVELOPER_ERROR' "$SRC/GoogleCalendarAuthorization.java"
grep -q '"authorization_activity_result"' "$SRC/GoogleCalendarAuthorization.java"
grep -q '"status_code", info.statusCode' "$SRC/GoogleCalendarAuthorization.java"
grep -q '"recoverable", info.recoverable' "$SRC/GoogleCalendarAuthorization.java"
grep -q 'metadata.get("project_short")' "$SRC/CalendarProcess.java"
grep -Fq 'missing_or_invalid_project' "$SRC/CalendarProcess.java"
grep -Fq 'missing_or_invalid_role' "$SRC/CalendarProcess.java"
grep -Fq '"watchdog_rejected_metadata"' "$SRC/CalendarProcessReader.java"
grep -Fq '"watchdog_rejected_metadata"' "$SRC/GoogleCalendarProcessSource.java"
! grep -Fq 'return "project:" + project' "$SRC/IdleProcessState.java"
grep -q 'compactProject + " — " + cleanRole' "$SRC/CalendarProcess.java"
test -f "$ROOT/app/src/main/res/drawable/ic_notification_codex_monitor.xml"
grep -q 'setSmallIcon(R.drawable.ic_notification_codex_monitor)' "$SRC/DualUsageNotificationManager.java"
grep -q 'setSmallIcon(R.drawable.ic_notification_codex_monitor)' "$SRC/ProcessNotificationManager.java"
grep -q 'setSmallIcon(R.drawable.ic_notification_codex_monitor)' "$SRC/ResetNotificationManager.java"
grep -q 'setSmallIcon(R.drawable.ic_notification_codex_monitor)' "$SRC/NowBarManager.java"
grep -q 'setSmallIcon(R.drawable.ic_notification_codex_monitor)' "$SRC/IdleReminderOverlayService.java"

# Diagnostics is first-class, bounded, sanitized, and always on.
grep -Fq 'android:key="settings_diagnostics"' "$RES/preferences_settings.xml"
! grep -Fq 'android:key="settings_notifications"' "$RES/preferences_settings.xml"
! grep -Fq 'android:key="now_bar_monitor_ui"' "$RES/preferences_settings_now_bar.xml"
! grep -Fq 'android:key="now_bar_display_mode_ui"' "$RES/preferences_settings_now_bar.xml"
! grep -Fq 'android:key="now_bar_percent_mode_ui"' "$RES/preferences_settings_now_bar.xml"
! grep -Fq 'android:key="now_bar_auto_start_ui"' "$RES/preferences_settings_now_bar.xml"
grep -Fq 'android:key="notification_test"' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'android:title="Diagnostics"' "$RES/preferences_settings.xml"
! grep -Fq 'diagnostic_logging_enabled' "$RES/preferences_settings_diagnostics.xml"
! grep -Fq 'diagnostic_build_identity' "$RES/preferences_settings_diagnostics.xml"
! grep -Fq 'diagnostic_log_status' "$RES/preferences_settings_diagnostics.xml"
grep -Fq 'export.setSummary(DiagnosticLog.formatBytes(stats.bytes));' "$SRC/SettingsActivity.java"
! grep -Fq 'Sanitization' "$RES/preferences_settings_diagnostics.xml"
! grep -Fq 'Sensitive data protection' "$RES/preferences_settings_diagnostics.xml"
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
! grep -R -F 'android:targetPackage="dev.kopandazavr.codexmonitor"' "$RES/preferences_settings"*.xml
! grep -R -F 'android:data="package:dev.kopandazavr.codexmonitor"' "$RES/preferences_settings"*.xml
! grep -Fq 'DashboardReorderActivity.class' "$SRC/SettingsActivity.java"
grep -Fq 'Ui.startSecondaryActivity(this, CalendarPermissionActivity.class);' "$SRC/OnboardingActivity.java"
grep -Fq 'Uri.parse("package:" + getPackageName())' "$SRC/OnboardingActivity.java"

# Core permissions/routes and CI release branch support remain.
grep -q 'android.permission.ACCESS_NETWORK_STATE' "$MANIFEST"
grep -q 'android.permission.POST_NOTIFICATIONS' "$MANIFEST"
grep -q 'android.permission.SCHEDULE_EXACT_ALARM' "$MANIFEST"
grep -q 'android:scheme="codexmonitor"' "$MANIFEST"
grep -q 'NowBarActionReceiver' "$MANIFEST"
grep -q 'POLL_INTERVAL_MS = TimeUnit.SECONDS.toMillis(10)' "$SRC/ProcessNotificationScheduler.java"
grep -q 'registerDefaultNetworkCallback' "$SRC/ProcessNotificationScheduler.java"
grep -q 'ensureAlwaysOn' "$SRC/NowBarManager.java"
! grep -Fq '"Stop", stopIntent' "$SRC/NowBarManager.java"
! grep -Fq '"Stop", stopIntent' "$SRC/DualUsageNotificationManager.java"
! grep -Fq 'setProgress(100, systemProgress, false)' "$SRC/DualUsageNotificationManager.java"
! grep -Fq 'systemProgressPercent' "$SRC/DualUsageNotificationManager.java"
grep -q 'weeklyResetProgress' "$SRC/NowBarManager.java"
grep -q 'ResetProgress.RESET_LIME' "$SRC/NowBarManager.java"
grep -q 'WidgetRepairJobService' "$MANIFEST"
grep -Fq 'branches: [main, alpha]' "$WORKFLOW"

# 2.24 project-profile and reset-progress contracts.
test -f "$SRC/ProjectProfileStore.java"
test -f "$SRC/ProjectBadgeView.java"
test -f "$SRC/ProjectSettingsDialog.java"
grep -Fq '"Data Matrix"' "$SRC/ProjectProfileStore.java"
grep -Fq '"DM", "terminal", COLOR_GREEN' "$SRC/ProjectProfileStore.java"
grep -Fq '"Codex Monitor"' "$SRC/ProjectProfileStore.java"
grep -Fq '"Заказы сигарет"' "$SRC/ProjectProfileStore.java"
grep -Fq '"Mira Technical"' "$SRC/ProjectProfileStore.java"
grep -Fq '"Написание книг про ии будущего"' "$SRC/ProjectProfileStore.java"
grep -Fq '"Mira Universe"' "$SRC/ProjectProfileStore.java"
grep -q 'ProjectProfileStore.resolve' "$SRC/MainActivity.java"
grep -q 'ProjectSettingsDialog.show' "$SRC/MainActivity.java"
grep -q 'ResetProgress.elapsedPercent' "$SRC/MainActivity.java"
grep -q 'ResetProgress.elapsedPercent' "$SRC/DualUsageNotificationManager.java"
# 2.39 role identity bootstrap + compact persistent reset copy.
grep -Fq 'metadata.get("role_icon")' "$SRC/CalendarProcess.java"
grep -Fq 'json.put("role_icon", roleIcon)' "$SRC/RoleProfileStore.java"
grep -Fq 'resolve(Context context, String incomingRole, String incomingIcon)' "$SRC/RoleProfileStore.java"
grep -Fq 'displayLabelById' "$SRC/RoleProfileStore.java"
grep -Fq 'process.roleIcon' "$SRC/MainActivity.java"
grep -Fq 'process.roleIcon' "$SRC/ProcessNotificationManager.java"
grep -Fq 'displayLabelById(this, idle.key, idle.role)' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'metadata.append(" role_icon=")' "$SRC/GoogleCalendarProcessSource.java"
grep -Fq 'compactDurationWithMinutes' "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/NowBarCopy.java"
grep -Fq '.append("% · Reset in ")' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'int remainingPercent = window.remainingPercent();' "$SRC/DualUsageNotificationManager.java"
# 2.40 notification identity polish.
grep -Fq 'notification_reset_alert_five' "$SRC/ResetNotificationManager.java"
grep -Fq 'notification_reset_alert_long' "$SRC/ResetNotificationManager.java"
grep -Fq 'realertResetSurface' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'allowance_five_hour' "$ROOT/app/src/main/res/values/colors.xml"
grep -Fq 'allowance_long_window' "$ROOT/app/src/main/res/values/colors.xml"
grep -Fq 'allowance_alert_foreground' "$ROOT/app/src/main/res/values/colors.xml"
test -f "$ROOT/app/src/main/res/layout/notification_reset_alert_five.xml"
test -f "$ROOT/app/src/main/res/layout/notification_reset_alert_long.xml"
grep -Fq 'text.append(" · ").append(resetTime)' "$SRC/DualUsageNotificationManager.java"
! grep -Fq 'notificationLimitText("5-hour"' "$SRC/DualUsageNotificationManager.java"
! grep -Fq 'notificationLimitText(longLabel' "$SRC/DualUsageNotificationManager.java"
! grep -Fq '" · reset " + fiveResetTime' "$SRC/DualUsageNotificationManager.java"
! grep -Fq '" · reset " + longResetTime' "$SRC/DualUsageNotificationManager.java"
! grep -R -q 'ResetProgress.timeRemainingPercent' "$SRC"
grep -q 'RESET_LIME = 0xFFB7F34A' "$SRC/ResetProgress.java"
grep -q 'acceleratedWarning && percent > 0' "$SRC/UsageWaveView.java"
test -f "$ROOT/app/src/main/assets/licenses/OpenAI-Apps-SDK-UI-LICENSE.txt"

bash "$ROOT/tests/wear-retirement-source-test.sh"
echo "Codex Monitor regression/source checks PASS"
