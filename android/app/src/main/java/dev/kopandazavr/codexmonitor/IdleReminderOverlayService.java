package dev.kopandazavr.codexmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Full-screen application-overlay surface for one or more newly completed watched roles. */
public final class IdleReminderOverlayService extends Service {
    private static final String ACTION_SHOW = "dev.kopandazavr.codexmonitor.action.SHOW_IDLE_OVERLAY";
    private static final String ACTION_SHOW_TEST =
            "dev.kopandazavr.codexmonitor.action.SHOW_IDLE_OVERLAY_TEST";
    private static final String TEST_ROLE_KEY = "__overlay_test__";
    private static final String CHANNEL_ID = AlertSoundManager.OPERATIONAL_CHANNEL_ID;
    private static final int FOREGROUND_ID = 8641;
    private static volatile IdleReminderOverlayService running;

    private final Map<String, IdleProcessState.IdleRole> roles = new LinkedHashMap<>();
    private WindowManager windowManager;
    private FrameLayout overlayRoot;
    private LinearLayout entries;

    static boolean canDraw(Context context) {
        return context != null && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context));
    }

    static boolean show(Context context, IdleProcessState.IdleRole idle) {
        if (context == null || idle == null || !canDraw(context)) return false;
        Intent intent = new Intent(context, IdleReminderOverlayService.class)
                .setAction(ACTION_SHOW)
                .putExtra(IdleReminderManager.EXTRA_ROLE_KEY, idle.key)
                .putExtra(IdleReminderManager.EXTRA_FINISHED_AT, idle.lastFinishedMillis);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            return true;
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "idle_process", "overlay_start_failed",
                    "error", exception.getClass().getSimpleName());
            return false;
        }
    }

    static boolean showTest(Context context) {
        if (context == null || !canDraw(context)) return false;
        Intent intent = new Intent(context, IdleReminderOverlayService.class)
                .setAction(ACTION_SHOW_TEST);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            return true;
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(context, "idle_process", "overlay_test_start_failed",
                    "error", exception.getClass().getSimpleName());
            return false;
        }
    }

    static void dismiss(Context context, String key) {
        IdleReminderOverlayService service = running;
        if (service == null || key == null) return;
        new Handler(Looper.getMainLooper()).post(() -> service.removeRole(key));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ensureChannel();
        startForeground(FOREGROUND_ID, foregroundNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !canDraw(this)) {
            stopSelfIfEmpty();
            return START_NOT_STICKY;
        }
        if (ACTION_SHOW_TEST.equals(intent.getAction())) {
            long now = System.currentTimeMillis();
            showRole(new IdleProcessState.IdleRole(
                    TEST_ROLE_KEY, "Codex Monitor", "CM", "Main Agent", "Test overlay",
                    now - 300_000L, now, -1L, false, 0L));
            return START_NOT_STICKY;
        }
        if (!ACTION_SHOW.equals(intent.getAction())) {
            stopSelfIfEmpty();
            return START_NOT_STICKY;
        }
        String key = intent.getStringExtra(IdleReminderManager.EXTRA_ROLE_KEY);
        long finished = intent.getLongExtra(IdleReminderManager.EXTRA_FINISHED_AT, 0L);
        IdleProcessState.IdleRole idle = IdleProcessState.find(this, key);
        if (idle == null || idle.lastFinishedMillis != finished || !idle.reminderEnabled) {
            stopSelfIfEmpty();
            return START_NOT_STICKY;
        }
        showRole(idle);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        removeRoot();
        roles.clear();
        if (running == this) running = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showRole(IdleProcessState.IdleRole idle) {
        if (windowManager == null) return;
        roles.put(idle.key, idle);
        ensureRoot();
        rebuildEntries();
    }

    private void ensureRoot() {
        if (overlayRoot != null || windowManager == null) return;

        overlayRoot = new FrameLayout(this);
        overlayRoot.setBackground(new DiagonalStripeDrawable(
                0xE61B1B1F, 0xE6222226, dp(26)));
        overlayRoot.setClickable(true);
        overlayRoot.setOnClickListener(view -> dismissAll());

        ScrollView scroll = new ScrollView(this);
        scroll.setClickable(true);
        scroll.setOnClickListener(view -> dismissAll());
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(20), dp(36), dp(20), dp(36));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setGravity(Gravity.CENTER_VERTICAL);
        shell.setPadding(dp(4), dp(8), dp(4), dp(8));
        shell.setClickable(true);
        shell.setOnClickListener(view -> dismissAll());

        TextView header = text("Codex Monitor", 18f, Color.WHITE);
        header.setGravity(Gravity.CENTER);
        shell.addView(header, matchWrap());

        TextView title = text("Session finished", 28f, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(4), 0, dp(16));
        shell.addView(title, titleParams);

        entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        shell.addView(entries, matchWrap());

        TextView hint = text("Tap the overlay to dismiss", 13f, 0xFFE4E4E7);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.setMargins(0, dp(16), 0, 0);
        shell.addView(hint, hintParams);

        scroll.addView(shell, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        overlayRoot.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            windowManager.addView(overlayRoot, params);
            overlayShownHaptic();
            DiagnosticLog.info(this, "idle_process", "overlay_window_added",
                    "entries", roles.size());
            DiagnosticLog.info(this, "idle_process", "overlay_haptic",
                    "duration_ms", 330);
        } catch (RuntimeException exception) {
            DiagnosticLog.warn(this, "idle_process", "overlay_add_failed",
                    "error", exception.getClass().getSimpleName());
            overlayRoot = null;
            entries = null;
        }
    }

    private void rebuildEntries() {
        if (entries == null) return;
        entries.removeAllViews();
        for (IdleProcessState.IdleRole idle : roles.values()) {
            entries.addView(entryView(idle), entryParams());
        }
    }

    private View entryView(IdleProcessState.IdleRole idle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(14), dp(16));
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setCornerRadius(dp(24));
        cardBackground.setColor(0xD9212124);
        cardBackground.setStroke(dp(1), 0x88FFFFFF);
        card.setBackground(cardBackground);
        card.setClickable(true);
        card.setOnClickListener(view -> dismissAll());

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);

        String role = idle.role == null || idle.role.isEmpty() ? "Watched role" : idle.role;
        TextView roleView = text(role, 20f, Color.WHITE);
        copy.addView(roleView, matchWrap());

        if (idle.project != null && !idle.project.isEmpty()) {
            TextView project = text(idle.project, 14f, 0xFFE4E4E7);
            LinearLayout.LayoutParams projectParams = matchWrap();
            projectParams.setMargins(0, dp(3), 0, 0);
            copy.addView(project, projectParams);
        }
        if (idle.topic != null && !idle.topic.isEmpty()) {
            TextView topic = text(idle.topic, 13f, 0xFFCACACE);
            LinearLayout.LayoutParams topicParams = matchWrap();
            topicParams.setMargins(0, dp(3), 0, 0);
            copy.addView(topic, topicParams);
        }

        String finished = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(idle.lastFinishedMillis));
        StringBuilder metaCopy = new StringBuilder("Finished · ").append(finished);
        long duration = idle.lastStartedMillis > 0L
                ? Math.max(0L, idle.lastFinishedMillis - idle.lastStartedMillis) : 0L;
        if (duration > 0L) {
            metaCopy.append(" · ").append(formatDuration(duration));
        }
        TextView meta = text(metaCopy.toString(), 13f, 0xFFB8B8BD);
        LinearLayout.LayoutParams metaParams = matchWrap();
        metaParams.setMargins(0, dp(7), 0, 0);
        copy.addView(meta, metaParams);

        card.addView(copy, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout bell = new LinearLayout(this);
        bell.setOrientation(LinearLayout.VERTICAL);
        bell.setGravity(Gravity.CENTER);
        bell.setPadding(dp(8), dp(8), dp(8), dp(8));
        bell.setClickable(true);
        bell.setFocusable(true);
        updateBell(bell, idle);
        boolean testRole = TEST_ROLE_KEY.equals(idle.key);
        bell.setContentDescription(testRole ? "Test overlay sample"
                : role + " notifications " + (idle.reminderEnabled ? "on" : "off"));
        bell.setClickable(!testRole);
        bell.setFocusable(!testRole);
        if (!testRole) {
            bell.setOnClickListener(view -> {
                strongHaptic();
                long now = System.currentTimeMillis();
                boolean enabled = IdleProcessState.toggleReminder(this, idle.key, now);
                IdleReminderManager.onReminderToggled(this, idle.key, enabled, now);
                IdleProcessState.IdleRole updated = IdleProcessState.find(this, idle.key);
                if (updated != null) {
                    roles.put(idle.key, updated);
                    updateBell(view, updated);
                }
                DualUsageNotificationManager.repostForProcessChangeDelayed(this, 120L);
                DiagnosticLog.info(this, "idle_process", "overlay_bell_toggled",
                        "role", idle.displayLabel(),
                        "enabled", enabled);
            });
        }

        ImageView icon = new ImageView(this);
        icon.setId(android.R.id.icon);
        bell.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView state = text("", 12f, Color.BLACK);
        state.setId(android.R.id.text1);
        state.setGravity(Gravity.CENTER);
        bell.addView(state, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        updateBell(bell, idle);

        LinearLayout.LayoutParams bellParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        bellParams.setMargins(dp(12), 0, 0, 0);
        card.addView(bell, bellParams);
        return card;
    }

    private void updateBell(View view, IdleProcessState.IdleRole idle) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(idle.reminderEnabled ? 0xFFFFC107 : 0xFF3A3A3C);
        background.setStroke(dp(2), idle.reminderEnabled ? 0xFFFFFFFF : 0xFF8E8E93);
        view.setBackground(background);
        ImageView icon = view.findViewById(android.R.id.icon);
        if (icon != null) {
            icon.setImageResource(idle.reminderEnabled ? R.drawable.ic_bell_on : R.drawable.ic_bell_off);
            icon.setColorFilter(idle.reminderEnabled ? Color.BLACK : Color.WHITE);
        }
        TextView state = view.findViewById(android.R.id.text1);
        if (state != null) {
            state.setText(idle.reminderEnabled ? "ON" : "OFF");
            state.setTextColor(idle.reminderEnabled ? Color.BLACK : Color.WHITE);
        }
    }

    private void removeRole(String key) {
        roles.remove(key);
        if (roles.isEmpty()) {
            stopSelf();
        } else {
            rebuildEntries();
        }
    }

    private void dismissAll() {
        strongHaptic();
        DiagnosticLog.info(this, "idle_process", "overlay_dismissed",
                "entries", roles.size());
        roles.clear();
        stopSelf();
    }

    private void stopSelfIfEmpty() {
        if (roles.isEmpty()) stopSelf();
    }

    private void removeRoot() {
        if (overlayRoot != null && windowManager != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (RuntimeException ignored) {
            }
        }
        overlayRoot = null;
        entries = null;
    }

    private TextView text(String value, float sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams entryParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private void strongHaptic() {
        vibrate(160L);
    }

    private void overlayShownHaptic() {
        vibrate(330L);
    }

    private void vibrate(long durationMillis) {
        try {
            VibrationEffect effect = VibrationEffect.createOneShot(durationMillis, 255);
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager manager =
                        (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                if (manager != null) manager.getDefaultVibrator().vibrate(effect);
            } else {
                Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vibrator != null) vibrator.vibrate(effect);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private Notification foregroundNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 8641,
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_codex_monitor)
                .setContentTitle("Completion overlay")
                .setContentText("Codex Monitor is showing a completed watched session")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(false)
                .build();
    }

    private void ensureChannel() {
        AlertSoundManager.ensureChannels(this);
    }

    private static String formatDuration(long durationMillis) {
        long minutes = Math.max(1L, (durationMillis + 59_999L) / 60_000L);
        if (minutes < 60L) return minutes + "m session";
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        return rest == 0L ? hours + "h session" : hours + "h " + rest + "m session";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class DiagonalStripeDrawable extends Drawable {
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stripe = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int stripeWidth;

        DiagonalStripeDrawable(int baseColor, int stripeColor, int stripeWidth) {
            base.setColor(baseColor);
            stripe.setColor(stripeColor);
            this.stripeWidth = stripeWidth;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.drawRect(bounds, base);
            if (bounds.isEmpty() || stripeWidth <= 0) return;

            // Rotate a full oversized stripe plane around the overlay center. Drawing equal-width
            // rectangles with a 2x period guarantees equal dark/light bands over every corner of
            // the real bounds; unlike the old endpoint/overscan math this cannot miss the top.
            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();
            float extent = (float) Math.hypot(bounds.width(), bounds.height());
            float period = stripeWidth * 2.0f;
            int save = canvas.save();
            canvas.rotate(-45.0f, centerX, centerY);
            float top = centerY - extent;
            float bottom = centerY + extent;
            for (float x = centerX - extent - period;
                    x <= centerX + extent + period; x += period) {
                canvas.drawRect(x, top, x + stripeWidth, bottom, stripe);
            }
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            base.setAlpha(alpha);
            stripe.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            base.setColorFilter(colorFilter);
            stripe.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
