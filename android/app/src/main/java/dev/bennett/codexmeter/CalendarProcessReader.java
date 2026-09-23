package dev.bennett.codexmeter;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Reads GPT watchdogs from direct Google Calendar when fresh, with local Provider fallback. */
final class CalendarProcessReader {
    private static final long LOOKBACK_MS = TimeUnit.HOURS.toMillis(24);
    private static final long LOOKAHEAD_MS = TimeUnit.HOURS.toMillis(2);

    private CalendarProcessReader() {
    }

    /**
     * Returns all watchdog instances in the local observation window, including future ones.
     * Future observation is intentional: it lets idle lifecycle state remember role/topic/event
     * metadata before BEGIN so deleting a known watchdog early can still complete that role.
     */
    static List<CalendarProcess> observed(Context context, long nowMillis) {
        if (context == null) return new ArrayList<>();
        ProcessNotificationScheduler.schedule(context);
        if (GoogleCalendarAuthorization.isConnected(context)) {
            GoogleCalendarProcessSource.refreshIfDue(context, null);
            if (GoogleCalendarProcessSource.hasFreshCache(context, nowMillis)) {
                return GoogleCalendarProcessSource.cached(context);
            }
        }
        return queryProvider(context, nowMillis);
    }

    static List<CalendarProcess> active(Context context, long nowMillis) {
        return active(observed(context, nowMillis), nowMillis);
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

    static List<CalendarProcess> recentlyFinished(List<CalendarProcess> all, long nowMillis) {
        List<CalendarProcess> processes = new ArrayList<>();
        if (all == null) return processes;
        for (CalendarProcess process : all) {
            if (process.endMillis <= nowMillis) processes.add(process);
        }
        processes.sort(Comparator.comparingLong(
                (CalendarProcess process) -> process.endMillis).reversed());
        return processes;
    }

    /**
     * Returns false only on authoritative disappearance evidence. A fresh successful direct
     * Google Calendar cache is primary; otherwise the local Provider is a fallback. Auth/network,
     * permission and read failures remain UNKNOWN (true) so they cannot manufacture completion.
     */
    static boolean eventExists(Context context, long eventId) {
        if (context == null || eventId <= 0L) return true;
        long now = System.currentTimeMillis();
        if (GoogleCalendarAuthorization.isConnected(context)
                && GoogleCalendarProcessSource.hasFreshCache(context, now)) {
            boolean exists = GoogleCalendarProcessSource.cachedEventExists(
                    context, eventId, now);
            DiagnosticLog.info(context, "calendar_api", "watchdog_presence_checked",
                    "event_id", eventId,
                    "exists", exists,
                    "authoritative", true,
                    "source", "direct_api");
            return exists;
        }
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        try (Cursor cursor = context.getContentResolver().query(eventUri,
                new String[]{CalendarContract.Events._ID}, null, null, null)) {
            return cursor == null || cursor.moveToFirst();
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "calendar_process", "event_exists_read_failed",
                    "event_id", eventId,
                    "error", exception.getClass().getSimpleName());
            return true;
        }
    }

    private static List<CalendarProcess> queryProvider(Context context, long nowMillis) {
        List<CalendarProcess> processes = new ArrayList<>();
        if (context == null || context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
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
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(builder.build(), projection, null, null,
                CalendarContract.Instances.END + " ASC")) {
            if (cursor == null) return processes;
            int eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID);
            int titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE);
            int descriptionIndex = cursor.getColumnIndexOrThrow(
                    CalendarContract.Instances.DESCRIPTION);
            int beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN);
            int endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END);
            while (cursor.moveToNext()) {
                long begin = cursor.getLong(beginIndex);
                long end = cursor.getLong(endIndex);
                CalendarProcess process = CalendarProcess.fromEvent(
                        cursor.getLong(eventIdIndex),
                        cursor.getString(titleIndex),
                        cursor.getString(descriptionIndex),
                        begin,
                        end);
                if (process != null) processes.add(process);
            }
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "calendar_process", "read_failed",
                    "error", exception.getClass().getSimpleName());
            processes.clear();
        }
        return processes;
    }
}