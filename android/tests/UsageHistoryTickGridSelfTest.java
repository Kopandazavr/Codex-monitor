package dev.kopandazavr.codexmonitor;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class UsageHistoryTickGridSelfTest {
    public static void main(String[] args) {
        testTenMinuteAlignment();
        testOffsetZoneAlignment();
        testWeeklyFourHourAlignment();
        testWeeklyDayRollover();
        testWeeklyLabelPattern();
        System.out.println("UsageHistoryTickGridSelfTest PASS");
    }

    private static void testTenMinuteAlignment() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        long start = local(utc, 2026, Calendar.SEPTEMBER, 28, 22, 34, 25);
        long first = UsageHistoryTickGrid.firstZoomTick(start, false, utc);
        assert fields(utc, first, Calendar.HOUR_OF_DAY) == 22;
        assert fields(utc, first, Calendar.MINUTE) == 40;
        long next = UsageHistoryTickGrid.nextZoomTick(first, false, utc);
        assert fields(utc, next, Calendar.HOUR_OF_DAY) == 22;
        assert fields(utc, next, Calendar.MINUTE) == 50;
    }

    private static void testOffsetZoneAlignment() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Kathmandu");
        long start = local(zone, 2026, Calendar.SEPTEMBER, 28, 22, 34, 25);
        long first = UsageHistoryTickGrid.firstZoomTick(start, false, zone);
        assert fields(zone, first, Calendar.HOUR_OF_DAY) == 22;
        assert fields(zone, first, Calendar.MINUTE) == 40;
    }

    private static void testWeeklyFourHourAlignment() {
        TimeZone zone = TimeZone.getTimeZone("America/Los_Angeles");
        long start = local(zone, 2026, Calendar.SEPTEMBER, 27, 1, 17, 0);
        long first = UsageHistoryTickGrid.firstZoomTick(start, true, zone);
        assert fields(zone, first, Calendar.HOUR_OF_DAY) == 4;
        assert fields(zone, first, Calendar.MINUTE) == 0;
        long next = UsageHistoryTickGrid.nextZoomTick(first, true, zone);
        assert fields(zone, next, Calendar.HOUR_OF_DAY) == 8;
    }

    private static void testWeeklyDayRollover() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        long twenty = local(utc, 2026, Calendar.SEPTEMBER, 27, 20, 0, 0);
        long next = UsageHistoryTickGrid.nextZoomTick(twenty, true, utc);
        Calendar value = Calendar.getInstance(utc, Locale.US);
        value.setTimeInMillis(next);
        assert value.get(Calendar.DAY_OF_MONTH) == 28;
        assert value.get(Calendar.HOUR_OF_DAY) == 0;
    }

    private static void testWeeklyLabelPattern() {
        assert "EEE H:mm".equals(UsageHistoryTickGrid.tickPattern(true, true, true));
        assert "EEE h a".equals(UsageHistoryTickGrid.tickPattern(true, true, false));
        assert "EEE d".equals(UsageHistoryTickGrid.tickPattern(true, false, true));
        assert "HH:mm".equals(UsageHistoryTickGrid.tickPattern(false, true, true));
    }

    private static long local(TimeZone zone, int year, int month, int day,
            int hour, int minute, int second) {
        Calendar value = Calendar.getInstance(zone, Locale.US);
        value.clear();
        value.set(year, month, day, hour, minute, second);
        return value.getTimeInMillis();
    }

    private static int fields(TimeZone zone, long millis, int field) {
        Calendar value = Calendar.getInstance(zone, Locale.US);
        value.setTimeInMillis(millis);
        return value.get(field);
    }
}
