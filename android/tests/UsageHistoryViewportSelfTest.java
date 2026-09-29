package dev.kopandazavr.codexmonitor;

import java.util.concurrent.TimeUnit;

public final class UsageHistoryViewportSelfTest {
    public static void main(String[] args) {
        testFiveHourShortHistoryFloor();
        testWeeklyShortHistoryFloor();
        testExactBoundaryNoPan();
        testLongHistoryPan();
        testRestoreKeepsFloor();
        System.out.println("UsageHistoryViewportSelfTest PASS");
    }

    private static void testFiveHourShortHistoryFloor() {
        long start=1_000_000L;
        long end=start+TimeUnit.MINUTES.toMillis(22);
        long minimum=TimeUnit.MINUTES.toMillis(60);
        assert UsageHistoryViewport.zoomSpan(minimum,start,end)==minimum;
        assert !UsageHistoryViewport.canPan(start,end,minimum);
    }

    private static void testWeeklyShortHistoryFloor() {
        long start=2_000_000L;
        long end=start+TimeUnit.HOURS.toMillis(8);
        long minimum=TimeUnit.HOURS.toMillis(24);
        assert UsageHistoryViewport.zoomSpan(minimum,start,end)==minimum;
        assert !UsageHistoryViewport.canPan(start,end,minimum);
    }

    private static void testExactBoundaryNoPan() {
        long start=3_000_000L;
        long fiveHourMinimum=TimeUnit.MINUTES.toMillis(60);
        long weeklyMinimum=TimeUnit.HOURS.toMillis(24);
        assert !UsageHistoryViewport.canPan(start,start+fiveHourMinimum,fiveHourMinimum);
        assert !UsageHistoryViewport.canPan(start,start+weeklyMinimum,weeklyMinimum);
    }

    private static void testLongHistoryPan() {
        long start=4_000_000L;
        long minimum=TimeUnit.MINUTES.toMillis(60);
        assert UsageHistoryViewport.canPan(
                start,start+minimum+TimeUnit.MINUTES.toMillis(1),minimum);
    }

    private static void testRestoreKeepsFloor() {
        long start=5_000_000L;
        long measuredEnd=start+TimeUnit.MINUTES.toMillis(15);
        long minimum=TimeUnit.MINUTES.toMillis(60);
        assert UsageHistoryViewport.restoreSpan(
                minimum,start,measuredEnd,TimeUnit.MINUTES.toMillis(15))==minimum;
    }
}
