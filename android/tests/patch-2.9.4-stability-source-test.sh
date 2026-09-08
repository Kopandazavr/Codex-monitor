#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app/src/main/java/dev/bennett/codexmeter/CodexMeterApplication.java"
GUARD="$ROOT/app/src/main/java/dev/bennett/codexmeter/NotificationRepostGuard.java"
RECEIVER="$ROOT/app/src/main/java/dev/bennett/codexmeter/NowBarActionReceiver.java"
SETTINGS="$ROOT/app/src/main/java/dev/bennett/codexmeter/SettingsActivity.java"
EDITOR="$ROOT/app/src/main/java/dev/bennett/codexmeter/DashboardReorderActivity.java"

# Repeated Settings/subpage/editor transitions must not queue unguarded main-thread notification
# rebuilds. The lifecycle path is collapsed to the last resumed activity and runtime rendering
# failures are contained rather than crashing the foreground task.
grep -Fq 'NotificationRepostGuard.repostAfterActivityResume(this);' "$APP"
grep -Fq 'MAIN.removeCallbacks(pendingActivityResume);' "$GUARD"
grep -Fq 'repostSafely(app, "activity_resume");' "$GUARD"
grep -Fq 'catch (RuntimeException exception)' "$GUARD"
grep -Fq '"guarded_repost_failed"' "$GUARD"

# Edit Dashboard remains the same secondary editor and keeps its drag/reorder implementation.
grep -Fq 'Ui.startSecondaryActivity(requireActivity(), DashboardReorderActivity.class);' "$SETTINGS"
grep -Fq 'touchHelper.startDrag(holder);' "$EDITOR"
grep -Fq 'persistOrder();' "$EDITOR"

# One-card idle bell uses the same state/scheduler semantics but no longer inserts the old 120 ms
# app-side visual delay before rebuilding the existing notification surface.
grep -Fq 'NotificationRepostGuard.toggleIdleReminder(context, intent);' "$RECEIVER"
grep -Fq 'IdleProcessState.toggleReminder(context, key, now);' "$GUARD"
grep -Fq 'IdleReminderManager.onReminderToggled(context, key, enabled, now);' "$GUARD"
grep -Fq 'repostSafely(context.getApplicationContext(), "idle_reminder_toggle");' "$GUARD"
! grep -Fq 'IdleReminderManager.toggleFromIntent(context, intent);' "$RECEIVER"

echo 'Codex Monitor 2.9.4 stability source contract PASS'
