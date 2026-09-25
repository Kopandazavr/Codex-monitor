package dev.kopandazavr.codexmonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
    private boolean startMonitorAfterNotificationPermission;
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
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (granted) ensureNotificationFeatureDefault();
            if (granted && this.startMonitorAfterNotificationPermission) {
                this.startMonitorAfterNotificationPermission = false;
                enableLiveMonitor();
                return;
            }
            this.startMonitorAfterNotificationPermission = false;
        }
        render();
    }

    private void render() {
        if (this.content == null) return;
        ensureNotificationFeatureDefault();
        this.content.removeAllViews();

        TextView readiness = Ui.text(this, SetupReadiness.requiredSummary(this),
                14.0f, Ui.secondaryText(this.dark));
        LinearLayout.LayoutParams readinessParams = new LinearLayout.LayoutParams(-1, -2);
        readinessParams.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 4));
        this.content.addView(readiness, readinessParams);

        addSectionHeader("Required");
        RoundedLinearLayout required = Ui.seslRowCard(this, this.dark);
        addAccountRow(required);

        String notificationState = notificationSummary();
        CardItemView notifications = Ui.actionRow(this, "Notifications", notificationState,
                R.drawable.ic_oui_notification, view -> requestNotificationAccess(false));
        setMatchingTextColor(notifications, notificationState,
                SetupReadiness.notificationsAllowed(this) ? statusGreen() : STATUS_YELLOW);
        addSetupRow(required, notifications, true);

        boolean calendarConnected = GoogleCalendarAuthorization.isConnected(this);
        String calendarState = calendarSummary();
        CardItemView calendar = Ui.actionRow(this, "Google Calendar", calendarState,
                R.drawable.ic_oui_calendar_week, view -> requestCalendarAccess());
        setMatchingTextColor(calendar, calendarState,
                calendarConnected ? statusGreen() : STATUS_YELLOW);
        addSetupRow(required, calendar, true);

        boolean overlayAllowed = IdleReminderOverlayService.canDraw(this);
        String overlayState = overlayAllowed ? "Allowed" : "Tap to allow";
        CardItemView overlay = Ui.actionRow(this, "Completion overlay", overlayState,
                R.drawable.ic_oui_notification, view -> requestOverlayAccess());
        setMatchingTextColor(overlay, overlayState,
                overlayAllowed ? statusGreen() : STATUS_YELLOW);
        addSetupRow(required, overlay, true);

        boolean exactAlarmAllowed = SetupReadiness.exactAlarmAllowed(this);
        String alarmState = exactAlarmAllowed ? "Allowed" : "Tap to allow";
        CardItemView alarms = Ui.actionRow(this, "Alarms & reminders", alarmState,
                R.drawable.ic_oui_alarm, view -> requestExactAlarmAccess());
        setMatchingTextColor(alarms, alarmState,
                exactAlarmAllowed ? statusGreen() : STATUS_YELLOW);
        addSetupRow(required, alarms, false);
        this.content.addView(required, sectionCardParams());

        String monitorState = monitorSummary();
        RoundedLinearLayout monitorCard = Ui.seslRowCard(this, this.dark);
        CardItemView monitor = Ui.actionRow(this, "Live monitor", monitorState,
                R.drawable.ic_oui_time, view -> enableLiveMonitor());
        setMatchingTextColor(monitor, monitorState,
                NowBarManager.isActive(this) ? statusGreen() : STATUS_YELLOW);
        addSetupRow(monitorCard, monitor, false);
        LinearLayout.LayoutParams monitorParams = sectionCardParams();
        monitorParams.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        this.content.addView(monitorCard, monitorParams);

        addSectionHeader("Optional");
        RoundedLinearLayout optional = Ui.seslRowCard(this, this.dark);
        boolean localAllowed = SetupReadiness.localCalendarAllowed(this);
        String localState = localAllowed ? "Allowed" : "Tap to allow fallback";
        CardItemView localCalendar = Ui.actionRow(this, "Local Calendar fallback", localState,
                R.drawable.ic_oui_calendar_week, view -> requestLocalCalendarAccess());
        setMatchingTextColor(localCalendar, localState,
                localAllowed ? statusGreen() : STATUS_YELLOW);
        addSetupRow(optional, localCalendar, false);
        this.content.addView(optional, sectionCardParams());

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

    private void addAccountRow(RoundedLinearLayout setup) {
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

        View divider = new View(this);
        divider.setBackgroundColor(Ui.divider(this.dark));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, 1);
        dividerParams.setMargins(Ui.dp(this, 14), 0, 0, 0);
        setup.addView(divider, dividerParams);
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

    private String monitorSummary() {
        if (NowBarManager.isActive(this)) return "Active";
        if (QuickSetupPreferences.shouldStartMonitor(this)) {
            return "Waiting for the first usage refresh";
        }
        return "Tap to keep limits and processes live";
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

    private void requestNotificationAccess(boolean forMonitor) {
        if (SetupReadiness.notificationsAllowed(this)) {
            ensureNotificationFeatureDefault();
            if (forMonitor) enableLiveMonitor();
            else Toast.makeText(this, "Notifications are already allowed.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            this.startMonitorAfterNotificationPermission = forMonitor;
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
        if (!hasNotificationPermission() || ResetAlertPreferences.hasExplicitStyle(this)) return;
        ResetAlertPreferences.save(this, ResetAlertPreferences.STYLE_NOTIFICATION,
                ResetAlertPreferences.getMetric(this), ResetAlertPreferences.getThreshold(this));
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

    private void enableLiveMonitor() {
        if (NowBarManager.isActive(this)) {
            Toast.makeText(this, "Live monitor is already active.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!SecureTokenStore.isSignedIn(this)) {
            Toast.makeText(this, "Connect ChatGPT first.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!hasNotificationPermission()) {
            requestNotificationAccess(true);
            return;
        }
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(this);
        if (snapshot != null && (snapshot.fiveHour != null || snapshot.longWindow() != null)
                && NowBarManager.start(this)) {
            QuickSetupPreferences.clearMonitorStart(this);
            DualUsageNotificationManager.repostDelayed(this, 150L);
            Toast.makeText(this, "Live monitor enabled.", Toast.LENGTH_SHORT).show();
            render();
            return;
        }
        QuickSetupPreferences.requestMonitorStart(this);
        RefreshScheduler.scheduleImmediate(this);
        Toast.makeText(this,
                "Loading your usage once; the live monitor will start automatically.",
                Toast.LENGTH_LONG).show();
        render();
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
