#!/usr/bin/env bash
set -euo pipefail
trap 'echo "2.45 source contract failed at line $LINENO" >&2' ERR

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"

VERSION_NAME="$(awk -F'"' '/versionName = "/ { print $2; exit }' "$ROOT/app/build.gradle.kts")"
VERSION_CODE="$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")"
[[ "$VERSION_NAME" == "2.45.0" ]]
[[ "$VERSION_CODE" -eq 71 ]]

# The custom two-row title must have enough parent headroom for both rows on Samsung OneUI.
grep -Fq 'int toolbarHeight = Ui.dp(this, 108);' "$SRC/MainActivity.java"
grep -Fq 'Ui.dp(this, 40)' "$SRC/MainActivity.java"
grep -Fq 'Ui.dp(this, 48)' "$SRC/MainActivity.java"

# Legacy watchdogs must not use provider-specific Calendar event ids as their logical identity.
grep -Fq 'return "legacy-semantic:" + semanticIdentityString();' "$SRC/CalendarProcess.java"
grep -Fq 'sameLogicalLegacyWatchdog' "$SRC/WatchdogInstanceState.java"
grep -Fq 'existing.startedMillis == process.workStartMillis()' "$SRC/WatchdogInstanceState.java"
grep -Fq 'existing.deadlineMillis == process.beginMillis' "$SRC/WatchdogInstanceState.java"

# Completion delivery identity must also remain source-independent, so duplicate histories
# from a fallback -> Direct transition collapse to one delivery preference/alarm key.
grep -Fq 'return legacySessionIdentity(project, projectShort, role, topic,' "$SRC/IdleProcessState.java"
grep -Fq 'legacySessionIdentity(record.project, record.projectShort' "$SRC/IdleProcessState.java"
grep -Fq 'String completionIdentity()' "$SRC/IdleProcessState.java"

echo "Codex Monitor 2.45 source contract PASS"
