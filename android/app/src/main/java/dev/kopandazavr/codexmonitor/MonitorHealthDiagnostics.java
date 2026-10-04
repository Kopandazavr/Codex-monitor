package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.Intent;
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
        recordDirectPollSuccess(context, AccountContainerStore.selectedId(context), watchdogCount);
    }

    static void recordDirectPollSuccess(Context context, String containerId, int watchdogCount) {
        if (context == null) return;
        SharedPreferences p = prefs(context);
        String previousFailure = clean(readString(context, containerId, KEY_FAILURE, ""));
        p.edit().putLong(key(containerId, KEY_LAST_DIRECT_SUCCESS), System.currentTimeMillis())
                .remove(key(containerId, KEY_FAILURE)).apply();
        recordObservationSource(context, containerId, "direct_api");
        if (!previousFailure.isEmpty()) {
            DiagnosticLog.info(context, "calendar_api", "calendar_poll_recovered",
                    "container_id", containerId,
                    "previous_failure_category", previousFailure,
                    "watchdogs", Math.max(0, watchdogCount), "source", "direct_api");
        }
        notifyHealthChanged(context, containerId);
    }

    static void recordPollFailure(Context context, Throwable error) {
        recordPollFailure(context, AccountContainerStore.selectedId(context),
                failureCategory(error));
    }

    static void recordPollFailure(Context context, String category) {
        recordPollFailure(context, AccountContainerStore.selectedId(context), category);
    }

    static void recordPollFailure(Context context, String containerId, Throwable error) {
        recordPollFailure(context, containerId, failureCategory(error));
    }

    static void recordPollFailure(Context context, String containerId, String category) {
        if (context == null) return;
        String normalized = clean(category);
        if (normalized.isEmpty()) normalized = "unknown";
        SharedPreferences p = prefs(context);
        String previous = clean(readString(context, containerId, KEY_FAILURE, ""));
        p.edit().putString(key(containerId, KEY_FAILURE), normalized).apply();
        if (!normalized.equals(previous)) {
            DiagnosticLog.warn(context, "calendar_api", "calendar_poll_failure_changed",
                    "container_id", containerId,
                    "previous_failure_category", previous,
                    "failure_category", normalized,
                    "source", clean(readString(context, containerId, KEY_CURRENT_SOURCE, "")));
            notifyHealthChanged(context, containerId);
        }
    }

    static void recordObservationSource(Context context, String source) {
        recordObservationSource(context, AccountContainerStore.selectedId(context), source);
    }

    static void recordObservationSource(Context context, String containerId, String source) {
        if (context == null) return;
        String normalized = clean(source);
        if (normalized.isEmpty()) return;
        SharedPreferences p = prefs(context);
        String current = clean(readString(context, containerId, KEY_CURRENT_SOURCE, ""));
        if (normalized.equals(current)) return;
        p.edit().putString(key(containerId, KEY_LAST_SOURCE), current)
                .putString(key(containerId, KEY_CURRENT_SOURCE), normalized).apply();
        DiagnosticLog.info(context, "calendar_process", "observation_source_changed",
                "container_id", containerId,
                "previous_source", current, "source", normalized);
        notifyHealthChanged(context, containerId);
    }

    static void recordCounts(Context context, int activeCount, int idleCount) {
        recordCounts(context, AccountContainerStore.selectedId(context), activeCount, idleCount);
    }

    static void recordCounts(Context context, String containerId, int activeCount, int idleCount) {
        if (context == null) return;
        int active = Math.max(0, activeCount), idle = Math.max(0, idleCount);
        SharedPreferences p = prefs(context);
        int oldActive = readInt(context, containerId, KEY_ACTIVE_COUNT, -1);
        int oldIdle = readInt(context, containerId, KEY_IDLE_COUNT, -1);
        if (oldActive == active && oldIdle == idle) return;
        p.edit().putInt(key(containerId, KEY_ACTIVE_COUNT), active)
                .putInt(key(containerId, KEY_IDLE_COUNT), idle).apply();
        DiagnosticLog.info(context, "calendar_process", "monitor_counts_changed",
                "container_id", containerId,
                "active_process_count", active, "idle_role_count", idle);
    }

    static void recordStrictRejection(Context context, long eventId, String source, String reason) {
        recordStrictRejection(context, AccountContainerStore.selectedId(context),
                eventId, source, reason);
    }

    static void recordStrictRejection(Context context, String containerId,
            long eventId, String source, String reason) {
        if (context == null) return;
        prefs(context).edit().putString(key(containerId, KEY_LAST_REJECTION),
                clean(source) + ":" + eventId + ":" + clean(reason)).apply();
    }

    static boolean isDirectHealthy(Context context) {
        return isDirectHealthy(context, AccountContainerStore.selectedId(context));
    }

    static boolean isDirectHealthy(Context context, String containerId) {
        if (context == null || !GoogleCalendarAuthorization.isConnected(context, containerId)) {
            return false;
        }
        if (!GoogleCalendarProcessSource.hasFreshCache(
                context, containerId, System.currentTimeMillis())) {
            return false;
        }
        String source = clean(readString(context, containerId, KEY_CURRENT_SOURCE, ""));
        String failure = clean(readString(context, containerId, KEY_FAILURE, ""));
        return "direct_api".equals(source) && failure.isEmpty();
    }

    static boolean isProviderFallbackActive(Context context) {
        return isProviderFallbackActive(context, AccountContainerStore.selectedId(context));
    }

    static boolean isProviderFallbackActive(Context context, String containerId) {
        return context != null && "calendar_provider_fallback".equals(
                clean(readString(context, containerId, KEY_CURRENT_SOURCE, "")));
    }

    static String userFacingSummary(Context context) {
        return userFacingSummary(context, AccountContainerStore.selectedId(context));
    }

    static String userFacingSummary(Context context, String containerId) {
        if (context == null) return "Direct Google Calendar health is unavailable.";
        String source = clean(readString(context, containerId, KEY_CURRENT_SOURCE, ""));
        String failure = clean(readString(context, containerId, KEY_FAILURE, ""));
        long success = readLong(context, containerId, KEY_LAST_DIRECT_SUCCESS, 0L);
        StringBuilder out = new StringBuilder(
                "Direct Google Calendar access is degraded.\n\n");
        if (isProviderFallbackActive(context, containerId)) {
            out.append("Current source: CalendarProvider fallback (active).");
        } else if ("direct_api".equals(source)) {
            out.append("Current source: direct API cache is stale or the latest poll failed.");
        } else {
            out.append("Current source: direct API unavailable.");
        }
        if (!failure.isEmpty()) out.append("\nLatest direct failure: ").append(failure);
        out.append("\nLast successful direct poll: ").append(age(success));
        out.append("\nAuthorization: ")
                .append(GoogleCalendarAuthorization.statusSummary(context, containerId));
        return out.toString();
    }

    static String summary(Context context) {
        return summary(context, AccountContainerStore.selectedId(context));
    }

    static String summary(Context context, String containerId) {
        if (context == null) return "Monitor health unavailable";
        long success = readLong(context, containerId, KEY_LAST_DIRECT_SUCCESS, 0L);
        String source = clean(readString(context, containerId, KEY_CURRENT_SOURCE, ""));
        String lastSource = clean(readString(context, containerId, KEY_LAST_SOURCE, ""));
        String failure = clean(readString(context, containerId, KEY_FAILURE, ""));
        String rejection = clean(readString(context, containerId, KEY_LAST_REJECTION, ""));
        int active = Math.max(0, readInt(context, containerId, KEY_ACTIVE_COUNT, 0));
        int idle = Math.max(0, readInt(context, containerId, KEY_IDLE_COUNT, 0));
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

    static void clearContainer(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return;
        SharedPreferences.Editor e = prefs(context).edit();
        String[] keys = {KEY_LAST_DIRECT_SUCCESS, KEY_CURRENT_SOURCE, KEY_LAST_SOURCE,
                KEY_FAILURE, KEY_ACTIVE_COUNT, KEY_IDLE_COUNT, KEY_LAST_REJECTION};
        for (String base : keys) e.remove(key(containerId, base));
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            for (String base : keys) e.remove(base);
        }
        e.apply();
    }

    private static String readString(Context context, String containerId,
            String base, String fallback) {
        SharedPreferences p = prefs(context);
        String scoped = key(containerId, base);
        if (p.contains(scoped)) return p.getString(scoped, fallback);
        return AccountContainerStore.isLegacyOwner(context, containerId)
                ? p.getString(base, fallback) : fallback;
    }

    private static long readLong(Context context, String containerId, String base, long fallback) {
        SharedPreferences p = prefs(context);
        String scoped = key(containerId, base);
        if (p.contains(scoped)) return p.getLong(scoped, fallback);
        return AccountContainerStore.isLegacyOwner(context, containerId)
                ? p.getLong(base, fallback) : fallback;
    }

    private static int readInt(Context context, String containerId, String base, int fallback) {
        SharedPreferences p = prefs(context);
        String scoped = key(containerId, base);
        if (p.contains(scoped)) return p.getInt(scoped, fallback);
        return AccountContainerStore.isLegacyOwner(context, containerId)
                ? p.getInt(base, fallback) : fallback;
    }

    private static String key(String containerId, String base) {
        return base + "::" + AccountNotificationNamespace.safe(containerId);
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

    private static void notifyHealthChanged(Context context, String containerId) {
        if (context == null) return;
        Intent intent = new Intent(AppConstants.ACTION_CALENDAR_HEALTH_CHANGED)
                .putExtra(OAuthService.EXTRA_CONTAINER_ID, containerId)
                .setPackage(context.getPackageName());
        context.sendBroadcast(intent, AppConstants.INTERNAL_PERMISSION);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
