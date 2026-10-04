#!/usr/bin/env bash
set -euo pipefail
trap 'echo "2.29 source contract failed at line $LINENO" >&2' ERR
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
SHARED="$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor"

VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")"
[[ "$VERSION_CODE" -ge 55 ]]
grep -Fq 'VERSION_CODE = ' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = ' "$SRC/AppConstants.java"

grep -Fq 'toolbar.setTitle(HOME_TITLE, HOME_TITLE);' "$SRC/HomeVersionLabel.java"
grep -Fq 'toolbar.setSubtitle(expandedSubtitle);' "$SRC/HomeVersionLabel.java"
grep -Fq 'toolbar.setCollapsedSubtitle(collapsedSubtitle);' "$SRC/HomeVersionLabel.java"
! grep -Fq 'findHomeTitle' "$SRC/HomeVersionLabel.java"

test -f "$SRC/RoleProfileStore.java"
test -f "$SRC/RoleSettingsDialog.java"
test -f "$SHARED/RoleProfileEditState.java"
grep -Fq '"role-profile:" + UUID.randomUUID()' "$SRC/RoleProfileStore.java"
grep -Fq 'Calendar aliases remain immutable routing evidence. Editing is copy-on-edit.' "$SHARED/RoleProfileEditState.java"
grep -Fq 'RoleSettingsDialog.show(this, process, this::onRoleProfileChanged)' "$SRC/MainActivity.java"
grep -Fq 'RoleSettingsDialog.show(this, idle, this::onRoleProfileChanged)' "$SRC/MainActivity.java"
grep -Fq 'RoleProfileStore.displayName' "$SRC/ProcessNotificationManager.java"
! grep -Fq 'IdleProcessState.history(' "$SRC/RoleSettingsDialog.java"

grep -Fq 'rowForProcess(context, rows, process)' "$SRC/IdleProcessState.java"
grep -Fq 'mergeMutable(target, legacy)' "$SRC/IdleProcessState.java"
grep -Fq 'target.history.add(record)' "$SRC/IdleProcessState.java"

grep -Fq '"completion_dispatched"' "$SRC/IdleReminderManager.java"
grep -Fq 'ProcessNotificationManager.postCompletionAlert(' "$SRC/IdleReminderManager.java"
grep -Fq 'context, containerId, idle, nowMillis' "$SRC/IdleReminderManager.java"
grep -Fq 'completionNotificationChannelId(' "$SRC/ProcessNotificationManager.java"
grep -Fq 'PROCESS_COMPLETION_CHANNEL_ID' "$SRC/AlertSoundManager.java"
grep -Fq 'markCompletionBaseline(context, containerId, idle)' "$SRC/IdleReminderManager.java"
! grep -Fq 'AlertSoundManager.playProcessCompletion(context)' "$SRC/IdleReminderManager.java"
! grep -Fq 'overlayShownHaptic' "$SRC/IdleReminderOverlayService.java"
! grep -Fq 'vibrate(330L)' "$SRC/IdleReminderOverlayService.java"

grep -Fq 'nowMillis < beginMillis' "$SRC/CalendarProcess.java"
grep -Fq 'Math.max(0L, beginMillis - nowMillis)' "$SRC/CalendarProcess.java"
grep -Fq 'String instanceIdentity()' "$SRC/CalendarProcess.java"
grep -Fq '"legacy-event:" + eventId' "$SRC/CalendarProcess.java"
grep -Fq '"instance:" + instanceId' "$SRC/CalendarProcess.java"
grep -Fq 'WatchdogInstanceState.remember(pending, row.key, process)' "$SRC/IdleProcessState.java"
grep -Fq 'instance.deadlineMillis' "$SRC/IdleProcessState.java"
grep -Fq 'watchdog_deadline_reached' "$SRC/IdleProcessState.java"

test -f "$SRC/WatchdogCanonicalizer.java"
grep -Fq 'p.beginMillis>memory.winnerBeginMillis' "$SRC/WatchdogCanonicalizer.java"
grep -Fq 'candidate.providerUpdatedMillis>current.providerUpdatedMillis' "$SRC/WatchdogCanonicalizer.java"
grep -Fq 'memory.shadowEventIds.contains(p.eventId)' "$SRC/WatchdogCanonicalizer.java"
grep -Fq 'provider_updated' "$SRC/GoogleCalendarProcessSource.java"
grep -Fq 'selection_memory_json' "$SRC/CalendarProcessReader.java"

echo "Codex Monitor 2.29 source contract PASS"
