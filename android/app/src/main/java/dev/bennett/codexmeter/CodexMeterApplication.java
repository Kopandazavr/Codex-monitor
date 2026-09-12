package dev.bennett.codexmeter;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.content.ComponentCallbacks2;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Installs process/screen diagnostics and enforces the small app's canonical automatic defaults. */
public final class CodexMeterApplication extends Application
        implements Application.ActivityLifecycleCallbacks {
    private static final String RECONCILE_PREFS = "codex_notification_reconcile_v1";
    private static final String KEY_LAST_UPDATE_TIME = "last_update_time";
    private static final long BACKGROUND_DEBOUNCE_MS = 750L;
    private Handler lifecycleHandler;
    private int startedActivities;
    private boolean processBackgrounded = true;
    private final Runnable markProcessBackgrounded = () -> {
        if (startedActivities == 0) {
            processBackgrounded = true;
            ForegroundUsageRefresh.stopActivePolling(this);
            DiagnosticLog.info(this, "process", "application_backgrounded");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        lifecycleHandler = new Handler(Looper.getMainLooper());
        normalizeAutomaticDefaults();
        DiagnosticLog.install(this);
        if (ProcessNotificationScheduler.DIAGNOSTIC_FIVE_SECOND_REPAINT) {
            DiagnosticLog.setTemporaryTestCapture(this, true);
        }
        registerActivityLifecycleCallbacks(this);
        DiagnosticLog.info(this, "process", "application_started");
        reconcileNotificationSurfacesAfterInstallOrUpdate();
        IdleReminderManager.restore(this);
        DualUsageNotificationManager.repostDelayed(this, 350L);
    }

    private void normalizeAutomaticDefaults() {
        if (!AppPreferences.getRefreshOnLaunch(this)) {
            AppPreferences.setRefreshOnLaunch(this, true);
        }
        if (!AppPreferences.getAutomaticRefresh(this)) {
            AppPreferences.setAutomaticRefresh(this, true);
        }
        if (!UsagePacePreferences.isEnabled(this)) {
            UsagePacePreferences.setEnabled(this, true);
        }
    }

    /**
     * PackageInfo.lastUpdateTime changes on every installed APK replacement, even when a
     * development build deliberately keeps the same versionCode. That makes it a better
     * first-reconciliation marker than versionCode for the persistent test lineage.
     */
    private void reconcileNotificationSurfacesAfterInstallOrUpdate() {
        long installedAt;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            installedAt = info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException | RuntimeException exception) {
            DiagnosticLog.warn(this, "notification_surface", "install_marker_read_failed",
                    "error", exception.getClass().getSimpleName());
            return;
        }
        SharedPreferences state = getSharedPreferences(RECONCILE_PREFS, MODE_PRIVATE);
        long previous = state.getLong(KEY_LAST_UPDATE_TIME, 0L);
        if (previous == installedAt) return;

        NotificationManager manager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            try {
                // App-scoped cancelAll removes old usage/process/reminder/alert cards left by the
                // previous runtime. The delayed state-driven repost below rebuilds only valid ones.
                manager.cancelAll();
            } catch (RuntimeException exception) {
                DiagnosticLog.warn(this, "notification_surface", "install_clear_failed",
                        "error", exception.getClass().getSimpleName());
            }
        }
        state.edit().putLong(KEY_LAST_UPDATE_TIME, installedAt).apply();
        DiagnosticLog.info(this, "notification_surface", "install_reconciled",
                "previous_update", previous,
                "current_update", installedAt);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        DiagnosticLog.info(this, "process", "memory_trimmed", "level", level,
                "ui_hidden", level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        DiagnosticLog.warn(this, "process", "low_memory");
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle state) {
        DiagnosticLog.info(this, "screen", "created",
                "activity", activity.getClass().getSimpleName(),
                "restored", state != null);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        DiagnosticLog.info(this, "screen", "resumed",
                "activity", activity.getClass().getSimpleName());
        Branding.apply(activity);
        HomeVersionLabel.apply(activity);
        // Settings can start the native monitor from cached usage without a network refresh.
        // Collapse rapid Activity transitions and never let maintenance notification rendering
        // kill the foreground screen if an OEM RemoteViews path throws.
        NotificationRepostGuard.repostAfterActivityResume(this);
    }

    @Override
    public void onActivityStopped(Activity activity) {
        DiagnosticLog.info(this, "screen", "stopped",
                "activity", activity.getClass().getSimpleName(),
                "changing_configuration", activity.isChangingConfigurations());
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0 && lifecycleHandler != null) {
            lifecycleHandler.removeCallbacks(markProcessBackgrounded);
            lifecycleHandler.postDelayed(markProcessBackgrounded, BACKGROUND_DEBOUNCE_MS);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (lifecycleHandler != null) {
            lifecycleHandler.removeCallbacks(markProcessBackgrounded);
        }
        boolean returningFromBackground = startedActivities == 0 && processBackgrounded;
        startedActivities++;
        if (returningFromBackground) {
            processBackgrounded = false;
            DiagnosticLog.info(this, "process", "application_foregrounded",
                    "activity", activity.getClass().getSimpleName());
            // Suspend competing periodic jobs before the immediate real foreground fetch. The
            // dashboard path may request the same fetch; request() deliberately coalesces it.
            ForegroundUsageRefresh.startActivePolling(this);
            ForegroundUsageRefresh.request(this, "foreground_transition");
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle state) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
