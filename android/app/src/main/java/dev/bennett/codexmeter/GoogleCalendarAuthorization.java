package dev.bennett.codexmeter;

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

    static void beginInteractive(Activity activity, int requestCode,
            ActionCallback callback) {
        AuthorizationClient client = Identity.getAuthorizationClient(activity);
        client.authorize(request())
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        PendingIntent pending = result.getPendingIntent();
                        if (pending == null) {
                            markNeedsAction(activity, "Authorization requires user interaction.");
                            callback.onFinished(false, "Google Calendar authorization is unavailable.");
                            return;
                        }
                        try {
                            activity.startIntentSenderForResult(pending.getIntentSender(),
                                    requestCode, null, 0, 0, 0);
                            callback.onFinished(false, "Choose a Google account and allow Calendar.");
                        } catch (IntentSender.SendIntentException exception) {
                            markNeedsAction(activity, exception.getClass().getSimpleName());
                            callback.onFinished(false,
                                    "Could not open Google Calendar authorization.");
                        }
                    } else if (accept(activity, result)) {
                        callback.onFinished(true, "Google Calendar connected.");
                    } else {
                        markNeedsAction(activity, "No Calendar access token returned.");
                        callback.onFinished(false, "Google Calendar authorization did not complete.");
                    }
                })
                .addOnFailureListener(exception -> {
                    markNeedsAction(activity, safeMessage(exception));
                    DiagnosticLog.warn(activity, "calendar_api", "authorization_failed",
                            "error", exception.getClass().getSimpleName());
                    callback.onFinished(false, "Google Calendar authorization failed.");
                });
    }

    static boolean consumeInteractiveResult(Activity activity, Intent data) {
        try {
            AuthorizationResult result = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(data);
            return accept(activity, result);
        } catch (Exception exception) {
            markNeedsAction(activity, safeMessage(exception));
            DiagnosticLog.warn(activity, "calendar_api", "authorization_result_failed",
                    "error", exception.getClass().getSimpleName());
            return false;
        }
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
                        markNeedsAction(context, "Google Calendar authorization requires interaction.");
                        callback.onResult(null);
                        return;
                    }
                    if (!accept(context, result)) {
                        markNeedsAction(context, "No Calendar access token returned.");
                        callback.onResult(null);
                        return;
                    }
                    callback.onResult(cachedToken);
                })
                .addOnFailureListener(exception -> {
                    markNeedsAction(context, safeMessage(exception));
                    DiagnosticLog.warn(context, "calendar_api", "token_refresh_failed",
                            "error", exception.getClass().getSimpleName());
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
                    callback.onFinished(true, "Google Calendar disconnected.");
                })
                .addOnFailureListener(exception -> {
                    prefs(context).edit().putString(KEY_LAST_ERROR, safeMessage(exception)).apply();
                    DiagnosticLog.warn(context, "calendar_api", "revoke_failed",
                            "error", exception.getClass().getSimpleName());
                    callback.onFinished(false, "Could not disconnect Google Calendar.");
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

    private static void markNeedsAction(Context context, String error) {
        if (context == null) return;
        prefs(context).edit()
                .putBoolean(KEY_NEEDS_ACTION, true)
                .putString(KEY_LAST_ERROR, error == null ? "" : error)
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

    private static String safeMessage(Exception exception) {
        String value = exception == null ? null : exception.getMessage();
        if (value == null || value.trim().isEmpty()) {
            return exception == null ? "Unknown error" : exception.getClass().getSimpleName();
        }
        return value.length() > 180 ? value.substring(0, 180) : value;
    }
}
