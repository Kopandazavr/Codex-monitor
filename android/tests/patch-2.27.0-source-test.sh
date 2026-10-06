#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
SHARED="$ROOT/shared/src/main/java/dev/kopandazavr/codexmonitor"

VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")"
[[ "$VERSION_CODE" -ge 53 ]]
grep -Fq 'VERSION_CODE = ' "$SRC/AppConstants.java"
grep -Fq 'VERSION_NAME = ' "$SRC/AppConstants.java"

# Legacy orphan reclaim is deliberately narrow and commit-time revalidated.
grep -Fq 'isReclaimableLegacyOrphan' "$SHARED/ProjectProfileRules.java"
grep -Fq 'UUID.fromString(profileId.substring(prefix.length()))' "$SHARED/ProjectProfileRules.java"
grep -Fq 'aliases == null || aliases.size() != 1' "$SHARED/ProjectProfileRules.java"
grep -Fq '!"folder".equals(iconKey) || !"gray".equals(colorKey)' "$SHARED/ProjectProfileRules.java"
grep -Fq 'externalAliasOwners(profiles, target.id, true)' "$SRC/ProjectProfileStore.java"
grep -Fq 'externalAliasOwners(profiles, session.profileId, false)' "$SRC/ProjectProfileStore.java"
grep -Fq 'profiles.remove(externalOwner);' "$SRC/ProjectProfileStore.java"
grep -Fq 'removeRoutesOwnedBy(routes, orphanId);' "$SRC/ProjectProfileStore.java"
grep -Fq 'routes.put(normalized, session.profileId);' "$SRC/ProjectProfileStore.java"
grep -Fq 'save(context, profiles, routes);' "$SRC/ProjectProfileStore.java"

# Empty Short Name is the explicit no-override state; there is no nested apply/check control.
grep -Fq 'edit.setShortOverride(s == null ? "" : s.toString());' "$SRC/ProjectSettingsDialog.java"
! grep -Fq 'Apply short name' "$SRC/ProjectSettingsDialog.java"
! grep -Fq 'Ui.button(activity, "✓"' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'setHint(ProjectProfileStore.fallbackShort(profile, watchdogShort))' "$SRC/ProjectSettingsDialog.java"
grep -Fq 'return automaticAcronym(primaryAlias);' "$SHARED/ProjectProfileRules.java"

# Project identity fill is opaque, same-hue and ~40% of outline intensity.
grep -Fq '* 0.40f' "$SRC/ProjectProfileStore.java"
grep -Fq 'return 0xFF000000 | (red << 16) | (green << 8) | blue;' "$SRC/ProjectProfileStore.java"
! grep -Fq '0x4D000000' "$SRC/ProjectProfileStore.java"
grep -Fq 'pillBackground.setStroke(Ui.dp(context, 1.5f), accent);' "$SRC/ProjectBadgeView.java"
grep -Fq 'disc.setStroke(Ui.dp(context, 1.5f), accent);' "$SRC/ProjectBadgeView.java"

# Dashboard 5-hour liquid now uses exactly the Usage History blue source; notification blue untouched.
! grep -Fq 'FIVE_HOUR_BLUE' "$SRC/UsageWaveView.java"
grep -Fq 'fillPaint.setColor(weekly ? WEEKLY_ORANGE : Ui.accent(getContext(), dark));' "$SRC/UsageWaveView.java"
grep -Fq 'int seriesColor = isWeekly() ? WEEKLY_ORANGE : Ui.accent(getContext(), dark);' "$SRC/UsageBurnChartView.java"

# Later toolbar work supersedes the legacy HomeVersionLabel owner while preserving full build identity.
! test -f "$SRC/HomeVersionLabel.java"
! grep -Fq 'HomeVersionLabel.apply(activity);' "$SRC/CodexMonitorApplication.java"
grep -Fq 'return "v" + name + " (" + code + ")";' "$SRC/MainActivity.java"

# Accepted 2.26 process controls are frozen.
grep -Fq 'PROCESS_ACTION_GUTTER_DP = 42' "$SRC/MainActivity.java"
grep -Fq 'PROCESS_ACTION_VISIBLE_DP = 36' "$SRC/MainActivity.java"
grep -Fq 'PROCESS_ACTION_CENTER_OFFSET_DP = 1' "$SRC/MainActivity.java"

echo "Codex Monitor 2.27 source contract PASS"
