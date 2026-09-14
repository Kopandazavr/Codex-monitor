package dev.bennett.codexmeter;

import android.content.Context;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility shim for the removed in-app updater.
 *
 * Codex Monitor is a personal-use build now; release discovery, update cards, background checks and
 * update notifications are intentionally disabled. The small API surface remains temporarily so
 * older runtime call sites can degrade to a no-op without carrying Settings/UI updater state.
 */
public final class UpdatePreferences {
    private UpdatePreferences() {
    }

    public static boolean automaticChecks(Context context) {
        return false;
    }

    public static void setAutomaticChecks(Context context, boolean enabled) {
        ReleaseUpdateScheduler.cancel(context);
        UpdateNotificationManager.dismiss(context);
    }

    public static String channel(Context context) {
        return UpdateChannel.STABLE;
    }

    public static void setChannel(Context context, String channel) {
    }

    public static boolean notifyUpdatesEnabled(Context context) {
        return false;
    }

    public static void setNotifyUpdatesEnabled(Context context, boolean enabled) {
        UpdateNotificationManager.dismiss(context);
    }

    public static int checkIntervalHours(Context context) {
        return UpdateCheckFrequency.DAILY;
    }

    public static void setCheckIntervalHours(Context context, int hours) {
    }

    public static String notifiedVersion(Context context) {
        return "";
    }

    public static void setNotifiedVersion(Context context, String version) {
    }

    public static void clearNotifiedVersion(Context context) {
    }

    public static long lastCheckMillis(Context context) {
        return 0L;
    }

    public static String etag(Context context) {
        return "";
    }

    public static String lastError(Context context) {
        return "";
    }

    public static void saveSuccess(Context context, String json, String etag) {
    }

    public static void markNotModified(Context context) {
    }

    public static void saveError(Context context, String error) {
    }

    public static List<GitHubRelease> releases(Context context) {
        return Collections.emptyList();
    }

    public static boolean hasUsableCache(Context context) {
        return false;
    }

    public static void clearEtag(Context context) {
    }

    public static GitHubRelease latestStable(Context context) {
        return null;
    }

    public static GitHubRelease findVersion(Context context, String version) {
        return null;
    }

    public static GitHubRelease availableUpdate(Context context) {
        return null;
    }

    public static String installedVersion(Context context) {
        try {
            String version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return version == null || version.trim().isEmpty()
                    ? AppConstants.VERSION_NAME : version;
        } catch (Exception exception) {
            return AppConstants.VERSION_NAME;
        }
    }

    public static void setInstallError(Context context, String error) {
    }

    public static String installError(Context context) {
        return "";
    }
}
