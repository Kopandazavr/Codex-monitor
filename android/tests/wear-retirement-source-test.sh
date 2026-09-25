#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKFLOW="$ROOT/../.github/workflows/build-apk.yml"

# User decision 2026-09-25: Wear OS is retired unless the user explicitly reverses it.
# Historical source may remain, but it is outside build/lint/test/version/release/acceptance work.
grep -Fq 'Wear OS companion is retired/unsupported' "$ROOT/settings.gradle.kts"
! grep -Fq 'include(":wear")' "$ROOT/settings.gradle.kts"
! grep -Fq ':wear:' "$ROOT/build.sh"
! grep -Fq ':wear:' "$ROOT/lint.sh"
! grep -Fq 'wear/build.gradle.kts' "$ROOT/run-tests.sh"
! grep -Fq 'codexmonitor/wear/' "$ROOT/run-tests.sh"
! grep -Fq 'WearGlanceFormat.java' "$ROOT/run-tests.sh"
! grep -Fq 'platforms;android-37.0' "$WORKFLOW"
! grep -Fq 'CodexMonitor-Wear' "$WORKFLOW"
! grep -Fq 'Codex Monitor Wear OS' "$WORKFLOW"
grep -Fq 'WEAR_RETIREMENT.md' "$ROOT/../README.md"

echo "Wear retirement contract PASS"
