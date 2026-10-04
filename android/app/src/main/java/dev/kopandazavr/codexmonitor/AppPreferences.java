package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import dev.kopandazavr.codexmonitor.wear.PhoneWearSync;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class AppPreferences {
    private static final String KEY_APP_STYLE = "app_surface_style";
    private static final String KEY_APP_THEME = "app_theme";
    private static final String KEY_DASHBOARD_ADDITIONAL_LIMITS = "dashboard_additional_limits";
    private static final String KEY_DASHBOARD_FIVE_HOUR = "dashboard_five_hour";
    private static final String KEY_DASHBOARD_HIDDEN_SECTIONS = "dashboard_hidden_sections";
    private static final String KEY_DASHBOARD_MONTHLY = "dashboard_monthly";
    private static final String KEY_DASHBOARD_RESET_CREDITS = "dashboard_reset_credits";
    private static final String KEY_DASHBOARD_SECTION_ORDER = "dashboard_section_order";
    private static final String KEY_DASHBOARD_USAGE_CREDITS = "dashboard_usage_credits";
    private static final String KEY_DASHBOARD_USAGE_HISTORY = "dashboard_usage_history";
    private static final String KEY_DASHBOARD_WEEKLY = "dashboard_weekly";
    private static final String KEY_HISTORY_SECTION_OVERRIDES = "history_section_overrides";
    private static final String KEY_MATERIAL_YOU = "material_you";
    private static final String KEY_ERROR = "last_error";
    private static final String KEY_ERROR_AT = "last_error_at";
    private static final String KEY_OAUTH_PENDING = "oauth_pending";
    private static final String KEY_OAUTH_STARTED_AT = "oauth_started_at";
    private static final String KEY_OAUTH_URL = "oauth_url";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_ONBOARDING_STEP = "onboarding_step";
    private static final String KEY_AUTOMATIC_REFRESH = "automatic_refresh";
    private static final String KEY_REFRESH_FAILURES = "refresh_failures";
    private static final String KEY_REFRESH_MINUTES = "refresh_minutes";
    private static final String KEY_REFRESH_ON_LAUNCH = "refresh_on_launch";
    private static final String KEY_RESET_CREDITS = "reset_credits_snapshot";
    private static final String KEY_RESET_ERROR = "reset_credits_error";
    private static final String KEY_RESET_ERROR_AT = "reset_credits_error_at";
    private static final String KEY_SCHEDULER_ERROR = "scheduler_error";
    private static final String KEY_SNAPSHOT = "last_snapshot";
    private static final String KEY_HISTORY_FIVE_HOUR = "usage_history_five_hour";
    private static final String KEY_HISTORY_WEEKLY = "usage_history_weekly";
    private static final String KEY_HISTORY_MONTHLY = "usage_history_monthly";
    private static final long OAUTH_STALE_AFTER_MS = 720000;
    private static final String PREFS = "codex_monitor_settings_v1";

    private AppPreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, 0);
    }

    public static boolean saveSnapshot(Context context, UsageSnapshot usageSnapshot) {
        return saveSnapshot(context, AccountContainerStore.selectedId(context), usageSnapshot);
    }

    static boolean saveSnapshot(Context context, String containerId, UsageSnapshot usageSnapshot) {
        if (usageSnapshot == null) return false;
        try {
            return prefs(context).edit()
                    .putString(accountKey(containerId, KEY_SNAPSHOT),
                            usageSnapshot.toJson().toString())
                    .remove(accountKey(containerId, KEY_ERROR))
                    .remove(accountKey(containerId, KEY_ERROR_AT))
                    .commit();
        } catch (Exception e) {
            setLastError(context, containerId, "Could not cache the latest usage response.");
            return false;
        }
    }

    public static UsageSnapshot loadSnapshot(Context context) {
        return loadSnapshot(context, AccountContainerStore.selectedId(context));
    }

    static UsageSnapshot loadSnapshot(Context context, String containerId) {
        String stored = scopedString(context, containerId, KEY_SNAPSHOT, null);
        if (stored == null || stored.isEmpty()) return null;
        try {
            return UsageSnapshot.fromJson(new JSONObject(stored));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void clearSnapshot(Context context) {
        clearSnapshot(context, AccountContainerStore.selectedId(context));
    }

    static void clearSnapshot(Context context, String containerId) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .remove(accountKey(containerId, KEY_SNAPSHOT))
                .remove(accountKey(containerId, KEY_ERROR))
                .remove(accountKey(containerId, KEY_ERROR_AT))
                .remove(accountKey(containerId, KEY_RESET_CREDITS))
                .remove(accountKey(containerId, KEY_RESET_ERROR))
                .remove(accountKey(containerId, KEY_RESET_ERROR_AT))
                .remove(accountKey(containerId, KEY_HISTORY_FIVE_HOUR))
                .remove(accountKey(containerId, KEY_HISTORY_WEEKLY))
                .remove(accountKey(containerId, KEY_HISTORY_MONTHLY))
                .remove(accountKey(containerId, KEY_REFRESH_FAILURES));
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            editor.remove(KEY_SNAPSHOT).remove(KEY_ERROR).remove(KEY_ERROR_AT)
                    .remove(KEY_RESET_CREDITS).remove(KEY_RESET_ERROR).remove(KEY_RESET_ERROR_AT)
                    .remove(KEY_HISTORY_FIVE_HOUR).remove(KEY_HISTORY_WEEKLY)
                    .remove(KEY_HISTORY_MONTHLY).remove(KEY_REFRESH_FAILURES);
        }
        editor.apply();
        ResetNotificationManager.clearContainerState(context, containerId);
        ResetCreditExpiryScheduler.cancelAll(context, containerId);
        if (containerId.equals(AccountContainerStore.selectedId(context))) {
            if (!hasAnySignedInAccount(context)) {
                NowBarManager.stop(context);
                NowBarPreferences.clearSuppression(context);
            }
            PhoneWearSync.pushUsage(context, null);
        }
    }

    public static void setLastError(Context context, String value) {
        setLastError(context, AccountContainerStore.selectedId(context), value);
    }

    static void setLastError(Context context, String containerId, String value) {
        if (value == null || value.trim().isEmpty()) {
            clearLastError(context, containerId);
        } else {
            prefs(context).edit()
                    .putString(accountKey(containerId, KEY_ERROR), trim(value, "Refresh failed."))
                    .putLong(accountKey(containerId, KEY_ERROR_AT), System.currentTimeMillis())
                    .apply();
        }
    }

    public static void clearLastError(Context context) {
        clearLastError(context, AccountContainerStore.selectedId(context));
    }

    static void clearLastError(Context context, String containerId) {
        prefs(context).edit()
                .remove(accountKey(containerId, KEY_ERROR))
                .remove(accountKey(containerId, KEY_ERROR_AT))
                .apply();
    }

    public static UsageHistory loadUsageHistory(Context context, String kind) {
        return loadUsageHistory(context, AccountContainerStore.selectedId(context), kind);
    }

    static UsageHistory loadUsageHistory(Context context, String containerId, String kind) {
        String stored = scopedString(context, containerId, historyKey(kind), null);
        if (stored == null || stored.isEmpty()) return UsageHistory.empty(kind);
        try {
            return UsageHistory.fromJson(new JSONObject(stored), kind);
        } catch (Exception ignored) {
            return UsageHistory.empty(kind);
        }
    }

    public static boolean saveUsageHistory(Context context, UsageHistory history) {
        return saveUsageHistory(context, AccountContainerStore.selectedId(context), history);
    }

    static boolean saveUsageHistory(Context context, String containerId, UsageHistory history) {
        if (history == null) return false;
        try {
            return prefs(context).edit()
                    .putString(accountKey(containerId, historyKey(history.kind)),
                            history.toJson().toString())
                    .commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void clearUsageHistory(Context context) {
        clearUsageHistory(context, AccountContainerStore.selectedId(context));
    }

    static void clearUsageHistory(Context context, String containerId) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .remove(accountKey(containerId, KEY_HISTORY_FIVE_HOUR))
                .remove(accountKey(containerId, KEY_HISTORY_WEEKLY))
                .remove(accountKey(containerId, KEY_HISTORY_MONTHLY));
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            editor.remove(KEY_HISTORY_FIVE_HOUR).remove(KEY_HISTORY_WEEKLY)
                    .remove(KEY_HISTORY_MONTHLY);
        }
        editor.apply();
    }

    private static String historyKey(String kind) {
        if (UsageHistory.WEEKLY.equals(kind)) return KEY_HISTORY_WEEKLY;
        if (UsageHistory.MONTHLY.equals(kind)) return KEY_HISTORY_MONTHLY;
        return KEY_HISTORY_FIVE_HOUR;
    }

    public static String getLastError(Context context) {
        return getLastError(context, AccountContainerStore.selectedId(context));
    }

    static String getLastError(Context context, String containerId) {
        return scopedString(context, containerId, KEY_ERROR, "");
    }

    public static String getVisibleRefreshError(Context context) {
        String containerId = AccountContainerStore.selectedId(context);
        String lastError = getLastError(context, containerId);
        if (lastError.isEmpty()) return "";
        UsageSnapshot snapshot = loadSnapshot(context, containerId);
        if (snapshot != null) {
            long errorAt = scopedLong(context, containerId, KEY_ERROR_AT, 0L);
            if (errorAt <= 0 || errorAt > snapshot.fetchedAtMillis) {
                return Math.max(0L, System.currentTimeMillis() - snapshot.fetchedAtMillis) < 900000
                        ? "" : lastError;
            }
            clearLastError(context, containerId);
            return "";
        }
        return lastError;
    }

    public static boolean saveResetCredits(Context context, ResetCreditsSnapshot snapshot) {
        return saveResetCredits(context, AccountContainerStore.selectedId(context), snapshot);
    }

    static boolean saveResetCredits(Context context, String containerId,
            ResetCreditsSnapshot snapshot) {
        if (snapshot == null) return false;
        try {
            return prefs(context).edit()
                    .putString(accountKey(containerId, KEY_RESET_CREDITS),
                            snapshot.toJson().toString())
                    .remove(accountKey(containerId, KEY_RESET_ERROR))
                    .remove(accountKey(containerId, KEY_RESET_ERROR_AT))
                    .commit();
        } catch (Exception e) {
            setResetCreditsError(context, containerId, "Could not cache Codex reset credits.");
            return false;
        }
    }

    public static ResetCreditsSnapshot loadResetCredits(Context context) {
        return loadResetCredits(context, AccountContainerStore.selectedId(context));
    }

    static ResetCreditsSnapshot loadResetCredits(Context context, String containerId) {
        ResetCreditsSnapshot result = null;
        String stored = scopedString(context, containerId, KEY_RESET_CREDITS, null);
        if (stored != null && !stored.isEmpty()) {
            try {
                result = ResetCreditsSnapshot.fromJson(new JSONObject(stored));
            } catch (Exception ignored) {
            }
        }
        UsageSnapshot usage = loadSnapshot(context, containerId);
        if (usage != null && usage.resetCreditsAvailable >= 0) {
            if (result == null) {
                return ResetCreditsSnapshot.summary(
                        usage.resetCreditsAvailable, usage.fetchedAtMillis);
            }
            if (usage.fetchedAtMillis > result.fetchedAtMillis
                    && usage.resetCreditsAvailable != result.availableCount) {
                return ResetCreditsSnapshot.summary(
                        usage.resetCreditsAvailable, usage.fetchedAtMillis);
            }
        }
        return result;
    }

    public static void setResetCreditsError(Context context, String value) {
        setResetCreditsError(context, AccountContainerStore.selectedId(context), value);
    }

    static void setResetCreditsError(Context context, String containerId, String value) {
        if (value == null || value.trim().isEmpty()) {
            clearResetCreditsError(context, containerId);
        } else {
            prefs(context).edit()
                    .putString(accountKey(containerId, KEY_RESET_ERROR),
                            trim(value, "Reset-credit refresh failed."))
                    .putLong(accountKey(containerId, KEY_RESET_ERROR_AT),
                            System.currentTimeMillis())
                    .apply();
        }
    }

    public static void clearResetCreditsError(Context context) {
        clearResetCreditsError(context, AccountContainerStore.selectedId(context));
    }

    static void clearResetCreditsError(Context context, String containerId) {
        prefs(context).edit()
                .remove(accountKey(containerId, KEY_RESET_ERROR))
                .remove(accountKey(containerId, KEY_RESET_ERROR_AT))
                .apply();
    }

    public static String getResetCreditsError(Context context) {
        return getResetCreditsError(context, AccountContainerStore.selectedId(context));
    }

    static String getResetCreditsError(Context context, String containerId) {
        return scopedString(context, containerId, KEY_RESET_ERROR, "");
    }

    public static String getVisibleResetCreditsError(Context context) {
        String containerId = AccountContainerStore.selectedId(context);
        String error = getResetCreditsError(context, containerId);
        if (error.isEmpty()) return "";
        ResetCreditsSnapshot snapshot = loadResetCredits(context, containerId);
        if (snapshot != null) {
            long errorAt = scopedLong(context, containerId, KEY_RESET_ERROR_AT, 0L);
            if (errorAt <= 0 || errorAt > snapshot.fetchedAtMillis) {
                return Math.max(0L, System.currentTimeMillis() - snapshot.fetchedAtMillis) < 1800000
                        ? "" : error;
            }
            clearResetCreditsError(context, containerId);
            return "";
        }
        return error;
    }

    public static void setSchedulerError(Context context, String str) {
        if (str == null || str.trim().isEmpty()) {
            prefs(context).edit().remove(KEY_SCHEDULER_ERROR).apply();
        } else {
            prefs(context).edit().putString(KEY_SCHEDULER_ERROR, trim(str, "Background scheduling is unavailable.")).apply();
        }
    }

    public static String getSchedulerError(Context context) {
        return prefs(context).getString(KEY_SCHEDULER_ERROR, "");
    }

    public static int getRefreshMinutes(Context context) {
        int i = prefs(context).getInt(KEY_REFRESH_MINUTES, 30);
        if (validRefresh(i)) {
            return i;
        }
        return 30;
    }

    public static void setRefreshMinutes(Context context, int i) {
        SharedPreferences.Editor editorEdit = prefs(context).edit();
        if (!validRefresh(i)) {
            i = 30;
        }
        editorEdit.putInt(KEY_REFRESH_MINUTES, i).apply();
    }

    public static boolean getAutomaticRefresh(Context context) {
        return prefs(context).getBoolean(KEY_AUTOMATIC_REFRESH, true);
    }

    public static void setAutomaticRefresh(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTOMATIC_REFRESH, enabled).apply();
    }

    public static int getRefreshFailures(Context context) {
        return getRefreshFailures(context, AccountContainerStore.selectedId(context));
    }

    static int getRefreshFailures(Context context, String containerId) {
        int value = (int) scopedLong(context, containerId, KEY_REFRESH_FAILURES, 0L);
        return Math.max(0, Math.min(3, value));
    }

    public static void recordRefreshSuccess(Context context) {
        recordRefreshSuccess(context, AccountContainerStore.selectedId(context));
    }

    static void recordRefreshSuccess(Context context, String containerId) {
        prefs(context).edit().remove(accountKey(containerId, KEY_REFRESH_FAILURES)).apply();
    }

    public static void recordRefreshFailure(Context context) {
        recordRefreshFailure(context, AccountContainerStore.selectedId(context));
    }

    static void recordRefreshFailure(Context context, String containerId) {
        int failures = Math.min(3, getRefreshFailures(context, containerId) + 1);
        prefs(context).edit()
                .putInt(accountKey(containerId, KEY_REFRESH_FAILURES), failures)
                .apply();
    }

    public static boolean getRefreshOnLaunch(Context context) {
        return prefs(context).getBoolean(KEY_REFRESH_ON_LAUNCH, true);
    }

    public static void setRefreshOnLaunch(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_REFRESH_ON_LAUNCH, enabled).apply();
    }

    public static boolean showDashboardFiveHour(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_FIVE_HOUR, true);
    }

    public static void setShowDashboardFiveHour(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_DASHBOARD_FIVE_HOUR, show).apply();
    }

    public static boolean showDashboardWeekly(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_WEEKLY, true);
    }

    public static void setShowDashboardWeekly(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_DASHBOARD_WEEKLY, show).apply();
    }

    public static boolean showDashboardMonthly(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_MONTHLY, true);
    }

    public static void setShowDashboardMonthly(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_DASHBOARD_MONTHLY, show).apply();
    }

    public static boolean showDashboardAdditionalLimits(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_ADDITIONAL_LIMITS, true);
    }

    public static boolean showDashboardUsageCredits(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_USAGE_CREDITS, true);
    }

    public static void setShowDashboardUsageCredits(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_DASHBOARD_USAGE_CREDITS, show).apply();
    }

    public static boolean showDashboardUsageHistory(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_USAGE_HISTORY, true);
    }

    public static void setShowDashboardUsageHistory(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_DASHBOARD_USAGE_HISTORY, show).apply();
    }

    public static boolean showDashboardResetCredits(Context context) {
        return prefs(context).getBoolean(KEY_DASHBOARD_RESET_CREDITS, true);
    }

    public static void setShowDashboardResetCredits(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_DASHBOARD_RESET_CREDITS, show).apply();
    }

    /** Processes is fixed on Dashboard and is permanently expanded. */

    /** Hidden section keys (currently model-specific limits) as a {@link DashboardSections} CSV. */
    public static String getDashboardHiddenSections(Context context) {
        return prefs(context).getString(KEY_DASHBOARD_HIDDEN_SECTIONS, "");
    }

    public static void setDashboardHiddenSections(Context context, String hiddenCsv) {
        if (hiddenCsv == null || hiddenCsv.trim().isEmpty()) {
            prefs(context).edit().remove(KEY_DASHBOARD_HIDDEN_SECTIONS).apply();
        } else {
            prefs(context).edit().putString(KEY_DASHBOARD_HIDDEN_SECTIONS, hiddenCsv).apply();
        }
    }

    public static boolean isDashboardSectionHidden(Context context, String key) {
        return DashboardSections.isHidden(getDashboardHiddenSections(context), key);
    }

    public static void setDashboardSectionHidden(Context context, String key, boolean hidden) {
        setDashboardHiddenSections(context,
                DashboardSections.setHidden(getDashboardHiddenSections(context), key, hidden));
    }

    /** Usage-history highlight overrides as a {@link HistorySections} CSV. */
    public static String getHistorySectionOverrides(Context context) {
        return prefs(context).getString(KEY_HISTORY_SECTION_OVERRIDES, "");
    }

    public static void setHistorySectionOverrides(Context context, String overridesCsv) {
        if (overridesCsv == null || overridesCsv.trim().isEmpty()) {
            prefs(context).edit().remove(KEY_HISTORY_SECTION_OVERRIDES).apply();
        } else {
            prefs(context).edit().putString(KEY_HISTORY_SECTION_OVERRIDES, overridesCsv).apply();
        }
    }

    public static boolean isHistorySectionVisible(Context context, String key) {
        return HistorySections.isVisible(getHistorySectionOverrides(context), key);
    }

    public static void setHistorySectionVisible(Context context, String key, boolean visible) {
        setHistorySectionOverrides(context,
                HistorySections.setVisible(getHistorySectionOverrides(context), key, visible));
    }

    public static void setDashboardVisibility(Context context, boolean fiveHour,
            boolean weekly, boolean monthly, boolean additionalLimits, boolean usageCredits,
            boolean resetCredits, boolean usageHistory) {
        prefs(context).edit()
                .putBoolean(KEY_DASHBOARD_FIVE_HOUR, fiveHour)
                .putBoolean(KEY_DASHBOARD_WEEKLY, weekly)
                .putBoolean(KEY_DASHBOARD_MONTHLY, monthly)
                .putBoolean(KEY_DASHBOARD_ADDITIONAL_LIMITS, additionalLimits)
                .putBoolean(KEY_DASHBOARD_USAGE_CREDITS, usageCredits)
                .putBoolean(KEY_DASHBOARD_RESET_CREDITS, resetCredits)
                .putBoolean(KEY_DASHBOARD_USAGE_HISTORY, usageHistory)
                .apply();
    }

    /** Saved dashboard section order as a comma-separated {@link DashboardSections} key list. */
    public static String getDashboardOrder(Context context) {
        return prefs(context).getString(KEY_DASHBOARD_SECTION_ORDER, "");
    }

    public static void setDashboardOrder(Context context, java.util.List<String> order) {
        String csv = DashboardSections.serialize(order);
        if (csv.isEmpty()) {
            prefs(context).edit().remove(KEY_DASHBOARD_SECTION_ORDER).apply();
        } else {
            prefs(context).edit().putString(KEY_DASHBOARD_SECTION_ORDER, csv).apply();
        }
    }

    private static boolean validRefresh(int i) {
        return i == 5 || i == 10 || i == 15 || i == 30 || i == 60 || i == 120;
    }

    public static String getAppTheme(Context context) {
        String string = prefs(context).getString(KEY_APP_THEME, WidgetOptions.THEME_SYSTEM);
        return (WidgetOptions.THEME_DARK.equals(string) || WidgetOptions.THEME_LIGHT.equals(string)) ? string : WidgetOptions.THEME_SYSTEM;
    }

    public static void setAppTheme(Context context, String str) {
        if (!WidgetOptions.THEME_DARK.equals(str) && !WidgetOptions.THEME_LIGHT.equals(str)) {
            str = WidgetOptions.THEME_SYSTEM;
        }
        prefs(context).edit().putString(KEY_APP_THEME, str).commit();
    }

    /** When enabled, accents follow Android Material You system colors (API 31+). */
    public static boolean isMaterialYouEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MATERIAL_YOU, false);
    }

    public static void setMaterialYouEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MATERIAL_YOU, enabled).commit();
    }

    public static String getAppStyle(Context context) {
        return WidgetOptions.SURFACE_ONE_UI;
    }

    public static void setAppStyle(Context context, String str) {
        prefs(context).edit().putString(KEY_APP_STYLE, WidgetOptions.SURFACE_ONE_UI).apply();
    }

    public static WidgetOptions loadDefaultWidgetOptions(Context context) {
        SharedPreferences sharedPreferencesPrefs = prefs(context);
        return normalizeLoaded(new WidgetOptions(
                sharedPreferencesPrefs.getString("default_style", WidgetOptions.STYLE_AUTO),
                sharedPreferencesPrefs.getString("default_density", "auto"),
                sharedPreferencesPrefs.getString("default_surface_style", WidgetOptions.SURFACE_ONE_UI),
                sharedPreferencesPrefs.getString("default_graphic_scale", "auto"),
                sharedPreferencesPrefs.getString("default_theme", WidgetOptions.THEME_SYSTEM),
                sharedPreferencesPrefs.getString("default_accent", WidgetOptions.ACCENT_BLUE),
                sharedPreferencesPrefs.getInt("default_opacity", 88),
                sharedPreferencesPrefs.getString("default_reset_mode", WidgetOptions.RESET_ABSOLUTE),
                sharedPreferencesPrefs.getString("default_display_mode", WidgetOptions.DISPLAY_REMAINING),
                sharedPreferencesPrefs.getString("default_metric_mode", WidgetOptions.METRIC_BOTH),
                false,
                sharedPreferencesPrefs.getBoolean("default_show_plan", false),
                sharedPreferencesPrefs.getBoolean("default_show_updated", false),
                sharedPreferencesPrefs.getBoolean("default_show_refresh", true),
                sharedPreferencesPrefs.getBoolean("default_show_reset_credits", false),
                sharedPreferencesPrefs.getBoolean("default_show_reset_action", false))
                .withPercentSymbol(sharedPreferencesPrefs.getBoolean(
                        "default_show_percent_symbol", true))
                .withVisibleMeters(sharedPreferencesPrefs.getString("default_visible_meters", "")));
    }

    public static void saveDefaultWidgetOptions(Context context, WidgetOptions widgetOptions) {
        prefs(context).edit()
                .putString("default_style", widgetOptions.layout)
                .putString("default_layout", widgetOptions.layout)
                .putString("default_density", widgetOptions.density)
                .putString("default_surface_style", widgetOptions.surfaceStyle)
                .putString("default_graphic_scale", widgetOptions.graphicScale)
                .putString("default_theme", widgetOptions.theme)
                .putString("default_accent", widgetOptions.accent)
                .putInt("default_opacity", widgetOptions.opacity)
                .putString("default_reset_mode", widgetOptions.resetMode)
                .putString("default_display_mode", widgetOptions.displayMode)
                .putString("default_metric_mode", widgetOptions.metricMode)
                .putString("default_visible_meters", widgetOptions.visibleMeters)
                .putBoolean("default_show_title", widgetOptions.showTitle)
                .putBoolean("default_show_plan", widgetOptions.showPlan)
                .putBoolean("default_show_updated", widgetOptions.showUpdated)
                .putBoolean("default_show_refresh", widgetOptions.showRefresh)
                .putBoolean("default_show_reset_credits", widgetOptions.showResetCredits)
                .putBoolean("default_show_reset_action", widgetOptions.showResetAction)
                .putBoolean("default_show_percent_symbol", widgetOptions.showPercentSymbol)
                .apply();
    }

    public static WidgetOptions loadWidgetOptions(Context context, int i) {
        if (i == 0) {
            return loadDefaultWidgetOptions(context);
        }
        SharedPreferences sharedPreferencesPrefs = prefs(context);
        WidgetOptions widgetOptionsLoadDefaultWidgetOptions = loadDefaultWidgetOptions(context);
        String str = "widget_" + i + "_";
        return normalizeLoaded(new WidgetOptions(
                sharedPreferencesPrefs.getString(str + "style",
                        widgetOptionsLoadDefaultWidgetOptions.layout),
                sharedPreferencesPrefs.getString(str + "density",
                        widgetOptionsLoadDefaultWidgetOptions.density),
                sharedPreferencesPrefs.getString(str + "surface_style",
                        widgetOptionsLoadDefaultWidgetOptions.surfaceStyle),
                sharedPreferencesPrefs.getString(str + "graphic_scale",
                        widgetOptionsLoadDefaultWidgetOptions.graphicScale),
                sharedPreferencesPrefs.getString(str + "theme",
                        widgetOptionsLoadDefaultWidgetOptions.theme),
                sharedPreferencesPrefs.getString(str + "accent",
                        widgetOptionsLoadDefaultWidgetOptions.accent),
                sharedPreferencesPrefs.getInt(str + "opacity",
                        widgetOptionsLoadDefaultWidgetOptions.opacity),
                sharedPreferencesPrefs.getString(str + "reset_mode",
                        widgetOptionsLoadDefaultWidgetOptions.resetMode),
                sharedPreferencesPrefs.getString(str + "display_mode",
                        widgetOptionsLoadDefaultWidgetOptions.displayMode),
                sharedPreferencesPrefs.getString(str + "metric_mode",
                        widgetOptionsLoadDefaultWidgetOptions.metricMode),
                false,
                sharedPreferencesPrefs.getBoolean(str + "show_plan",
                        widgetOptionsLoadDefaultWidgetOptions.showPlan),
                sharedPreferencesPrefs.getBoolean(str + "show_updated",
                        widgetOptionsLoadDefaultWidgetOptions.showUpdated),
                sharedPreferencesPrefs.getBoolean(str + "show_refresh",
                        widgetOptionsLoadDefaultWidgetOptions.showRefresh),
                sharedPreferencesPrefs.getBoolean(str + "show_reset_credits",
                        widgetOptionsLoadDefaultWidgetOptions.showResetCredits),
                sharedPreferencesPrefs.getBoolean(str + "show_reset_action",
                        widgetOptionsLoadDefaultWidgetOptions.showResetAction))
                .withPercentSymbol(sharedPreferencesPrefs.getBoolean(str + "show_percent_symbol",
                        widgetOptionsLoadDefaultWidgetOptions.showPercentSymbol))
                .withVisibleMeters(sharedPreferencesPrefs.getString(str + "visible_meters",
                        widgetOptionsLoadDefaultWidgetOptions.visibleMeters)));
    }

    public static String getWidgetTapAction(Context context, int appWidgetId) {
        if (appWidgetId == 0) {
            return WidgetOptions.TAP_OPEN_APP;
        }
        return WidgetOptions.normalizeTapAction(
                prefs(context).getString("widget_" + appWidgetId + "_tap_action",
                        WidgetOptions.TAP_OPEN_APP));
    }

    public static void saveWidgetTapAction(Context context, int appWidgetId, String action) {
        if (appWidgetId != 0) {
            prefs(context).edit().putString("widget_" + appWidgetId + "_tap_action",
                    WidgetOptions.normalizeTapAction(action)).apply();
        }
    }

    /**
     * Keeps One UI surface defaults and hides unused chrome flags without wiping the user's
     * layout preference or visible-meters selection.
     */
    private static WidgetOptions normalizeLoaded(WidgetOptions options) {
        return new WidgetOptions(options.layout, WidgetOptions.DENSITY_AUTO,
                WidgetOptions.SURFACE_ONE_UI, "auto", options.theme, options.accent,
                options.opacity, WidgetOptions.RESET_HIDDEN, options.displayMode, options.metricMode,
                false, false, false, false, false, false)
                .withPercentSymbol(options.showPercentSymbol)
                .withVisibleMeters(options.visibleMeters);
    }

    public static void saveWidgetOptions(Context context, int i, WidgetOptions widgetOptions) {
        String str = "widget_" + i + "_";
        String metricMode = metricModeFromVisible(widgetOptions.effectiveVisibleMeters());
        prefs(context).edit()
                .putString(str + "style", widgetOptions.layout)
                .putString(str + "layout", widgetOptions.layout)
                .putString(str + "density", widgetOptions.density)
                .putString(str + "surface_style", widgetOptions.surfaceStyle)
                .putString(str + "graphic_scale", widgetOptions.graphicScale)
                .putString(str + "theme", widgetOptions.theme)
                .putString(str + "accent", widgetOptions.accent)
                .putInt(str + "opacity", widgetOptions.opacity)
                .putString(str + "reset_mode", widgetOptions.resetMode)
                .putString(str + "display_mode", widgetOptions.displayMode)
                .putString(str + "metric_mode", metricMode)
                .putString(str + "visible_meters", widgetOptions.visibleMeters)
                .putBoolean(str + "show_title", widgetOptions.showTitle)
                .putBoolean(str + "show_plan", widgetOptions.showPlan)
                .putBoolean(str + "show_updated", widgetOptions.showUpdated)
                .putBoolean(str + "show_refresh", widgetOptions.showRefresh)
                .putBoolean(str + "show_reset_credits", widgetOptions.showResetCredits)
                .putBoolean(str + "show_reset_action", widgetOptions.showResetAction)
                .putBoolean(str + "show_percent_symbol", widgetOptions.showPercentSymbol)
                .apply();
    }

    private static String metricModeFromVisible(String visibleCsv) {
        java.util.List<String> keys = WidgetMeters.parse(visibleCsv);
        boolean five = WidgetMeters.contains(keys, WidgetMeters.FIVE_HOUR);
        boolean weekly = WidgetMeters.contains(keys, WidgetMeters.WEEKLY);
        if (five && !weekly) {
            return WidgetOptions.METRIC_FIVE_HOUR;
        }
        if (weekly && !five) {
            return WidgetOptions.METRIC_WEEKLY;
        }
        return WidgetOptions.METRIC_BOTH;
    }

    public static void deleteWidgetOptions(Context context, int i) {
        String str = "widget_" + i + "_";
        SharedPreferences.Editor editorEdit = prefs(context).edit();
        for (String str2 : new String[]{"style", "layout", "density", "surface_style",
                "graphic_scale", "theme", "accent", "opacity", "reset_mode", "display_mode",
                "metric_mode", "visible_meters", "show_title", "show_plan", "show_updated",
                "show_refresh", "show_reset_credits", "show_reset_action", "show_percent_symbol",
                "tap_action"}) {
            editorEdit.remove(str + str2);
        }
        editorEdit.apply();
    }

    public static LockWidgetOptions loadLockWidgetOptions(Context context, int i) {
        if (i == 0) {
            return LockWidgetOptions.defaults();
        }
        SharedPreferences sharedPreferencesPrefs = prefs(context);
        String str = "lock_widget_" + i + "_";
        return new LockWidgetOptions(
                sharedPreferencesPrefs.getString(str + "metric_mode", "both"),
                sharedPreferencesPrefs.getBoolean(str + "show_reset_credits", false),
                sharedPreferencesPrefs.getBoolean(str + "show_reset_action", false),
                sharedPreferencesPrefs.getBoolean(str + "show_countdown", true),
                sharedPreferencesPrefs.getString(str + "visible_meters", ""));
    }

    public static void saveLockWidgetOptions(Context context, int i, LockWidgetOptions lockWidgetOptions) {
        if (i != 0 && lockWidgetOptions != null) {
            String str = "lock_widget_" + i + "_";
            prefs(context).edit()
                    .putString(str + "metric_mode", lockWidgetOptions.metricMode)
                    .putBoolean(str + "show_reset_credits", lockWidgetOptions.showResetCredits)
                    .putBoolean(str + "show_reset_action", lockWidgetOptions.showResetAction)
                    .putBoolean(str + "show_countdown", lockWidgetOptions.showCountdown)
                    .putString(str + "visible_meters", lockWidgetOptions.visibleMeters)
                    .apply();
        }
    }

    public static void deleteLockWidgetOptions(Context context, int i) {
        String str = "lock_widget_" + i + "_";
        prefs(context).edit()
                .remove(str + "metric_mode")
                .remove(str + "show_reset_credits")
                .remove(str + "show_reset_action")
                .remove(str + "show_countdown")
                .remove(str + "visible_meters")
                .apply();
    }

    public static void setOAuthPending(Context context, boolean pending, String url) {
        setOAuthPending(context, AccountContainerStore.selectedId(context), pending, url);
    }

    static void setOAuthPending(Context context, String containerId, boolean pending, String url) {
        SharedPreferences prefs = prefs(context);
        String pendingKey = accountKey(containerId, KEY_OAUTH_PENDING);
        String urlKey = accountKey(containerId, KEY_OAUTH_URL);
        String startedKey = accountKey(containerId, KEY_OAUTH_STARTED_AT);
        SharedPreferences.Editor editor = prefs.edit().putBoolean(pendingKey, pending)
                .putString(urlKey, url == null ? "" : url);
        if (pending) {
            editor.putLong(startedKey, System.currentTimeMillis());
        } else {
            editor.remove(startedKey);
        }
        editor.apply();
    }

    public static boolean isOAuthPending(Context context) {
        return isOAuthPending(context, AccountContainerStore.selectedId(context));
    }

    static boolean isOAuthPending(Context context, String containerId) {
        SharedPreferences shared = prefs(context);
        String pendingKey = accountKey(containerId, KEY_OAUTH_PENDING);
        String startedKey = accountKey(containerId, KEY_OAUTH_STARTED_AT);
        boolean scoped = shared.contains(pendingKey);
        boolean pending = scoped
                ? shared.getBoolean(pendingKey, false)
                : legacyBoolean(context, containerId, KEY_OAUTH_PENDING, false);
        if (!pending) return false;
        long started = scoped
                ? shared.getLong(startedKey, 0L)
                : legacyLong(context, containerId, KEY_OAUTH_STARTED_AT, 0L);
        if (started <= 0 || System.currentTimeMillis() - started > OAUTH_STALE_AFTER_MS) {
            setOAuthPending(context, containerId, false, "");
            return false;
        }
        return true;
    }

    public static String getOAuthUrl(Context context) {
        return getOAuthUrl(context, AccountContainerStore.selectedId(context));
    }

    static String getOAuthUrl(Context context, String containerId) {
        if (!isOAuthPending(context, containerId)) return "";
        SharedPreferences shared = prefs(context);
        String key = accountKey(containerId, KEY_OAUTH_URL);
        return shared.contains(key)
                ? shared.getString(key, "")
                : legacyString(context, containerId, KEY_OAUTH_URL, "");
    }

    public static boolean isOnboardingComplete(Context context) {
        String containerId = AccountContainerStore.selectedId(context);
        SharedPreferences shared = prefs(context);
        String key = accountKey(containerId, KEY_ONBOARDING_COMPLETE);
        if (shared.contains(key)) return shared.getBoolean(key, false);
        return legacyBoolean(context, containerId, KEY_ONBOARDING_COMPLETE, false);
    }

    public static int getOnboardingStep(Context context) {
        String containerId = AccountContainerStore.selectedId(context);
        SharedPreferences shared = prefs(context);
        String key = accountKey(containerId, KEY_ONBOARDING_STEP);
        int value = shared.contains(key)
                ? shared.getInt(key, OnboardingFlow.STEP_WELCOME)
                : (int) legacyLong(context, containerId, KEY_ONBOARDING_STEP,
                        OnboardingFlow.STEP_WELCOME);
        return OnboardingFlow.normalizeStep(value);
    }

    public static void setOnboardingStep(Context context, int step) {
        prefs(context).edit()
                .putInt(accountKey(AccountContainerStore.selectedId(context), KEY_ONBOARDING_STEP),
                        OnboardingFlow.normalizeStep(step))
                .apply();
    }

    public static void completeOnboarding(Context context) {
        String containerId = AccountContainerStore.selectedId(context);
        prefs(context).edit()
                .putBoolean(accountKey(containerId, KEY_ONBOARDING_COMPLETE), true)
                .remove(accountKey(containerId, KEY_ONBOARDING_STEP))
                .apply();
    }

    private static String scopedString(Context context, String containerId,
            String base, String fallback) {
        SharedPreferences shared = prefs(context);
        String key = accountKey(containerId, base);
        if (shared.contains(key)) return shared.getString(key, fallback);
        return legacyString(context, containerId, base, fallback);
    }

    private static long scopedLong(Context context, String containerId,
            String base, long fallback) {
        SharedPreferences shared = prefs(context);
        String key = accountKey(containerId, base);
        if (shared.contains(key)) {
            try {
                Object value = shared.getAll().get(key);
                return value instanceof Integer ? ((Integer) value).longValue()
                        : value instanceof Long ? (Long) value : fallback;
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return legacyLong(context, containerId, base, fallback);
    }

    static void clearAccountData(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return;
        String[] bases = {
                KEY_SNAPSHOT, KEY_ERROR, KEY_ERROR_AT,
                KEY_OAUTH_PENDING, KEY_OAUTH_STARTED_AT, KEY_OAUTH_URL,
                KEY_ONBOARDING_COMPLETE, KEY_ONBOARDING_STEP,
                KEY_REFRESH_FAILURES,
                KEY_RESET_CREDITS, KEY_RESET_ERROR, KEY_RESET_ERROR_AT,
                KEY_HISTORY_FIVE_HOUR, KEY_HISTORY_WEEKLY, KEY_HISTORY_MONTHLY
        };
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String base : bases) editor.remove(accountKey(containerId, base));
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            for (String base : bases) editor.remove(base);
        }
        editor.apply();
    }

    private static boolean hasAnySignedInAccount(Context context) {
        for (AccountContainerStore.Account account : AccountContainerStore.all(context)) {
            if (SecureTokenStore.isSignedIn(context, account.id)) return true;
        }
        return false;
    }

    private static String accountKey(String containerId, String base) {
        String id = containerId == null ? "" : containerId.trim();
        return base + "::" + id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static boolean legacyBoolean(Context context, String containerId,
            String key, boolean fallback) {
        return AccountContainerStore.isLegacyOwner(context, containerId)
                ? prefs(context).getBoolean(key, fallback) : fallback;
    }

    private static long legacyLong(Context context, String containerId,
            String key, long fallback) {
        if (!AccountContainerStore.isLegacyOwner(context, containerId)) return fallback;
        SharedPreferences shared = prefs(context);
        try {
            if (!shared.contains(key)) return fallback;
            Object value = shared.getAll().get(key);
            return value instanceof Integer ? ((Integer) value).longValue()
                    : value instanceof Long ? (Long) value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String legacyString(Context context, String containerId,
            String key, String fallback) {
        return AccountContainerStore.isLegacyOwner(context, containerId)
                ? prefs(context).getString(key, fallback) : fallback;
    }

    private static String trim(String str, String str2) {
        if (str != null && !str.trim().isEmpty()) {
            str2 = str.trim();
        }
        return str2.length() > 240 ? str2.substring(0, 240) : str2;
    }
}
