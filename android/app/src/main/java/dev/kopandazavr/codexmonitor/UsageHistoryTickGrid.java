package dev.kopandazavr.codexmonitor;

import java.util.Calendar;
import java.util.TimeZone;

/** Clock-aligned tick helpers kept free of Android dependencies for regression testing. */
final class UsageHistoryTickGrid {
    private UsageHistoryTickGrid() {}

    static long firstZoomTick(long axisStartMillis, boolean weekly, TimeZone timeZone) {
        TimeZone zone = timeZone == null ? TimeZone.getDefault() : timeZone;
        Calendar source = Calendar.getInstance(zone);
        source.setTimeInMillis(axisStartMillis);
        int year = source.get(Calendar.YEAR);
        int month = source.get(Calendar.MONTH);
        int day = source.get(Calendar.DAY_OF_MONTH);
        int hour = source.get(Calendar.HOUR_OF_DAY);
        int minute = source.get(Calendar.MINUTE);
        int second = source.get(Calendar.SECOND);
        int millisecond = source.get(Calendar.MILLISECOND);

        if (weekly) {
            boolean exact = hour % 4 == 0 && minute == 0 && second == 0 && millisecond == 0;
            int targetHour = exact ? hour : ((hour / 4) + 1) * 4;
            return localBoundary(zone, year, month, day, targetHour, 0);
        }

        int totalMinutes = hour * 60 + minute;
        boolean exact = minute % 10 == 0 && second == 0 && millisecond == 0;
        int targetMinutes = exact ? totalMinutes : ((totalMinutes / 10) + 1) * 10;
        return localBoundary(zone, year, month, day,
                targetMinutes / 60, targetMinutes % 60);
    }

    static long nextZoomTick(long currentTickMillis, boolean weekly, TimeZone timeZone) {
        TimeZone zone = timeZone == null ? TimeZone.getDefault() : timeZone;
        Calendar current = Calendar.getInstance(zone);
        current.setTimeInMillis(currentTickMillis);
        int year = current.get(Calendar.YEAR);
        int month = current.get(Calendar.MONTH);
        int day = current.get(Calendar.DAY_OF_MONTH);
        if (weekly) {
            return localBoundary(zone, year, month, day,
                    current.get(Calendar.HOUR_OF_DAY) + 4, 0);
        }
        int totalMinutes = current.get(Calendar.HOUR_OF_DAY) * 60
                + current.get(Calendar.MINUTE) + 10;
        return localBoundary(zone, year, month, day,
                totalMinutes / 60, totalMinutes % 60);
    }

    static String tickPattern(boolean weekly, boolean zoomed, boolean is24Hour) {
        if (weekly && !zoomed) return "EEE d";
        if (weekly) return is24Hour ? "EEE H:mm" : "EEE h a";
        return is24Hour ? "HH:mm" : "h:mm";
    }

    private static long localBoundary(TimeZone zone, int year, int month, int day,
            int hour, int minute) {
        Calendar boundary = Calendar.getInstance(zone);
        boundary.clear();
        boundary.set(year, month, day, 0, 0, 0);
        if (hour >= 24) {
            boundary.add(Calendar.DAY_OF_MONTH, hour / 24);
            hour %= 24;
        }
        boundary.set(Calendar.HOUR_OF_DAY, hour);
        boundary.set(Calendar.MINUTE, minute);
        boundary.set(Calendar.SECOND, 0);
        boundary.set(Calendar.MILLISECOND, 0);
        return boundary.getTimeInMillis();
    }
}
