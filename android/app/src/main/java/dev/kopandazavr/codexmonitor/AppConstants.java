package dev.kopandazavr.codexmonitor;

import android.os.Build;

/* JADX INFO: loaded from: classes.dex */
public final class AppConstants {
    public static final String ACTION_OAUTH_READY = "dev.kopandazavr.codexmonitor.action.OAUTH_READY";
    public static final String ACTION_OAUTH_RESULT = "dev.kopandazavr.codexmonitor.action.OAUTH_RESULT";
    public static final String ACTION_REFRESH_WIDGET = "dev.kopandazavr.codexmonitor.action.REFRESH_WIDGET";
    public static final String ACTION_RESET_ALERT = "dev.kopandazavr.codexmonitor.action.RESET_ALERT";
    public static final String ACTION_RESET_CREDIT_EXPIRY_ALERT =
            "dev.kopandazavr.codexmonitor.action.RESET_CREDIT_EXPIRY_ALERT";
    public static final String ACTION_RESET_CREDITS_UPDATED = "dev.kopandazavr.codexmonitor.action.RESET_CREDITS_UPDATED";
    public static final String ACTION_USAGE_UPDATED = "dev.kopandazavr.codexmonitor.action.USAGE_UPDATED";
    // Canonical Codex Monitor app callback scheme.
    public static final String APP_LINK = "codexmonitor://auth/complete";
    public static final String AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize";
    public static final String AUTH_BASE = "https://auth.openai.com";
    public static final String CHATGPT_BACKEND = "https://chatgpt.com/backend-api";
    public static final String EXTRA_AUTH_URL = "auth_url";
    public static final String EXTRA_CREDIT_ID = "credit_id";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";
    public static final String EXTRA_PROMPT_USE_RESET = "prompt_use_reset";
    public static final String EXTRA_SUCCESS = "success";
    // Canonical signature permission for the Codex Monitor package.
    public static final String INTERNAL_PERMISSION = "dev.kopandazavr.codexmonitor.permission.INTERNAL";
    public static final String OAUTH_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann";
    public static final int[] OAUTH_PORTS = {1455, 1457};
    public static final String OAUTH_SCOPE = "openid profile email offline_access";
    public static final String ORIGINATOR = "codex-monitor-android";
    public static final String RESET_CREDITS_CONSUME_URL = "https://chatgpt.com/backend-api/wham/rate-limit-reset-credits/consume";
    public static final String RESET_CREDITS_URL = "https://chatgpt.com/backend-api/wham/rate-limit-reset-credits";
    public static final String REVOKE_URL = "https://auth.openai.com/oauth/revoke";
    public static final String TOKEN_URL = "https://auth.openai.com/oauth/token";
    public static final String USAGE_URL = "https://chatgpt.com/backend-api/wham/usage";
    public static final int VERSION_CODE = 43;
    public static final String VERSION_NAME = "2.17.0";

    private AppConstants() {
    }

    public static String userAgent() {
        return ORIGINATOR + "/" + VERSION_NAME + " (Android "
                + (Build.VERSION.RELEASE == null ? "unknown" : Build.VERSION.RELEASE) + "; "
                + (Build.MODEL == null ? "Android" : Build.MODEL) + ")";
    }
}
