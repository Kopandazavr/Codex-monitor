package dev.bennett.codexmeter;

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
import java.util.List;
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
    private static final List<Runnable> waiters = new ArrayList<>();
    private static boolean refreshInFlight;

    private GoogleCalendarProcessSource() {
    }

    static boolean hasFreshCache(Context context, long nowMillis) {
        if (context == null || !GoogleCalendarAuthorization.isConnected(context)) return false;
        long refreshed = prefs(context).getLong(KEY_LAST_REFRESH, 0L);
        return refreshed > 0L && nowMillis >= refreshed
                && nowMillis - refreshed <= AUTHORITATIVE_CACHE_MS;
    }

    static List<CalendarProcess> cached(Context context) {
        List<CalendarProcess> result = new ArrayList<>();
        if (context == null) return result;
        String raw = prefs(context).getString(KEY_EVENTS, "[]");
        try {
            JSONArray rows = new JSONArray(raw == null ? "[]" : raw);
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.optJSONObject(index);
                if (row == null) continue;
                CalendarProcess process = CalendarProcess.fromEvent(
                        row.optLong("id", 0L),
                        row.optString("title", ""),
                        row.optString("description", ""),
                        row.optLong("begin", 0L),
                        row.optLong("end", 0L));
                if (process != null) result.add(process);
            }
        } catch (Exception exception) {
            DiagnosticLog.warn(context, "calendar_api", "cache_read_failed",
                    "error", exception.getClass().getSimpleName());
        }
        return result;
    }

    static boolean cachedEventExists(Context context, long eventId, long nowMillis) {
        if (!hasFreshCache(context, nowMillis)) return true;
        for (CalendarProcess process : cached(context)) {
            if (process.eventId == eventId) return true;
        }
        return false;
    }

    static void refreshIfDue(Context context, Runnable completion) {
        if (context == null || !GoogleCalendarAuthorization.isConnected(context)) {
            run(completion);
            return;
        }
        long now = System.currentTimeMillis();
        long last = prefs(context).getLong(KEY_LAST_REFRESH, 0L);
        if (last > 0L && now >= last && now - last < REFRESH_INTERVAL_MS) {
            run(completion);
            return;
        }
        refresh(context, completion);
    }

    static void forceRefresh(Context context, Runnable completion) {
        if (context == null || !GoogleCalendarAuthorization.isConnected(context)) {
            run(completion);
            return;
        }
        refresh(context, completion);
    }

    private static void refresh(Context context, Runnable completion) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (completion != null) waiters.add(completion);
            if (refreshInFlight) return;
            refreshInFlight = true;
        }

        GoogleCalendarAuthorization.accessToken(app, token -> {
            if (token == null || token.isEmpty()) {
                finishRefresh();
                return;
            }
            new Thread(() -> {
                try {
                    List<CalendarProcess> processes =
                            fetch(app, token, System.currentTimeMillis());
                    store(app, processes, System.currentTimeMillis());
                    DiagnosticLog.info(app, "calendar_api", "refresh_succeeded",
                            "watchdogs", processes.size(),
                            "source", "direct_api");
                } catch (Exception exception) {
                    DiagnosticLog.warn(app, "calendar_api", "refresh_failed",
                            "error", exception.getClass().getSimpleName());
                } finally {
                    finishRefresh();
                }
            }, "codex-calendar-api").start();
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
            throw new IllegalStateException("Calendar API HTTP " + status);
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
            if (begin <= 0L || end <= begin) continue;
            CalendarProcess process = CalendarProcess.fromEvent(
                    stableEventId(remoteId), title, description, begin, end);
            if (process != null) result.add(process);
        }
        return result;
    }

    private static void store(Context context, List<CalendarProcess> processes, long refreshedAt) {
        JSONArray rows = new JSONArray();
        if (processes != null) {
            for (CalendarProcess process : processes) {
                JSONObject row = new JSONObject();
                try {
                    row.put("id", process.eventId);
                    row.put("title", process.title);
                    row.put("description", process.description);
                    row.put("begin", process.beginMillis);
                    row.put("end", process.endMillis);
                    rows.put(row);
                } catch (Exception ignored) {
                }
            }
        }
        prefs(context).edit()
                .putString(KEY_EVENTS, rows.toString())
                .putLong(KEY_LAST_REFRESH, refreshedAt)
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

    private static void finishRefresh() {
        List<Runnable> callbacks;
        synchronized (LOCK) {
            refreshInFlight = false;
            callbacks = new ArrayList<>(waiters);
            waiters.clear();
        }
        for (Runnable callback : callbacks) run(callback);
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            try {
                runnable.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
