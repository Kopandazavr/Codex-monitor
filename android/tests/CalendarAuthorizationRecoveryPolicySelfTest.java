package dev.kopandazavr.codexmonitor;

/** API-free regression matrix for 2.48 recovery gates; Play services tested on PHONE. */
public final class CalendarAuthorizationRecoveryPolicySelfTest {
    public static void main(String[] args) {
        long now = 1_000_000L;
        assert CalendarAuthorizationRecoveryPolicy.delayMillis("transient", 1) == 60_000L;
        assert CalendarAuthorizationRecoveryPolicy.delayMillis("transient", 2) == 300_000L;
        assert CalendarAuthorizationRecoveryPolicy.delayMillis("transient", 3) == 900_000L;
        assert CalendarAuthorizationRecoveryPolicy.delayMillis("transient", 4) == 3_600_000L;
        assert CalendarAuthorizationRecoveryPolicy.delayMillis("transient", 100)
                == 3_600_000L;
        assert CalendarAuthorizationRecoveryPolicy.delayMillis("consent", 1) == 900_000L;
        assert CalendarAuthorizationRecoveryPolicy.delayMillis("consent", 2)
                == CalendarAuthorizationRecoveryPolicy.NEVER;

        // Healthy Direct request and normal 401 fresh-token retry remain admitted.
        assert CalendarAuthorizationRecoveryPolicy.mayPoll(true, true, "", 0, now);
        // Transient API failure preserves original binding, but cannot hammer Google.
        assert !CalendarAuthorizationRecoveryPolicy.mayPoll(
                true, false, "transient", now + 60_000L, now);
        assert CalendarAuthorizationRecoveryPolicy.mayPoll(
                true, false, "transient", now, now);
        assert CalendarAuthorizationRecoveryPolicy.mayMonitor(
                true, false, "transient", now + 60_000L);
        // hasResolution displays consent now; just one later noninteractive observation.
        assert !CalendarAuthorizationRecoveryPolicy.mayPoll(
                true, false, "consent", now + 900_000L, now);
        assert CalendarAuthorizationRecoveryPolicy.mayPoll(
                true, false, "consent", now, now);
        assert !CalendarAuthorizationRecoveryPolicy.mayPoll(
                true, false, "consent", CalendarAuthorizationRecoveryPolicy.NEVER, now);
        assert !CalendarAuthorizationRecoveryPolicy.mayMonitor(
                true, false, "consent", CalendarAuthorizationRecoveryPolicy.NEVER);
        // Explicit disconnect or cancel must never be mistaken for a transient condition.
        assert !CalendarAuthorizationRecoveryPolicy.mayPoll(true, false,
                "cancelled", 0L, now);
        assert !CalendarAuthorizationRecoveryPolicy.mayPoll(false, false,
                "transient", 0L, now);
        // A's retry must not unlock B's token or introduce an account-wide retry.
        long accountADue = now;
        long accountBDue = now + 900_000L;
        assert CalendarAuthorizationRecoveryPolicy.mayPoll(
                true, false, "transient", accountADue, now);
        assert !CalendarAuthorizationRecoveryPolicy.mayPoll(
                true, false, "transient", accountBDue, now);
        // Foreground/network may accelerate only temporary errors, with 2-minute throttle.
        assert !CalendarAuthorizationRecoveryPolicy.mayAccelerate(
                "transient", now - 60_000L, now);
        assert CalendarAuthorizationRecoveryPolicy.mayAccelerate(
                "transient", now - 120_000L, now);
        assert !CalendarAuthorizationRecoveryPolicy.mayAccelerate(
                "consent", 0L, now);
        System.out.println("CalendarAuthorizationRecoveryPolicySelfTest PASS");
    }
}
