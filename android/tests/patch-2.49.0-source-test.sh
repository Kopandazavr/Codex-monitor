#!/usr/bin/env bash
set -euo pipefail
trap 'echo "2.49 source contract failed at line $LINENO" >&2' ERR
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/kopandazavr/codexmonitor"
MAIN="$SRC/MainActivity.java"
WAVE="$SRC/UsageWaveView.java"
[[ "$(awk -F'"' '/versionName = "/ { print $2; exit }' "$ROOT/app/build.gradle.kts")" == "2.49.0" ]]
[[ "$(awk '/versionCode = / { print $3; exit }' "$ROOT/app/build.gradle.kts")" -eq 75 ]]
# The forecast is still computed for acceleration; only two primary Home tiles hide copy.
grep -Fq 'UsagePacePreferences.assess(this, snapshot, window, now)' "$MAIN"
grep -Fq 'invertedWave, pace.accelerated' "$MAIN"
grep -Fq 'compactReset = "5-hour".equals(label) || "Weekly".equals(label)' "$WAVE"
grep -Fq 'pace = compactReset || paceEstimate == null ? "" : paceEstimate;' "$WAVE"
grep -Fq 'resetTop = reset == null ? "" : reset;' "$WAVE"
grep -Fq 'resetBottom = "";' "$WAVE"
grep -Fq 'canvas.drawText(resetTop, left, 82f * density, resetPaint)' "$WAVE"
grep -Fq 'canvas.clipRect(left, 52f * density, right, 100f * density)' "$WAVE"
# Right glyphs must take colors from the actual orange vs dark wave areas,
# including boundary crossings, not a single global weekly-black flag.
grep -Fq 'if (weekly && fillPercent > 0)' "$WAVE"
grep -Fq 'canvas.clipPath(fillPath)' "$WAVE"
grep -Fq 'drawRightMetrics(canvas, rightCenter, density, 0xFF000000)' "$WAVE"
grep -Fq 'canvas.clipOutPath(fillPath)' "$WAVE"
grep -Fq 'drawRightMetrics(canvas, rightCenter, density, Ui.mainText(dark))' "$WAVE"
grep -Fq 'drawRightMetrics(canvas, rightCenter, density, foreground)' "$WAVE"
# Wave fill and reset ring still draw unchanged.
grep -Fq 'canvas.drawPath(fillPath, fillPaint);' "$WAVE"
grep -Fq 'resetRingPaint.setColor(RESET_LIME)' "$WAVE"
echo "2.49 primary usage card visual source contract PASS"
