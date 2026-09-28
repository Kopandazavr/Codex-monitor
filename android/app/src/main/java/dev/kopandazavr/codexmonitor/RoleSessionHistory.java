package dev.kopandazavr.codexmonitor;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Pure formatting/pagination rules for the Role Settings completed-session history. */
final class RoleSessionHistory {
    static final int PAGE_SIZE = 10;

    private RoleSessionHistory() {
    }

    static int initialVisibleCount(int total) {
        return Math.min(Math.max(0, total), PAGE_SIZE);
    }

    static int nextVisibleCount(int current, int total) {
        int boundedTotal = Math.max(0, total);
        int base = Math.max(0, current);
        if (base == 0 && boundedTotal > 0) base = PAGE_SIZE;
        return Math.min(boundedTotal, base + PAGE_SIZE);
    }

    static String formatTiming(long startedMillis, long finishedMillis) {
        if (startedMillis <= 0L || finishedMillis < startedMillis) return "";
        Locale locale = Locale.getDefault();
        Calendar start = Calendar.getInstance();
        Calendar finish = Calendar.getInstance();
        start.setTimeInMillis(startedMillis);
        finish.setTimeInMillis(finishedMillis);
        boolean sameDay = start.get(Calendar.ERA) == finish.get(Calendar.ERA)
                && start.get(Calendar.YEAR) == finish.get(Calendar.YEAR)
                && start.get(Calendar.DAY_OF_YEAR) == finish.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            SimpleDateFormat date = new SimpleDateFormat("dd.MM.yy", locale);
            SimpleDateFormat time = new SimpleDateFormat("HH:mm", locale);
            return date.format(new Date(startedMillis)) + " · "
                    + time.format(new Date(startedMillis)) + "–"
                    + time.format(new Date(finishedMillis));
        }
        SimpleDateFormat dateTime = new SimpleDateFormat("dd.MM.yy HH:mm", locale);
        return dateTime.format(new Date(startedMillis)) + " – "
                + dateTime.format(new Date(finishedMillis));
    }

    static String formatLength(long startedMillis, long finishedMillis) {
        if (startedMillis <= 0L || finishedMillis < startedMillis) return "";
        long minutes = Math.max(0L, (finishedMillis - startedMillis) / 60_000L);
        long hours = minutes / 60L;
        long remainder = minutes % 60L;
        if (hours == 0L) return "Session length · " + remainder + "m";
        return "Session length · " + hours + "h"
                + (remainder == 0L ? "" : " " + remainder + "m");
    }
}
