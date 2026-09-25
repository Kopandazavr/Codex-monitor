package dev.kopandazavr.codexmonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import dev.oneuiproject.oneui.widget.CardItemView;
import dev.oneuiproject.oneui.widget.RoundedLinearLayout;

/** One-page first-run setup focused on the controls needed to make Codex Monitor useful quickly. */
public final class OnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_AUTH_RETURN = "oauth_return";
    public static final String EXTRA_PERMISSIONS_CONNECTIONS = "permissions_connections";
    private static final int REQUEST_NOTIFICATIONS = 8601;
    private static final int STATUS_GREEN_LIGHT = 0xFF16843D;
    private static final int STATUS_GREEN_DARK = 0xFF6EDC8C;
    private static final int STATUS_RED_LIGHT = 0xFFD32F2F;
    private static final int STATUS_RED_DARK = 0xFFFF6B6B;
    private static final int STATUS_YELLOW = 0xFFFFC107;

    private LinearLayout content;
    private Button doneButton;
    private boolean dark;
    private boolean receiverRegistered;
    private boolean oauthRequested;
    private boolean settingsEntry;
    private static final int RECOMMENDED_NONE = 0;
    private static final int RECOMMENDED_BATTERY = 1;
    private static final int RECOMMENDED_NEVER_SLEEPING = 2;

    private int pendingRecommendedConfirmation = RECOMMENDED_NONE;
    private String authMessage = "";
    private String lastLaunchedAuthUrl = "";

    private final BroadcastReceiver authReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (AppConstants.ACTION_OAUTH_READY.equals(action)) {
                String url = intent.getStringExtra(AppConstants.EXTRA_AUTH_URL);
                if (url != null && !url.isEmpty()) {
                    authMessage = "ChatGPT sign-in is open in your browser.";
                    render();
                    openAuthUrl(url);
                }
                return;
            }
            if (AppConstants.ACTION_OAUTH_RESULT.equals(action)) {
                oauthRequested = false;
                boolean success = intent.getBooleanExtra(AppConstants.EXTRA_SUCCESS, false);
                String message = intent.getStringExtra(AppConstants.EXTRA_MESSAGE);
                if (success || SecureTokenStore.isSignedIn(OnboardingActivity.this)) {
                    authMessage = "ChatGPT connected.";
                    RefreshScheduler.scheduleImmediate(OnboardingActivity.this);
                } else {
                    authMessage = message == null || message.trim().isEmpty()
                            ? "Sign-in did not complete. Please try again."
                            : message;
                    Toast.makeText(OnboardingActivity.this, authMessage, Toast.LENGTH_LONG).show();
                }
                render();
            }
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        Ui.applySelectedTheme(this);
        super.onCreate(bundle);
        this.settingsEntry =
                getIntent().getBooleanExtra(EXTRA_PERMISSIONS_CONNECTIONS, false);
        if (AppPreferences.isOnboardingComplete(this) && !this.settingsEntry) {
            openMain();
            return;
        }
        this.dark = Ui.isDark(this);
        installStaticLayout();
        this.oauthRequested = AppPreferences.isOAuthPending(this);
        if (getIntent().getBooleanExtra(EXTRA_AUTH_RETURN, false)
                && !SecureTokenStore.isSignedIn(this)) {
            this.authMessage = "Sign-in did not complete. You can safely try again.";
        }
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        this.settingsEntry = intent.getBooleanExtra(
                EXTRA_PERMISSIONS_CONNECTIONS, this.settingsEntry);
        this.oauthRequested = AppPreferences.isOAuthPending(this);
        if (SecureTokenStore.isSignedIn(this)) {
            this.authMessage = "ChatGPT connected.";
            RefreshScheduler.scheduleImmediate(this);
        } else if (intent.getBooleanExtra(EXTRA_AUTH_RETURN, false)) {
            this.authMessage = "Sign-in did not complete. You can safely try again.";
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.content != null) render();
        if (this.pendingRecommendedConfirmation != RECOMMENDED_NONE) {
            int pending = this.pendingRecommendedConfirmation;
            this.pendingRecommendedConfirmation = RECOMMENDED_NONE;
            showRecommendedConfirmation(pending);
        }
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConstants.ACTION_OAUTH_READY);
        filter.addAction(AppConstants.ACTION_OAUTH_RESULT);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(this.authReceiver, filter, AppConstants.INTERNAL_PERMISSION, null,
                        Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(this.authReceiver, filter, AppConstants.INTERNAL_PERMISSION, null);
            }
            this.receiverRegistered = true;
        } catch (RuntimeException exception) {
            this.receiverRegistered = false;
            this.authMessage = "Sign-in updates are unavailable: " + safeMessage(exception);
            Toast.makeText(this, this.authMessage, Toast.LENGTH_LONG).show();
            render();
        }
    }

    @Override
    protected void onStop() {
        if (this.receiverRegistered) {
            try {
                unregisterReceiver(this.authReceiver);
            } catch (RuntimeException ignored) {
            }
            this.receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQUEST_NOTIFICATIONS && granted) {
            ensureNotificationFeatureDefault();
            NowBarManager.ensureAlwaysOn(this);
        }
        render();
    }

    private void render() {
        if (this.content == null) return;
        ensureNotificationFeatureDefault();
        this.content.removeAllViews();

        int readinessStatus = SetupReadiness.overallStatus(this);
        TextView readiness = Ui.text(this, SetupReadiness.requiredSummary(this),
                14.0f, readinessForeground(readinessStatus));
        readiness.setGravity(Gravity.CENTER);
        readiness.setPadding(Ui.dp(this, 12), Ui.dp(this, 5),
                Ui.dp(this, 12), Ui.dp(this, 5));
        GradientDrawable readinessPill = new GradientDrawable();
        readinessPill.setCornerRadius(Ui.dp(this, 16));
        readinessPill.setColor(readinessBackground(readinessStatus));
        readiness.setBackground(readinessPill);
        LinearLayout.LayoutParams readinessParams =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, -2);
        readinessParams.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 7));
        this.content.addView(readiness, readinessParams);

        addSectionHeader("Required");

        RoundedLinearLayout account = Ui.seslRowCard(this, this.dark);
        addAccountRow(account, false);
        LinearLayout.LayoutParams accountParams = sectionCardParams();
        accountParams.setMargins(0, 0, 0, Ui.dp(this, 6));
        this.content.addView(account, accountParams);

        RoundedLinearLayout required = Ui.seslRowCard(this, this.dark);
        String notificationState = notificationSummary();
        CardItemView notifications = Ui.actionRow(this, "Notifications", notificationState,
                R.drawable.ic_oui_notification, view -> requestNotificationAccess());
        setMatchingTextColor(notifications, notificationState,
                SetupReadiness.notificationsAllowed(this) ? statusGreen() : statusRed());
        addSetupRow(required, notifications, true);

        boolean calendarConnected = GoogleCalendarAuthorization.isConnected(this);
        String calendarState = calendarSummary();
        CardItemView calendar = Ui.actionRow(this, "Google Calendar", calendarState,
                R.drawable.ic_oui_calendar_week, view -> requestCalendarAccess());
        setMatchingTextColor(calendar, calendarState,
                calendarConnected ? statusGreen() : statusRed());
        addSetupRow(required, calendar, true);

        boolean overlayAllowed = IdleReminderOverlayService.canDraw(this);
        String overlayState = overlayAllowed ? "Allowed" : "Tap to allow";
        CardItemView overlay = Ui.actionRow(this, "Completion overlay", overlayState,
                R.drawable.ic_oui_notification, view -> requestOverlayAccess());
        setMatchingTextColor(overlay, overlayState,
                overlayAllowed ? statusGreen() : statusRed());
        addSetupRow(required, overlay, true);

        boolean exactAlarmAllowed = SetupReadiness.exactAlarmAllowed(this);
        String alarmState = exactAlarmAllowed ? "Allowed" : "Tap to allow";
        CardItemView alarms = Ui.actionRow(this, "Alarms & reminders", alarmState,
                R.drawable.ic_oui_alarm, view -> requestExactAlarmAccess());
        setMatchingTextColor(alarms, alarmState,
                exactAlarmAllowed ? statusGreen() : statusRed());
        addSetupRow(required, alarms, false);
        this.content.addView(required, sectionCardParams());

        addSectionHeader("Optional");
        RoundedLinearLayout optional = Ui.seslRowCard(this, this.dark);
        boolean localAllowed = SetupReadiness.localCalendarAllowed(this);
        String localState = localAllowed ? "Allowed" : "Tap to allow fallback";
        CardItemView localCalendar = Ui.actionRow(this, "Local Calendar fallback", localState,
                R.drawable.ic_oui_calendar_week, view -> requestLocalCalendarAccess());
        setMatchingTextColor(localCalendar, localState,
                localAllowed ? statusGreen() : Ui.secondaryText(this.dark));
        addSetupRow(optional, localCalendar, false);
        this.content.addView(optional, sectionCardParams());

        addSectionHeader("Recommended");
        RoundedLinearLayout recommended = Ui.seslRowCard(this, this.dark);
        boolean batteryDone = SetupReadiness.batteryUnrestrictedAcknowledged(this);
        String batteryState = batteryDone ? "Reviewed · Unrestricted"
                : "Tap to set Battery usage → Unrestricted";
        CardItemView battery = Ui.actionRow(this, "Battery usage", batteryState,
                R.drawable.ic_oui_time, view -> requestBatteryUnrestricted());
        setMatchingTextColor(battery, batteryState,
                batteryDone ? statusGreen() : STATUS_YELLOW);
        addSetupRow(recommended, battery, true);

        boolean neverSleepingDone = SetupReadiness.neverSleepingAcknowledged(this);
        String neverSleepingState = neverSleepingDone ? "Reviewed · Never sleeping"
                : "Tap to add to Never sleeping apps";
        CardItemView neverSleeping = Ui.actionRow(this, "Samsung background limits",
                neverSleepingState, R.drawable.ic_oui_time,
                view -> requestNeverSleepingApps());
        setMatchingTextColor(neverSleeping, neverSleepingState,
                neverSleepingDone ? statusGreen() : STATUS_YELLOW);
        addSetupRow(recommended, neverSleeping, false);
        this.content.addView(recommended, sectionCardParams());

        if (this.doneButton != null) this.doneButton.setEnabled(true);
    }

    private void installStaticLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setFitsSystemWindows(true);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 14),
                Ui.dp(this, 18), Ui.dp(this, 18));

        TextView title = Ui.title(this,
                this.settingsEntry ? "Permissions & connections" : "Quick setup", this.dark);
        title.setTextSize(30.0f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), Ui.dp(this, 8));
        root.addView(title, titleParams);

        this.content = new LinearLayout(this);
        this.content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams =
                new LinearLayout.LayoutParams(-1, 0, 1.0f);
        root.addView(this.content, contentParams);

        this.doneButton = Ui.nativePrimaryButton(this,
                this.settingsEntry ? "Done" : "Open Codex Monitor");
        this.doneButton.setOnClickListener(view -> completeAndOpenMain());
        LinearLayout.LayoutParams doneParams =
                new LinearLayout.LayoutParams(-1, Ui.dp(this, 54));
        doneParams.setMargins(0, Ui.dp(this, 8), 0, 0);
        root.addView(this.doneButton, doneParams);

        setContentView(root);
    }

    private void addSetupRow(RoundedLinearLayout setup, CardItemView row, boolean divider) {
        row.setShowBottomDivider(divider);
        setup.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(this, 54)));
    }

    private void addAccountRow(RoundedLinearLayout setup, boolean dividerAfter) {
        boolean signedIn = SecureTokenStore.isSignedIn(this);
        LinearLayout row = Ui.horizontal(this, Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 10), 0);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, "ChatGPT", 15.0f, Ui.mainText(this.dark));
        title.setTypeface(Ui.mediumTypeface(this));
        copy.addView(title);
        TextView summary = Ui.text(this, accountSummary(), 11.5f, Ui.secondaryText(this.dark));
        copy.addView(summary);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));

        Button action = Ui.topAction(this, signedIn ? "Sign out" : "Sign in", this.dark);
        action.setTextSize(13.0f);
        action.setMinimumHeight(0);
        action.setMinHeight(0);
        if (signedIn) action.setTextColor(this.dark ? 0xFFFF6B6B : 0xFFFF3B30);
        action.setOnClickListener(view -> {
            if (SecureTokenStore.isSignedIn(this)) confirmSignOut();
            else startSignIn();
        });
        row.addView(action, new LinearLayout.LayoutParams(-2, Ui.dp(this, 38)));
        setup.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(this, 54)));

        if (dividerAfter) {
            View divider = new View(this);
            divider.setBackgroundColor(Ui.divider(this.dark));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, 1);
            dividerParams.setMargins(Ui.dp(this, 14), 0, 0, 0);
            setup.addView(divider, dividerParams);
        }
    }

    private void addSectionHeader(String title) {
        TextView header = Ui.text(this, title, 13.0f, Ui.secondaryText(this.dark));
        header.setTypeface(Ui.mediumTypeface(this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        this.content.addView(header, params);
    }

    private LinearLayout.LayoutParams sectionCardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, Ui.dp(this, 3));
        return params;
    }

    private int statusGreen() {
        return this.dark ? STATUS_GREEN_DARK : STATUS_GREEN_LIGHT;
    }

    private int statusRed() {
        return this.dark ? STATUS_RED_DARK : STATUS_RED_LIGHT;
    }

    private int readinessForeground(int status) {
        if (status == SetupReadiness.STATUS_REQUIRED_MISSING) return statusRed();
        if (status == SetupReadiness.STATUS_RECOMMENDED_MISSING) {
            return this.dark ? 0xFFFFD54F : 0xFF9A6A00;
        }
        return statusGreen();
    }

    private int readinessBackground(int status) {
        if (status == SetupReadiness.STATUS_REQUIRED_MISSING) {
            return this.dark ? 0x443D1010 : 0x22D32F2F;
        }
        if (status == SetupReadiness.STATUS_RECOMMENDED_MISSING) {
            return this.dark ? 0x443D3210 : 0x22FFC107;
        }
        return this.dark ? 0x4420442A : 0x2216843D;
    }

    private void setMatchingTextColor(View view, String text, int color) {
        if (view instanceof TextView) {
            TextView label = (TextView) view;
            if (text.contentEquals(label.getText())) label.setTextColor(color);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                setMatchingTextColor(group.getChildAt(index), text, color);
            }
        }
    }

    private void setStatusTokenColor(View view, String text, String token, int color) {
        if (view instanceof TextView) {
            TextView label = (TextView) view;
            if (text.contentEquals(label.getText())) {
                int start = text.indexOf(token);
                if (start >= 0) {
                    SpannableString styled = new SpannableString(text);
                    styled.setSpan(new ForegroundColorSpan(color), start, start + token.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    label.setText(styled);
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                setStatusTokenColor(group.getChildAt(index), text, token, color);
            }
        }
    }

    private String accountSummary() {
        if (!SecureTokenStore.isSignedIn(this)) {
            return AppPreferences.isOAuthPending(this) ? "Sign-in in progress" : "Not connected";
        }
        AuthTokens tokens = SecureTokenStore.load(this);
        return tokens != null && !tokens.email.isEmpty()
                ? "Connected · " + tokens.email : "Connected";
    }

    private String notificationSummary() {
        return SetupReadiness.notificationsAllowed(this)
                ? "Allowed" : "Tap to allow";
    }

    private String calendarSummary() {
        return GoogleCalendarAuthorization.statusSummary(this);
    }

    private void startSignIn() {
        if (SecureTokenStore.isSignedIn(this)) {
            Toast.makeText(this, "ChatGPT is already connected.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean resuming = AppPreferences.isOAuthPending(this);
        this.oauthRequested = true;
        this.authMessage = resuming
                ? "Resuming secure ChatGPT sign-in…"
                : "Preparing secure ChatGPT sign-in…";
        render();
        try {
            startForegroundService(new Intent(this, OAuthService.class)
                    .setAction(OAuthService.ACTION_START));
        } catch (RuntimeException exception) {
            this.oauthRequested = false;
            AppPreferences.setOAuthPending(this, false, "");
            this.authMessage = "Could not start sign-in: " + safeMessage(exception);
            Toast.makeText(this, this.authMessage, Toast.LENGTH_LONG).show();
            render();
        }
    }

    private void requestNotificationAccess() {
        if (SetupReadiness.notificationsAllowed(this)) {
            ensureNotificationFeatureDefault();
            Toast.makeText(this, "Notifications are already allowed.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Could not open notification settings.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void ensureNotificationFeatureDefault() {
        if (!hasNotificationPermission()) return;
        ResetNotificationManager.ensureChannel(this);
    }

    private void requestOverlayAccess() {
        if (IdleReminderOverlayService.canDraw(this)) {
            Toast.makeText(this, "Completion overlay access is already allowed.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Could not open overlay permission settings.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestCalendarAccess() {
        Ui.startSecondaryActivity(this, GoogleCalendarAuthorizationActivity.class);
    }

    private void requestLocalCalendarAccess() {
        Ui.startSecondaryActivity(this, CalendarPermissionActivity.class);
    }

    private void requestExactAlarmAccess() {
        if (SetupReadiness.exactAlarmAllowed(this)) {
            Toast.makeText(this, "Alarms & reminders are already allowed.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Could not open Alarms & reminders settings.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setMessage("This removes encrypted ChatGPT tokens and cached usage from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out", (dialog, which) -> {
                    AuthTokens tokens = SecureTokenStore.load(this);
                    SecureTokenStore.clear(this);
                    AppPreferences.clearSnapshot(this);
                    AppPreferences.setOAuthPending(this, false, "");
                    RefreshScheduler.cancelAll(this);
                    ResetAlertScheduler.cancelAll(this);
                    WidgetRenderer.updateAll(this);
                    Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show();
                    render();
                    if (tokens != null) {
                        Context app = getApplicationContext();
                        new Thread(() -> OAuthClient.revokeBestEffort(app, tokens),
                                "codex-sign-out").start();
                    }
                })
                .show();
    }

    private void requestBatteryUnrestricted() {
        this.pendingRecommendedConfirmation = RECOMMENDED_BATTERY;
        Toast.makeText(this,
                "Open Battery, then choose Unrestricted for Codex Monitor.",
                Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException exception) {
            this.pendingRecommendedConfirmation = RECOMMENDED_NONE;
            Toast.makeText(this, "Could not open app battery settings.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestNeverSleepingApps() {
        this.pendingRecommendedConfirmation = RECOMMENDED_NEVER_SLEEPING;
        Intent samsung = new Intent(
                "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY")
                .setPackage("com.samsung.android.lool")
                .putExtra("activity_type", 2);
        try {
            startActivity(samsung);
        } catch (RuntimeException exception) {
            try {
                startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
            } catch (RuntimeException fallback) {
                this.pendingRecommendedConfirmation = RECOMMENDED_NONE;
                Toast.makeText(this, "Could not open Samsung background limits.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showRecommendedConfirmation(int item) {
        final boolean battery = item == RECOMMENDED_BATTERY;
        String title = battery ? "Battery usage checked?" : "Never sleeping apps checked?";
        String message = battery
                ? "Is Codex Monitor set to Unrestricted battery usage?"
                : "Did you add Codex Monitor to Never sleeping apps?";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Not yet", (dialog, which) -> {
                    if (battery) SetupReadiness.setBatteryUnrestrictedAcknowledged(this, false);
                    else SetupReadiness.setNeverSleepingAcknowledged(this, false);
                    render();
                })
                .setPositiveButton("Yes, done", (dialog, which) -> {
                    if (battery) SetupReadiness.setBatteryUnrestrictedAcknowledged(this, true);
                    else SetupReadiness.setNeverSleepingAcknowledged(this, true);
                    render();
                })
                .show();
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openAuthUrl(String url) {
        if (!url.equals(this.lastLaunchedAuthUrl) || hasWindowFocus()) {
            this.lastLaunchedAuthUrl = url;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (RuntimeException exception) {
                this.authMessage = "No browser is available to complete sign-in.";
                Toast.makeText(this, this.authMessage, Toast.LENGTH_LONG).show();
                render();
            }
        }
    }

    private void completeAndOpenMain() {
        cancelPendingSignIn();
        ensureNotificationFeatureDefault();
        NowBarManager.ensureAlwaysOn(this);
        if (this.settingsEntry) {
            finish();
            return;
        }
        AppPreferences.completeOnboarding(this);
        openMain();
    }

    private void cancelPendingSignIn() {
        if (!this.oauthRequested && !AppPreferences.isOAuthPending(this)) return;
        this.oauthRequested = false;
        try {
            startService(new Intent(this, OAuthService.class)
                    .setAction(OAuthService.ACTION_CANCEL_SILENT));
        } catch (RuntimeException ignored) {
        }
        AppPreferences.setOAuthPending(this, false, "");
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
