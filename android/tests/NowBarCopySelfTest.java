package dev.kopandazavr.codexmonitor;

import java.util.concurrent.TimeUnit;

/** Regression coverage for persistent-notification reset countdown precision. */
public final class NowBarCopySelfTest {
    private NowBarCopySelfTest() {}

    public static void main(String[] args) {
        require("2h 22m".equals(NowBarCopy.compactDurationWithMinutes(
                TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(22))),
                "hours + minutes");
        require("6d 2h 55m".equals(NowBarCopy.compactDurationWithMinutes(
                TimeUnit.DAYS.toMillis(6) + TimeUnit.HOURS.toMillis(2)
                        + TimeUnit.MINUTES.toMillis(55))),
                "days + hours + minutes");
        require("18m".equals(NowBarCopy.compactDurationWithMinutes(
                TimeUnit.MINUTES.toMillis(18))), "minutes only");
        require("0m".equals(NowBarCopy.compactDurationWithMinutes(0L)), "elapsed cache copy");
        System.out.println("NowBarCopy detailed reset duration self-test passed.");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
