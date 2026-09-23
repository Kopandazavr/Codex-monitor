package dev.bennett.codexmeter;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/** Visible/recoverable/revocable Google Calendar authorization trampoline. */
public final class GoogleCalendarAuthorizationActivity extends AppCompatActivity {
    private static final int REQUEST_AUTHORIZE = 8612;
    private static final long TRANSIENT_RETRY_DELAY_MS = 1_200L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean flowStarted;
    private boolean transientRetryUsed;

    @Override
    protected void onCreate(Bundle state) {
        Ui.applySelectedTheme(this);
        super.onCreate(state);
        if (GoogleCalendarAuthorization.isConnected(this)) {
            showConnectedDialog();
        } else {
            begin();
        }
    }

    private void begin() {
        if (flowStarted) return;
        flowStarted = true;
        GoogleCalendarAuthorization.beginInteractive(this, REQUEST_AUTHORIZE,
                (success, message) -> runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                        GoogleCalendarProcessSource.forceRefresh(this,
                                () -> DualUsageNotificationManager.repostDelayed(this, 120L));
                        finish();
                    } else if (!isFinishing()
                            && !message.startsWith("Choose a Google account")) {
                        if (!scheduleTransientRetry("authorize_callback")) {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                }));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_AUTHORIZE) return;
        GoogleCalendarAuthorization.AuthOutcome outcome =
                GoogleCalendarAuthorization.consumeInteractiveResult(
                        this, resultCode, data);
        if (outcome.success) {
            Toast.makeText(this, outcome.message, Toast.LENGTH_SHORT).show();
            GoogleCalendarProcessSource.forceRefresh(this,
                    () -> DualUsageNotificationManager.repostDelayed(this, 120L));
            finish();
            return;
        }
        if (scheduleTransientRetry("activity_result")) return;
        Toast.makeText(this, outcome.message, Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean scheduleTransientRetry(String source) {
        if (transientRetryUsed || !GoogleCalendarAuthorization.shouldRetryTransient(this)) {
            return false;
        }
        transientRetryUsed = true;
        flowStarted = false;
        DiagnosticLog.info(this, "calendar_api", "authorization_retry_scheduled",
                "source", source,
                "attempt", 2,
                "delay_ms", TRANSIENT_RETRY_DELAY_MS);
        Toast.makeText(this, "Temporary Google services error. Retrying once…",
                Toast.LENGTH_SHORT).show();
        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            DiagnosticLog.info(this, "calendar_api", "authorization_retry_started",
                    "attempt", 2);
            begin();
        }, TRANSIENT_RETRY_DELAY_MS);
        return true;
    }

    private void showConnectedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Google Calendar connected")
                .setMessage("Direct read-only event access is enabled. "
                        + "Local Android Calendar remains a fallback.")
                .setNegativeButton("Done", (dialog, which) -> finish())
                .setPositiveButton("Disconnect", (dialog, which) ->
                        GoogleCalendarAuthorization.revoke(this,
                                (success, message) -> runOnUiThread(() -> {
                                    Toast.makeText(this, message,
                                            success ? Toast.LENGTH_SHORT
                                                    : Toast.LENGTH_LONG).show();
                                    finish();
                                })))
                .setOnCancelListener(dialog -> finish())
                .show();
    }
}
