package dev.kopandazavr.codexmonitor;

/** Pure ownership policy for one completion edge. */
final class CompletionAudioRoute {
    private CompletionAudioRoute() {}

    /**
     * Speaker mode must post through a notification channel with no sound so the notification
     * can retain channel-owned vibration without racing a second audible system path.
     */
    static boolean useSilentNotificationDelivery(boolean speakerRequested) {
        return speakerRequested;
    }

    /** The app owns audible playback only while explicit phone-speaker routing is requested. */
    static boolean playDirectSound(boolean speakerRequested) {
        return speakerRequested;
    }

    static int audibleOwnerCount(boolean speakerRequested) {
        int channelOwners = useSilentNotificationDelivery(speakerRequested) ? 0 : 1;
        int appOwners = playDirectSound(speakerRequested) ? 1 : 0;
        return channelOwners + appOwners;
    }
}
