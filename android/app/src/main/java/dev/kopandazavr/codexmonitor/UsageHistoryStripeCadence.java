package dev.kopandazavr.codexmonitor;

/** Pure visual cadence policy for Usage History fill stripes. */
final class UsageHistoryStripeCadence {
    private static final float ZOOMED_SPACING_MULTIPLIER = 3f;

    private UsageHistoryStripeCadence() {}

    static float spacingPx(float baseSpacingPx, boolean zoomed, boolean weekly) {
        if (!(baseSpacingPx > 0f)) return baseSpacingPx;
        // Both 5-hour and Weekly deliberately share the same visual zoom multiplier.
        return baseSpacingPx * (zoomed ? ZOOMED_SPACING_MULTIPLIER : 1f);
    }
}
