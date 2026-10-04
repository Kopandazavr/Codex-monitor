package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Direct Google Calendar REST source with a small durable watchdog cache. */
final class GoogleCalendarProcessSource {
    private static final String PREFS = "codex_google_calendar_process_source_v1";
    private static final String KEY_EVENTS = "events_json";
    private static final String KEY_LAST_REFRESH = "last_refresh";
    private static final long LOOKBACK_MS = TimeUnit.HOURS.toMillis(24);
    private static final long LOOKAHEAD_MS = TimeUnit.HOURS.toMillis(2);
    private static final long REFRESH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long AUTHORITATIVE_CACHE_MS = TimeUnit.MINUTES.toMillis(3);

    private static final Object LOCK = new Object();
    private static final Map<String, RefreshState> REFRESH_STATES = new HashMap<>();

    private static final class RefreshState {
        final List<Runnable> waiters = new ArrayList<>();
        boolean inFlight;
    }

    private GoogleCalendarProcessSource() {
    }

    private static final class CalendarHttpException extends Exception {
        final int status;

        CalendarHttpException(int status) {
            super("Calendar API HTTP " + status);
            this.status = status;
        }
    }


    static boolean hasFreshCache(Context context, long nowMillis) {
        return hasFreshCache(context, AccountContainerStore.selectedId(context), nowMillis);
    }

    static boolean hasFreshCache(Context context, String containerId, long nowMillis) {
        if (context == null || !GoogleCalendarAuthorization.isConnected(context, containerId)) {
            return false;
        }
        long refreshed = prefs(context).getLong(key(containerId, KEY_LAST_REFRESH), 0L);
        return refreshed > 0L && nowMillis >= refreshed
                && nowMillis - refreshed <= AUTHORITATIVE_CACHE_MS;
    }

    static List<CalendarProcess> cached(Context context) {
        return cached(context, AccountContainerStore.selectedId(context));
    }

    static List<CalendarProcess> cached(Context context, String containerId) {
        List<CalendarProcess> result = new ArrayList<>();
        if (context == null) return result;
        String raw = prefs(context).getString(key(containerId, KEY_EVENTS), "[]");
        try {
            JSONArray rows = new JSONArray(raw == null ? "[]" : raw);
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.optJSONObject(index);
                if (row == null) continue;
                long eventId = row.optLong("id", 0L);
                String title = row.optString("title", "");
                String description = row.optString("description", "");
                long begin = row.optLong("begin", 0L);
                long end = row.optLong("end", 0L);
                long providerUpdated = row.optLong("provider_updated", 0L);
                CalendarProcess process = CalendarProcess.fromDirectEvent(
                        eventId, title, description, begin, end, providerUpdated);
                if (process != null) {
                    WatchdogObservationDiagnostics.recordIdentity(context, eventId,
                            "direct_api", "direct_cache", description, process);
                    result.add(process);
                } else {
                    String reason = CalendarProcess.rejectionReason(
                            title, description, begin, end);
                    if (!"not_watchdog".equals(reason)) {
                        // "watchdog_rejected_metadata"; "source", "direct_cache"
                        WatchdogObservationDiagnostics.logRejected(context, "calendar_api",
                                eventId, "direct_cache", "direct_cache",
                                title, description, begin, end);
                    }
                }
            }
        } catch (Exception exception) {
            DiagnosticLog.warn(context, "calendar_api", "cache_read_failed",
                    "container_id", containerId,
                    "error", exception.getClass().getSimpleName());
        }
        return result;
    }

    static boolean cachedEventExists(Context context, long eventId, long nowMillis) {
        return cachedEventExists(context, AccountContainerStore.selectedId(context),
                eventId, nowMillis);
    }

    static boolean cachedEventExists(Context context, String containerId,
            long eventId, long nowMillis) {
        if (!hasFreshCache(context, containerId, nowMillis)) return true;
        for (CalendarProcess process : cached(context, containerId)) {
            if (process.eventId == eventId) return true;
        }
        return false;
    }

    static void refreshIfDue(Context context, Runnable completion) {
        refreshIfDue(context, AccountContainerStore.selectedId(context), completion);
    }

    static void refreshIfDue(Context context, String containerId, Runnable completion) {
        if (context == null || !GoogleCalendarAuthorization.isConnected(context, containerId)) {
            run(completion);
            return;
        }
        long now = System.currentTimeMillis();
        long last = prefs(context).getLong(key(containerId, KEY_LAST_REFRESH), 0L);
        if (last > 0L && now >= last && now - last < REFRESH_INTERVAL_MS) {
            run(completion);
            return;
        }
        refresh(context, containerId, completion);
    }

    static void forceRefresh(Context context, Runnable completion) {
        forceRefresh(context, AccountContainerStore.selectedId(context), completion);
    }

    static void forceRefresh(Context context, String containerId, Runnable completion) {
        if (context == null || !GoogleCalendarAuthorization.isConnected(context, containerId)) {
            run(completion);
            return;
        }
        refresh(context, containerId, completion);
    }

    private static void refresh(Context context, String containerId, Runnable completion) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            RefreshState state = stateLocked(containerId);
            if (completion != null) state.waiters.add(completion);
            if (state.inFlight) return;
            state.inFlight = true;
        }
        requestAndFetch(app, containerId, false);
    }

    private static void requestAndFetch(Context app, String containerId,
            boolean retriedUnauthorized) {
        GoogleCalendarAuthorization.accessToken(app, containerId, token -> {
            if (token == null || token.isEmpty()) {
                MonitorHealthDiagnostics.recordPollFailure(app,
                        "authorization_token_unavailable");
                finishRefresh(containerId);
                return;
            }
            new Thread(() -> {
                try {
                    List<CalendarProcess> processes =
                            fetch(app, token, System.currentTimeMillis());
                    store(app, containerId, processes, System.currentTimeMillis());
                    MonitorHealthDiagnostics.recordDirectPollSuccess(app, processes.size());
                    if (retriedUnauthorized) {
                        DiagnosticLog.info(app, "calendar_api",
                                "calendar_http_401_recovered_after_token_refresh",
                                "container_id", containerId,
                                "events", processes.size());
                    }
                    finishRefresh(containerId);
                } catch (CalendarHttpException exception) {
                    if (exception.status == 401 && !retriedUnauthorized) {
                        GoogleCalendarAuthorization.invalidateCachedToken(
                                app, containerId, "calendar_http_401");
                        DiagnosticLog.warn(app, "calendar_api",
                                "calendar_http_401_retrying_with_fresh_token",
                                "container_id", containerId);
                        requestAndFetch(app, containerId, true);
                        return;
                    }
                    MonitorHealthDiagnostics.recordPollFailure(app, exception);
                    finishRefresh(containerId);
                } catch (Exception exception) {
                    MonitorHealthDiagnostics.recordPollFailure(app, exception);
                    finishRefresh(containerId);
                }
            }, "codex-calendar-api-" + safeSuffix(containerId)).start();
        });
    }

    private static List<CalendarProcess> fetch(Context context, String token, long nowMillis)
            throws Exception {
        String timeMin = URLEncoder.encode(
                Instant.ofEpochMilli(Math.max(0L, nowMillis - LOOKBACK_MS)).toString(),
                StandardCharsets.UTF_8.name());
        String timeMax = URLEncoder.encode(
                Instant.ofEpochMilli(nowMillis + LOOKAHEAD_MS).toString(),
                StandardCharsets.UTF_8.name());
        String query = URLEncoder.encode("GPT_WATCHDOG", StandardCharsets.UTF_8.name());
        String url = "https://www.googleapis.com/calendar/v3/calendars/primary/events"
                + "?singleEvents=true&orderBy=startTime&maxResults=100"
                + "&q=" + query + "&timeMin=" + timeMin + "&timeMax=" + timeMax;

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String body = read(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new CalendarHttpException(status);
        }

        List<CalendarProcess> result = new ArrayList<>();
        JSONObject root = new JSONObject(body);
        JSONArray items = root.optJSONArray("items");
        if (items == null) return result;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null || "cancelled".equals(item.optString("status"))) continue;
            String remoteId = item.optString("id", "");
            String title = item.optString("summary", "");
            String description = item.optString("description", "");
            long begin = eventMillis(item.optJSONObject("start"));
            long end = eventMillis(item.optJSONObject("end"));
            long providerUpdated = timestampMillis(item.optString("updated", ""));
            if (begin <= 0L || end <= begin) continue;
            long eventId = stableEventId(remoteId);
            CalendarProcess process = CalendarProcess.fromDirectEvent(
                    eventId, title, description, begin, end, providerUpdated);
            if (process != null) {
                WatchdogObservationDiagnostics.recordIdentity(context, eventId,
                        "direct_api", "remote_fetch", description, process);
                result.add(process);
            } else {
                String reason = CalendarProcess.rejectionReason(
                        title, description, begin, end);
                if (!"not_watchdog".equals(reason)) {
                    // "watchdog_rejected_metadata"; "source", "direct_api"
                    WatchdogObservationDiagnostics.logRejected(context, "calendar_api",
                            eventId, "direct_api", "remote_fetch",
                            title, description, begin, end);
                }
            }
        }
        return result;
    }

    private static void store(Context context, String containerId,
            List<CalendarProcess> processes, long refreshedAt) {
        JSONArray rows = new JSONArray();
        if (processes != null) {
            for (CalendarProcess process : processes) {
                JSONObject row = new JSONObject();
                try {
                    row.put("id", process.eventId);
                    row.put("title", CalendarProcess.WATCHDOG_PREFIX + process.project);
                    StringBuilder metadata = new StringBuilder("codex_monitor_watchdog=v1");
                    if (!process.project.isEmpty()) metadata.append(" project=").append(process.project);
                    if (!process.projectShort.isEmpty()) {
                        metadata.append(" project_short=").append(process.projectShort);
                    }
                    if (!process.role.isEmpty()) metadata.append(" role=").append(process.role);
                    if (!process.roleIcon.isEmpty()) {
                        metadata.append(" role_icon=").append(process.roleIcon);
                    }
                    if (!process.topic.isEmpty()) metadata.append(" topic=").append(process.topic);
                    if (!process.instanceId.isEmpty()) {
                        metadata.append(" instance_id=").append(process.instanceId);
                    }
                    row.put("description", metadata.toString());
                    row.put("begin", process.beginMillis);
                    row.put("end", process.endMillis);
                    row.put("provider_updated", process.providerUpdatedMillis);
                    rows.put(row);
                } catch (Exception ignored) {
                }
            }
        }
        prefs(context).edit()
                .putString(key(containerId, KEY_EVENTS), rows.toString())
                .putLong(key(containerId, KEY_LAST_REFRESH), refreshedAt)
                .apply();
    }

    private static long eventMillis(JSONObject edge) {
        if (edge == null) return 0L;
        String dateTime = edge.optString("dateTime", "");
        if (dateTime.isEmpty()) return 0L;
        try {
            return OffsetDateTime.parse(dateTime).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            try {
                return Instant.parse(dateTime).toEpochMilli();
            } catch (Exception ignoredAgain) {
                return 0L;
            }
        }
    }

    private static long timestampMillis(String value) {
        if(value==null||value.isEmpty())return 0L;
        try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}
        catch(Exception ignored){try{return Instant.parse(value).toEpochMilli();}
        catch(Exception ignoredAgain){return 0L;}}
    }

    private static long stableEventId(String remoteId) {
        if (remoteId == null || remoteId.isEmpty()) return 0L;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(remoteId.getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) value = (value << 8) | (digest[i] & 0xffL);
            return value & Long.MAX_VALUE;
        } catch (Exception ignored) {
            return remoteId.hashCode() & 0x7fffffffL;
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }

    private static void finishRefresh(String containerId) {
        List<Runnable> callbacks;
        synchronized (LOCK) {
            RefreshState state = stateLocked(containerId);
            state.inFlight = false;
            callbacks = new ArrayList<>(state.waiters);
            state.waiters.clear();
        }
        for (Runnable callback : callbacks) run(callback);
    }

    private static RefreshState stateLocked(String containerId) {
        String key = containerId == null ? "" : containerId;
        RefreshState state = REFRESH_STATES.get(key);
        if (state == null) {
            state = new RefreshState();
            REFRESH_STATES.put(key, state);
        }
        return state;
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            try {
                runnable.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    static void clearContainer(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return;
        SharedPreferences.Editor editor = prefs(context).edit()
                .remove(key(containerId, KEY_EVENTS))
                .remove(key(containerId, KEY_LAST_REFRESH));
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            editor.remove(KEY_EVENTS).remove(KEY_LAST_REFRESH);
        }
        editor.apply();
        synchronized (LOCK) {
            REFRESH_STATES.remove(containerId);
        }
    }

    private static String key(String containerId, String base) {
        return base + "::" + safeSuffix(containerId);
    }

    private static String safeSuffix(String containerId) {
        String id = containerId == null ? "" : containerId.trim();
        return id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
