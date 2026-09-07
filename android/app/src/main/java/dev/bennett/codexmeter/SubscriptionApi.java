package dev.bennett.codexmeter;

import android.content.Context;
import android.os.SystemClock;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Best-effort reader for ChatGPT subscription metadata used only for display. */
final class SubscriptionApi {
    private static final long REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(6);

    private SubscriptionApi() {
    }

    static void refreshAndCacheLocked(Context context, AuthTokens tokens) {
        refreshAndCacheLocked(context, tokens, false, "side_refresh");
    }

    static void refreshAndCacheLocked(Context context, AuthTokens tokens, boolean force,
            String trigger) {
        if (context == null || tokens == null) return;
        long now = System.currentTimeMillis();
        String safeTrigger = trigger == null || trigger.trim().isEmpty()
                ? "unspecified" : trigger.trim();
        SubscriptionStore.seedFromJwt(context, tokens, now);
        if (tokens.accountId.isEmpty()) {
            DiagnosticLog.warn(context, "refresh", "subscription_refresh_skipped",
                    "trigger", safeTrigger, "reason", "missing_account_id", "force", force);
            return;
        }
        long storedUntil = SubscriptionStore.storedActiveUntilMillis(context);
        boolean cachedExpired = storedUntil > 0L && storedUntil <= now;
        long lastAttempt = SubscriptionStore.lastAttemptMillis(context);
        long attemptAge = lastAttempt <= 0L ? -1L : Math.max(0L, now - lastAttempt);
        if (!force && !cachedExpired && lastAttempt > 0L && attemptAge < REFRESH_INTERVAL_MS) {
            DiagnosticLog.info(context, "refresh", "subscription_refresh_skipped",
                    "trigger", safeTrigger,
                    "reason", "ttl",
                    "force", false,
                    "attempt_age_ms", attemptAge,
                    "stored_active_until", storedUntil);
            return;
        }
        DiagnosticLog.info(context, "refresh", "subscription_refresh_fetching",
                "trigger", safeTrigger,
                "force", force,
                "cached_expired", cachedExpired,
                "stored_active_until", storedUntil,
                "last_attempt", lastAttempt);
        SubscriptionStore.markAttempt(context, now);
        long started = SystemClock.elapsedRealtime();
        HttpsURLConnection connection = null;
        try {
            String encodedAccount = URLEncoder.encode(tokens.accountId,
                    StandardCharsets.UTF_8.name());
            String url = AppConstants.CHATGPT_BACKEND + "/subscriptions?account_id="
                    + encodedAccount;
            DiagnosticLog.info(context, "network", "request_started",
                    "operation", "subscription",
                    "trigger", safeTrigger,
                    "method", "GET",
                    "url", DiagnosticSanitizer.safeUrl(url),
                    "force", force);
            connection = (HttpsURLConnection) URI.create(url).toURL().openConnection();
            UsageApi.applyHeaders(connection, tokens);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            String body = OAuthClient.readBody(connection, status);
            DiagnosticLog.info(context, "network", "request_finished",
                    "operation", "subscription",
                    "trigger", safeTrigger,
                    "status", status,
                    "duration_ms", SystemClock.elapsedRealtime() - started,
                    "response_bytes", body.getBytes(StandardCharsets.UTF_8).length);
            if (status < 200 || status >= 300) {
                DiagnosticLog.warn(context, "refresh", "subscription_refresh_http_rejected",
                        "trigger", safeTrigger, "status", status);
                return;
            }
            SubscriptionInfo parsed = parse(body, now);
            if (parsed == null || !parsed.hasDisplayableData()) {
                DiagnosticLog.warn(context, "refresh", "subscription_parse_rejected",
                        "trigger", safeTrigger, "reason", "no_displayable_data");
                return;
            }
            boolean backendStale = parsed.activeUntilMillis > 0L
                    && parsed.activeUntilMillis <= now;
            DiagnosticLog.info(context, "refresh", "subscription_parsed",
                    "trigger", safeTrigger,
                    "plan", parsed.planType,
                    "active_until", parsed.activeUntilMillis,
                    "backend_stale", backendStale,
                    "parsed_at", now);

            SubscriptionInfo cached = SubscriptionStore.load(context);
            String plan = parsed.planType.isEmpty() && cached != null
                    ? cached.planType : parsed.planType;
            long until = parsed.activeUntilMillis <= 0L && cached != null
                    ? cached.activeUntilMillis : parsed.activeUntilMillis;
            long previousUntil = SubscriptionStore.storedActiveUntilMillis(context);
            SubscriptionStore.save(context, new SubscriptionInfo(plan, until,
                    parsed.willRenew, parsed.hasWillRenew, now));
            DiagnosticLog.info(context, "refresh", "subscription_snapshot_replaced",
                    "trigger", safeTrigger,
                    "previous_active_until", previousUntil,
                    "new_active_until", until,
                    "backend_stale", backendStale,
                    "fetched_at", now);
        } catch (Exception exception) {
            // This is an internal ChatGPT endpoint and must never break usage refresh.
            DiagnosticLog.warn(context, "network", "subscription_refresh_failed",
                    "trigger", safeTrigger,
                    "message", safeMessage(exception),
                    "duration_ms", SystemClock.elapsedRealtime() - started);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static SubscriptionInfo parse(String body, long now) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            Object root = new JSONTokener(body).nextValue();
            JSONObject object = pickSubscription(root);
            if (object == null) return null;
            String plan = firstNonEmpty(object.optString("plan_type", ""),
                    object.optString("plan", ""), object.optString("name", ""));
            long activeUntil = parseTimestamp(object.opt("active_until"));
            if (activeUntil <= 0L) {
                activeUntil = parseTimestamp(object.opt("current_period_end"));
            }
            boolean hasWillRenew = object.has("will_renew") && !object.isNull("will_renew");
            boolean willRenew = hasWillRenew && object.optBoolean("will_renew", false);
            return new SubscriptionInfo(plan, activeUntil, willRenew, hasWillRenew, now);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JSONObject pickSubscription(Object root) {
        if (root instanceof JSONArray) {
            JSONArray array = (JSONArray) root;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null && looksLikeSubscription(item)) return item;
            }
            return array.optJSONObject(0);
        }
        if (!(root instanceof JSONObject)) return null;
        JSONObject object = (JSONObject) root;
        JSONObject nested = object.optJSONObject("subscription");
        if (nested != null) return nested;
        JSONArray subscriptions = object.optJSONArray("subscriptions");
        if (subscriptions != null) {
            for (int i = 0; i < subscriptions.length(); i++) {
                JSONObject item = subscriptions.optJSONObject(i);
                if (item != null && looksLikeSubscription(item)) return item;
            }
            JSONObject first = subscriptions.optJSONObject(0);
            if (first != null) return first;
        }
        return object;
    }

    private static boolean looksLikeSubscription(JSONObject object) {
        return object.has("active_until") || object.has("current_period_end")
                || object.has("plan_type") || object.has("will_renew");
    }

    static long parseTimestamp(Object value) {
        if (value == null || value == JSONObject.NULL) return 0L;
        if (value instanceof Number) {
            long raw = ((Number) value).longValue();
            return raw > 0L && raw < 100000000000L ? raw * 1000L : Math.max(0L, raw);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return 0L;
        try {
            long raw = Long.parseLong(text);
            return raw > 0L && raw < 100000000000L ? raw * 1000L : Math.max(0L, raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String safeMessage(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (message == null || message.trim().isEmpty()) return exception == null
                ? "unknown" : exception.getClass().getSimpleName();
        String trimmed = message.trim();
        return trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed;
    }
}
