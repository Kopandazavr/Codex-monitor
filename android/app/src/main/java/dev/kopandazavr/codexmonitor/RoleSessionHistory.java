package dev.kopandazavr.codexmonitor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Pure row-model and formatting rules for the Role Settings completed-session history. */
final class RoleSessionHistory {
    static final class RowSpec {
        final boolean dayHeader;
        final int sessionIndex;
        final long dayStartMillis;

        private RowSpec(boolean dayHeader, int sessionIndex, long dayStartMillis) {
            this.dayHeader = dayHeader;
            this.sessionIndex = sessionIndex;
            this.dayStartMillis = dayStartMillis;
        }

        static RowSpec day(long dayStartMillis) {
            return new RowSpec(true, -1, dayStartMillis);
        }

        static RowSpec session(int sessionIndex, long dayStartMillis) {
            return new RowSpec(false, sessionIndex, dayStartMillis);
        }
    }

    private RoleSessionHistory() {}

    static List<RowSpec> buildRows(long[] finishedMillisNewestFirst) {
        List<RowSpec> rows = new ArrayList<>();
        if (finishedMillisNewestFirst == null) return rows;
        long previousDay = Long.MIN_VALUE;
        for (int i = 0; i < finishedMillisNewestFirst.length; i++) {
            long dayStart = dayStartMillis(finishedMillisNewestFirst[i]);
            if (dayStart != previousDay) {
                rows.add(RowSpec.day(dayStart));
                previousDay = dayStart;
            }
            rows.add(RowSpec.session(i, dayStart));
        }
        return rows;
    }

    static long dayStartMillis(long millis) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(Math.max(0L, millis));
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    static String formatDayHeader(long dayStartMillis) {
        return new SimpleDateFormat("EEE · dd.MM.yy", Locale.getDefault())
                .format(new Date(dayStartMillis));
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
