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
            testPagination();
            testSameDayFormatting();
            testMultiDayFormatting();
            testLengthFormatting();
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
        System.out.println("RoleSessionHistorySelfTest PASS");
    }

    private static void testPagination() {
        assert RoleSessionHistory.initialVisibleCount(0) == 0;
        assert RoleSessionHistory.initialVisibleCount(6) == 6;
        assert RoleSessionHistory.initialVisibleCount(25) == 10;
        assert RoleSessionHistory.nextVisibleCount(10, 25) == 20;
        assert RoleSessionHistory.nextVisibleCount(20, 25) == 25;
        assert RoleSessionHistory.nextVisibleCount(25, 25) == 25;
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
