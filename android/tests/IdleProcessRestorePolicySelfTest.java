package dev.kopandazavr.codexmonitor;

/** API-free checks of the immutable local episode guard. */
public final class IdleProcessRestorePolicySelfTest {
    public static void main(String[] args) {
        assert IdleProcessRestorePolicy.isDeleted(200, 200);
        assert !IdleProcessRestorePolicy.isDeleted(100, 200);
        assert !IdleProcessRestorePolicy.isDeleted(0, 0);
        assert IdleProcessRestorePolicy.sameEpisode("instance-A", 10, 100, 200,
                "instance-A", 10, 100, 200);
        assert !IdleProcessRestorePolicy.sameEpisode("instance-A", 10, 100, 200,
                "instance-B", 10, 100, 200);
        assert !IdleProcessRestorePolicy.sameEpisode("instance-A", 10, 100, 200,
                "instance-A", 11, 100, 200);
        assert !IdleProcessRestorePolicy.sameEpisode("instance-A", 10, 100, 200,
                "instance-A", 10, 100, 201);
        assert !IdleProcessRestorePolicy.sameEpisode("", 10, 100, 200,
                "", 10, 101, 200);
        assert !IdleProcessRestorePolicy.sameEpisode("instance-A", 10, 100, 0,
                "instance-A", 10, 100, 0);
        System.out.println("IdleProcessRestorePolicySelfTest PASS");
    }
}
