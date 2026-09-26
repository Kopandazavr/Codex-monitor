package dev.kopandazavr.codexmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import java.io.IOException;

/**
 * Canonical PHONE notification-channel and semantic alert-sound contract.
 *
 * <p>Only two channels are user-meaningful/audible: Process completion and Limits reset.
 * Every ongoing notification/foreground-service surface shares one silent operational channel.
 * Semantic sounds are played by the app so the optional phone-speaker route can be requested
 * without coupling sound delivery to a transient notification card.</p>
 */
final class AlertSoundManager {
    static final String OPERATIONAL_CHANNEL_ID = "codex_operational_v1";
    static final String PROCESS_COMPLETION_CHANNEL_ID = "codex_process_completion_v1";
    static final String LIMITS_RESET_CHANNEL_ID = "codex_limits_reset_v1";

    private static final String PREFS = "codex_alert_sound_settings_v1";
    private static final String KEY_PHONE_SPEAKER = "phone_speaker";
    private static final String KEY_CHANNEL_MIGRATED = "channel_migrated_v1";

    private static final String[] LEGACY_CHANNEL_IDS = {
            "codex_live_monitor_v2",
            "codex_live_monitor_v1",
            "codex_live_monitor",
            "codex_active_processes_v1",
            "codex_idle_reminders_v2",
            "codex_idle_reminders_v1",
            "codex_idle_overlay_service_v1",
            "codex_reset_alarm",
            "codex_reset_notify",
            "codex_reset_silent",
            "oauth_sign_in"
    };

    private AlertSoundManager() {}

    static void ensureChannels(Context context) {
        if (context == null) return;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        SharedPreferences prefs = preferences(context);
        boolean migrating = !prefs.getBoolean(KEY_CHANNEL_MIGRATED, false);
        NotificationChannel oldCompletion = migrating
                ? firstChannel(manager, "codex_idle_reminders_v2", "codex_idle_reminders_v1")
                : null;
        NotificationChannel oldLimits = migrating
                ? firstChannel(manager, "codex_reset_notify", "codex_reset_alarm",
                        "codex_reset_silent")
                : null;

        ensureOperational(manager);
        ensureSemantic(manager, PROCESS_COMPLETION_CHANNEL_ID, "Process completion",
                "Sound played once when a watched process completes",
                oldCompletion, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        ensureSemantic(manager, LIMITS_RESET_CHANNEL_ID, "Limits reset",
                "Shared sound for 5-hour and Weekly limit resets",
                oldLimits, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

        if (migrating) {
            for (String legacyId : LEGACY_CHANNEL_IDS) {
                try {
                    if (!OPERATIONAL_CHANNEL_ID.equals(legacyId)
                            && !PROCESS_COMPLETION_CHANNEL_ID.equals(legacyId)
                            && !LIMITS_RESET_CHANNEL_ID.equals(legacyId)) {
                        manager.deleteNotificationChannel(legacyId);
                    }
                } catch (RuntimeException exception) {
                    DiagnosticLog.warn(context, "notification", "legacy_channel_delete_failed",
                            "channel", legacyId,
                            "error", exception.getClass().getSimpleName());
                }
            }
            prefs.edit().putBoolean(KEY_CHANNEL_MIGRATED, true).apply();
            DiagnosticLog.info(context, "notification", "channel_model_migrated",
                    "operational", OPERATIONAL_CHANNEL_ID,
                    "completion", PROCESS_COMPLETION_CHANNEL_ID,
                    "limits_reset", LIMITS_RESET_CHANNEL_ID);
        }
    }

    static boolean playOnPhoneSpeaker(Context context) {
        return context != null && preferences(context).getBoolean(KEY_PHONE_SPEAKER, false);
    }

    static void setPlayOnPhoneSpeaker(Context context, boolean enabled) {
        if (context == null) return;
        preferences(context).edit().putBoolean(KEY_PHONE_SPEAKER, enabled).apply();
        DiagnosticLog.info(context, "notification", "speaker_route_preference_changed",
                "enabled", enabled);
    }

    static boolean playProcessCompletion(Context context) {
        return playSemantic(context, PROCESS_COMPLETION_CHANNEL_ID, "process_completion");
    }

    static boolean playLimitsReset(Context context) {
        return playSemantic(context, LIMITS_RESET_CHANNEL_ID, "limits_reset");
    }

    private static boolean playSemantic(Context context, String channelId, String event) {
        if (context == null) return false;
        ensureChannels(context);
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = manager == null ? null : manager.getNotificationChannel(channelId);
        if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            DiagnosticLog.info(context, "notification", "semantic_sound_suppressed",
                    "event", event, "reason", "channel_disabled");
            return false;
        }
        Uri sound = channel.getSound();
        if (sound == null) {
            DiagnosticLog.info(context, "notification", "semantic_sound_suppressed",
                    "event", event, "reason", "channel_silent");
            return false;
        }
        AudioAttributes attributes = channel.getAudioAttributes();
        if (attributes == null) attributes = notificationAudioAttributes();

        boolean speakerRequested = playOnPhoneSpeaker(context);
        if (speakerRequested && playViaPreferredSpeaker(context, sound, attributes, event)) {
            return true;
        }
        return playViaNormalRoute(context, sound, attributes, event,
                speakerRequested ? "speaker_fallback" : "normal");
    }

    private static boolean playViaPreferredSpeaker(Context context, Uri sound,
            AudioAttributes attributes, String event) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo speaker = null;
        if (audio != null) {
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    speaker = device;
                    break;
                }
            }
        }
        if (speaker == null) {
            DiagnosticLog.warn(context, "notification", "speaker_route_fallback",
                    "event", event, "reason", "speaker_unavailable");
            return false;
        }

        final MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(attributes);
            player.setDataSource(context, sound);
            player.prepare();
            if (!player.setPreferredDevice(speaker)) {
                player.release();
                DiagnosticLog.warn(context, "notification", "speaker_route_fallback",
                        "event", event, "reason", "preferred_device_rejected");
                return false;
            }
            player.setOnCompletionListener(MediaPlayer::release);
            player.setOnErrorListener((failed, what, extra) -> {
                failed.release();
                DiagnosticLog.warn(context, "notification", "speaker_route_playback_error",
                        "event", event, "what", what, "extra", extra);
                playViaNormalRoute(context, sound, attributes, event, "speaker_error_fallback");
                return true;
            });
            player.start();
            DiagnosticLog.info(context, "notification", "semantic_sound_played",
                    "event", event, "route", "preferred_phone_speaker");
            return true;
        } catch (IOException | RuntimeException exception) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
            }
            DiagnosticLog.warn(context, "notification", "speaker_route_fallback",
                    "event", event, "reason", exception.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean playViaNormalRoute(Context context, Uri sound,
            AudioAttributes attributes, String event, String route) {
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(context.getApplicationContext(), sound);
            if (ringtone == null) {
                Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if (fallback != null && !fallback.equals(sound)) {
                    ringtone = RingtoneManager.getRingtone(context.getApplicationContext(), fallback);
                }
            }
            if (ringtone == null) {
                DiagnosticLog.warn(context, "notification", "semantic_sound_unavailable",
                        "event", event);
                return false;
            }
            ringtone.setAudioAttributes(attributes);
            ringtone.play();
            DiagnosticLog.info(context, "notification", "semantic_sound_played",
                    "event", event, "route", route);
            return true;
        } catch (RuntimeException exception) {
            DiagnosticLog.error(context, "notification", "semantic_sound_failed", exception,
                    "event", event, "route", route);
            return false;
        }
    }

    private static void ensureOperational(NotificationManager manager) {
        if (manager.getNotificationChannel(OPERATIONAL_CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(OPERATIONAL_CHANNEL_ID,
                "Operational notifications", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Silent live monitor, process, sign-in and overlay service surfaces");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    private static void ensureSemantic(NotificationManager manager, String id, String name,
            String description, NotificationChannel legacy, Uri defaultSound) {
        if (manager.getNotificationChannel(id) != null) return;
        NotificationChannel channel = new NotificationChannel(
                id, name, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(description);
        channel.setShowBadge(false);
        Uri sound = legacy == null ? defaultSound : legacy.getSound();
        AudioAttributes attributes = legacy == null ? null : legacy.getAudioAttributes();
        if (sound != null) {
            channel.setSound(sound, attributes == null ? notificationAudioAttributes() : attributes);
        } else {
            channel.setSound(null, null);
        }
        channel.enableVibration(legacy != null && legacy.shouldVibrate());
        manager.createNotificationChannel(channel);
    }

    private static NotificationChannel firstChannel(NotificationManager manager, String... ids) {
        for (String id : ids) {
            NotificationChannel channel = manager.getNotificationChannel(id);
            if (channel != null) return channel;
        }
        return null;
    }

    private static AudioAttributes notificationAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
