package dev.bennett.codexmeter;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/** Visible/recoverable/revocable Google Calendar authorization trampoline. */
public final class GoogleCalendarAuthorizationActivity extends AppCompatActivity {
    private static final int REQUEST_AUTHORIZE = 8612;
    private boolean flowStarted;

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
                    } else if (!isFinishing() && !message.startsWith("Choose a Google account")) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        finish();
                    }
                }));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_AUTHORIZE) return;
        boolean success = resultCode == RESULT_OK
                && GoogleCalendarAuthorization.consumeInteractiveResult(this, data);
        Toast.makeText(this,
                success ? "Google Calendar connected."
                        : "Google Calendar authorization was not completed.",
                success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        if (success) {
            GoogleCalendarProcessSource.forceRefresh(this,
                    () -> DualUsageNotificationManager.repostDelayed(this, 120L));
        }
        finish();
    }

    private void showConnectedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Google Calendar connected")
                .setMessage("Direct read-only event access is enabled. Local Android Calendar remains a fallback.")
                .setNegativeButton("Done", (dialog, which) -> finish())
                .setPositiveButton("Disconnect", (dialog, which) ->
                        GoogleCalendarAuthorization.revoke(this,
                                (success, message) -> runOnUiThread(() -> {
                                    Toast.makeText(this, message,
                                            success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                                    finish();
                                })))
                .setOnCancelListener(dialog -> finish())
                .show();
    }
}
