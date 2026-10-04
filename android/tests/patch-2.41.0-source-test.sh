#!/usr/bin/env bash
set -euo pipefail
trap 'echo "2.41 source contract failed at line $LINENO" >&2' ERR

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"

VERSION_NAME="$(awk -F'"' '/versionName = "/ { print $2; exit }' "$ROOT/app/build.gradle.kts")"
VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")"
[[ "$VERSION_NAME" == "2.41.0" ]]
[[ "$VERSION_CODE" == "67" ]]
grep -Fq 'VERSION_NAME = "2.41.0"' "$SRC/AppConstants.java"
grep -Fq 'VERSION_CODE = 67' "$SRC/AppConstants.java"

# Selected Now Bar and combined usage/process surface share one account-scoped identity.
grep -Fq 'private static final int NOTIFICATION_ID = 8610;' "$SRC/NowBarManager.java"
grep -Fq 'private static final int NOTIFICATION_ID = 8610;' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'manager.notify(AccountNotificationNamespace.tag(containerId),' "$SRC/NowBarManager.java"
grep -Fq 'manager.notify(AccountNotificationNamespace.tag(containerId)' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'manager.cancel(NOTIFICATION_ID);' "$SRC/NowBarManager.java"
! grep -Fq 'NowBarManager.isActive' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'static boolean shouldRun(Context context)' "$SRC/ProcessNotificationScheduler.java"

# Foreground account selection has one reconciliation owner; stale explicit intents do not retarget.
test -f "$SRC/ForegroundAccountCoordinator.java"
grep -Fq 'ForegroundAccountCoordinator.select(activity, account.id)' "$SRC/AccountSwitcherView.java"
grep -Fq 'ForegroundAccountCoordinator.select(this, containerId.trim())' "$SRC/MainActivity.java"
grep -Fq 'ForegroundAccountCoordinator.select(requireContext(), account.id)' "$SRC/SettingsActivity.java"
grep -Fq 'private boolean selectAccountFromIntent(Intent intent)' "$SRC/MainActivity.java"
grep -Fq 'return AccountContainerStore.find(context, explicit) == null ? null : explicit;' "$SRC/NowBarActionReceiver.java"
grep -Fq 'return AccountContainerStore.find(context, requested) == null ? null : requested;' "$SRC/IdleReminderManager.java"
grep -Fq 'return AccountContainerStore.find(context, requested) == null ? null : requested;' "$SRC/NowBarResetReminder.java"

# Calendar diagnostics and Provider fallback are container-owned.
grep -Fq 'key(containerId, KEY_CURRENT_SOURCE)' "$SRC/MonitorHealthDiagnostics.java"
grep -Fq 'recordDirectPollSuccess(Context context, String containerId' "$SRC/MonitorHealthDiagnostics.java"
grep -Fq 'context, containerId, processes.size(), idleRoles.size()' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'providerAccountName(context, containerId)' "$SRC/CalendarProcessReader.java"
grep -Fq 'CalendarContract.Calendars.ACCOUNT_TYPE + " = ?"' "$SRC/CalendarProcessReader.java"
grep -Fq 'GoogleCalendarAuthorization.GOOGLE_ACCOUNT_TYPE' "$SRC/CalendarProcessReader.java"
grep -Fq 'IdleReminderManager.cancelAllScheduled(context, previous)' "$SRC/LocalCalendarFallbackOwner.java"
grep -Fq 'isAccountSharedWithAnotherContainer' "$SRC/GoogleCalendarAuthorization.java"

# Remove Account invalidates first, drains owner runtime, and blocks late async resurrection.
test -f "$SRC/AccountContainerLifecycleGuard.java"
grep -Fq 'AccountContainerLifecycleGuard.invalidate(context, target)' "$SRC/AccountContainerLifecycle.java"
grep -Fq 'IdleReminderManager.cancelAllScheduled(context, target)' "$SRC/AccountContainerLifecycle.java"
grep -Fq 'GoogleCalendarProcessSource.clearContainer(context, target)' "$SRC/AccountContainerLifecycle.java"
grep -Fq 'MonitorHealthDiagnostics.clearContainer(context, target)' "$SRC/AccountContainerLifecycle.java"
grep -Fq 'ProcessNotificationMode.clearContainer(context, target)' "$SRC/AccountContainerLifecycle.java"
grep -Fq 'callbacks.addAll(state.waiters)' "$SRC/GoogleCalendarProcessSource.java"
grep -Fq 'usage_commit_skipped_removed' "$SRC/UsageApi.java"
grep -Fq 'AccountContainerLifecycleGuard.isAlive(context, containerId)' "$SRC/SubscriptionApi.java"
grep -Fq 'AccountContainerLifecycleGuard.isAlive(context, containerId)' "$SRC/ResetCreditApi.java"

# Interactive operations remain pinned to their initiating local account.
grep -Fq 'private String flowContainerId = "";' "$SRC/GoogleCalendarAuthorizationActivity.java"
grep -Fq 'this.flowContainerId, resultCode, data' "$SRC/GoogleCalendarAuthorizationActivity.java"
grep -Fq 'private String targetContainerId = "";' "$SRC/ResetCreditActivity.java"
grep -Fq 'consumeBestAvailable(applicationContext, ResetCreditActivity.this.targetContainerId)' "$SRC/ResetCreditActivity.java"
grep -Fq '.putExtra(OAuthService.EXTRA_CONTAINER_ID, containerId)' "$SRC/MainActivity.java"
grep -Fq 'AppPreferences.setLastError(this, containerId' "$SRC/OAuthService.java"

# Completion overlay uses a raw/effective Short Name, not the old bracketed badge string.
grep -Fq 'ProjectProfileStore.effectiveShort' "$SRC/IdleReminderOverlayService.java"
! grep -Fq 'ProjectProfileStore.badgeText' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'RoleProfileStore.displayLabelById' "$SRC/IdleReminderOverlayService.java"

echo "Codex Monitor 2.41 source contract PASS"
