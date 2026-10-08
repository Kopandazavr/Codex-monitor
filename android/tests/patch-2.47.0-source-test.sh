#!/usr/bin/env bash
set -euo pipefail
trap 'echo "2.47 source contract failed at line $LINENO" >&2' ERR
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
MAIN="$SRC/MainActivity.java"
IDLE="$SRC/IdleProcessState.java"
CARD="$SRC/PersistentCardCoordinator.java"
[[ "$(awk -F'"' '/versionName = "/ { print $2; exit }' "$ROOT/app/build.gradle.kts")" == "2.47.0" ]]
[[ "$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")" -eq 73 ]]
# No predecessor repost, fingerprint no-op suppression and attention recovery.
! grep -Fq 'manager.notify(AccountNotificationNamespace.tag(predecessor)' "$CARD"
grep -Fq 'unchanged_suppressed' "$CARD"
grep -Fq 'needsQuietRestore' "$CARD"
grep -Fq 'persistent_card_publish_decision' "$CARD"
grep -Fq 'FINGERPRINT_EXTRA' "$SRC/DualUsageNotificationManager.java"
# Local per-container latest-episode restoration guards; no calendar mutation.
grep -Fq 'static List<IdleRole> deleted(' "$IDLE"
grep -Fq 'static boolean dismissSpecific(' "$IDLE"
grep -Fq 'static boolean restore(' "$IDLE"
grep -Fq 'IdleProcessRestorePolicy.sameEpisode' "$IDLE"
grep -Fq 'row.dismissedThroughMillis = 0L;' "$IDLE"
! grep -Fq 'GoogleCalendarAuthorization.' "$SRC/IdleProcessRestorePolicy.java"
# Transient expansion and adjacent dashboard pair, including no-reset/signed-out.
grep -Fq 'private boolean deletedProcessesExpanded;' "$MAIN"
grep -Fq 'this.deletedProcessesExpanded = false;' "$MAIN"
grep -Fq 'private void addProcessesSectionPair(' "$MAIN"
grep -Fq 'addDashboardCard(column, buildDeletedProcessesCard())' "$MAIN"
grep -Fq 'if (showResets) addDashboardCard(column, buildResetCreditsCard())' "$MAIN"
grep -Fq '"Delete Processes"' "$MAIN"
grep -Fq 'R.drawable.ic_idle_restore' "$MAIN"
grep -Fq 'refreshProcessesCard();' "$MAIN"
test -f "$ROOT/app/src/main/res/drawable/ic_idle_restore.xml"
echo "Codex Monitor 2.47 source contract PASS"
