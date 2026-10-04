package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import dev.kopandazavr.codexmonitor.wear.PhoneWearSync;
import java.net.URI;
import javax.net.ssl.HttpsURLConnection;

/* JADX INFO: loaded from: classes.dex */
public final class UsageApi {
    static final Object NETWORK_LOCK = new Object();

    private UsageApi() {
    }

    /** Direct callers are explicit/user-driven refreshes and must bypass subscription TTL. */
    public static UsageSnapshot refreshAndCache(Context context) throws Exception {
        return refreshAndCache(context, AccountContainerStore.selectedId(context));
    }

    static UsageSnapshot refreshAndCache(Context context, String containerId) throws Exception {
        return refreshAndCacheInternal(context, containerId, true, "manual_direct");
    }

    /** Scheduler entry point: immediate user actions may force side data; periodic work does not. */
    static UsageSnapshot refreshAndCacheScheduled(Context context, boolean forceSubscription,
            String trigger) throws Exception {
        return refreshAndCacheScheduled(context, AccountContainerStore.selectedId(context),
                forceSubscription, trigger);
    }

    static UsageSnapshot refreshAndCacheScheduled(Context context, String containerId,
            boolean forceSubscription, String trigger) throws Exception {
        return refreshAndCacheInternal(context, containerId, forceSubscription,
                trigger == null || trigger.trim().isEmpty() ? "scheduled" : trigger.trim());
    }

    private static UsageSnapshot refreshAndCacheInternal(Context context, String containerId,
            boolean forceSubscription, String trigger) throws Exception {
        AuthTokens authTokens;
        Response responseRequestUsage;
        String str;
        UsageSnapshot usageSnapshot;
        long started = SystemClock.elapsedRealtime();
        String safeTrigger = trigger == null || trigger.trim().isEmpty()
                ? "unspecified" : trigger.trim();
        DiagnosticLog.info(context, "refresh", "usage_refresh_started",
                "trigger", safeTrigger,
                "force_subscription", forceSubscription);
        try {
            synchronized (NETWORK_LOCK) {
                AuthTokens authTokensUsableTokens = usableTokens(context, containerId);
                Response responseRequestUsage2 = requestUsage(context, authTokensUsableTokens,
                        safeTrigger);
                if (responseRequestUsage2.status == 401) {
                    DiagnosticLog.warn(context, "auth", "usage_token_rejected_refreshing",
                            "trigger", safeTrigger);
                    AuthTokens authTokensRefresh = OAuthClient.refresh(context,
                            authTokensUsableTokens);
                    SecureTokenStore.save(context, containerId, authTokensRefresh);
                    authTokens = authTokensRefresh;
                    responseRequestUsage = requestUsage(context, authTokensRefresh, safeTrigger);
                } else {
                    authTokens = authTokensUsableTokens;
                    responseRequestUsage = responseRequestUsage2;
                }
                if (responseRequestUsage.status < 200 || responseRequestUsage.status >= 300) {
                    if (responseRequestUsage.status == 403) {
                        str = "Codex usage access was denied for this account.";
                    } else {
                        str = responseRequestUsage.status == 404
                                ? "The Codex usage endpoint is unavailable or has changed."
                                : "Usage refresh failed (HTTP " + responseRequestUsage.status + ").";
                    }
                    throw new Exception(OAuthClient.readError(responseRequestUsage.body, str));
                }
                long parsedAt = System.currentTimeMillis();
                usageSnapshot = UsageParser.parse(responseRequestUsage.body, parsedAt);
                if (!usageSnapshot.hasDisplayableData()) {
                    DiagnosticLog.warn(context, "refresh", "usage_snapshot_rejected",
                            "trigger", safeTrigger, "reason", "no_displayable_data");
                    throw new Exception("OpenAI returned no recognizable Codex usage data.");
                }
                UsageSnapshot previousSnapshot =
                        AppPreferences.loadSnapshot(context, containerId);
                DiagnosticLog.info(context, "refresh", "usage_snapshot_parsed",
                        "trigger", safeTrigger,
                        "previous_id", snapshotIdentity(previousSnapshot),
                        "previous_fetched_at", fetchedAt(previousSnapshot),
                        "new_id", snapshotIdentity(usageSnapshot),
                        "new_fetched_at", usageSnapshot.fetchedAtMillis,
                        "five_reset", resetAt(usageSnapshot.fiveHour, usageSnapshot.fetchedAtMillis),
                        "long_reset", resetAt(usageSnapshot.longWindow(), usageSnapshot.fetchedAtMillis));
                if (!AppPreferences.saveSnapshot(context, containerId, usageSnapshot)) {
                    DiagnosticLog.warn(context, "refresh", "usage_snapshot_rejected",
                            "trigger", safeTrigger, "reason", "persistence_failed");
                    throw new Exception("Usage was received, but it could not be saved on this device.");
                }
                DiagnosticLog.info(context, "refresh", "usage_snapshot_replaced",
                        "trigger", safeTrigger,
                        "previous_id", snapshotIdentity(previousSnapshot),
                        "new_id", snapshotIdentity(usageSnapshot),
                        "new_fetched_at", usageSnapshot.fetchedAtMillis);
                UsageHistoryRecorder.record(context, containerId, usageSnapshot);
                boolean selected = containerId.equals(AccountContainerStore.selectedId(context));
                if (selected) PhoneWearSync.pushUsage(context, usageSnapshot);
                try {
                    // Explicit refreshes force this request; periodic work retains the normal TTL.
                    SubscriptionApi.refreshAndCacheLocked(context, containerId, authTokens,
                            forceSubscription, safeTrigger);
                } catch (RuntimeException exception) {
                    DiagnosticLog.error(context, "refresh", "subscription_side_refresh_failed",
                            exception, "trigger", safeTrigger);
                }
                notifyUsageUpdated(context, containerId, safeTrigger, usageSnapshot);
                if (selected) NowBarManager.onUsageUpdated(context, usageSnapshot);
                // NowBarManager remains authoritative for monitor state/alarms; the compact renderer
                // then rebuilds the current persistent usage/process surfaces from saved state.
                DualUsageNotificationManager.postFromSnapshot(context, containerId, usageSnapshot);
                DiagnosticLog.info(context, "refresh", "usage_notification_rebuilt",
                        "trigger", safeTrigger,
                        "snapshot_id", snapshotIdentity(usageSnapshot));
                ResetNotificationManager.onUsageUpdated(
                        context, containerId, previousSnapshot, usageSnapshot);
                try {
                    ResetAlertScheduler.scheduleFromSnapshot(
                            context, containerId, usageSnapshot);
                } catch (RuntimeException exception) {
                    DiagnosticLog.error(context, "scheduler", "reset_alert_schedule_failed",
                            exception);
                }
                try {
                    ResetCreditApi.refreshAndCacheLocked(context, containerId, authTokens);
                } catch (Exception exception) {
                    DiagnosticLog.error(context, "refresh", "reset_credit_side_refresh_failed",
                            exception);
                    if (selected) {
                        ResetNotificationManager.onResetCreditSummaryUpdated(context,
                                usageSnapshot.resetCreditsAvailable);
                    }
                    AppPreferences.setResetCreditsError(context, containerId,
                            safeMessage(exception));
                }
            }
        } catch (Exception exception) {
            DiagnosticLog.error(context, "refresh", "usage_refresh_failed", exception,
                    "trigger", safeTrigger,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            throw exception;
        }
        DiagnosticLog.info(context, "refresh", "usage_refresh_succeeded",
                "trigger", safeTrigger,
                "duration_ms", SystemClock.elapsedRealtime() - started,
                "snapshot_id", snapshotIdentity(usageSnapshot),
                "five_hour", usageSnapshot.fiveHour != null,
                "weekly", usageSnapshot.weekly != null,
                "monthly", usageSnapshot.monthly != null,
                "additional_limits", usageSnapshot.additionalLimits.size());
        return usageSnapshot;
    }

    static AuthTokens usableTokens(Context context) throws Exception {
        return usableTokens(context, AccountContainerStore.selectedId(context));
    }

    static AuthTokens usableTokens(Context context, String containerId) throws Exception {
        AuthTokens authTokensLoad = SecureTokenStore.load(context, containerId);
        if (authTokensLoad == null) {
            throw new Exception("Sign in to ChatGPT first.");
        }
        if (authTokensLoad.shouldRefresh(System.currentTimeMillis())) {
            DiagnosticLog.info(context, "auth", "token_refresh_due",
                    "container_id", containerId);
            AuthTokens authTokensRefresh = OAuthClient.refresh(context, authTokensLoad);
            SecureTokenStore.save(context, containerId, authTokensRefresh);
            return authTokensRefresh;
        }
        return authTokensLoad;
    }

    private static Response requestUsage(Context context, AuthTokens authTokens, String trigger)
            throws Exception {
        HttpsURLConnection httpsURLConnection = (HttpsURLConnection)
                URI.create(AppConstants.USAGE_URL).toURL().openConnection();
        long started = SystemClock.elapsedRealtime();
        DiagnosticLog.info(context, "network", "request_started",
                "operation", "usage",
                "trigger", trigger,
                "method", "GET",
                "url", DiagnosticSanitizer.safeUrl(AppConstants.USAGE_URL));
        try {
            applyHeaders(httpsURLConnection, authTokens);
            httpsURLConnection.setRequestMethod("GET");
            int responseCode = httpsURLConnection.getResponseCode();
            String body = OAuthClient.readBody(httpsURLConnection, responseCode);
            DiagnosticLog.info(context, "network", "request_finished",
                    "operation", "usage",
                    "trigger", trigger,
                    "status", responseCode,
                    "duration_ms", SystemClock.elapsedRealtime() - started,
                    "response_bytes",
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            return new Response(responseCode, body);
        } catch (Exception exception) {
            DiagnosticLog.error(context, "network", "request_failed", exception,
                    "operation", "usage",
                    "trigger", trigger,
                    "duration_ms", SystemClock.elapsedRealtime() - started);
            throw exception;
        } finally {
            httpsURLConnection.disconnect();
        }
    }

    static void applyHeaders(HttpsURLConnection httpsURLConnection, AuthTokens authTokens) {
        httpsURLConnection.setConnectTimeout(15000);
        httpsURLConnection.setReadTimeout(25000);
        httpsURLConnection.setUseCaches(false);
        httpsURLConnection.setRequestProperty("Accept", "application/json");
        httpsURLConnection.setRequestProperty("Authorization", "Bearer " + authTokens.accessToken);
        httpsURLConnection.setRequestProperty("User-Agent", AppConstants.userAgent());
        httpsURLConnection.setRequestProperty("originator", AppConstants.ORIGINATOR);
        if (!authTokens.accountId.isEmpty()) {
            httpsURLConnection.setRequestProperty("ChatGPT-Account-Id", authTokens.accountId);
        }
    }

    static void installCookieManager() {
        // Usage and reset-credit requests authenticate exclusively with explicit Bearer headers.
        // A process-global CookieHandler would let one account leak cookies into another account,
        // so multi-account mode intentionally does not install one.
    }

    static String safeMessage(Exception exc) {
        String message = exc == null ? "" : exc.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Reset-credit refresh failed.";
        }
        String strTrim = message.trim();
        return strTrim.length() > 240 ? strTrim.substring(0, 240) : strTrim;
    }

    private static void notifyUsageUpdated(Context context, String containerId,
            String trigger, UsageSnapshot snapshot) {
        try {
            context.sendBroadcast(new Intent(AppConstants.ACTION_USAGE_UPDATED)
                            .setPackage(context.getPackageName())
                            .putExtra(OAuthService.EXTRA_CONTAINER_ID, containerId),
                    AppConstants.INTERNAL_PERMISSION);
            DiagnosticLog.info(context, "refresh", "usage_update_broadcast_sent",
                    "trigger", trigger,
                    "snapshot_id", snapshotIdentity(snapshot));
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "refresh", "usage_update_broadcast_failed",
                    exception, "trigger", trigger);
        }
    }

    private static long fetchedAt(UsageSnapshot snapshot) {
        return snapshot == null ? 0L : snapshot.fetchedAtMillis;
    }

    private static long resetAt(UsageWindow window, long observedAt) {
        return window == null ? 0L : window.effectiveResetAtMillis(observedAt);
    }

    private static String snapshotIdentity(UsageSnapshot snapshot) {
        if (snapshot == null) return "none";
        try {
            return snapshot.fetchedAtMillis + ":" + Integer.toHexString(
                    snapshot.toJson().toString().hashCode());
        } catch (Exception ignored) {
            return String.valueOf(snapshot.fetchedAtMillis);
        }
    }

    private static final class Response {
        final String body;
        final int status;

        Response(int i, String str) {
            this.status = i;
            this.body = str == null ? "" : str;
        }
    }
}
