package dev.kopandazavr.codexmonitor;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.common.AccountPicker;
import java.util.Collections;

/** Visible/recoverable/revocable Google Calendar authorization trampoline. */
public final class GoogleCalendarAuthorizationActivity extends AppCompatActivity {
    private static final int REQUEST_CHOOSE_ACCOUNT = 8611;
    private static final int REQUEST_AUTHORIZE = 8612;
    private static final int REQUEST_ADD_ACCOUNT = 8613;
    private static final long TRANSIENT_RETRY_DELAY_MS = 1_200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean flowStarted;
    private boolean transientRetryUsed;
    private Account chosenAccount;
    private String flowContainerId = "";

    @Override
    protected void onCreate(Bundle state) {
        Ui.applySelectedTheme(this);
        super.onCreate(state);
        AccountContainerStore.ensureInitialized(this);
        this.flowContainerId = state == null ? "" : state.getString("flow_container_id", "");
        if (this.flowContainerId == null || this.flowContainerId.isEmpty()
                || AccountContainerStore.find(this, this.flowContainerId) == null) {
            String requested = getIntent() == null ? "" :
                    getIntent().getStringExtra(OAuthService.EXTRA_CONTAINER_ID);
            requested = requested == null ? "" : requested.trim();
            this.flowContainerId = AccountContainerStore.find(this, requested) == null
                    ? AccountContainerStore.selectedId(this) : requested;
        }
        if (GoogleCalendarAuthorization.isConnected(this, this.flowContainerId)) {
            showConnectedDialog();
        } else {
            showConnectionChoice();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("flow_container_id", this.flowContainerId);
        super.onSaveInstanceState(outState);
    }

    private void showConnectionChoice() {
        AccountContainerStore.Account container =
                AccountContainerStore.find(this, this.flowContainerId);
        String name = container == null ? "this account" : container.name;
        new AlertDialog.Builder(this)
                .setTitle("Connect Google Calendar")
                .setMessage("Choose a Google account for Calendar in " + name
                        + ". You can use an account already on this phone or add another one.")
                .setPositiveButton("Choose account", (dialog, which) -> chooseExistingAccount())
                .setNeutralButton("Add Google account", (dialog, which) -> addGoogleAccount())
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void chooseExistingAccount() {
        try {
            AccountPicker.AccountChooserOptions options =
                    new AccountPicker.AccountChooserOptions.Builder()
                            .setAllowableAccountsTypes(Collections.singletonList(
                                    GoogleCalendarAuthorization.GOOGLE_ACCOUNT_TYPE))
                            .setAlwaysShowAccountPicker(true)
                            .build();
            startActivityForResult(AccountPicker.newChooseAccountIntent(options),
                    REQUEST_CHOOSE_ACCOUNT);
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Could not open Google account chooser.",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void addGoogleAccount() {
        try {
            Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT)
                    .putExtra(Settings.EXTRA_ACCOUNT_TYPES,
                            new String[]{GoogleCalendarAuthorization.GOOGLE_ACCOUNT_TYPE});
            startActivityForResult(intent, REQUEST_ADD_ACCOUNT);
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Could not open Google account settings.",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void begin(Account account) {
        if (flowStarted || account == null) return;
        this.chosenAccount = account;
        flowStarted = true;
        GoogleCalendarAuthorization.beginInteractive(
                this, this.flowContainerId, REQUEST_AUTHORIZE, account,
                (success, message) -> runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                        GoogleCalendarProcessSource.forceRefresh(this, this.flowContainerId,
                                () -> DualUsageNotificationManager.repostDelayed(
                                        this, this.flowContainerId, 120L));
                        finish();
                    } else if (!isFinishing()
                            && !message.startsWith("Allow Calendar access")) {
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
        if (requestCode == REQUEST_CHOOSE_ACCOUNT) {
            if (resultCode != RESULT_OK || data == null) {
                finish();
                return;
            }
            String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
            if (accountName == null || accountName.trim().isEmpty()) {
                Toast.makeText(this, "No Google account was selected.",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            begin(new Account(accountName.trim(),
                    accountType == null || accountType.trim().isEmpty()
                            ? GoogleCalendarAuthorization.GOOGLE_ACCOUNT_TYPE
                            : accountType.trim()));
            return;
        }
        if (requestCode == REQUEST_ADD_ACCOUNT) {
            if (!isFinishing()) chooseExistingAccount();
            return;
        }
        if (requestCode != REQUEST_AUTHORIZE) return;
        GoogleCalendarAuthorization.AuthOutcome outcome =
                GoogleCalendarAuthorization.consumeInteractiveResult(
                        this, this.flowContainerId, resultCode, data);
        if (outcome.success) {
            Toast.makeText(this, outcome.message, Toast.LENGTH_SHORT).show();
            GoogleCalendarProcessSource.forceRefresh(this, this.flowContainerId,
                    () -> DualUsageNotificationManager.repostDelayed(
                            this, this.flowContainerId, 120L));
            finish();
            return;
        }
        if (scheduleTransientRetry("activity_result")) return;
        Toast.makeText(this, outcome.message, Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean scheduleTransientRetry(String source) {
        if (transientRetryUsed || chosenAccount == null
                || !GoogleCalendarAuthorization.shouldRetryTransient(
                        this, this.flowContainerId)) {
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
            begin(chosenAccount);
        }, TRANSIENT_RETRY_DELAY_MS);
        return true;
    }

    private void showConnectedDialog() {
        AccountContainerStore.Account container =
                AccountContainerStore.find(this, this.flowContainerId);
        String localName = container == null ? "this account" : container.name;
        String googleName = GoogleCalendarAuthorization.accountName(
                this, this.flowContainerId);
        String detail = googleName.isEmpty()
                ? "Direct read-only event access is enabled for " + localName + "."
                : googleName + " supplies read-only Calendar data for " + localName + ".";
        new AlertDialog.Builder(this)
                .setTitle("Google Calendar connected")
                .setMessage(detail + " Local Android Calendar remains a fallback.")
                .setNegativeButton("Done", (dialog, which) -> finish())
                .setPositiveButton("Disconnect", (dialog, which) ->
                        GoogleCalendarAuthorization.revoke(
                                this, this.flowContainerId,
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
