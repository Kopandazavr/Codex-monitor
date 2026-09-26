#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
SHARED="$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor"

grep -Fq 'versionCode = 52' "$ROOT/app/build.gradle.kts"
grep -Fq 'versionName = "2.26.0"' "$ROOT/app/build.gradle.kts"
grep -Fq 'VERSION_CODE = 52' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = "2.26.0"' "$SRC/AppConstants.java"

# Project Settings is one in-memory transaction: no per-control persistent writes.
grep -Fq 'ProjectProfileStore.beginEdit(activity, profileId)' "$SRC/ProjectSettingsDialog.java"
grep -Fq '.setNegativeButton("Cancel", null)' "$SRC/ProjectSettingsDialog.java"
grep -Fq '.setPositiveButton("Done", null)' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'String error = edit.commit(activity);' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'if (onChanged != null) onChanged.run();' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'private void changed()' "$SRC/ProjectSettingsDialog.java"
! grep -Fq 'ProjectProfileStore.setAppearance' "$SRC/ProjectSettingsDialog.java"
! grep -Fq 'ProjectProfileStore.setShortOverride' "$SRC/ProjectSettingsDialog.java"
! grep -Fq 'ProjectProfileStore.addAlias' "$SRC/ProjectSettingsDialog.java"
! grep -Fq 'ProjectProfileStore.makePrimary' "$SRC/ProjectSettingsDialog.java"
! grep -Fq 'ProjectProfileStore.deleteAlias' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'render(true);' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'ICON_COLUMNS = 6' "$SRC/ProjectSettingsDialog.java"

# Draft behavior + commit-time collision revalidation.
test -f "$SHARED/ProjectProfileEditState.java"
grep -Fq 'ProjectProfileEditState copy()' "$SHARED/ProjectProfileEditState.java"
grep -Fq 'String addAlias(String alias, Map<String, String> externalOwners)' "$SHARED/ProjectProfileEditState.java"
grep -Fq 'String deleteAlias(String alias)' "$SHARED/ProjectProfileEditState.java"
grep -Fq 'static synchronized EditSession beginEdit' "$SRC/ProjectProfileStore.java"
grep -Fq 'private static synchronized String commitEdit' "$SRC/ProjectProfileStore.java"
grep -Fq 'Map<String, String> currentOwners = externalAliasOwners' "$SRC/ProjectProfileStore.java"
grep -Fq 'profiles.set(index, replacement);' "$SRC/ProjectProfileStore.java"
grep -Fq 'save(context, profiles);' "$SRC/ProjectProfileStore.java"

# Incoming watchdog project names keep stable local identity even after visible alias deletion.
grep -Fq 'KEY_ROUTES = "incoming_routes_json"' "$SRC/ProjectProfileStore.java"
grep -Fq 'MutableProfile routed = findMutable(profiles, routes.get(normalized));' "$SRC/ProjectProfileStore.java"
grep -Fq '"legacy_orphan_reconciled"' "$SRC/ProjectProfileStore.java"
grep -Fq 'owner.id.startsWith("project:")' "$SRC/ProjectProfileStore.java"
grep -Fq '"folder".equals(owner.iconKey)' "$SRC/ProjectProfileStore.java"

# 70% transparent = ~30% alpha same accent for both identity surfaces.
grep -Fq 'return 0x4D000000 | (accentColor(profile) & 0x00FFFFFF);' "$SRC/ProjectProfileStore.java"
grep -Fq 'pillBackground.setColor(surfaceTint);' "$SRC/ProjectBadgeView.java"
grep -Fq 'disc.setColor(surfaceTint);' "$SRC/ProjectBadgeView.java"
grep -Fq 'pillBackground.setStroke(Ui.dp(context, 1.5f), accent);' "$SRC/ProjectBadgeView.java"
grep -Fq 'disc.setStroke(Ui.dp(context, 1.5f), accent);' "$SRC/ProjectBadgeView.java"

# Bell/trash visible footprint matches the 36dp project disc while preserving old center Y=19dp.
grep -Fq 'PROCESS_ACTION_GUTTER_DP = 42' "$SRC/MainActivity.java"
grep -Fq 'PROCESS_ACTION_VISIBLE_DP = 36' "$SRC/MainActivity.java"
grep -Fq 'PROCESS_ACTION_CENTER_OFFSET_DP = 1' "$SRC/MainActivity.java"
grep -Fq 'Ui.dp(this, PROCESS_ACTION_VISIBLE_DP)' "$SRC/MainActivity.java"
grep -Fq 'setTranslationY(Ui.dp(this, PROCESS_ACTION_CENTER_OFFSET_DP))' "$SRC/MainActivity.java"
! grep -Fq 'setTranslationY(-Ui.dp(this, 2))' "$SRC/MainActivity.java"

echo "Codex Monitor 2.26 source contract PASS"
