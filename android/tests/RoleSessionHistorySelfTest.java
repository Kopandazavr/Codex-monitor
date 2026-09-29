package dev.kopandazavr.codexmonitor;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public final class RoleSessionHistorySelfTest {
    public static void main(String[] args) {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            testDayGroupingByStartAndCollapse();
            testCrossMidnightOwnershipAndAggregate();
            testDefaultCollapseModel();
            testSameDayFormatting();
            testMultiDayFormatting();
            testLengthFormatting();
            testAggregateFormatting();
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
        System.out.println("RoleSessionHistorySelfTest PASS");
    }

    private static void testDayGroupingByStartAndCollapse() {
        long[] started = {
                utc(2026, Calendar.SEPTEMBER, 29, 12, 0),
                utc(2026, Calendar.SEPTEMBER, 29, 8, 0),
                utc(2026, Calendar.SEPTEMBER, 28, 23, 50),
                utc(2026, Calendar.SEPTEMBER, 28, 2, 0),
                utc(2026, Calendar.SEPTEMBER, 27, 20, 0)
        };
        long[] finished = {
                utc(2026, Calendar.SEPTEMBER, 29, 13, 0),
                utc(2026, Calendar.SEPTEMBER, 29, 9, 0),
                utc(2026, Calendar.SEPTEMBER, 29, 0, 10),
                utc(2026, Calendar.SEPTEMBER, 28, 3, 0),
                utc(2026, Calendar.SEPTEMBER, 27, 20, 30)
        };
        long today=RoleSessionHistory.dayStartMillis(
                utc(2026, Calendar.SEPTEMBER, 29, 14, 0));
        Set<Long> expanded=new HashSet<>();
        expanded.add(today);
        java.util.List<RoleSessionHistory.RowSpec> rows =
                RoleSessionHistory.buildRows(started,finished,expanded);
        assert rows.size()==5;
        assert rows.get(0).dayHeader && rows.get(0).expanded;
        assert rows.get(0).sessionCount==2;
        assert rows.get(0).totalDurationMillis==120L*60_000L;
        assert !rows.get(1).dayHeader && rows.get(1).sessionIndex==0;
        assert !rows.get(2).dayHeader && rows.get(2).sessionIndex==1;
        assert rows.get(3).dayHeader && !rows.get(3).expanded;
        assert rows.get(3).sessionCount==2;
        assert rows.get(4).dayHeader && !rows.get(4).expanded;
        assert "Tue · 29.09.26".equals(
                RoleSessionHistory.formatDayHeader(rows.get(0).dayStartMillis));
    }

    private static void testCrossMidnightOwnershipAndAggregate() {
        long start=utc(2026, Calendar.SEPTEMBER, 28, 23, 50);
        long finish=utc(2026, Calendar.SEPTEMBER, 29, 0, 10);
        long day=RoleSessionHistory.dayStartMillis(start);
        Set<Long> expanded=new HashSet<>();
        expanded.add(day);
        java.util.List<RoleSessionHistory.RowSpec> rows =
                RoleSessionHistory.buildRows(new long[]{start},new long[]{finish},expanded);
        assert rows.size()==2;
        assert rows.get(0).dayStartMillis==day;
        assert rows.get(0).sessionCount==1;
        assert rows.get(0).totalDurationMillis==20L*60_000L;
        assert rows.get(1).sessionIndex==0;
    }

    private static void testDefaultCollapseModel() {
        long now=utc(2026, Calendar.SEPTEMBER, 29, 15, 0);
        long todayStart=utc(2026, Calendar.SEPTEMBER, 29, 8, 0);
        long olderStart=utc(2026, Calendar.SEPTEMBER, 28, 23, 0);
        long selected=RoleSessionHistory.defaultExpandedDay(now,
                new long[]{todayStart,olderStart},
                new long[]{todayStart+60_000L,olderStart+60_000L});
        assert selected==RoleSessionHistory.dayStartMillis(now);
        assert RoleSessionHistory.defaultExpandedDay(now,
                new long[]{olderStart},new long[]{olderStart+60_000L})==Long.MIN_VALUE;
    }

    private static void testSameDayFormatting() {
        long start = utc(2026, Calendar.SEPTEMBER, 28, 10, 5);
        long finish = utc(2026, Calendar.SEPTEMBER, 28, 11, 42);
        assert "28.09.26 · 10:05–11:42".equals(
                RoleSessionHistory.formatTiming(start, finish));
    }

    private static void testMultiDayFormatting() {
        long start = utc(2026, Calendar.SEPTEMBER, 28, 23, 50);
        long finish = utc(2026, Calendar.SEPTEMBER, 29, 0, 10);
        assert "28.09.26 23:50 – 29.09.26 00:10".equals(
                RoleSessionHistory.formatTiming(start, finish));
    }

    private static void testLengthFormatting() {
        long start = utc(2026, Calendar.SEPTEMBER, 28, 10, 0);
        assert "Session length · 42m".equals(
                RoleSessionHistory.formatLength(start, start + 42L * 60_000L));
        assert "Session length · 1h 5m".equals(
                RoleSessionHistory.formatLength(start, start + 65L * 60_000L));
        assert "Session length · 2h".equals(
                RoleSessionHistory.formatLength(start, start + 120L * 60_000L));
    }

    private static void testAggregateFormatting() {
        assert "3h 42m".equals(RoleSessionHistory.formatDuration(222L*60_000L));
        assert "2h".equals(RoleSessionHistory.formatDuration(120L*60_000L));
        assert "0m".equals(RoleSessionHistory.formatDuration(0L));
    }

    private static long utc(int year, int month, int day, int hour, int minute) {
        Calendar value = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        value.clear();
        value.set(year, month, day, hour, minute, 0);
        return value.getTimeInMillis();
    }
}
