package dev.kopandazavr.codexmonitor;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class RoleSessionHistorySelfTest {
    public static void main(String[] args) {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            testDayGrouping();
            testSameDayFormatting();
            testMultiDayFormatting();
            testLengthFormatting();
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
        System.out.println("RoleSessionHistorySelfTest PASS");
    }

    private static void testDayGrouping() {
        long[] finished = {
                utc(2026, Calendar.SEPTEMBER, 29, 12, 0),
                utc(2026, Calendar.SEPTEMBER, 29, 8, 0),
                utc(2026, Calendar.SEPTEMBER, 28, 23, 59),
                utc(2026, Calendar.SEPTEMBER, 28, 2, 0),
                utc(2026, Calendar.SEPTEMBER, 27, 20, 0)
        };
        java.util.List<RoleSessionHistory.RowSpec> rows =
                RoleSessionHistory.buildRows(finished);
        assert rows.size() == 8;
        assert rows.get(0).dayHeader;
        assert !rows.get(1).dayHeader && rows.get(1).sessionIndex == 0;
        assert !rows.get(2).dayHeader && rows.get(2).sessionIndex == 1;
        assert rows.get(3).dayHeader;
        assert !rows.get(4).dayHeader && rows.get(4).sessionIndex == 2;
        assert !rows.get(5).dayHeader && rows.get(5).sessionIndex == 3;
        assert rows.get(6).dayHeader;
        assert !rows.get(7).dayHeader && rows.get(7).sessionIndex == 4;
        assert "Tue · 29.09.26".equals(
                RoleSessionHistory.formatDayHeader(rows.get(0).dayStartMillis));
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

    private static long utc(int year, int month, int day, int hour, int minute) {
        Calendar value = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        value.clear();
        value.set(year, month, day, hour, minute, 0);
        return value.getTimeInMillis();
    }
}
