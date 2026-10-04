package dev.kopandazavr.codexmonitor;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Schedules one account-isolated alarm for each configured reset-credit reminder. */
public final class ResetCreditExpiryScheduler {
    static final String EXTRA_CONTAINER_ID = OAuthService.EXTRA_CONTAINER_ID;
    static final String EXTRA_CREDIT_ID = "expiry_credit_id";
    static final String EXTRA_EXPIRES_AT = "expiry_expires_at";
    static final String EXTRA_LEAD_TIME = "expiry_lead_time";
    private static final long DELIVERY_GRACE_MS = 1000L;
    private static final String KEY_ALARM_URIS = "alarm_uris";
    private static final String PREFS = "codex_monitor_reset_expiry_alarms_v1";
    private static final int REQUEST_CODE = 74209;

    private ResetCreditExpiryScheduler() {
    }

    public static void scheduleFromSnapshot(Context context, ResetCreditsSnapshot snapshot) {
        scheduleFromSnapshot(
                context, AccountContainerStore.selectedId(context), snapshot);
    }

    static void scheduleFromSnapshot(Context context, String containerId,
            ResetCreditsSnapshot snapshot) {
        Context app = appContext(context);
        if (app == null) return;
        cancelAll(app, containerId);
        if (snapshot == null || !SecureTokenStore.isSignedIn(app, containerId)
                || !ResetAlertPreferences.enabled(app)
                || !ResetAlertPreferences.resetCreditExpiryEnabled(app)) {
            return;
        }

        List<ResetCreditExpiryReminder> reminders = ResetCreditExpiryReminder.plan(
                snapshot.credits,
                ResetAlertPreferences.getResetCreditExpiryLeadTimes(app),
                System.currentTimeMillis());
        AlarmManager manager = (AlarmManager)
                app.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;

        long now = System.currentTimeMillis();
        Set<String> scheduledUris = new HashSet<>();
        for (ResetCreditExpiryReminder reminder : reminders) {
            if (ResetNotificationManager.isResetCreditExpiryReminderAnnounced(
                    app, containerId, reminder.token())) {
                continue;
            }
            Uri data = data(containerId, reminder.creditId,
                    reminder.expiresAtMillis, reminder.leadTimeMillis);
            PendingIntent pendingIntent = pending(
                    app, containerId, data, reminder);
            long triggerAt = Math.max(
                    now + DELIVERY_GRACE_MS, reminder.triggerAtMillis);
            try {
                if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
                    manager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                } else {
                    manager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                }
                scheduledUris.add(data.toString());
            } catch (SecurityException exception) {
                manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
                scheduledUris.add(data.toString());
            }
        }
        prefs(app).edit()
                .putStringSet(key(containerId), scheduledUris)
                .apply();
    }

    public static void cancelAll(Context context) {
        Context app = appContext(context);
        if (app == null) return;
        for (AccountContainerStore.Account account : AccountContainerStore.all(app)) {
            cancelAll(app, account.id);
        }
    }

    static void cancelAll(Context context, String containerId) {
        Context app = appContext(context);
        if (app == null) return;
        AlarmManager manager = (AlarmManager)
                app.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences preferences = prefs(app);
        String scopedKey = key(containerId);
        Set<String> scopedUris = preferences.getStringSet(scopedKey, null);
        if (manager != null && scopedUris != null) {
            for (String uri : new HashSet<>(scopedUris)) {
                PendingIntent pendingIntent = existingPending(
                        app, containerId, Uri.parse(uri));
                if (pendingIntent != null) {
                    manager.cancel(pendingIntent);
                    pendingIntent.cancel();
                }
            }
        }

        if (manager != null && AccountContainerStore.isLegacyOwner(app, containerId)) {
            Set<String> legacyUris = preferences.getStringSet(KEY_ALARM_URIS, null);
            if (legacyUris != null) {
                for (String uri : new HashSet<>(legacyUris)) {
                    PendingIntent legacy = existingLegacyPending(app, Uri.parse(uri));
                    if (legacy != null) {
                        manager.cancel(legacy);
                        legacy.cancel();
                    }
                }
            }
        }

        SharedPreferences.Editor editor = preferences.edit().remove(scopedKey);
        if (AccountContainerStore.isLegacyOwner(app, containerId)) {
            editor.remove(KEY_ALARM_URIS);
        }
        editor.apply();
    }

    private static PendingIntent pending(Context context, String containerId, Uri data,
            ResetCreditExpiryReminder reminder) {
        Intent intent = baseIntent(context, containerId, data)
                .putExtra(EXTRA_CREDIT_ID, reminder.creditId)
                .putExtra(EXTRA_EXPIRES_AT, reminder.expiresAtMillis)
                .putExtra(EXTRA_LEAD_TIME, reminder.leadTimeMillis);
        return PendingIntent.getBroadcast(context,
                requestCode(containerId), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent existingPending(
            Context context, String containerId, Uri data) {
        return PendingIntent.getBroadcast(context,
                requestCode(containerId),
                baseIntent(context, containerId, data),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent existingLegacyPending(Context context, Uri data) {
        Intent legacy = new Intent(context, ResetCreditExpiryReceiver.class)
                .setAction(AppConstants.ACTION_RESET_CREDIT_EXPIRY_ALERT)
                .setData(data);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, legacy,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent baseIntent(Context context, String containerId, Uri data) {
        return new Intent(context, ResetCreditExpiryReceiver.class)
                .setAction(AppConstants.ACTION_RESET_CREDIT_EXPIRY_ALERT)
                .setData(data)
                .putExtra(EXTRA_CONTAINER_ID, containerId);
    }

    private static Uri data(String containerId, String creditId,
            long expiresAt, long leadTime) {
        return new Uri.Builder()
                .scheme("codexmonitor")
                .authority("reset-credit-expiry")
                .appendPath(AccountNotificationNamespace.safe(containerId))
                .appendPath(creditId == null || creditId.isEmpty() ? "_" : creditId)
                .appendQueryParameter("expires", String.valueOf(expiresAt))
                .appendQueryParameter("lead", String.valueOf(leadTime))
                .build();
    }

    private static int requestCode(String containerId) {
        return AccountNotificationNamespace.requestCode(
                containerId, "reset_credit_expiry_" + REQUEST_CODE);
    }

    private static String key(String containerId) {
        return KEY_ALARM_URIS + "::" + AccountNotificationNamespace.safe(containerId);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Context appContext(Context context) {
        if (context == null) return null;
        Context applicationContext = context.getApplicationContext();
        return applicationContext == null ? context : applicationContext;
    }
}
