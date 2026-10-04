#!/usr/bin/env bash
set -euo pipefail
trap 'echo "2.40 source contract failed at line $LINENO" >&2' ERR

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
RES="$ROOT/app/src/main/res"

VERSION_NAME="$(awk -F'"' '/versionName = "/ { print $2; exit }' "$ROOT/app/build.gradle.kts")"
VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")"
[[ "$VERSION_NAME" == "2.40.0" ]]
[[ "$VERSION_CODE" == "66" ]]
grep -Fq 'VERSION_NAME = "2.40.0"' "$SRC/AppConstants.java"
grep -Fq 'VERSION_CODE = 66' "$SRC/AppConstants.java"

# Stable outer account-container registry + idempotent Main migration owner.
test -f "$SRC/AccountContainerStore.java"
grep -Fq 'KEY_ACCOUNTS = "accounts_json"' "$SRC/AccountContainerStore.java"
grep -Fq 'KEY_SELECTED = "selected_id"' "$SRC/AccountContainerStore.java"
grep -Fq 'KEY_LEGACY_OWNER = "legacy_owner_id"' "$SRC/AccountContainerStore.java"
grep -Fq '"Main"' "$SRC/AccountContainerStore.java"
grep -Fq '"account:" + UUID.randomUUID()' "$SRC/AccountContainerStore.java"
grep -Fq 'accounts.size() <= 1' "$SRC/AccountContainerStore.java"
grep -Fq 'setColor(Context context, String id, String colorKey)' "$SRC/AccountContainerStore.java"

# ChatGPT credentials, OAuth ownership, snapshots and histories are explicit-container state.
grep -Fq 'blobKey(containerId)' "$SRC/SecureTokenStore.java"
grep -Fq 'SecureTokenStore.save(this, containerId, tokens)' "$SRC/OAuthService.java"
grep -Fq 'EXTRA_CONTAINER_ID' "$SRC/OAuthService.java"
grep -Fq 'accountKey(containerId, KEY_SNAPSHOT)' "$SRC/AppPreferences.java"
grep -Fq 'loadUsageHistory(Context context, String containerId' "$SRC/AppPreferences.java"
grep -Fq 'refreshAndCacheScheduled(Context context, String containerId' "$SRC/UsageApi.java"
! grep -Fq 'installCookieManager' "$SRC/UsageApi.java"
! grep -Fq 'installCookieManager' "$SRC/ResetCreditApi.java"

# One global coordinator services every signed-in account, including while the app is foreground.
grep -Fq 'for (AccountContainerStore.Account account : AccountContainerStore.all(app))' "$SRC/UsageRefreshJobService.java"
grep -Fq 'UsageApi.refreshAndCacheScheduled(' "$SRC/UsageRefreshJobService.java"
grep -Fq 'app, account.id, forceSubscription' "$SRC/UsageRefreshJobService.java"
grep -Fq 'for (AccountContainerStore.Account account : AccountContainerStore.all(app))' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'app, account.id, forceSubscription, trigger' "$SRC/ForegroundUsageRefresh.java"
grep -Fq 'refreshAccountSequentially' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'GoogleCalendarProcessSource.forceRefresh(app, account.id' "$SRC/ProcessNotificationScheduler.java"
grep -Fq 'scheduleAtNextKnownReset' "$SRC/RefreshScheduler.java"

# Google Calendar identity/cache is account-scoped; device account chooser/add flow is explicit.
grep -Fq 'accountName(Context context, String containerId)' "$SRC/GoogleCalendarAuthorization.java"
grep -Fq 'accessToken(Context context, String containerId' "$SRC/GoogleCalendarAuthorization.java"
grep -Fq 'forceRefresh(Context context, String containerId' "$SRC/GoogleCalendarProcessSource.java"
grep -Fq 'setAlwaysShowAccountPicker(true)' "$SRC/GoogleCalendarAuthorizationActivity.java"
grep -Fq 'Settings.ACTION_ADD_ACCOUNT' "$SRC/GoogleCalendarAuthorizationActivity.java"
grep -Fq 'Choose account' "$SRC/GoogleCalendarAuthorizationActivity.java"
grep -Fq 'Add Google account' "$SRC/GoogleCalendarAuthorizationActivity.java"

# Local Android Calendar fallback has one owner and explicit cross-account transfer UI.
test -f "$SRC/LocalCalendarFallbackOwner.java"
grep -Fq 'KEY_OWNER_ID = "owner_id"' "$SRC/LocalCalendarFallbackOwner.java"
grep -Fq 'LocalCalendarFallbackOwner.isOwner' "$SRC/CalendarProcessReader.java"
grep -Fq 'Used by ' "$SRC/OnboardingActivity.java"
grep -Fq 'Move Local Calendar fallback?' "$SRC/OnboardingActivity.java"
grep -Fq 'Local calendar fallback can only be assigned to one account.' "$SRC/OnboardingActivity.java"

# Global account selector is persistent in Main/onboarding and ends with Add Account.
test -f "$SRC/AccountSwitcherView.java"
grep -Fq '"+ Add Account"' "$SRC/AccountSwitcherView.java"
grep -Fq 'setGroupDividerEnabled(true)' "$SRC/AccountSwitcherView.java"
grep -Fq 'installAccountHeader(page.toolbar)' "$SRC/MainActivity.java"
grep -Fq 'page.toolbar.setExpandable(false)' "$SRC/MainActivity.java"
grep -Fq 'return "v" + name + " (" + code + ")"' "$SRC/MainActivity.java"
grep -Fq 'AccountSwitcherView.create(this, this.dark' "$SRC/OnboardingActivity.java"
grep -Fq 'android:key="settings_accounts"' "$RES/xml/preferences_settings.xml"
test -f "$RES/xml/preferences_settings_accounts.xml"

# Runtime/process/history identity and notification namespace include the owning container.
grep -Fq 'AccountNotificationNamespace.tag(containerId)' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'AccountNotificationNamespace.tag(containerId)' "$SRC/ProcessNotificationManager.java"
grep -Fq 'scopedKey(containerId' "$SRC/IdleReminderManager.java"
grep -Fq 'rowsKey(containerId)' "$SRC/IdleProcessState.java"
grep -Fq 'WatchdogInstanceState.remember(context, containerId' "$SRC/IdleProcessState.java"
grep -Fq 'notification_account_text' "$RES/layout/notification_usage_dual_bars.xml"
grep -Fq 'notification_account_text' "$RES/layout/notification_usage_dual_bars_expanded.xml"
grep -Fq 'notification_account_text' "$RES/layout/notification_processes.xml"
grep -Fq 'notification_account_text' "$RES/layout/notification_processes_expanded.xml"
grep -Fq 'AccountContainerStore.accentColor(account)' "$SRC/DualUsageNotificationManager.java"
grep -Fq 'AccountContainerStore.accentColor(account)' "$SRC/ProcessNotificationManager.java"

# Per-account reset alerts/bells/credits cannot collide or dedupe across containers.
grep -Fq 'EXTRA_CONTAINER_ID = OAuthService.EXTRA_CONTAINER_ID' "$SRC/ResetAlertScheduler.java"
grep -Fq 'scheduleFromSnapshot(Context context, String containerId' "$SRC/ResetAlertScheduler.java"
grep -Fq 'accountStateKey(containerId' "$SRC/ResetNotificationManager.java"
grep -Fq 'KEY_ACCOUNT_STATE_MIGRATED' "$SRC/ResetNotificationManager.java"
grep -Fq 'clearContainer(Context context, String containerId)' "$SRC/NowBarResetReminder.java"
grep -Fq 'scheduleFromSnapshot(Context context, String containerId' "$SRC/ResetCreditExpiryScheduler.java"

# TODO-058: full custom visible card uses canonical allowance colors + dark foreground.
grep -Fq '<color name="allowance_five_hour">#70A7FF</color>' "$RES/values/colors.xml"
grep -Fq '<color name="allowance_long_window">#F4B95F</color>' "$RES/values/colors.xml"
grep -Fq '<color name="allowance_alert_foreground">#202124</color>' "$RES/values/colors.xml"
grep -Fq 'android:background="@color/allowance_five_hour"' "$RES/layout/notification_reset_alert_five.xml"
grep -Fq 'android:background="@color/allowance_long_window"' "$RES/layout/notification_reset_alert_long.xml"
grep -Fq 'android:textColor="@color/allowance_alert_foreground"' "$RES/layout/notification_reset_alert_five.xml"
grep -Fq 'android:textColor="@color/allowance_alert_foreground"' "$RES/layout/notification_reset_alert_long.xml"
grep -Fq 'setCustomContentView(alert)' "$SRC/ResetNotificationManager.java"
grep -Fq 'realertResetSurface' "$SRC/DualUsageNotificationManager.java"

# TODO-059: overlay Short Name uses project accent; role line reuses profile/icon label contract.
grep -Fq 'ProjectProfileStore.badgeText' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'ProjectProfileStore.accentColor' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'RoleProfileStore.displayLabelById' "$SRC/IdleReminderOverlayService.java"
grep -Fq 'return withIcon(profile.roleIcon, name);' "$SRC/RoleProfileStore.java"

# User-approved 2.40 notification copy keeps percentage before Reset in, without window labels.
grep -Fq '.append("% · Reset in ")' "$SRC/DualUsageNotificationManager.java"

echo "Codex Monitor 2.40 source contract PASS"
