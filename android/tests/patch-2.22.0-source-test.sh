#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
RES="$ROOT/app/src/main/res"


grep -Fq 'buildProcessesCard()' "$SRC/MainActivity.java"
grep -Fq 'boolean processesAdded = false;' "$SRC/MainActivity.java"
grep -Fq 'addDashboardCard(column, buildUsageHistoryCard());' "$SRC/MainActivity.java"
grep -Fq 'addDashboardCard(column, buildProcessesCard());' "$SRC/MainActivity.java"
grep -Fq 'No detected agents or processes yet' "$SRC/MainActivity.java"
grep -Fq 'process.topic' "$SRC/MainActivity.java"
grep -Fq 'idle.topic' "$SRC/MainActivity.java"
grep -Fq 'process.elapsedPercent(nowMillis)' "$SRC/MainActivity.java"
grep -Fq 'IdleProcessState.toggleReminder' "$SRC/MainActivity.java"
grep -Fq 'IdleReminderManager.onReminderToggled' "$SRC/MainActivity.java"
grep -Fq 'IdleProcessState.dismiss' "$SRC/MainActivity.java"
! grep -Fq 'PROCESSES =' "$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor/DashboardSections.java"

grep -Fq 'ACTION_PROCESS_UPDATED' "$SRC/AppConstants.java"
grep -Fq 'ACTION_PROCESS_UPDATED' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'ACTION_PROCESS_UPDATED' "$SRC/MainActivity.java"
grep -Fq 'POLL_INTERVAL_MS = TimeUnit.SECONDS.toMillis(10)' "$SRC/ProcessNotificationScheduler.java"
! grep -Fq 'UsageApi.' "$SRC/ProcessNotificationScheduler.java"

grep -Fq 'setStatusTokenColor(summary, summaryText, "Connected", statusGreen())' "$SRC/OnboardingActivity.java"

DIAG="$RES/xml/preferences_settings_diagnostics.xml"
! grep -Fq 'diagnostic_build_identity' "$DIAG"
! grep -Fq 'diagnostic_log_status' "$DIAG"
grep -Fq 'android:key="diagnostic_monitor_health"' "$DIAG"
grep -Fq 'android:key="export_diagnostic_logs"' "$DIAG"
grep -Fq 'configureDiagnosticsToolbar(toolbar)' "$SRC/SettingsActivity.java"
grep -Fq 'toolbar.setTitle("Diagnostics", collapsed)' "$SRC/SettingsActivity.java"
grep -Fq 'toolbar.setCollapsedSubtitle(null)' "$SRC/SettingsActivity.java"

grep -Fq 'COMBINED = "combined"' "$SRC/ProcessNotificationMode.java"
grep -Fq 'PER_PROCESS = "per_process"' "$SRC/ProcessNotificationMode.java"
grep -Fq 'GROUPED = "grouped"' "$SRC/ProcessNotificationMode.java"
grep -Fq 'DEFAULT_CADENCE_MINUTES = 5' "$SRC/IdleProcessState.java"

echo "Codex Monitor 2.22 source contract PASS"
