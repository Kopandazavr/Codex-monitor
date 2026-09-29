package dev.kopandazavr.codexmonitor;

public final class CompletionAudioRouteSelfTest {
    public static void main(String[] args) {
        testNormalRouteHasOneAudibleOwner();
        testSpeakerRouteHasOneAudibleOwner();
        System.out.println("CompletionAudioRouteSelfTest PASS");
    }

    private static void testNormalRouteHasOneAudibleOwner() {
        assert !CompletionAudioRoute.useSilentNotificationDelivery(false);
        assert !CompletionAudioRoute.playDirectSound(false);
        assert CompletionAudioRoute.audibleOwnerCount(false) == 1;
    }

    private static void testSpeakerRouteHasOneAudibleOwner() {
        assert CompletionAudioRoute.useSilentNotificationDelivery(true);
        assert CompletionAudioRoute.playDirectSound(true);
        assert CompletionAudioRoute.audibleOwnerCount(true) == 1;
    }
}
