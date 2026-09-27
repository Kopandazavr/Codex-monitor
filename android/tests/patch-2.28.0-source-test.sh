#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
SHARED="$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor"
RES="$ROOT/app/src/main/res"

grep -Fq 'versionCode = 54' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionName = "2.28.0"' "$ROOT/app/build.gradle.kts"
grep -Fq 'VERSION_CODE = 54' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = "2.28.0"' "$SRC/AppConstants.java"

# Short Name is uppercase, explicit-only unique, and empty still means automatic Primary fallback.
grep -Fq 'normalizeShort(String value)' "$SHARED/ProjectProfileRules.java"
grep -Fq 'toUpperCase(Locale.ROOT)' "$SHARED/ProjectProfileRules.java"
grep -Fq 'new InputFilter.AllCaps()' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'externalShortOwners(profiles, session.profileId)' "$SRC/ProjectProfileStore.java"
grep -Fq 'That Short Name already belongs to ' "$SRC/ProjectProfileStore.java"
grep -Fq 'return automaticAcronym(primaryAlias);' "$SHARED/ProjectProfileRules.java"

# Legacy ghost reclaim is bounded by stable route/provenance; genuine owners remain collisions.
grep -Fq 'isReclaimableUnroutedLegacyGhost' "$SHARED/ProjectProfileRules.java"
grep -Fq 'hasStableRoute || calendarObserved' "$SHARED/ProjectProfileRules.java"
grep -Fq 'isReclaimableStaleGhost' "$SRC/ProjectProfileStore.java"
grep -Fq 'removeRoutesOwnedBy(routes, orphanId);' "$SRC/ProjectProfileStore.java"

# Stable many-alias routing + provenance + copy-on-edit.
grep -Fq 'calendar_aliases' "$SRC/ProjectProfileStore.java"
grep -Fq 'aliasOwner.calendarAliases.add(normalized)' "$SRC/ProjectProfileStore.java"
grep -Fq 'routed.aliases.add(raw)' "$SRC/ProjectProfileStore.java"
grep -Fq 'routed.calendarAliases.add(normalized)' "$SRC/ProjectProfileStore.java"
grep -Fq 'Calendar aliases are immutable routing evidence. Editing is copy-on-edit.' "$SHARED/ProjectProfileEditState.java"
grep -Fq 'calendar ? R.drawable.ic_alias_calendar : R.drawable.ic_project_pencil' "$SRC/ProjectSettingsDialog.java"
grep -Fq '.setTitle("Edit alias")' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'Button makePrimary = miniButton(activity, "Make Primary")' "$SRC/ProjectSettingsDialog.java"
! grep -Fq 'miniButton(activity, "Delete")' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'R.drawable.ic_idle_trash' "$SRC/ProjectSettingsDialog.java"

# Notification identity uses effective local Short Name/color, refreshes after Done, keeps bell/topic, no trash.
grep -Fq 'ProjectProfileStore.effectiveShort(profile)' "$SRC/ProcessNotificationManager.java"
grep -Fq 'ProjectProfileStore.accentColor(profile)' "$SRC/ProcessNotificationManager.java"
grep -Fq 'roleAndTopic + " · " + clean(process.topic)' "$SRC/ProcessNotificationManager.java"
grep -Fq 'notification_process_reminder' "$RES/layout/notification_process_row.xml"
! grep -Fq 'notification_process_dismiss' "$RES/layout/notification_process_row.xml"
grep -Fq 'private void onProjectProfileChanged()' "$SRC/MainActivity.java"
grep -Fq 'repostForProcessChangeDelayed(this, 120L)' "$SRC/MainActivity.java"

# Diagnostics keeps complete build identity in both expanded and collapsed header states.
grep -Fq '"Version " + BuildConfig.VERSION_NAME + " · Build " + BuildConfig.VERSION_CODE' "$SRC/SettingsActivity.java"
grep -Fq 'toolbar.setCollapsedSubtitle(collapsedSubtitle);' "$SRC/SettingsActivity.java"
grep -Fq 'BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"' "$SRC/SettingsActivity.java"

# Dashboard dismissal is display-only; durable session history is retained.
grep -Fq 'static List<SessionRecord> history(Context context, String key)' "$SRC/IdleProcessState.java"
grep -Fq 'json.put("history", historyArray);' "$SRC/IdleProcessState.java"
grep -Fq 'appendHistory(row, project, projectShort, role, topic, eventId' "$SRC/IdleProcessState.java"
grep -Fq 'row.dismissedThroughMillis = Math.max' "$SRC/IdleProcessState.java"
! grep -Fq 'rows.remove(clean(key))' "$SRC/IdleProcessState.java"

echo "Codex Monitor 2.28 source contract PASS"
