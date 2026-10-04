package dev.kopandazavr.codexmonitor;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads GPT watchdogs from direct Google Calendar when fresh, with local Provider fallback. */
final class CalendarProcessReader {
    private static final long LOOKBACK_MS = TimeUnit.HOURS.toMillis(24);
    private static final long LOOKAHEAD_MS = TimeUnit.HOURS.toMillis(2);
    private static final String CANONICAL_PREFS = "codex_watchdog_canonical_v1";
    private static final String KEY_SELECTION_MEMORY = "selection_memory_json";

    private CalendarProcessReader() {
    }

    /**
     * Returns all watchdog instances in the observation window for the selected account.
     * Background callers use the explicit-container overload.
     */
    static List<CalendarProcess> observed(Context context, long nowMillis) {
        return observed(context, AccountContainerStore.selectedId(context), nowMillis);
    }

    static List<CalendarProcess> observed(Context context, String containerId, long nowMillis) {
        if (context == null) return new ArrayList<>();
        ProcessNotificationScheduler.schedule(context);
        if (GoogleCalendarAuthorization.isConnected(context, containerId)) {
            GoogleCalendarProcessSource.refreshIfDue(context, containerId, null);
            if (GoogleCalendarProcessSource.hasFreshCache(context, containerId, nowMillis)) {
                MonitorHealthDiagnostics.recordObservationSource(
                        context, containerId, "direct_api");
                return canonicalize(context, containerId,
                        GoogleCalendarProcessSource.cached(context, containerId));
            }
        }
        if (LocalCalendarFallbackOwner.isOwner(context, containerId)) {
            MonitorHealthDiagnostics.recordObservationSource(
                    context, containerId, "calendar_provider_fallback");
            return canonicalize(context, containerId,
                    queryProvider(context, containerId, nowMillis));
        }
        MonitorHealthDiagnostics.recordObservationSource(
                context, containerId, "no_calendar_fallback");
        return new ArrayList<>();
    }

    static List<CalendarProcess> active(Context context, long nowMillis) {
        return active(observed(context, nowMillis), nowMillis);
    }

    static List<CalendarProcess> active(Context context, String containerId, long nowMillis) {
        return active(observed(context, containerId, nowMillis), nowMillis);
    }

    static List<CalendarProcess> active(List<CalendarProcess> all, long nowMillis) {
        List<CalendarProcess> processes = new ArrayList<>();
        if (all == null) return processes;
        for (CalendarProcess process : all) {
            if (process.isVisibleActive(nowMillis)) processes.add(process);
        }
        processes.sort(Comparator.comparingLong(process -> process.beginMillis));
        return processes;
    }

    static List<CalendarProcess> recentlyFinished(Context context, long nowMillis) {
        return recentlyFinished(observed(context, nowMillis), nowMillis);
    }

    static List<CalendarProcess> recentlyFinished(
            Context context, String containerId, long nowMillis) {
        return recentlyFinished(observed(context, containerId, nowMillis), nowMillis);
    }

    static List<CalendarProcess> recentlyFinished(List<CalendarProcess> all, long nowMillis) {
        List<CalendarProcess> processes = new ArrayList<>();
        if (all == null) return processes;
        for (CalendarProcess process : all) {
            if (process.beginMillis <= nowMillis) processes.add(process);
        }
        processes.sort(Comparator.comparingLong(
                (CalendarProcess process) -> process.beginMillis).reversed());
        return processes;
    }

    static boolean eventExists(Context context, long eventId) {
        return eventExists(context, AccountContainerStore.selectedId(context), eventId, false);
    }

    static boolean eventExists(Context context, long eventId, boolean directSource) {
        return eventExists(context, AccountContainerStore.selectedId(context),
                eventId, directSource);
    }

    static boolean eventExists(Context context, String containerId,
            long eventId, boolean directSource) {
        if (context == null || eventId <= 0L) return true;
        long now = System.currentTimeMillis();
        if (directSource) {
            if (!GoogleCalendarAuthorization.isConnected(context, containerId)
                    || !GoogleCalendarProcessSource.hasFreshCache(
                    context, containerId, now)) {
                DiagnosticLog.info(context, "calendar_api", "watchdog_presence_unknown",
                        "container_id", containerId,
                        "event_id", eventId,
                        "reason", "direct_cache_not_fresh");
                return true;
            }
            boolean exists = GoogleCalendarProcessSource.cachedEventExists(
                    context, containerId, eventId, now);
            DiagnosticLog.info(context, "calendar_api", "watchdog_presence_checked",
                    "container_id", containerId,
                    "event_id", eventId,
                    "exists", exists,
                    "authoritative", true,
                    "source", "direct_api");
            return exists;
        }
        if (!LocalCalendarFallbackOwner.isOwner(context, containerId)) return true;
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        List<Long> calendarIds = providerCalendarIds(context, containerId);
        if (calendarIds != null && calendarIds.isEmpty()) return true;
        Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        String selection = calendarIds == null ? null : calendarSelection(calendarIds);
        String[] selectionArgs = calendarIds == null ? null : calendarSelectionArgs(calendarIds);
        try (Cursor cursor = context.getContentResolver().query(eventUri,
                new String[]{CalendarContract.Events._ID}, selection, selectionArgs, null)) {
            return cursor == null || cursor.moveToFirst();
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "calendar_process", "event_exists_read_failed",
                    "container_id", containerId,
                    "event_id", eventId,
                    "error", exception.getClass().getSimpleName());
            return true;
        }
    }

    private static List<CalendarProcess> canonicalize(Context context, String containerId,
            List<CalendarProcess> processes) {
        Map<String, WatchdogCanonicalizer.Memory> memory =
                loadSelectionMemory(context, containerId);
        List<CalendarProcess> selected = WatchdogCanonicalizer.select(processes, memory);
        saveSelectionMemory(context, containerId, memory);
        if (processes != null && selected.size() != processes.size()) {
            DiagnosticLog.info(context, "calendar_process", "watchdog_duplicates_suppressed",
                    "container_id", containerId,
                    "observed", processes.size(), "canonical", selected.size());
        }
        return selected;
    }

    private static Map<String, WatchdogCanonicalizer.Memory> loadSelectionMemory(
            Context context, String containerId) {
        Map<String, WatchdogCanonicalizer.Memory> memory = new HashMap<>();
        if (context == null) return memory;
        String raw = context.getSharedPreferences(CANONICAL_PREFS, Context.MODE_PRIVATE)
                .getString(scopedKey(containerId), "{}");
        try {
            JSONObject root = new JSONObject(raw == null ? "{}" : raw);
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject json = root.optJSONObject(key);
                if (json == null) continue;
                WatchdogCanonicalizer.Memory item = new WatchdogCanonicalizer.Memory();
                item.winnerEventId = json.optLong("winner_id", 0L);
                item.winnerBeginMillis = json.optLong("winner_begin", 0L);
                item.winnerUpdatedMillis = json.optLong("winner_updated", 0L);
                JSONArray shadows = json.optJSONArray("shadows");
                if (shadows != null) {
                    for (int i = 0; i < shadows.length(); i++) {
                        long id = shadows.optLong(i, 0L);
                        if (id > 0L) item.shadowEventIds.add(id);
                    }
                }
                memory.put(key, item);
            }
        } catch (JSONException e) {
            DiagnosticLog.warn(context, "calendar_process", "canonical_memory_parse_failed",
                    "container_id", containerId,
                    "error", e.getClass().getSimpleName());
        }
        return memory;
    }

    private static void saveSelectionMemory(Context context, String containerId,
            Map<String, WatchdogCanonicalizer.Memory> memory) {
        if (context == null) return;
        JSONObject root = new JSONObject();
        for (Map.Entry<String, WatchdogCanonicalizer.Memory> entry : memory.entrySet()) {
            WatchdogCanonicalizer.Memory item = entry.getValue();
            JSONObject json = new JSONObject();
            try {
                json.put("winner_id", item.winnerEventId);
                json.put("winner_begin", item.winnerBeginMillis);
                json.put("winner_updated", item.winnerUpdatedMillis);
                JSONArray shadows = new JSONArray();
                for (Long id : item.shadowEventIds) shadows.put(id);
                json.put("shadows", shadows);
                root.put(entry.getKey(), json);
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(CANONICAL_PREFS, Context.MODE_PRIVATE).edit()
                .putString(scopedKey(containerId), root.toString()).apply();
    }

    private static String scopedKey(String containerId) {
        String id = containerId == null ? "" : containerId.trim();
        return KEY_SELECTION_MEMORY + "::"
                + id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static List<CalendarProcess> queryProvider(
            Context context, String containerId, long nowMillis) {
        List<CalendarProcess> processes = new ArrayList<>();
        if (context == null || context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return processes;
        }
        List<Long> calendarIds = providerCalendarIds(context, containerId);
        if (calendarIds != null && calendarIds.isEmpty()) {
            DiagnosticLog.info(context, "calendar_process", "provider_owner_unresolved",
                    "container_id", containerId);
            return processes;
        }
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, Math.max(0L, nowMillis - LOOKBACK_MS));
        ContentUris.appendId(builder, nowMillis + LOOKAHEAD_MS);
        String[] projection = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END
        };
        String selection = calendarIds == null ? null : calendarSelection(calendarIds);
        String[] selectionArgs = calendarIds == null ? null : calendarSelectionArgs(calendarIds);
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(builder.build(), projection, selection, selectionArgs,
                CalendarContract.Instances.END + " ASC")) {
            if (cursor == null) return processes;
            int eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID);
            int titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE);
            int descriptionIndex = cursor.getColumnIndexOrThrow(
                    CalendarContract.Instances.DESCRIPTION);
            int beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN);
            int endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END);
            while (cursor.moveToNext()) {
                long eventId = cursor.getLong(eventIdIndex);
                String title = cursor.getString(titleIndex);
                String description = cursor.getString(descriptionIndex);
                long begin = cursor.getLong(beginIndex);
                long end = cursor.getLong(endIndex);
                CalendarProcess process = CalendarProcess.fromEvent(
                        eventId, title, description, begin, end);
                if (process != null) {
                    WatchdogObservationDiagnostics.recordIdentity(context, eventId,
                            "calendar_provider", "provider_fallback",
                            description, process);
                    processes.add(process);
                    continue;
                }
                String reason = CalendarProcess.rejectionReason(
                        title, description, begin, end);
                if (!"not_watchdog".equals(reason)) {
                    WatchdogObservationDiagnostics.logRejected(context, "calendar_process",
                            eventId, "calendar_provider", "provider_fallback",
                            title, description, begin, end);
                }
            }
        } catch (RuntimeException exception) {
            MonitorHealthDiagnostics.recordPollFailure(context, containerId,
                    "calendar_provider_read_" + exception.getClass().getSimpleName());
            DiagnosticLog.warn(context, "calendar_process", "read_failed",
                    "container_id", containerId,
                    "error", exception.getClass().getSimpleName());
            processes.clear();
        }
        return processes;
    }

    /**
     * null means the single-container legacy provider may remain unfiltered.
     * An empty list in multi-account mode means provenance is not safe enough to consume.
     */
    private static List<Long> providerCalendarIds(Context context, String containerId) {
        if (AccountContainerStore.all(context).size() <= 1) return null;
        List<Long> ids = new ArrayList<>();
        String googleAccount =
                GoogleCalendarAuthorization.providerAccountName(context, containerId);
        if (googleAccount.isEmpty()) return ids;
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID},
                CalendarContract.Calendars.ACCOUNT_NAME + " = ? AND "
                        + CalendarContract.Calendars.ACCOUNT_TYPE + " = ?",
                new String[]{googleAccount,
                        GoogleCalendarAuthorization.GOOGLE_ACCOUNT_TYPE}, null)) {
            if (cursor == null) return ids;
            int idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID);
            while (cursor.moveToNext()) ids.add(cursor.getLong(idIndex));
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "calendar_process", "calendar_owner_lookup_failed",
                    "container_id", containerId,
                    "error", exception.getClass().getSimpleName());
        }
        return ids;
    }

    private static String calendarSelection(List<Long> calendarIds) {
        StringBuilder out = new StringBuilder(CalendarContract.Instances.CALENDAR_ID)
                .append(" IN (");
        for (int i = 0; i < calendarIds.size(); i++) {
            if (i > 0) out.append(',');
            out.append('?');
        }
        return out.append(')').toString();
    }

    private static String[] calendarSelectionArgs(List<Long> calendarIds) {
        String[] args = new String[calendarIds.size()];
        for (int i = 0; i < calendarIds.size(); i++) {
            args[i] = Long.toString(calendarIds.get(i));
        }
        return args;
    }

}