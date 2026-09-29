package dev.kopandazavr.codexmonitor;

public final class UsageHistoryStripeCadenceSelfTest {
    public static void main(String[] args) {
        testUnzoomedCadenceUnchanged();
        testZoomedCadenceThreeTimesSparserForBothWindows();
        System.out.println("UsageHistoryStripeCadenceSelfTest PASS");
    }

    private static void testUnzoomedCadenceUnchanged() {
        float base = 14f;
        assert close(UsageHistoryStripeCadence.spacingPx(base, false, false), base);
        assert close(UsageHistoryStripeCadence.spacingPx(base, false, true), base);
    }

    private static void testZoomedCadenceThreeTimesSparserForBothWindows() {
        float base = 14f;
        float expected = 42f;
        assert close(UsageHistoryStripeCadence.spacingPx(base, true, false), expected);
        assert close(UsageHistoryStripeCadence.spacingPx(base, true, true), expected);
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 0.0001f;
    }
}
