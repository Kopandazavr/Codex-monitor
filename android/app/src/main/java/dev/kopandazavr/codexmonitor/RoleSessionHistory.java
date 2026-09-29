package dev.kopandazavr.codexmonitor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Pure row-model and formatting rules for the Role Settings completed-session history. */
final class RoleSessionHistory {
    static final class RowSpec {
        final boolean dayHeader;
        final int sessionIndex;
        final long dayStartMillis;
        final int sessionCount;
        final long totalDurationMillis;
        final boolean expanded;

        private RowSpec(boolean dayHeader, int sessionIndex, long dayStartMillis,
                int sessionCount, long totalDurationMillis, boolean expanded) {
            this.dayHeader = dayHeader;
            this.sessionIndex = sessionIndex;
            this.dayStartMillis = dayStartMillis;
            this.sessionCount = sessionCount;
            this.totalDurationMillis = totalDurationMillis;
            this.expanded = expanded;
        }

        static RowSpec day(long dayStartMillis, int sessionCount,
                long totalDurationMillis, boolean expanded) {
            return new RowSpec(true, -1, dayStartMillis,
                    sessionCount, totalDurationMillis, expanded);
        }

        static RowSpec session(int sessionIndex, long dayStartMillis) {
            return new RowSpec(false, sessionIndex, dayStartMillis, 0, 0L, false);
        }
    }

    private static final class DayGroup {
        final long dayStartMillis;
        final List<Integer> sessionIndices = new ArrayList<>();
        long totalDurationMillis;

        DayGroup(long dayStartMillis) {
            this.dayStartMillis = dayStartMillis;
        }
    }

    private RoleSessionHistory() {}

    static List<RowSpec> buildRows(long[] startedMillisNewestFirst,
            long[] finishedMillisNewestFirst, Set<Long> expandedDays) {
        List<RowSpec> rows = new ArrayList<>();
        if (startedMillisNewestFirst == null || finishedMillisNewestFirst == null) return rows;
        int count = Math.min(startedMillisNewestFirst.length, finishedMillisNewestFirst.length);
        Map<Long, DayGroup> groups = new TreeMap<>(Collections.reverseOrder());
        for (int i = 0; i < count; i++) {
            long started = startedMillisNewestFirst[i];
            long finished = finishedMillisNewestFirst[i];
            long groupingMillis = started > 0L ? started : finished;
            long dayStart = dayStartMillis(groupingMillis);
            DayGroup group = groups.get(dayStart);
            if (group == null) {
                group = new DayGroup(dayStart);
                groups.put(dayStart, group);
            }
            group.sessionIndices.add(i);
            if (started > 0L && finished >= started) {
                long duration = finished - started;
                if (Long.MAX_VALUE - group.totalDurationMillis < duration) {
                    group.totalDurationMillis = Long.MAX_VALUE;
                } else {
                    group.totalDurationMillis += duration;
                }
            }
        }
        for (DayGroup group : groups.values()) {
            boolean expanded = expandedDays != null && expandedDays.contains(group.dayStartMillis);
            rows.add(RowSpec.day(group.dayStartMillis, group.sessionIndices.size(),
                    group.totalDurationMillis, expanded));
            if (expanded) {
                for (int sessionIndex : group.sessionIndices) {
                    rows.add(RowSpec.session(sessionIndex, group.dayStartMillis));
                }
            }
        }
        return rows;
    }

    static long defaultExpandedDay(long nowMillis, long[] startedMillisNewestFirst,
            long[] finishedMillisNewestFirst) {
        if (startedMillisNewestFirst == null || finishedMillisNewestFirst == null) {
            return Long.MIN_VALUE;
        }
        long today = dayStartMillis(nowMillis);
        int count = Math.min(startedMillisNewestFirst.length, finishedMillisNewestFirst.length);
        for (int i = 0; i < count; i++) {
            long groupingMillis = startedMillisNewestFirst[i] > 0L
                    ? startedMillisNewestFirst[i] : finishedMillisNewestFirst[i];
            if (dayStartMillis(groupingMillis) == today) return today;
        }
        return Long.MIN_VALUE;
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
        return "Session length · " + formatDuration(finishedMillis - startedMillis);
    }

    static String formatDuration(long durationMillis) {
        long minutes = Math.max(0L, durationMillis / 60_000L);
        long hours = minutes / 60L;
        long remainder = minutes % 60L;
        if (hours == 0L) return remainder + "m";
        return hours + "h" + (remainder == 0L ? "" : " " + remainder + "m");
    }
}
