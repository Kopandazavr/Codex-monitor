package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class MonitorHealthDiagnostics {
    private static final String PREFS = "codex_monitor_health_v1";
    private static final String KEY_LAST_DIRECT_SUCCESS = "last_direct_success";
    private static final String KEY_CURRENT_SOURCE = "current_source";
    private static final String KEY_LAST_SOURCE = "last_source";
    private static final String KEY_FAILURE = "last_poll_failure";
    private static final String KEY_ACTIVE_COUNT = "active_count";
    private static final String KEY_IDLE_COUNT = "idle_count";
    private static final String KEY_LAST_REJECTION = "last_rejection";

    private MonitorHealthDiagnostics() {}

    static void recordDirectPollSuccess(Context context, int watchdogCount) {
        if (context == null) return;
        SharedPreferences p = prefs(context);
        String previousFailure = clean(p.getString(KEY_FAILURE, ""));
        p.edit().putLong(KEY_LAST_DIRECT_SUCCESS, System.currentTimeMillis())
                .remove(KEY_FAILURE).apply();
        recordObservationSource(context, "direct_api");
        if (!previousFailure.isEmpty()) {
            DiagnosticLog.info(context, "calendar_api", "calendar_poll_recovered",
                    "previous_failure_category", previousFailure,
                    "watchdogs", Math.max(0, watchdogCount), "source", "direct_api");
        }
    }

    static void recordPollFailure(Context context, Throwable error) {
        recordPollFailure(context, failureCategory(error));
    }

    static void recordPollFailure(Context context, String category) {
        if (context == null) return;
        String normalized = clean(category);
        if (normalized.isEmpty()) normalized = "unknown";
        SharedPreferences p = prefs(context);
        String previous = clean(p.getString(KEY_FAILURE, ""));
        p.edit().putString(KEY_FAILURE, normalized).apply();
        if (!normalized.equals(previous)) {
            DiagnosticLog.warn(context, "calendar_api", "calendar_poll_failure_changed",
                    "previous_failure_category", previous,
                    "failure_category", normalized,
                    "source", clean(p.getString(KEY_CURRENT_SOURCE, "")));
        }
    }

    static void recordObservationSource(Context context, String source) {
        if (context == null) return;
        String normalized = clean(source);
        if (normalized.isEmpty()) return;
        SharedPreferences p = prefs(context);
        String current = clean(p.getString(KEY_CURRENT_SOURCE, ""));
        if (normalized.equals(current)) return;
        p.edit().putString(KEY_LAST_SOURCE, current)
                .putString(KEY_CURRENT_SOURCE, normalized).apply();
        DiagnosticLog.info(context, "calendar_process", "observation_source_changed",
                "previous_source", current, "source", normalized);
    }

    static void recordCounts(Context context, int activeCount, int idleCount) {
        if (context == null) return;
        int active = Math.max(0, activeCount), idle = Math.max(0, idleCount);
        SharedPreferences p = prefs(context);
        int oldActive = p.getInt(KEY_ACTIVE_COUNT, -1), oldIdle = p.getInt(KEY_IDLE_COUNT, -1);
        if (oldActive == active && oldIdle == idle) return;
        p.edit().putInt(KEY_ACTIVE_COUNT, active).putInt(KEY_IDLE_COUNT, idle).apply();
        DiagnosticLog.info(context, "calendar_process", "monitor_counts_changed",
                "active_process_count", active, "idle_role_count", idle);
    }

    static void recordStrictRejection(Context context, long eventId, String source, String reason) {
        if (context == null) return;
        prefs(context).edit().putString(KEY_LAST_REJECTION,
                clean(source) + ":" + eventId + ":" + clean(reason)).apply();
    }

    static String summary(Context context) {
        if (context == null) return "Monitor health unavailable";
        SharedPreferences p = prefs(context);
        long success = p.getLong(KEY_LAST_DIRECT_SUCCESS, 0L);
        String source = clean(p.getString(KEY_CURRENT_SOURCE, ""));
        String lastSource = clean(p.getString(KEY_LAST_SOURCE, ""));
        String failure = clean(p.getString(KEY_FAILURE, ""));
        String rejection = clean(p.getString(KEY_LAST_REJECTION, ""));
        int active = Math.max(0, p.getInt(KEY_ACTIVE_COUNT, 0));
        int idle = Math.max(0, p.getInt(KEY_IDLE_COUNT, 0));
        StringBuilder out = new StringBuilder("Direct poll ").append(age(success))
                .append(" · source ").append(source.isEmpty() ? "none" : source);
        if (!lastSource.isEmpty() && !lastSource.equals(source)) {
            out.append(" (prev ").append(lastSource).append(")");
        }
        out.append(" · ").append(active).append(" active / ").append(idle).append(" idle");
        if (!failure.isEmpty()) out.append(" · failure ").append(failure);
        if (!rejection.isEmpty()) out.append(" · rejection ").append(rejection);
        return out.toString();
    }

    private static String failureCategory(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof UnknownHostException) return "dns";
            if (cursor instanceof SocketTimeoutException) return "network_timeout";
            if (cursor instanceof ConnectException) return "network_connect";
            cursor = cursor.getCause();
        }
        String message = error == null ? "" : clean(error.getMessage());
        int http = message.indexOf("HTTP ");
        if (http >= 0) {
            int start = http + 5, end = start;
            while (end < message.length() && Character.isDigit(message.charAt(end))) end++;
            if (end > start) return "http_" + message.substring(start, end);
        }
        return error == null ? "unknown"
                : clean(error.getClass().getSimpleName()).toLowerCase(Locale.ROOT);
    }

    private static String age(long timestamp) {
        if (timestamp <= 0L) return "never";
        long delta = Math.max(0L, System.currentTimeMillis() - timestamp);
        if (delta < TimeUnit.MINUTES.toMillis(1)) return delta / 1000L + "s ago";
        if (delta < TimeUnit.HOURS.toMillis(1)) {
            return Math.max(1L, delta / TimeUnit.MINUTES.toMillis(1)) + "m ago";
        }
        return Math.max(1L, delta / TimeUnit.HOURS.toMillis(1)) + "h ago";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
