package dev.kopandazavr.codexmonitor;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.RevokeAccessRequest;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Scope;
import java.util.Collections;
import java.util.List;

/** Google Calendar OAuth state, deliberately separate from the app's ChatGPT OAuth flow. */
final class GoogleCalendarAuthorization {
    static final String CALENDAR_SCOPE =
            "https://www.googleapis.com/auth/calendar.events.readonly";
    private static final String PREFS = "codex_google_calendar_auth_v1";
    private static final String KEY_CONNECTED = "connected";
    private static final String KEY_NEEDS_ACTION = "needs_action";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final long TOKEN_CACHE_MS = 45L * 60L * 1000L;

    private static volatile String cachedToken;
    private static volatile long cachedTokenAt;

    interface TokenCallback {
        void onResult(String token);
    }

    interface ActionCallback {
        void onFinished(boolean success, String message);
    }

    static final class AuthOutcome {
        final boolean success;
        final String message;

        AuthOutcome(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }
    }

    private static final class FailureInfo {
        final int statusCode;
        final String statusName;
        final String kind;
        final boolean recoverable;
        final String errorClass;

        FailureInfo(int statusCode, String statusName, String kind,
                boolean recoverable, String errorClass) {
            this.statusCode = statusCode;
            this.statusName = statusName;
            this.kind = kind;
            this.recoverable = recoverable;
            this.errorClass = errorClass;
        }
    }

    private GoogleCalendarAuthorization() {
    }

    static boolean isConnected(Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs.getBoolean(KEY_CONNECTED, false)
                && !prefs.getBoolean(KEY_NEEDS_ACTION, false);
    }

    static String statusSummary(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getBoolean(KEY_CONNECTED, false)
                && !prefs.getBoolean(KEY_NEEDS_ACTION, false)) {
            return "Connected · direct Calendar API";
        }
        if (prefs.getBoolean(KEY_NEEDS_ACTION, false)) {
            return "Reconnect required";
        }
        String error = prefs.getString(KEY_LAST_ERROR, "");
        return error == null || error.isEmpty()
                ? "Not connected · tap to authorize"
                : "Not connected · tap to retry";
    }

    static boolean shouldRetryTransient(Context context) {
        String error = prefs(context).getString(KEY_LAST_ERROR, "");
        return "transient:NETWORK_ERROR".equals(error)
                || "transient:INTERNAL_ERROR".equals(error);
    }

    static void beginInteractive(Activity activity, int requestCode,
            ActionCallback callback) {
        DiagnosticLog.info(activity, "calendar_api", "authorization_started",
                "scope", "calendar.events.readonly");
        AuthorizationClient client = Identity.getAuthorizationClient(activity);
        client.authorize(request())
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        PendingIntent pending = result.getPendingIntent();
                        if (pending == null) {
                            markNeedsAction(activity, "resolution_missing");
                            DiagnosticLog.warn(activity, "calendar_api",
                                    "authorization_resolution_missing",
                                    "recoverable", false);
                            callback.onFinished(false,
                                    "Google Calendar authorization is unavailable.");
                            return;
                        }
                        try {
                            DiagnosticLog.info(activity, "calendar_api",
                                    "authorization_resolution_launched");
                            activity.startIntentSenderForResult(pending.getIntentSender(),
                                    requestCode, null, 0, 0, 0);
                            callback.onFinished(false,
                                    "Choose a Google account and allow Calendar.");
                        } catch (IntentSender.SendIntentException exception) {
                            AuthOutcome outcome = recordFailure(activity,
                                    "resolution_launch", exception);
                            callback.onFinished(false, outcome.message);
                        }
                    } else if (accept(activity, result)) {
                        callback.onFinished(true, "Google Calendar connected.");
                    } else {
                        markNeedsAction(activity, "token_missing");
                        DiagnosticLog.warn(activity, "calendar_api",
                                "authorization_token_missing",
                                "recoverable", false);
                        callback.onFinished(false,
                                "Google Calendar authorization returned no access token.");
                    }
                })
                .addOnFailureListener(exception -> {
                    AuthOutcome outcome = recordFailure(activity, "authorize", exception);
                    callback.onFinished(false, outcome.message);
                });
    }

    /**
     * Consumes the resolution activity result and returns a classified user-facing outcome.
     * Diagnostics intentionally record only status/result metadata, never tokens/account data.
     */
    static AuthOutcome consumeInteractiveResult(Activity activity, int resultCode, Intent data) {
        DiagnosticLog.info(activity, "calendar_api", "authorization_activity_result",
                "result_code", resultCode,
                "data_present", data != null);
        // Some Google Play services failures return a non-OK Activity result together with
        // diagnostic result data. Parse that data first so OAuth/client misconfiguration is not
        // accidentally flattened into a generic user-cancel path.
        if (data != null) {
            try {
                AuthorizationResult result = Identity.getAuthorizationClient(activity)
                        .getAuthorizationResultFromIntent(data);
                if (accept(activity, result)) {
                    return new AuthOutcome(true, "Google Calendar connected.");
                }
                markNeedsAction(activity, "token_missing");
                DiagnosticLog.warn(activity, "calendar_api", "authorization_token_missing",
                        "stage", "activity_result",
                        "result_code", resultCode,
                        "recoverable", false);
                return new AuthOutcome(false,
                        "Google Calendar authorization returned no access token.");
            } catch (Exception exception) {
                return recordFailure(activity, "activity_result", exception);
            }
        }
        if (resultCode != Activity.RESULT_OK) {
            markDisconnected(activity);
            DiagnosticLog.info(activity, "calendar_api", "authorization_cancelled",
                    "result_code", resultCode,
                    "data_present", false);
            return new AuthOutcome(false, "Google Calendar authorization was canceled.");
        }
        markNeedsAction(activity, "result_missing");
        DiagnosticLog.warn(activity, "calendar_api", "authorization_result_missing",
                "result_code", resultCode,
                "recoverable", true);
        return new AuthOutcome(false,
                "Google Calendar returned no authorization result. Try again.");
    }

    /** Compatibility wrapper retained for bounded callers/tests. */
    static boolean consumeInteractiveResult(Activity activity, Intent data) {
        return consumeInteractiveResult(activity, Activity.RESULT_OK, data).success;
    }

    static void accessToken(Context context, TokenCallback callback) {
        if (context == null) {
            callback.onResult(null);
            return;
        }
        String token = cachedToken;
        long age = System.currentTimeMillis() - cachedTokenAt;
        if (token != null && !token.isEmpty() && age >= 0L && age < TOKEN_CACHE_MS) {
            callback.onResult(token);
            return;
        }
        Identity.getAuthorizationClient(context).authorize(request())
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        markNeedsAction(context, "resolution_required");
                        DiagnosticLog.warn(context, "calendar_api",
                                "token_resolution_required",
                                "recoverable", true);
                        callback.onResult(null);
                        return;
                    }
                    if (!accept(context, result)) {
                        markNeedsAction(context, "token_missing");
                        DiagnosticLog.warn(context, "calendar_api",
                                "token_refresh_missing",
                                "recoverable", false);
                        callback.onResult(null);
                        return;
                    }
                    callback.onResult(cachedToken);
                })
                .addOnFailureListener(exception -> {
                    recordFailure(context, "token_refresh", exception);
                    callback.onResult(null);
                });
    }

    static void revoke(Context context, ActionCallback callback) {
        if (context == null) {
            callback.onFinished(false, "Context unavailable.");
            return;
        }
        RevokeAccessRequest revoke = RevokeAccessRequest.builder()
                .setScopes(scopes())
                .build();
        Identity.getAuthorizationClient(context).revokeAccess(revoke)
                .addOnSuccessListener(unused -> {
                    clearState(context);
                    DiagnosticLog.info(context, "calendar_api", "authorization_revoked");
                    callback.onFinished(true, "Google Calendar disconnected.");
                })
                .addOnFailureListener(exception -> {
                    FailureInfo info = failureInfo(exception);
                    prefs(context).edit()
                            .putString(KEY_LAST_ERROR,
                                    info.kind + ":" + info.statusName)
                            .apply();
                    logFailure(context, "revoke", info);
                    callback.onFinished(false,
                            userMessage(info, "Could not disconnect Google Calendar."));
                });
    }

    private static AuthorizationRequest request() {
        return AuthorizationRequest.builder()
                .setRequestedScopes(scopes())
                .build();
    }

    private static List<Scope> scopes() {
        return Collections.singletonList(new Scope(CALENDAR_SCOPE));
    }

    private static boolean accept(Context context, AuthorizationResult result) {
        if (result == null) return false;
        String token = result.getAccessToken();
        if (token == null || token.trim().isEmpty()) return false;
        cachedToken = token;
        cachedTokenAt = System.currentTimeMillis();
        prefs(context).edit()
                .putBoolean(KEY_CONNECTED, true)
                .putBoolean(KEY_NEEDS_ACTION, false)
                .remove(KEY_LAST_ERROR)
                .apply();
        DiagnosticLog.info(context, "calendar_api", "authorized",
                "scope", "calendar.events.readonly");
        return true;
    }

    private static AuthOutcome recordFailure(Context context, String stage, Exception exception) {
        FailureInfo info = failureInfo(exception);
        if ("cancelled".equals(info.kind)) {
            markDisconnected(context);
            DiagnosticLog.info(context, "calendar_api", "authorization_cancelled",
                    "stage", stage,
                    "error_class", info.errorClass,
                    "status_code", info.statusCode,
                    "status_name", info.statusName);
            return new AuthOutcome(false, "Google Calendar authorization was canceled.");
        }
        markNeedsAction(context, info.kind + ":" + info.statusName);
        logFailure(context, stage, info);
        return new AuthOutcome(false,
                userMessage(info, "Google Calendar authorization failed."));
    }

    private static void logFailure(Context context, String stage, FailureInfo info) {
        DiagnosticLog.warn(context, "calendar_api", "authorization_failed",
                "stage", stage,
                "kind", info.kind,
                "error_class", info.errorClass,
                "status_code", info.statusCode,
                "status_name", info.statusName,
                "recoverable", info.recoverable);
    }

    private static FailureInfo failureInfo(Exception exception) {
        int code = Integer.MIN_VALUE;
        boolean hasResolution = false;
        if (exception instanceof ApiException) {
            ApiException api = (ApiException) exception;
            code = api.getStatusCode();
            hasResolution = api.getStatus() != null && api.getStatus().hasResolution();
        }
        String statusName = code == Integer.MIN_VALUE
                ? "NO_API_STATUS" : CommonStatusCodes.getStatusCodeString(code);
        String kind;
        boolean recoverable;
        if (code == CommonStatusCodes.DEVELOPER_ERROR) {
            kind = "configuration";
            recoverable = false;
        } else if (code == CommonStatusCodes.CANCELED) {
            kind = "cancelled";
            recoverable = true;
        } else if (hasResolution || code == CommonStatusCodes.RESOLUTION_REQUIRED
                || code == CommonStatusCodes.SIGN_IN_REQUIRED) {
            kind = "recoverable";
            recoverable = true;
        } else if (code == CommonStatusCodes.NETWORK_ERROR
                || code == CommonStatusCodes.INTERNAL_ERROR
                || code == CommonStatusCodes.INTERRUPTED
                || code == CommonStatusCodes.TIMEOUT
                || code == CommonStatusCodes.API_NOT_CONNECTED
                || code == CommonStatusCodes.CONNECTION_SUSPENDED_DURING_CALL
                || code == CommonStatusCodes.RECONNECTION_TIMED_OUT
                || code == CommonStatusCodes.RECONNECTION_TIMED_OUT_DURING_UPDATE) {
            kind = "transient";
            recoverable = true;
        } else {
            kind = "unknown";
            recoverable = false;
        }
        return new FailureInfo(code, statusName, kind, recoverable,
                exception == null ? "Unknown" : exception.getClass().getSimpleName());
    }

    private static String userMessage(FailureInfo info, String fallback) {
        if ("configuration".equals(info.kind)) {
            return "Google Calendar OAuth configuration does not match this app build. "
                    + "See Diagnostics for status 10.";
        }
        if ("recoverable".equals(info.kind) || "transient".equals(info.kind)) {
            return "Google Calendar authorization hit a temporary Google services error. "
                    + "Try again.";
        }
        if (info.statusCode != Integer.MIN_VALUE) {
            return fallback + " Google status " + info.statusCode + ". See Diagnostics.";
        }
        return fallback + " See Diagnostics.";
    }

    private static void markNeedsAction(Context context, String error) {
        if (context == null) return;
        prefs(context).edit()
                .putBoolean(KEY_CONNECTED, false)
                .putBoolean(KEY_NEEDS_ACTION, true)
                .putString(KEY_LAST_ERROR, error == null ? "" : error)
                .apply();
        cachedToken = null;
        cachedTokenAt = 0L;
    }

    private static void markDisconnected(Context context) {
        if (context == null) return;
        prefs(context).edit()
                .putBoolean(KEY_CONNECTED, false)
                .putBoolean(KEY_NEEDS_ACTION, false)
                .remove(KEY_LAST_ERROR)
                .apply();
        cachedToken = null;
        cachedTokenAt = 0L;
    }

    private static void clearState(Context context) {
        prefs(context).edit().clear().apply();
        cachedToken = null;
        cachedTokenAt = 0L;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
