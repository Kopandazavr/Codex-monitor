package dev.kopandazavr.codexmonitor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
        if (PAGE_NOW_BAR.equals(page) || PAGE_DIAGNOSTICS.equals(page)) {
            return page;
        }
        return PAGE_ROOT;
    }

    private static String pageTitle(String page) {
        switch (page) {
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
        String identity = diagnosticBuildIdentity();
        String collapsedText = "Diagnostics   " + identity;
        SpannableString collapsed = new SpannableString(collapsedText);
        int identityStart = collapsedText.indexOf(identity);
        if (identityStart >= 0) {
            collapsed.setSpan(new ForegroundColorSpan(Ui.secondaryText(Ui.isDark(this))),
                    identityStart, collapsed.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            collapsed.setSpan(new RelativeSizeSpan(0.72f),
                    identityStart, collapsed.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        toolbar.setTitle("Diagnostics", collapsed);
        toolbar.setSubtitle(identity);
        toolbar.setCollapsedSubtitle(null);
    }

    private static String diagnosticBuildIdentity() {
        String identity = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
        String sha = BuildConfig.GIT_SHA == null ? "" : BuildConfig.GIT_SHA.trim();
        if (!sha.isEmpty() && !"unknown".equalsIgnoreCase(sha)) {
            identity += " · " + sha;
        }
        return identity;
    }

    @SuppressLint("FindPreferenceKeyNotFound")
    public static final class SettingsFragment extends PreferenceFragmentCompat {
        private static final String ARG_PAGE = "page";
        private static final int REQUEST_EXPORT_DIAGNOSTICS = 9203;

        private String page = PAGE_ROOT;
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
                export.setSummary("Always on · " + DiagnosticLog.formatBytes(stats.bytes)
                        + (stats.files == 1 ? " in 1 file"
                        : " across " + stats.files + " files"));
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
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE,
                                    requireContext().getPackageName()));
                    return true;
                });
            }

            Preference processMode = findPreference(ProcessNotificationMode.PREFERENCE_KEY);
            if (processMode != null) {
                processMode.setOnPreferenceChangeListener((preference, value) -> {
                    String previous = ProcessNotificationMode.current(requireContext());
                    String next = ProcessNotificationMode.normalize(String.valueOf(value));
                    if (previous.equals(next)) return true;
                    ProcessNotificationManager.clearAll(requireContext());
                    ProcessNotificationMode.set(requireContext(), next);
                    boolean reposted = DualUsageNotificationManager.repostFromCache(
                            requireContext());
                    DiagnosticLog.info(requireContext(), "notification",
                            "process_notification_mode_changed",
                            "from", previous, "to", next, "reposted", reposted);
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
