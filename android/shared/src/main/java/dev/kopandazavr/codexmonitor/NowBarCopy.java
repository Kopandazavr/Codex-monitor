package dev.kopandazavr.codexmonitor;

import java.util.concurrent.TimeUnit;

/**
 * Glanceable Now Bar / Live Update copy. When a usage window is fully exhausted
 * (0% remaining), percentage text is replaced with how long until that window's
 * natural reset, using days and/or hours (and minutes under an hour).
 */
public final class NowBarCopy {
    private NowBarCopy() {
    }

    /**
     * Compact critical / chip percentage for the focused window. Exhausted windows
     * show a compact reset countdown instead of {@code 0%}. {@code prefix} is a short
     * window marker such as {@code "W "} (weekly), {@code "M "} (monthly), or {@code ""}.
     */
    public static String focusCriticalText(String prefix, UsageWindow window,
            long observedAtMillis, long nowMillis) {
        if (prefix == null) {
            prefix = "";
        }
        if (window == null) {
            return prefix + "—";
        }
        int remaining = window.remainingPercent();
        if (remaining > 0) {
            return prefix + remaining + "%";
        }
        String duration = resetDurationText(window, observedAtMillis, nowMillis);
        return duration == null ? prefix + "0%" : prefix + duration;
    }

    /**
     * Samsung expanded-chip label: {@code Codex · 5-hour 12%} or, when exhausted,
     * {@code Codex · Weekly 2d 4h}. {@code windowLabel} names the focused window
     * ({@code "5-hour"}, {@code "Weekly"}, or {@code "Monthly"}).
     */
    public static String chipExpandedText(String windowLabel, UsageWindow window,
            long observedAtMillis, long nowMillis) {
        String focusLabel = (windowLabel == null || windowLabel.isEmpty()
                ? "5-hour" : windowLabel) + " ";
        if (window == null) {
            return "Codex · " + focusLabel + "unavailable";
        }
        int remaining = window.remainingPercent();
        if (remaining > 0) {
            return "Codex · " + focusLabel + remaining + "%";
        }
        String duration = resetDurationText(window, observedAtMillis, nowMillis);
        return duration == null
                ? "Codex · " + focusLabel + "0%"
                : "Codex · " + focusLabel + duration;
    }

    /**
     * Notification body line for one window: {@code 5-hour: 12% left} or, when
     * exhausted, {@code Weekly: resets in 2d 4h}.
     */
    public static String limitText(String label, UsageWindow window, long observedAtMillis,
            long nowMillis) {
        if (window == null) {
            return label + ": unavailable";
        }
        int remaining = window.remainingPercent();
        if (remaining > 0) {
            return label + ": " + remaining + "% left";
        }
        String duration = resetDurationText(window, observedAtMillis, nowMillis);
        return duration == null
                ? label + ": 0% left"
                : label + ": resets in " + duration;
    }

    /**
     * Compact Wear body fragment: {@code 5h 12%} or {@code Week resets 2d 4h}.
     */
    public static String wearLimitText(String label, UsageWindow window, long observedAtMillis,
            long nowMillis) {
        if (window == null) {
            return label + " --";
        }
        int remaining = window.remainingPercent();
        if (remaining > 0) {
            return label + " " + remaining + "%";
        }
        String duration = resetDurationText(window, observedAtMillis, nowMillis);
        return duration == null
                ? label + " 0%"
                : label + " resets " + duration;
    }

    /**
     * Days and/or hours until {@code window}'s natural reset. Returns null when the
     * reset time is unknown or not in the future.
     */
    public static String resetDurationText(UsageWindow window, long observedAtMillis,
            long nowMillis) {
        if (window == null) return null;
        long resetAt = window.effectiveResetAtMillis(observedAtMillis);
        if (resetAt <= nowMillis) return null;
        return compactDuration(resetAt - nowMillis);
    }

    /**
     * Persistent-notification reset countdown. Keeps minute precision even when days are present:
     * 6d 2h 55m, 2h 22m, 18m. Zero/elapsed input is 0m.
     */
    public static String compactDurationWithMinutes(long durationMillis) {
        if (durationMillis <= 0L) return "0m";
        long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(durationMillis));
        long days = minutes / TimeUnit.DAYS.toMinutes(1);
        long hours = (minutes % TimeUnit.DAYS.toMinutes(1)) / TimeUnit.HOURS.toMinutes(1);
        long remainingMinutes = minutes % TimeUnit.HOURS.toMinutes(1);
        StringBuilder value = new StringBuilder();
        if (days > 0L) value.append(days).append("d");
        if (hours > 0L) {
            if (value.length() > 0) value.append(' ');
            value.append(hours).append("h");
        }
        if (remainingMinutes > 0L || value.length() == 0) {
            if (value.length() > 0) value.append(' ');
            value.append(remainingMinutes).append("m");
        }
        return value.toString();
    }

    /**
     * Compact remaining duration: {@code 2d 4h}, {@code 2d}, {@code 4h 20m}, {@code 4h},
     * or {@code 12m}. Prefer days and/or hours when those units are needed.
     */
    public static String compactDuration(long durationMillis) {
        long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, durationMillis)));
        long days = minutes / TimeUnit.DAYS.toMinutes(1);
        long hours = (minutes % TimeUnit.DAYS.toMinutes(1)) / TimeUnit.HOURS.toMinutes(1);
        long remainingMinutes = minutes % TimeUnit.HOURS.toMinutes(1);
        if (days > 0L) {
            return hours > 0L ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0L) {
            return remainingMinutes > 0L ? hours + "h " + remainingMinutes + "m" : hours + "h";
        }
        return minutes + "m";
    }
}
