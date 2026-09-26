package dev.kopandazavr.codexmonitor;

/** Shared reset-cycle progress semantics for Dashboard rings and exhausted notification strips. */
final class ResetProgress {
    /** Bright saturated lime used by every reset-progress surface. */
    static final int RESET_LIME = 0xFFB7F34A;

    private ResetProgress() {}

    /** 0% just after reset; 100% immediately before the next reset. */
    static int elapsedPercent(UsageWindow window, long observedAtMillis, long nowMillis) {
        if (window == null || window.windowSeconds <= 0L) return 0;
        long reference = observedAtMillis > 0L ? observedAtMillis : nowMillis;
        long resetAt = window.effectiveResetAtMillis(reference);
        if (resetAt <= 0L) return 0;
        long cycleMillis;
        try {
            cycleMillis = Math.multiplyExact(window.windowSeconds, 1000L);
        } catch (ArithmeticException exception) {
            return 0;
        }
        if (cycleMillis <= 0L) return 0;
        long remainingMillis = Math.max(0L, resetAt - nowMillis);
        double remainingFraction = Math.min(1d, remainingMillis / (double) cycleMillis);
        return Math.max(0, Math.min(100,
                (int) Math.round((1d - remainingFraction) * 100d)));
    }
}
