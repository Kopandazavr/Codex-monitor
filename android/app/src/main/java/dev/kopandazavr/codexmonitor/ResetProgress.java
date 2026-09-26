package dev.kopandazavr.codexmonitor;

/** Shared reset-cycle progress semantics for Dashboard rings and exhausted notification strips. */
final class ResetProgress {
    private ResetProgress() {
    }

    static int timeRemainingPercent(UsageWindow window, long observedAtMillis, long nowMillis) {
        if (window == null || window.windowSeconds <= 0L) return 0;
        long reference = observedAtMillis > 0L ? observedAtMillis : nowMillis;
        long resetAt = window.effectiveResetAtMillis(reference);
        if (resetAt <= nowMillis) return 0;
        long cycleMillis;
        try {
            cycleMillis = Math.multiplyExact(window.windowSeconds, 1000L);
        } catch (ArithmeticException exception) {
            return 0;
        }
        if (cycleMillis <= 0L) return 0;
        double remainingFraction = Math.min(1d,
                Math.max(0L, resetAt - nowMillis) / (double) cycleMillis);
        return Math.max(0, Math.min(100,
                (int) Math.round(remainingFraction * 100d)));
    }
}
