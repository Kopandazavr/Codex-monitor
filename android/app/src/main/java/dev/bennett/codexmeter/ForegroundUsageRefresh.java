package dev.bennett.codexmeter;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coalesces app-foreground usage refreshes while keeping the last cached snapshot visible.
 * Presentation state is intentionally process-local: persisted usage data remains authoritative.
 *
 * While at least one app Activity remains in the foreground, usage itself is refreshed once per
 * minute. That foreground-only cadence is intentionally separate from the adaptive JobScheduler
 * cadence used in background and from the much faster local-only notification repaint cadence.
 */
final class ForegroundUsageRefresh {
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long ACTIVE_POLL_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);
    private static volatile boolean stale;
    private static volatile String activeTrigger = "";
    private static volatile Context pollingContext;
    private static volatile boolean activePolling;
    private static final Runnable ACTIVE_POLL = () -> {
        Context app = pollingContext;
        if (!activePolling || app == null) return;
        request(app, "foreground_periodic", false);
        if (activePolling) MAIN.postDelayed(ACTIVE_POLL, ACTIVE_POLL_INTERVAL_MS);
    };

    private ForegroundUsageRefresh() {
    }

    /** Lifecycle/manual foreground entry: usage + side subscription data should both be fresh. */
    static boolean request(Context context, String trigger) {
        return request(context, trigger, true);
    }

    /** Start the cheap foreground-only remote cadence; background scheduling remains adaptive. */
    static void startActivePolling(Context context) {
        Context app = appContext(context);
        if (app == null) return;
        pollingContext = app;
        activePolling = true;
        MAIN.removeCallbacks(ACTIVE_POLL);
        MAIN.postDelayed(ACTIVE_POLL, ACTIVE_POLL_INTERVAL_MS);
        DiagnosticLog.info(app, "refresh", "foreground_polling_started",
                "interval_ms", ACTIVE_POLL_INTERVAL_MS);
    }

    static void stopActivePolling(Context context) {
        Context app = appContext(context);
        activePolling = false;
        pollingContext = null;
        MAIN.removeCallbacks(ACTIVE_POLL);
        if (app != null) {
            DiagnosticLog.info(app, "refresh", "foreground_polling_stopped");
        }
    }

    private static boolean request(Context context, String trigger, boolean forceSubscription) {
        Context app = appContext(context);
        if (app == null || !SecureTokenStore.isSignedIn(app)) return false;
        String safeTrigger = trigger == null || trigger.trim().isEmpty()
                ? "foreground" : trigger.trim();
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            DiagnosticLog.info(app, "refresh", "foreground_refresh_coalesced",
                    "trigger", safeTrigger,
                    "active_trigger", activeTrigger);
            return false;
        }

        activeTrigger = safeTrigger;
        stale = false;
        DiagnosticLog.info(app, "refresh", "foreground_refresh_started",
                "trigger", safeTrigger,
                "force_subscription", forceSubscription);
        notifyUiState(app, "started", safeTrigger);
        EXECUTOR.execute(() -> run(app, safeTrigger, forceSubscription));
        return true;
    }

    static boolean isInFlight() {
        return IN_FLIGHT.get();
    }

    static boolean isStale() {
        return stale;
    }

    static String activeTrigger() {
        return activeTrigger;
    }

    private static void run(Context app, String trigger, boolean forceSubscription) {
        long started = SystemClock.elapsedRealtime();
        boolean success = false;
        try {
            UsageApi.refreshAndCacheScheduled(app, forceSubscription, trigger);
            success = true;
            stale = false;
        } catch (Exception exception) {
            stale = true;
            DiagnosticLog.warn(app, "refresh", "foreground_refresh_retained_cached_snapshot",
                    "trigger", trigger,
                    "error", exception.getClass().getSimpleName());
        } finally {
            IN_FLIGHT.set(false);
            activeTrigger = "";
            DiagnosticLog.info(app, "refresh", "foreground_refresh_finished",
                    "trigger", trigger,
                    "success", success,
                    "stale", stale,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            notifyUiState(app, success ? "succeeded" : "failed", trigger);
            RefreshScheduler.schedulePeriodic(app);
        }
    }

    private static void notifyUiState(Context context, String state, String trigger) {
        try {
            context.sendBroadcast(new Intent(AppConstants.ACTION_USAGE_UPDATED)
                            .setPackage(context.getPackageName()),
                    AppConstants.INTERNAL_PERMISSION);
            DiagnosticLog.info(context, "refresh", "foreground_ui_state_broadcast",
                    "state", state,
                    "trigger", trigger);
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "refresh", "foreground_ui_state_broadcast_failed",
                    "state", state,
                    "trigger", trigger,
                    "error", exception.getClass().getSimpleName());
        }
    }

    private static Context appContext(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        return app == null ? context : app;
    }
}
