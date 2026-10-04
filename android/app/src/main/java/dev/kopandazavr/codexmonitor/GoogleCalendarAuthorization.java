package dev.kopandazavr.codexmonitor;

import android.accounts.Account;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Google Calendar OAuth state, deliberately separate from the app's ChatGPT OAuth flow. */
final class GoogleCalendarAuthorization {
    static final String CALENDAR_SCOPE =
            "https://www.googleapis.com/auth/calendar.events.readonly";
    private static final String PREFS = "codex_google_calendar_auth_v1";
    private static final String KEY_CONNECTED = "connected";
    private static final String KEY_NEEDS_ACTION = "needs_action";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_PENDING_ACCOUNT_NAME = "pending_account_name";
    static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private static final long TOKEN_CACHE_MS = 45L * 60L * 1000L;

    private static final Map<String, String> CACHED_TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, Long> CACHED_TOKEN_AT = new ConcurrentHashMap<>();

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
        return isConnected(context, AccountContainerStore.selectedId(context));
    }

    static boolean isConnected(Context context, String containerId) {
        SharedPreferences prefs = prefs(context);
        String account = prefs.getString(key(containerId, KEY_ACCOUNT_NAME), "");
        return account != null && !account.trim().isEmpty()
                && prefs.getBoolean(key(containerId, KEY_CONNECTED), false)
                && !prefs.getBoolean(key(containerId, KEY_NEEDS_ACTION), false);
    }

    static String accountName(Context context) {
        return accountName(context, AccountContainerStore.selectedId(context));
    }

    static String accountName(Context context, String containerId) {
        String value = prefs(context).getString(key(containerId, KEY_ACCOUNT_NAME), "");
        return value == null ? "" : value.trim();
    }

    static String statusSummary(Context context) {
        return statusSummary(context, AccountContainerStore.selectedId(context));
    }

    static String statusSummary(Context context, String containerId) {
        SharedPreferences prefs = prefs(context);
        String account = accountName(context, containerId);
        if (isConnected(context, containerId)) {
            return account.isEmpty() ? "Connected · direct Calendar API"
                    : "Connected · " + account;
        }
        if (prefs.getBoolean(key(containerId, KEY_NEEDS_ACTION), false)) {
            return "Reconnect required";
        }
        if (AccountContainerStore.isLegacyOwner(context, containerId)
                && prefs.getBoolean(KEY_CONNECTED, false) && account.isEmpty()) {
            return "Reconnect required · choose account";
        }
        String error = prefs.getString(key(containerId, KEY_LAST_ERROR), "");
        return error == null || error.isEmpty()
                ? "Not connected · tap to authorize"
                : "Not connected · tap to retry";
    }

    static boolean shouldRetryTransient(Context context) {
        return shouldRetryTransient(context, AccountContainerStore.selectedId(context));
    }

    static boolean shouldRetryTransient(Context context, String containerId) {
        String error = prefs(context).getString(key(containerId, KEY_LAST_ERROR), "");
        return "transient:NETWORK_ERROR".equals(error)
                || "transient:INTERNAL_ERROR".equals(error);
    }

    static void beginInteractive(Activity activity, int requestCode,
            ActionCallback callback) {
        String accountName = accountName(activity);
        Account account = accountName.isEmpty()
                ? null : new Account(accountName, GOOGLE_ACCOUNT_TYPE);
        beginInteractive(activity, requestCode, account, callback);
    }

    static void beginInteractive(Activity activity, int requestCode, Account account,
            ActionCallback callback) {
        beginInteractive(activity, AccountContainerStore.selectedId(activity),
                requestCode, account, callback);
    }

    static void beginInteractive(Activity activity, String containerId, int requestCode,
            Account account, ActionCallback callback) {
        if (account == null || account.name == null || account.name.trim().isEmpty()) {
            callback.onFinished(false, "Choose a Google account first.");
            return;
        }
        String chosen = account.name.trim();
        prefs(activity).edit()
                .putString(key(containerId, KEY_PENDING_ACCOUNT_NAME), chosen)
                .apply();
        DiagnosticLog.info(activity, "calendar_api", "authorization_started",
                "scope", "calendar.events.readonly",
                "container_id", containerId);
        AuthorizationClient client = Identity.getAuthorizationClient(activity);
        client.authorize(request(account))
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        PendingIntent pending = result.getPendingIntent();
                        if (pending == null) {
                            markNeedsAction(activity, containerId, "resolution_missing");
                            callback.onFinished(false,
                                    "Google Calendar authorization is unavailable.");
                            return;
                        }
                        try {
                            activity.startIntentSenderForResult(pending.getIntentSender(),
                                    requestCode, null, 0, 0, 0);
                            callback.onFinished(false,
                                    "Allow Calendar access for the selected Google account.");
                        } catch (IntentSender.SendIntentException exception) {
                            AuthOutcome outcome = recordFailure(activity, containerId,
                                    "resolution_launch", exception);
                            callback.onFinished(false, outcome.message);
                        }
                    } else if (accept(activity, containerId, result, chosen)) {
                        callback.onFinished(true, "Google Calendar connected.");
                    } else {
                        markNeedsAction(activity, containerId, "token_missing");
                        callback.onFinished(false,
                                "Google Calendar authorization returned no access token.");
                    }
                })
                .addOnFailureListener(exception -> {
                    AuthOutcome outcome = recordFailure(activity, containerId,
                            "authorize", exception);
                    callback.onFinished(false, outcome.message);
                });
    }

    /**
     * Consumes the resolution activity result and returns a classified user-facing outcome.
     * Diagnostics intentionally record only status/result metadata, never tokens/account data.
     */
    static AuthOutcome consumeInteractiveResult(Activity activity, int resultCode, Intent data) {
        return consumeInteractiveResult(activity, AccountContainerStore.selectedId(activity),
                resultCode, data);
    }

    static AuthOutcome consumeInteractiveResult(Activity activity, String containerId,
            int resultCode, Intent data) {
        String pendingAccount = prefs(activity).getString(
                key(containerId, KEY_PENDING_ACCOUNT_NAME), "");
        DiagnosticLog.info(activity, "calendar_api", "authorization_activity_result",
                "container_id", containerId,
                "result_code", resultCode,
                "data_present", data != null);
        if (data != null) {
            try {
                AuthorizationResult result = Identity.getAuthorizationClient(activity)
                        .getAuthorizationResultFromIntent(data);
                if (accept(activity, containerId, result, pendingAccount)) {
                    return new AuthOutcome(true, "Google Calendar connected.");
                }
                markNeedsAction(activity, containerId, "token_missing");
                return new AuthOutcome(false,
                        "Google Calendar authorization returned no access token.");
            } catch (Exception exception) {
                return recordFailure(activity, containerId, "activity_result", exception);
            }
        }
        if (resultCode != Activity.RESULT_OK) {
            markDisconnected(activity, containerId);
            DiagnosticLog.info(activity, "calendar_api", "authorization_cancelled",
                    "container_id", containerId,
                    "result_code", resultCode,
                    "data_present", false);
            return new AuthOutcome(false, "Google Calendar authorization was canceled.");
        }
        markNeedsAction(activity, containerId, "result_missing");
        return new AuthOutcome(false,
                "Google Calendar returned no authorization result. Try again.");
    }

    /** Compatibility wrapper retained for bounded callers/tests. */
    static boolean consumeInteractiveResult(Activity activity, Intent data) {
        return consumeInteractiveResult(activity, Activity.RESULT_OK, data).success;
    }

    static void invalidateCachedToken(Context context, String reason) {
        invalidateCachedToken(context, AccountContainerStore.selectedId(context), reason);
    }

    static void invalidateCachedToken(Context context, String containerId, String reason) {
        CACHED_TOKENS.remove(containerId);
        CACHED_TOKEN_AT.remove(containerId);
        if (context != null) {
            DiagnosticLog.warn(context, "calendar_api", "cached_access_token_invalidated",
                    "container_id", containerId,
                    "reason", reason == null ? "" : reason);
        }
    }

    static void accessToken(Context context, TokenCallback callback) {
        accessToken(context, AccountContainerStore.selectedId(context), callback);
    }

    static void accessToken(Context context, String containerId, TokenCallback callback) {
        if (context == null) {
            callback.onResult(null);
            return;
        }
        String accountName = accountName(context, containerId);
        if (accountName.isEmpty()) {
            markNeedsAction(context, containerId, "account_missing");
            callback.onResult(null);
            return;
        }
        String token = CACHED_TOKENS.get(containerId);
        Long cachedAt = CACHED_TOKEN_AT.get(containerId);
        long age = cachedAt == null ? Long.MAX_VALUE : System.currentTimeMillis() - cachedAt;
        if (token != null && !token.isEmpty() && age >= 0L && age < TOKEN_CACHE_MS) {
            callback.onResult(token);
            return;
        }
        Account account = new Account(accountName, GOOGLE_ACCOUNT_TYPE);
        Identity.getAuthorizationClient(context).authorize(request(account))
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        markNeedsAction(context, containerId, "resolution_required");
                        DiagnosticLog.warn(context, "calendar_api",
                                "token_resolution_required",
                                "container_id", containerId,
                                "recoverable", true);
                        callback.onResult(null);
                        return;
                    }
                    if (!accept(context, containerId, result, accountName)) {
                        markNeedsAction(context, containerId, "token_missing");
                        DiagnosticLog.warn(context, "calendar_api",
                                "token_refresh_missing",
                                "container_id", containerId,
                                "recoverable", false);
                        callback.onResult(null);
                        return;
                    }
                    callback.onResult(CACHED_TOKENS.get(containerId));
                })
                .addOnFailureListener(exception -> {
                    recordFailure(context, containerId, "token_refresh", exception);
                    callback.onResult(null);
                });
    }

    static void revoke(Context context, ActionCallback callback) {
        revoke(context, AccountContainerStore.selectedId(context), callback);
    }

    static void revoke(Context context, String containerId, ActionCallback callback) {
        String accountName = accountName(context, containerId);
        if (context == null || accountName.isEmpty()) {
            clearState(context, containerId);
            callback.onFinished(true, "Google Calendar disconnected.");
            return;
        }
        if (isAccountSharedWithAnotherContainer(context, containerId, accountName)) {
            clearState(context, containerId);
            DiagnosticLog.info(context, "calendar_api", "authorization_detached_shared_grant",
                    "container_id", containerId);
            callback.onFinished(true, "Google Calendar disconnected from this account.");
            return;
        }
        Account account = new Account(accountName, GOOGLE_ACCOUNT_TYPE);
        RevokeAccessRequest revoke = RevokeAccessRequest.builder()
                .setAccount(account)
                .setScopes(scopes())
                .build();
        Identity.getAuthorizationClient(context).revokeAccess(revoke)
                .addOnSuccessListener(unused -> {
                    clearState(context, containerId);
                    DiagnosticLog.info(context, "calendar_api", "authorization_revoked",
                            "container_id", containerId);
                    callback.onFinished(true, "Google Calendar disconnected.");
                })
                .addOnFailureListener(exception -> {
                    FailureInfo info = failureInfo(exception);
                    prefs(context).edit()
                            .putString(key(containerId, KEY_LAST_ERROR),
                                    info.kind + ":" + info.statusName)
                            .apply();
                    logFailure(context, containerId, "revoke", info);
                    callback.onFinished(false,
                            userMessage(info, "Could not disconnect Google Calendar."));
                });
    }

    private static boolean isAccountSharedWithAnotherContainer(
            Context context, String containerId, String accountName) {
        if (context == null || accountName == null || accountName.trim().isEmpty()) return false;
        for (AccountContainerStore.Account account : AccountContainerStore.all(context)) {
            if (account.id.equals(containerId)) continue;
            String other = accountName(context, account.id);
            if (!other.isEmpty() && other.equalsIgnoreCase(accountName.trim())
                    && isConnected(context, account.id)) {
                return true;
            }
        }
        return false;
    }

    private static AuthorizationRequest request(Account account) {
        AuthorizationRequest.Builder builder = AuthorizationRequest.builder()
                .setRequestedScopes(scopes());
        if (account != null) builder.setAccount(account);
        return builder.build();
    }

    private static List<Scope> scopes() {
        return Collections.singletonList(new Scope(CALENDAR_SCOPE));
    }

    private static boolean accept(Context context, String containerId,
            AuthorizationResult result, String accountName) {
        if (!AccountContainerLifecycleGuard.isAlive(context, containerId)) return false;
        if (result == null) return false;
        String token = result.getAccessToken();
        String account = accountName == null ? "" : accountName.trim();
        if (token == null || token.trim().isEmpty() || account.isEmpty()) return false;
        CACHED_TOKENS.put(containerId, token);
        CACHED_TOKEN_AT.put(containerId, System.currentTimeMillis());
        prefs(context).edit()
                .putBoolean(key(containerId, KEY_CONNECTED), true)
                .putBoolean(key(containerId, KEY_NEEDS_ACTION), false)
                .putString(key(containerId, KEY_ACCOUNT_NAME), account)
                .remove(key(containerId, KEY_PENDING_ACCOUNT_NAME))
                .remove(key(containerId, KEY_LAST_ERROR))
                .apply();
        DiagnosticLog.info(context, "calendar_api", "authorized",
                "scope", "calendar.events.readonly",
                "container_id", containerId);
        return true;
    }

    private static AuthOutcome recordFailure(Context context, String containerId,
            String stage, Exception exception) {
        FailureInfo info = failureInfo(exception);
        if ("cancelled".equals(info.kind)) {
            markDisconnected(context, containerId);
            DiagnosticLog.info(context, "calendar_api", "authorization_cancelled",
                    "container_id", containerId,
                    "stage", stage,
                    "error_class", info.errorClass,
                    "status_code", info.statusCode,
                    "status_name", info.statusName);
            return new AuthOutcome(false, "Google Calendar authorization was canceled.");
        }
        markNeedsAction(context, containerId, info.kind + ":" + info.statusName);
        logFailure(context, containerId, stage, info);
        return new AuthOutcome(false,
                userMessage(info, "Google Calendar authorization failed."));
    }

    private static void logFailure(Context context, String containerId,
            String stage, FailureInfo info) {
        DiagnosticLog.warn(context, "calendar_api", "authorization_failed",
                "container_id", containerId,
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

    private static void markNeedsAction(Context context, String containerId, String error) {
        if (context == null) return;
        prefs(context).edit()
                .putBoolean(key(containerId, KEY_CONNECTED), false)
                .putBoolean(key(containerId, KEY_NEEDS_ACTION), true)
                .putString(key(containerId, KEY_LAST_ERROR), error == null ? "" : error)
                .apply();
        CACHED_TOKENS.remove(containerId);
        CACHED_TOKEN_AT.remove(containerId);
    }

    private static void markDisconnected(Context context, String containerId) {
        if (context == null) return;
        prefs(context).edit()
                .putBoolean(key(containerId, KEY_CONNECTED), false)
                .putBoolean(key(containerId, KEY_NEEDS_ACTION), false)
                .remove(key(containerId, KEY_LAST_ERROR))
                .remove(key(containerId, KEY_PENDING_ACCOUNT_NAME))
                .apply();
        CACHED_TOKENS.remove(containerId);
        CACHED_TOKEN_AT.remove(containerId);
    }

    static void clearContainerState(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return;
        clearState(context, containerId);
    }

    private static void clearState(Context context, String containerId) {
        if (context == null) return;
        SharedPreferences.Editor editor = prefs(context).edit()
                .remove(key(containerId, KEY_CONNECTED))
                .remove(key(containerId, KEY_NEEDS_ACTION))
                .remove(key(containerId, KEY_LAST_ERROR))
                .remove(key(containerId, KEY_ACCOUNT_NAME))
                .remove(key(containerId, KEY_PENDING_ACCOUNT_NAME));
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            editor.remove(KEY_CONNECTED).remove(KEY_NEEDS_ACTION).remove(KEY_LAST_ERROR);
        }
        editor.apply();
        CACHED_TOKENS.remove(containerId);
        CACHED_TOKEN_AT.remove(containerId);
    }

    private static String key(String containerId, String base) {
        String id = containerId == null ? "" : containerId.trim();
        return base + "::" + id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
