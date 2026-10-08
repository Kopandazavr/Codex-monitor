package dev.kopandazavr.codexmonitor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;
import dev.kopandazavr.codexmonitor.wear.PhoneWearSync;
import dev.oneuiproject.oneui.layout.ToolbarLayout;
import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Personal-use settings surface.
 *
 * Keep only the controls that are actively useful for operating Codex Monitor. Diagnostics is a
 * normal first-class page; About/Updates/Backup & transfer/Privacy pages were intentionally removed.
 */
public final class SettingsActivity extends AppCompatActivity {
    private static final String EXTRA_PAGE = "settings_page";
    private static final String PAGE_ROOT = "root";
    private static final String PAGE_ACCOUNTS = "accounts";
    private static final String PAGE_NOTIFICATIONS = "notifications";
    private static final String PAGE_NOW_BAR = "now_bar";
    private static final String PAGE_DIAGNOSTICS = "diagnostics";

    static Intent diagnosticsIntent(Context context) {
        return pageIntent(context, PAGE_DIAGNOSTICS);
    }

    private static Intent pageIntent(Context context, String page) {
        return new Intent(context, SettingsActivity.class).putExtra(EXTRA_PAGE, page);
    }

    @Override
    protected void onCreate(Bundle bundle) {
        Ui.applySelectedTheme(this);
        super.onCreate(bundle);
        AppPreferences.setAppStyle(this, WidgetOptions.SURFACE_ONE_UI);
        setContentView(R.layout.activity_settings);
        String page = normalizePage(getIntent().getStringExtra(EXTRA_PAGE));
        ToolbarLayout toolbar = findViewById(R.id.settings_toolbar_layout);
        Ui.configureReachToolbar(toolbar, pageTitle(page), true);
        if (PAGE_DIAGNOSTICS.equals(page)) {
            configureDiagnosticsToolbar(toolbar);
        }
        if (bundle == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings_fragment, SettingsFragment.newInstance(page))
                    .commit();
        }
    }

    private static String normalizePage(String page) {
        // 2.19 retires the standalone alert-notifications page. Legacy internal intents that
        // still name it fall back to the Settings root rather than reviving removed controls.
        if (PAGE_ACCOUNTS.equals(page) || PAGE_NOW_BAR.equals(page)
                || PAGE_DIAGNOSTICS.equals(page)) {
            return page;
        }
        return PAGE_ROOT;
    }

    private static String pageTitle(String page) {
        switch (page) {
            case PAGE_ACCOUNTS:
                return "Accounts";
            case PAGE_NOTIFICATIONS:
                return "Notifications";
            case PAGE_NOW_BAR:
                return "Live monitor";
            case PAGE_DIAGNOSTICS:
                return "Diagnostics";
            case PAGE_ROOT:
            default:
                return "Settings";
        }
    }

    private void configureDiagnosticsToolbar(ToolbarLayout toolbar) {
        String compactIdentity = diagnosticBuildIdentity();
        SpannableString expandedSubtitle = new SpannableString(
                "Version " + BuildConfig.VERSION_NAME + " · Build " + BuildConfig.VERSION_CODE);
        expandedSubtitle.setSpan(new ForegroundColorSpan(Ui.secondaryText(Ui.isDark(this))),
                0, expandedSubtitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableString collapsedSubtitle = new SpannableString(compactIdentity);
        collapsedSubtitle.setSpan(new ForegroundColorSpan(Ui.secondaryText(Ui.isDark(this))),
                0, collapsedSubtitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        collapsedSubtitle.setSpan(new RelativeSizeSpan(0.88f),
                0, collapsedSubtitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        toolbar.setTitle("Diagnostics", "Diagnostics");
        toolbar.setSubtitle(expandedSubtitle);
        toolbar.setCollapsedSubtitle(collapsedSubtitle);
    }

    private static String diagnosticBuildIdentity() {
        return BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
    }

    @SuppressLint("FindPreferenceKeyNotFound")
    public static final class SettingsFragment extends PreferenceFragmentCompat {
        private static final String ARG_PAGE = "page";
        private static final int REQUEST_EXPORT_DIAGNOSTICS = 9203;

        private String page = PAGE_ROOT;
        private PreferenceCategory accountsCategory;
        private Preference expiryTimesPreference;
        private Preference testNotificationPreference;
        private PreferenceCategory notificationLowUsageCategory;
        private PreferenceCategory notificationResetCreditCategory;
        private PreferenceCategory notificationTroubleshootingCategory;
        private SwitchPreferenceCompat nowBarMonitorPreference;
        private SwitchPreferenceCompat nowBarAutoStartPreference;
        private SwitchPreferenceCompat nowBarAcceleratedPreference;
        private ListPreference nowBarDisplayModePreference;
        private ListPreference nowBarPercentModePreference;
        private ListPreference nowBarMetricPreference;
        private ListPreference nowBarThresholdPreference;
        private Preference nowBarPermissionPreference;

        static SettingsFragment newInstance(String page) {
            SettingsFragment fragment = new SettingsFragment();
            Bundle arguments = new Bundle();
            arguments.putString(ARG_PAGE, normalizePage(page));
            fragment.setArguments(arguments);
            return fragment;
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("codex_monitor_settings_v1");
            page = normalizePage(getArguments() == null
                    ? null : getArguments().getString(ARG_PAGE));
            switch (page) {
                case PAGE_ACCOUNTS:
                    addPreferencesFromResource(R.xml.preferences_settings_accounts);
                    bindAccounts();
                    break;
                case PAGE_NOTIFICATIONS:
                    addPreferencesFromResource(R.xml.preferences_settings_notifications);
                    bindNotifications();
                    break;
                case PAGE_NOW_BAR:
                    addPreferencesFromResource(R.xml.preferences_settings_now_bar);
                    bindNowBar();
                    break;
                case PAGE_DIAGNOSTICS:
                    addPreferencesFromResource(R.xml.preferences_settings_diagnostics);
                    bindDiagnostics();
                    break;
                case PAGE_ROOT:
                default:
                    addPreferencesFromResource(R.xml.preferences_settings);
                    bindRoot();
                    break;
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode != REQUEST_EXPORT_DIAGNOSTICS
                    || resultCode != Activity.RESULT_OK
                    || data == null
                    || data.getData() == null) {
                return;
            }
            finishDiagnosticExport(data.getData());
        }

        @Override
        public void onViewCreated(View view, Bundle bundle) {
            super.onViewCreated(view, bundle);
            view.setBackgroundColor(Ui.background(requireContext(), Ui.isDark(requireContext())));
        }

        @Override
        public void onResume() {
            super.onResume();
            if (PAGE_ROOT.equals(page)) {
                updateRootSummaries();
            } else if (PAGE_ACCOUNTS.equals(page)) {
                refreshAccountsPage();
            } else if (PAGE_NOTIFICATIONS.equals(page)) {
                updatePermissionSummary();
            } else if (PAGE_NOW_BAR.equals(page)) {
                if (!NowBarManager.refreshActiveNotificationContract(requireContext())) {
                    Toast.makeText(requireContext(),
                            "Could not refresh the live notification; monitoring will retry automatically.",
                            Toast.LENGTH_LONG).show();
                }
                updateNowBarSummary();
            } else if (PAGE_DIAGNOSTICS.equals(page)) {
                updateDiagnosticSummary();
            }
        }

        private void bindRoot() {
            Preference setup = findPreference("permissions_connections");
            setup.setOnPreferenceClickListener(preference -> {
                DiagnosticLog.info(requireContext(), "user", "permissions_connections_opened");
                startActivity(new Intent(requireContext(), OnboardingActivity.class)
                        .putExtra(OnboardingActivity.EXTRA_PERMISSIONS_CONNECTIONS, true));
                return true;
            });

            bindPageLink("settings_accounts", PAGE_ACCOUNTS);
            bindPageLink("settings_now_bar", PAGE_NOW_BAR);
            bindPageLink("settings_diagnostics", PAGE_DIAGNOSTICS);
            updateRootSummaries();
        }

        private void bindPageLink(String key, String targetPage) {
            Preference preference = findPreference(key);
            if (preference == null) return;
            preference.setOnPreferenceClickListener(ignored -> {
                DiagnosticLog.info(requireContext(), "user", "settings_page_opened",
                        "page", targetPage);
                startActivity(pageIntent(requireContext(), targetPage));
                return true;
            });
        }

        private void bindAccounts() {
            accountsCategory = findPreference("accounts_list");
            Preference add = findPreference("account_add");
            if (add != null) {
                add.setOnPreferenceClickListener(preference -> {
                    AccountSwitcherView.promptCreate(
                            (AppCompatActivity) requireActivity(),
                            new AccountSwitcherView.Listener() {
                                @Override
                                public void onAccountSelected(AccountContainerStore.Account account) {
                                    refreshAccountsPage();
                                }

                                @Override
                                public void onAccountCreated(AccountContainerStore.Account account) {
                                    refreshAccountsPage();
                                    startActivity(new Intent(requireContext(),
                                            OnboardingActivity.class));
                                }
                            });
                    return true;
                });
            }
            refreshAccountsPage();
        }

        private void refreshAccountsPage() {
            if (!PAGE_ACCOUNTS.equals(page) || getContext() == null
                    || accountsCategory == null) {
                return;
            }
            Context context = requireContext();
            accountsCategory.removeAll();
            String selectedId = AccountContainerStore.selectedId(context);
            for (AccountContainerStore.Account account : AccountContainerStore.all(context)) {
                Preference row = new Preference(context) {
                    @Override
                    public void onBindViewHolder(PreferenceViewHolder holder) {
                        super.onBindViewHolder(holder);
                        TextView pill = (TextView) holder.findViewById(
                                R.id.account_visibility_pill);
                        if (pill == null) return;
                        boolean shown = PersistentCardVisibility.isShown(context, account.id);
                        pill.setText(shown ? "Shown" : "Hidden");
                        pill.setContentDescription(account.name + " persistent notification "
                                + (shown ? "shown" : "hidden") + "; tap to toggle");
                        GradientDrawable background = new GradientDrawable();
                        background.setCornerRadius(Ui.dp(context, 20));
                        background.setColor(shown
                                ? Ui.accent(context, Ui.isDark(context)) : 0xFF62666E);
                        pill.setBackground(background);
                        pill.setTextColor(0xFFFFFFFF);
                        pill.setOnClickListener(view -> {
                            if (PersistentCardVisibility.setShown(context, account.id, !shown)) {
                                refreshAccountsPage();
                            }
                        });
                    }
                };
                row.setWidgetLayoutResource(R.layout.preference_account_visibility_widget);
                row.setPersistent(false);
                SpannableString title = new SpannableString("● " + account.name);
                title.setSpan(new ForegroundColorSpan(
                                AccountContainerStore.accentColor(account)),
                        0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                row.setTitle(title);
                row.setSummary(accountSummary(context, account, selectedId));
                row.setOnPreferenceClickListener(preference -> {
                    showAccountActions(account.id);
                    return true;
                });
                accountsCategory.addPreference(row);
            }
        }

        private String accountSummary(Context context, AccountContainerStore.Account account,
                String selectedId) {
            List<String> parts = new ArrayList<>();
            if (account.id.equals(selectedId)) parts.add("Selected");
            AuthTokens chatgpt = SecureTokenStore.load(context, account.id);
            parts.add(chatgpt == null
                    ? "ChatGPT not connected"
                    : (chatgpt.email == null || chatgpt.email.trim().isEmpty()
                            ? "ChatGPT connected" : chatgpt.email.trim()));
            String google = GoogleCalendarAuthorization.accountName(context, account.id);
            parts.add(GoogleCalendarAuthorization.isConnected(context, account.id)
                    ? (google.isEmpty() ? "Calendar connected" : google)
                    : "Calendar not connected");
            if (LocalCalendarFallbackOwner.isOwner(context, account.id)) {
                parts.add("Local fallback");
            }
            return String.join(" · ", parts);
        }

        private void showAccountActions(String accountId) {
            Context context = requireContext();
            AccountContainerStore.Account account =
                    AccountContainerStore.find(context, accountId);
            if (account == null) {
                refreshAccountsPage();
                return;
            }
            List<String> labels = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            if (!account.id.equals(AccountContainerStore.selectedId(context))) {
                labels.add("Select");
                actions.add(() -> {
                    ForegroundAccountCoordinator.select(requireContext(), account.id);
                    refreshAccountsPage();
                });
            }

            labels.add("Rename");
            actions.add(() -> showRenameAccountDialog(account.id));

            labels.add("Change color");
            actions.add(() -> showAccountColorDialog(account.id));

            labels.add("Connections & permissions");
            actions.add(() -> {
                ForegroundAccountCoordinator.select(requireContext(), account.id);
                startActivity(new Intent(requireContext(), OnboardingActivity.class)
                        .putExtra(OnboardingActivity.EXTRA_PERMISSIONS_CONNECTIONS, true));
            });

            AccountContainerStore.Account fallbackOwner =
                    LocalCalendarFallbackOwner.owner(context);
            labels.add(LocalCalendarFallbackOwner.isOwner(context, account.id)
                    ? "Local Calendar fallback · assigned here"
                    : "Move Local Calendar fallback here"
                    + (fallbackOwner == null ? "" : " (from " + fallbackOwner.name + ")"));
            actions.add(() -> showLocalFallbackTransfer(account.id));

            if (AccountContainerStore.all(context).size() > 1) {
                labels.add("Remove Account");
                actions.add(() -> showRemoveAccountDialog(account.id));
            }

            new AlertDialog.Builder(context)
                    .setTitle(account.name)
                    .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                        if (which >= 0 && which < actions.size()) actions.get(which).run();
                    })
                    .setNegativeButton("Done", null)
                    .show();
        }

        private void showRenameAccountDialog(String accountId) {
            Context context = requireContext();
            AccountContainerStore.Account account =
                    AccountContainerStore.find(context, accountId);
            if (account == null) return;
            EditText input = new EditText(context);
            input.setSingleLine(true);
            input.setText(account.name);
            input.selectAll();
            int pad = Ui.dp(context, 20);
            android.widget.FrameLayout box = new android.widget.FrameLayout(context);
            box.setPadding(pad, 0, pad, 0);
            box.addView(input, new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle("Rename Account")
                    .setView(box)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String name = input.getText() == null
                                ? "" : input.getText().toString().trim();
                        if (name.isEmpty()) {
                            input.setError("Enter an account name.");
                            return;
                        }
                        if (!AccountContainerStore.rename(context, accountId, name)) {
                            Toast.makeText(context, "Could not rename account.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        dialog.dismiss();
                        refreshAccountsPage();
                    }));
            dialog.show();
        }

        private void showAccountColorDialog(String accountId) {
            Context context = requireContext();
            AccountContainerStore.Account account =
                    AccountContainerStore.find(context, accountId);
            if (account == null) return;
            String[] keys = AccountContainerStore.colorKeys();
            String[] labels = new String[keys.length];
            int selected = 0;
            for (int i = 0; i < keys.length; i++) {
                labels[i] = keys[i].substring(0, 1).toUpperCase(Locale.US)
                        + keys[i].substring(1);
                if (keys[i].equals(account.colorKey)) selected = i;
            }
            final int initial = selected;
            new AlertDialog.Builder(context)
                    .setTitle("Account color")
                    .setSingleChoiceItems(labels, initial, (dialog, which) -> {
                        if (which >= 0 && which < keys.length
                                && AccountContainerStore.setColor(
                                        context, accountId, keys[which])) {
                            dialog.dismiss();
                            refreshAccountsPage();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void showLocalFallbackTransfer(String accountId) {
            Context context = requireContext();
            AccountContainerStore.Account target =
                    AccountContainerStore.find(context, accountId);
            if (target == null) return;
            AccountContainerStore.Account owner = LocalCalendarFallbackOwner.owner(context);
            if (owner != null && owner.id.equals(target.id)) {
                Toast.makeText(context,
                        "Local Calendar fallback is already assigned to " + target.name + ".",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String ownerName = owner == null ? "another account" : owner.name;
            new AlertDialog.Builder(context)
                    .setTitle("Move Local Calendar fallback?")
                    .setMessage("Local calendar fallback can only be assigned to one account. "
                            + "Currently " + ownerName + ". Move to " + target.name + "?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Move", (dialog, which) -> {
                        if (!LocalCalendarFallbackOwner.transfer(context, target.id)) {
                            Toast.makeText(context, "Could not move Local Calendar fallback.",
                                    Toast.LENGTH_LONG).show();
                        }
                        refreshAccountsPage();
                    })
                    .show();
        }

        private void showRemoveAccountDialog(String accountId) {
            Context context = requireContext();
            AccountContainerStore.Account account =
                    AccountContainerStore.find(context, accountId);
            if (account == null || AccountContainerStore.all(context).size() <= 1) return;
            new AlertDialog.Builder(context)
                    .setTitle("Remove " + account.name + "?")
                    .setMessage("This removes this Codex Monitor account, its encrypted ChatGPT "
                            + "credentials, Calendar connection and local account history from "
                            + "this device. Monitoring and notifications for this account will stop.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove", (dialog, which) -> {
                        if (!AccountContainerLifecycle.remove(context, account.id)) {
                            Toast.makeText(context, "Could not remove account.",
                                    Toast.LENGTH_LONG).show();
                        }
                        refreshAccountsPage();
                    })
                    .show();
        }

        private void bindDiagnostics() {
            findPreference("export_diagnostic_logs").setOnPreferenceClickListener(preference -> {
                launchDiagnosticExport();
                return true;
            });
            findPreference("clear_diagnostic_logs").setOnPreferenceClickListener(preference -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Clear diagnostic logs?")
                        .setMessage("This permanently deletes all saved diagnostic events.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Clear", (dialog, which) -> {
                            DiagnosticLog.clear(requireContext());
                            updateDiagnosticSummary();
                            Toast.makeText(requireContext(), "Diagnostic logs cleared.",
                                    Toast.LENGTH_SHORT).show();
                        })
                        .show();
                return true;
            });
            testNotificationPreference = findPreference("notification_test");
            if (testNotificationPreference != null) {
                testNotificationPreference.setOnPreferenceClickListener(preference -> {
                    boolean sent = ResetNotificationManager.sendTestNotification(
                            requireContext());
                    Toast.makeText(requireContext(), sent
                            ? "Test notification sent."
                            : "Enable notifications and allow permission first.",
                            sent ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                    return true;
                });
            }
            Preference testOverlay = findPreference("overlay_test");
            if (testOverlay != null) {
                testOverlay.setOnPreferenceClickListener(preference -> {
                    boolean shown = IdleReminderOverlayService.showTest(requireContext());
                    Toast.makeText(requireContext(), shown
                            ? "Test overlay shown."
                            : "Allow display over other apps first.",
                            shown ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                    return true;
                });
            }
            updateDiagnosticSummary();
            updatePermissionSummary();
        }

        private void updateDiagnosticSummary() {
            if (!PAGE_DIAGNOSTICS.equals(page) || getContext() == null) return;
            DiagnosticLog.Stats stats = DiagnosticLog.stats(requireContext());
            Preference monitorHealth = findPreference("diagnostic_monitor_health");
            if (monitorHealth != null) {
                monitorHealth.setSummary(MonitorHealthDiagnostics.summary(requireContext()));
            }
            Preference export = findPreference("export_diagnostic_logs");
            if (export != null) {
                export.setSummary(DiagnosticLog.formatBytes(stats.bytes));
                export.setEnabled(stats.hasLogs());
            }
            Preference clear = findPreference("clear_diagnostic_logs");
            if (clear != null) clear.setEnabled(stats.hasLogs());
        }

        private void launchDiagnosticExport() {
            DiagnosticLog.Stats stats = DiagnosticLog.stats(requireContext());
            if (!stats.hasLogs()) {
                Toast.makeText(requireContext(), "There are no diagnostic logs to export.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            String filename = "codex-monitor-diagnostics-" + stamp + ".jsonl";
            if (Build.VERSION.SDK_INT >= 29) {
                Uri destination = null;
                try {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/x-ndjson");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS);
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    destination = requireContext().getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (destination == null) {
                        throw new IllegalStateException(
                                "Android could not create the Download file.");
                    }
                    DiagnosticLog.export(requireContext(), destination);
                    values.clear();
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    requireContext().getContentResolver().update(
                            destination, values, null, null);
                    updateDiagnosticSummary();
                    Toast.makeText(requireContext(), "Saved to Download/" + filename,
                            Toast.LENGTH_LONG).show();
                    return;
                } catch (Exception exception) {
                    if (destination != null) {
                        try {
                            requireContext().getContentResolver().delete(
                                    destination, null, null);
                        } catch (RuntimeException ignored) {
                        }
                    }
                    DiagnosticLog.error(requireContext(), "diagnostics",
                            "download_export_failed", exception);
                    Toast.makeText(requireContext(),
                            "Could not save diagnostic logs to Download: "
                                    + MainActivity.safeMessage(exception),
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }

            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/x-ndjson")
                    .putExtra(Intent.EXTRA_TITLE, filename);
            try {
                startActivityForResult(create, REQUEST_EXPORT_DIAGNOSTICS);
            } catch (RuntimeException exception) {
                DiagnosticLog.error(requireContext(), "diagnostics", "export_picker_failed",
                        exception);
                Toast.makeText(requireContext(),
                        "No file picker is available to export diagnostic logs.",
                        Toast.LENGTH_LONG).show();
            }
        }

        private void finishDiagnosticExport(Uri uri) {
            try {
                DiagnosticLog.export(requireContext(), uri);
                updateDiagnosticSummary();
                Toast.makeText(requireContext(),
                        "Diagnostic logs exported. Review the file before sharing it.",
                        Toast.LENGTH_LONG).show();
            } catch (Exception exception) {
                DiagnosticLog.error(requireContext(), "diagnostics", "export_failed", exception);
                Toast.makeText(requireContext(),
                        "Could not export diagnostic logs: "
                                + MainActivity.safeMessage(exception),
                        Toast.LENGTH_LONG).show();
            }
        }

        private void updateRootSummaries() {
            if (!PAGE_ROOT.equals(page) || getContext() == null) return;

            Preference setup = findPreference("permissions_connections");
            if (setup != null) {
                setup.setSummary(SetupReadiness.overallSummary(requireContext()));
            }

            Preference liveMonitor = findPreference("settings_now_bar");
            if (liveMonitor != null) {
                liveMonitor.setSummary("Live usage, processes, role reminders and Samsung controls");
            }

            Preference diagnostics = findPreference("settings_diagnostics");
            if (diagnostics != null) {
                DiagnosticLog.Stats stats = DiagnosticLog.stats(requireContext());
                diagnostics.setSummary("Always on · " + DiagnosticLog.formatBytes(stats.bytes));
            }
        }

        private void bindNotifications() {
            SwitchPreferenceCompat allow = findPreference("notifications_allowed_ui");
            allow.setEnabled(true);
            allow.setChecked(ResetAlertPreferences.enabled(requireContext()));
            allow.setOnPreferenceChangeListener((preference, value) -> {
                setNotificationsEnabled((Boolean) value);
                return true;
            });
            notificationLowUsageCategory = findPreference("notification_low_usage_category");
            notificationResetCreditCategory =
                    findPreference("notification_reset_credit_category");
            notificationTroubleshootingCategory =
                    findPreference("notification_troubleshooting_category");

            ListPreference metric = findPreference("notification_metric_ui");
            metric.setValue(ResetAlertPreferences.getMetric(requireContext()));
            metric.setOnPreferenceChangeListener((preference, value) -> {
                saveAlert(ResetAlertPreferences.getStyle(requireContext()),
                        String.valueOf(value),
                        ResetAlertPreferences.getThreshold(requireContext()));
                return true;
            });

            ListPreference threshold = findPreference("notification_threshold_ui");
            threshold.setValue(String.valueOf(
                    ResetAlertPreferences.getThreshold(requireContext())));
            threshold.setOnPreferenceChangeListener((preference, value) -> {
                saveAlert(ResetAlertPreferences.getStyle(requireContext()),
                        ResetAlertPreferences.getMetric(requireContext()),
                        Integer.parseInt(String.valueOf(value)));
                return true;
            });

            SwitchPreferenceCompat unexpectedRefills = findPreference("unexpected_refills_ui");
            unexpectedRefills.setPersistent(false);
            unexpectedRefills.setChecked(
                    ResetAlertPreferences.unexpectedRefillsEnabled(requireContext()));
            unexpectedRefills.setOnPreferenceChangeListener((preference, value) -> {
                ResetAlertPreferences.setUnexpectedRefillsEnabled(
                        requireContext(), (Boolean) value);
                return true;
            });

            SwitchPreferenceCompat resetCreditIncreases =
                    findPreference("reset_credit_increases_ui");
            resetCreditIncreases.setPersistent(false);
            resetCreditIncreases.setChecked(
                    ResetAlertPreferences.resetCreditIncreasesEnabled(requireContext()));
            resetCreditIncreases.setOnPreferenceChangeListener((preference, value) -> {
                ResetAlertPreferences.setResetCreditIncreasesEnabled(
                        requireContext(), (Boolean) value);
                return true;
            });

            SwitchPreferenceCompat resetCreditExpiry =
                    findPreference("reset_credit_expiry_ui");
            resetCreditExpiry.setPersistent(false);
            resetCreditExpiry.setChecked(
                    ResetAlertPreferences.resetCreditExpiryEnabled(requireContext()));
            resetCreditExpiry.setOnPreferenceChangeListener((preference, value) -> {
                boolean enabled = (Boolean) value;
                ResetAlertPreferences.setResetCreditExpiryEnabled(requireContext(), enabled);
                expiryTimesPreference.setEnabled(enabled);
                scheduleResetCreditExpiryReminders();
                return true;
            });

            expiryTimesPreference = findPreference("reset_credit_expiry_times_ui");
            expiryTimesPreference.setEnabled(
                    ResetAlertPreferences.resetCreditExpiryEnabled(requireContext()));
            expiryTimesPreference.setOnPreferenceClickListener(preference -> {
                showExpiryReminderTimesDialog();
                return true;
            });
            updateExpiryTimesSummary();

            testNotificationPreference = findPreference("notification_test");
            testNotificationPreference.setOnPreferenceClickListener(preference -> {
                boolean sent = ResetNotificationManager.sendTestNotification(requireContext());
                Toast.makeText(requireContext(), sent
                        ? "Test notification sent."
                        : "Enable notifications and allow permission first.",
                        sent ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                return true;
            });
            updatePermissionSummary();
            updateNotificationEnabledState();
        }

        private void updateNotificationEnabledState() {
            boolean enabled = ResetAlertPreferences.enabled(requireContext());
            if (notificationLowUsageCategory != null) {
                notificationLowUsageCategory.setVisible(enabled);
            }
            if (notificationResetCreditCategory != null) {
                notificationResetCreditCategory.setVisible(enabled);
            }
            if (notificationTroubleshootingCategory != null) {
                notificationTroubleshootingCategory.setVisible(enabled);
            }
        }

        private void showExpiryReminderTimesDialog() {
            List<Long> leadTimes = ResetAlertPreferences.getResetCreditExpiryLeadTimes(
                    requireContext());
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                    .setTitle("Reminder times")
                    .setNeutralButton("Add", (dialog, which) ->
                            showAddExpiryReminderDialog())
                    .setNegativeButton("Done", null);
            if (leadTimes.isEmpty()) {
                builder.setMessage("No reminder times are configured. Add one to choose how "
                        + "long before expiry Codex Monitor should notify you.");
            } else {
                String[] labels = new String[leadTimes.size()];
                for (int i = 0; i < leadTimes.size(); i++) {
                    labels[i] = formatLeadTime(leadTimes.get(i))
                            + " before expiry — tap to remove";
                }
                builder.setItems(labels, (dialog, which) -> {
                    List<Long> updated = new ArrayList<>(leadTimes);
                    long removed = updated.remove(which);
                    saveExpiryLeadTimes(updated);
                    Toast.makeText(requireContext(),
                            formatLeadTime(removed) + " reminder removed.",
                            Toast.LENGTH_SHORT).show();
                });
            }
            builder.show();
        }

        private void showAddExpiryReminderDialog() {
            boolean dark = Ui.isDark(requireContext());
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(Ui.dp(requireContext(), 24), Ui.dp(requireContext(), 8),
                    Ui.dp(requireContext(), 24), 0);
            TextView explanation = Ui.text(requireContext(),
                    "Notify me this long before each available reset credit expires.",
                    14.0f, Ui.secondaryText(dark));
            container.addView(explanation, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout inputRow = Ui.horizontal(requireContext(), 12);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, Ui.dp(requireContext(), 16), 0, 0);
            EditText amount = new EditText(requireContext());
            amount.setHint("Amount");
            amount.setSingleLine(true);
            amount.setTextColor(Ui.mainText(dark));
            amount.setHintTextColor(Ui.secondaryText(dark));
            amount.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            inputRow.addView(amount, new LinearLayout.LayoutParams(
                    0, Ui.dp(requireContext(), 54), 1.0f));
            String[] units = {"Minutes", "Hours", "Days", "Weeks"};
            Spinner unit = Ui.spinner(requireContext(), units, dark);
            unit.setSelection(1);
            LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(
                    Ui.dp(requireContext(), 142), Ui.dp(requireContext(), 54));
            unitParams.setMargins(Ui.dp(requireContext(), 8), 0, 0, 0);
            inputRow.addView(unit, unitParams);
            container.addView(inputRow, rowParams);

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle("Add reminder time")
                    .setView(container)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Add", null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        Long leadTime = parseLeadTime(
                                amount.getText().toString(),
                                unit.getSelectedItemPosition());
                        if (leadTime == null) {
                            amount.setError("Enter a time from 1 minute to 1 year, "
                                    + "in whole minutes.");
                            return;
                        }
                        List<Long> updated = new ArrayList<>(
                                ResetAlertPreferences.getResetCreditExpiryLeadTimes(
                                        requireContext()));
                        if (!updated.contains(leadTime)) updated.add(leadTime);
                        saveExpiryLeadTimes(updated);
                        dialog.dismiss();
                    }));
            dialog.show();
        }

        private Long parseLeadTime(String amount, int unitPosition) {
            long[] unitMillis = {
                    TimeUnit.MINUTES.toMillis(1),
                    TimeUnit.HOURS.toMillis(1),
                    TimeUnit.DAYS.toMillis(1),
                    TimeUnit.DAYS.toMillis(7)
            };
            if (amount == null || amount.trim().isEmpty()
                    || unitPosition < 0 || unitPosition >= unitMillis.length) {
                return null;
            }
            try {
                char decimalSeparator = DecimalFormatSymbols.getInstance()
                        .getDecimalSeparator();
                String normalized = decimalSeparator == '.'
                        ? amount.trim() : amount.trim().replace(decimalSeparator, '.');
                long value = new BigDecimal(normalized)
                        .multiply(BigDecimal.valueOf(unitMillis[unitPosition]))
                        .longValueExact();
                return value >= ResetCreditExpiryReminder.MIN_LEAD_TIME_MS
                        && value <= ResetCreditExpiryReminder.MAX_LEAD_TIME_MS
                        && value % ResetCreditExpiryReminder.MIN_LEAD_TIME_MS == 0L
                        ? value : null;
            } catch (ArithmeticException | NumberFormatException exception) {
                return null;
            }
        }

        private void saveExpiryLeadTimes(List<Long> leadTimes) {
            ResetAlertPreferences.setResetCreditExpiryLeadTimes(requireContext(), leadTimes);
            updateExpiryTimesSummary();
            scheduleResetCreditExpiryReminders();
        }

        private void updateExpiryTimesSummary() {
            if (expiryTimesPreference == null) return;
            List<Long> leadTimes = ResetAlertPreferences.getResetCreditExpiryLeadTimes(
                    requireContext());
            if (leadTimes.isEmpty()) {
                expiryTimesPreference.setSummary("No reminder times configured");
                return;
            }
            List<String> labels = new ArrayList<>();
            for (Long leadTime : leadTimes) labels.add(formatLeadTime(leadTime));
            expiryTimesPreference.setSummary(
                    String.join(", ", labels) + " before expiry");
        }

        private String formatLeadTime(long millis) {
            if (millis % TimeUnit.DAYS.toMillis(7) == 0L) {
                long weeks = millis / TimeUnit.DAYS.toMillis(7);
                return weeks + " week" + (weeks == 1 ? "" : "s");
            }
            if (millis % TimeUnit.DAYS.toMillis(1) == 0L) {
                long days = millis / TimeUnit.DAYS.toMillis(1);
                return days + " day" + (days == 1 ? "" : "s");
            }
            if (millis % TimeUnit.HOURS.toMillis(1) == 0L) {
                long hours = millis / TimeUnit.HOURS.toMillis(1);
                return hours + " hour" + (hours == 1 ? "" : "s");
            }
            long minutes = millis / TimeUnit.MINUTES.toMillis(1);
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }

        private void scheduleResetCreditExpiryReminders() {
            ResetNotificationManager.onResetCreditExpirySettingsChanged(
                    requireContext(), AppPreferences.loadResetCredits(requireContext()));
        }

        private void bindNowBar() {
            NowBarPreferences.setDisplayMode(requireContext(), NowBarDisplayMode.AUTO);
            NowBarPreferences.setPercentMode(requireContext(), NowBarPercentMode.AUTO);

            nowBarPermissionPreference = findPreference("now_bar_permission");
            if (nowBarPermissionPreference != null) {
                nowBarPermissionPreference.setOnPreferenceClickListener(preference -> {
                    AlertSoundManager.ensureChannels(requireContext());
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE,
                                    requireContext().getPackageName()));
                    return true;
                });
            }

            SwitchPreferenceCompat phoneSpeaker =
                    findPreference("alert_sounds_phone_speaker");
            if (phoneSpeaker != null) {
                phoneSpeaker.setPersistent(false);
                phoneSpeaker.setChecked(
                        AlertSoundManager.playOnPhoneSpeaker(requireContext()));
                phoneSpeaker.setOnPreferenceChangeListener((preference, value) -> {
                    AlertSoundManager.setPlayOnPhoneSpeaker(
                            requireContext(), (Boolean) value);
                    return true;
                });
            }

            updateNowBarSummary();
        }

        private void saveNowBarAutoStart(boolean enabled, String metric, int threshold) {
            NowBarPreferences.save(requireContext(), enabled, metric, threshold);
            updateNowBarAutoStartEnabledState();
            if (enabled) {
                NowBarPreferences.clearSuppression(requireContext());
                boolean started = NowBarManager.maybeAutoStart(
                        requireContext(), AppPreferences.loadSnapshot(requireContext()));
                if (started) {
                    Toast.makeText(requireContext(),
                            "Live monitor started from the current usage threshold.",
                            Toast.LENGTH_SHORT).show();
                }
            }
            updateNowBarSummary();
            PhoneWearSync.pushSettings(requireContext());
        }

        private void updateNowBarAutoStartEnabledState() {
            boolean enabled = NowBarPreferences.isAutoStartEnabled(requireContext());
            if (nowBarMetricPreference != null) nowBarMetricPreference.setVisible(enabled);
            if (nowBarThresholdPreference != null) {
                nowBarThresholdPreference.setVisible(enabled);
            }
        }

        private void updateNowBarAcceleratedEnabledState() {
            if (nowBarAcceleratedPreference == null || getContext() == null) return;
            nowBarAcceleratedPreference.setEnabled(
                    UsagePacePreferences.areWarningsEnabled(requireContext()));
        }

        private void updateNowBarSummary() {
            if (nowBarPermissionPreference == null || getContext() == null) return;
            nowBarPermissionPreference.setSummary("System notification settings");
        }

        private void setNotificationsEnabled(boolean enabled) {
            saveAlert(enabled ? ResetAlertPreferences.STYLE_NOTIFICATION
                    : ResetAlertPreferences.STYLE_OFF,
                    ResetAlertPreferences.getMetric(requireContext()),
                    ResetAlertPreferences.getThreshold(requireContext()));
            if (enabled && Build.VERSION.SDK_INT >= 33
                    && requireContext().checkSelfPermission(
                    "android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, 8601);
            }
            if (enabled) {
                ResetNotificationManager.ensureChannel(requireContext());
            } else {
                ResetNotificationManager.clearNotificationHistory(requireContext());
            }
            updateNotificationEnabledState();
        }

        private void saveAlert(String style, String metric, int threshold) {
            ResetAlertPreferences.save(requireContext(), style, metric, threshold);
            if (!ResetAlertPreferences.STYLE_OFF.equals(style)) {
                ResetNotificationManager.ensureChannel(requireContext());
                ResetNotificationManager.onUsageUpdated(
                        requireContext(), AppPreferences.loadSnapshot(requireContext()));
                ResetNotificationManager.onResetCreditsUpdated(
                        requireContext(), AppPreferences.loadResetCredits(requireContext()));
            }
            ResetAlertScheduler.scheduleFromSnapshot(
                    requireContext(), AppPreferences.loadSnapshot(requireContext()));
            scheduleResetCreditExpiryReminders();
        }

        private void updatePermissionSummary() {
            if (getContext() == null) return;
            NotificationManager manager = (NotificationManager) requireContext()
                    .getSystemService(NOTIFICATION_SERVICE);
            boolean allowed = manager != null && manager.areNotificationsEnabled()
                    && (Build.VERSION.SDK_INT < 33
                    || requireContext().checkSelfPermission(
                    "android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED);
            if (testNotificationPreference != null) {
                testNotificationPreference.setEnabled(allowed);
            }
        }
    }
}
