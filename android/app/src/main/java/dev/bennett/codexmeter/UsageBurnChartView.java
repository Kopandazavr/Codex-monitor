package dev.bennett.codexmeter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Factual measured-usage chart with reset-anchored absolute time and bounded tap-to-zoom. */
public final class UsageBurnChartView extends View {
    private static final long FIVE_HOUR_ZOOM_MS = TimeUnit.HOURS.toMillis(1);
    private static final long WEEKLY_ZOOM_MS = TimeUnit.DAYS.toMillis(1);
    private static final long FIVE_HOUR_ZOOM_TICK_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long FIVE_HOUR_DEFAULT_TICK_MS = TimeUnit.HOURS.toMillis(1);
    private static final long WEEKLY_ZOOM_TICK_MS = TimeUnit.HOURS.toMillis(4);
    private static final long WEEKLY_DEFAULT_TICK_MS = TimeUnit.DAYS.toMillis(1);
    private static final long TAP_TOGGLE_GUARD_MS = 250L;
    private static final float TAP_SLOP_MULTIPLIER = 2.0f;
    // Same orange used by the Weekly dashboard fill.
    private static final int WEEKLY_ORANGE = 0xFFFF9800;

    public interface OnZoomChangedListener {
        void onZoomChanged(boolean zoomed);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Typeface regularTypeface = Typeface.create("sec", Typeface.NORMAL);
    private final Typeface boldTypeface = Typeface.create("sec", Typeface.BOLD);
    private final int touchSlop;
    private final int tapSlop;

    private String label = "";
    private UsageWindow window;
    private List<UsageSample> samples = Collections.emptyList();
    private long observedAtMillis;
    private boolean zoomEnabled;
    private boolean zoomed;
    private long viewportStartMillis;
    private long viewportEndMillis;
    private OnZoomChangedListener zoomChangedListener;
    private float downX;
    private float downY;
    private float lastX;
    private boolean moved;
    private boolean horizontalPan;
    private long lastTapToggleUptimeMillis = Long.MIN_VALUE;

    public UsageBurnChartView(Context context) {
        this(context, null);
    }

    public UsageBurnChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        tapSlop = Math.max(touchSlop,
                Math.round(touchSlop * TAP_SLOP_MULTIPLIER));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setData(String label, UsageWindow window, UsageHistory history,
            long observedAtMillis, UsagePace.Assessment ignoredPace) {
        this.label = label == null ? "" : label;
        this.window = window;
        this.samples = history == null ? Collections.emptyList() : history.currentWindowSamples();
        this.observedAtMillis = observedAtMillis;
        boolean wasZoomed = zoomed;
        zoomed = false;
        viewportStartMillis = 0L;
        viewportEndMillis = 0L;
        String detail = samples.size() < 2 ? "Building measured history"
                : samples.size() + " measured samples";
        if (zoomEnabled) detail += ". Tap to zoom; horizontal drag pans when zoomed";
        setContentDescription(this.label + " measured usage chart. " + detail + ".");
        if (wasZoomed && zoomChangedListener != null) zoomChangedListener.onZoomChanged(false);
        invalidate();
    }

    public void setZoomEnabled(boolean enabled) {
        zoomEnabled = enabled;
        if (!enabled && zoomed) zoomOut();
    }

    public boolean isZoomed() {
        return zoomed;
    }

    public void zoomOut() {
        if (!zoomed) return;
        zoomed = false;
        viewportStartMillis = 0L;
        viewportEndMillis = 0L;
        horizontalPan = false;
        if (zoomChangedListener != null) zoomChangedListener.onZoomChanged(false);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
    }

    public void setOnZoomChangedListener(OnZoomChangedListener listener) {
        zoomChangedListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!zoomEnabled) return super.onTouchEvent(event);
        long[] axis = visibleAxis();
        if (axis == null || samples.isEmpty()) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                lastX = downX;
                moved = false;
                horizontalPan = false;
                // Keep small finger jitter from being stolen by the dashboard scroll parent.
                // As soon as movement is clearly vertical we release interception below.
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float dx = x - downX;
                float dy = event.getY() - downY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);
                if (zoomed && (horizontalPan
                        || (absDx > touchSlop && absDx > absDy))) {
                    moved = true;
                    horizontalPan = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    panBy(x - lastX);
                } else if (absDy > tapSlop && absDy > absDx) {
                    moved = true;
                    // Intentional vertical motion belongs to the dashboard scroll container.
                    getParent().requestDisallowInterceptTouchEvent(false);
                } else if (absDx > tapSlop || absDy > tapSlop) {
                    moved = true;
                }
                lastX = x;
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    performClick();
                    handleTapToggle(event.getX());
                }
                finishTouch();
                return true;
            case MotionEvent.ACTION_CANCEL:
                finishTouch();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void handleTapToggle(float touchX) {
        long now = SystemClock.uptimeMillis();
        if (lastTapToggleUptimeMillis != Long.MIN_VALUE
                && now - lastTapToggleUptimeMillis < TAP_TOGGLE_GUARD_MS) {
            DiagnosticLog.info(getContext(), "usage_history", "chart_tap_duplicate_suppressed",
                    "label", label,
                    "delta_ms", now - lastTapToggleUptimeMillis);
            return;
        }
        if (zoomed) {
            if (!isChartInteractionX(touchX)) {
                DiagnosticLog.info(getContext(), "usage_history", "chart_tap_outside_domain",
                        "label", label,
                        "zoomed", true);
                return;
            }
            lastTapToggleUptimeMillis = now;
            zoomOut();
            DiagnosticLog.info(getContext(), "usage_history", "chart_tap_toggle",
                    "label", label,
                    "zoomed", false);
            return;
        }
        if (!isMeasuredInteractionX(touchX)) {
            DiagnosticLog.info(getContext(), "usage_history", "chart_tap_outside_domain",
                    "label", label,
                    "zoomed", false);
            return;
        }
        if (zoomAt(touchX)) {
            lastTapToggleUptimeMillis = now;
            DiagnosticLog.info(getContext(), "usage_history", "chart_tap_toggle",
                    "label", label,
                    "zoomed", true);
        }
    }

    private void finishTouch() {
        getParent().requestDisallowInterceptTouchEvent(false);
        horizontalPan = false;
        invalidate();
    }

    private boolean zoomAt(float touchX) {
        long[] full = defaultAxis();
        if (full == null) return false;
        long zoomSpan = isWeekly() ? WEEKLY_ZOOM_MS : FIVE_HOUR_ZOOM_MS;
        long fullSpan = full[1] - full[0];
        if (fullSpan <= zoomSpan) return false;
        float left = chartLeft();
        float right = chartRight();
        double ratio = Math.max(0d, Math.min(1d,
                (touchX - left) / Math.max(1d, right - left)));
        long center = full[0] + Math.round(ratio * fullSpan);
        long measuredEnd = measuredEndMillis();
        if (measuredEnd <= full[0]) return false;
        long start = center - zoomSpan / 2L;
        long maxStart = Math.max(full[0], measuredEnd - zoomSpan);
        start = Math.max(full[0], Math.min(start, maxStart));
        viewportStartMillis = start;
        viewportEndMillis = start + zoomSpan;
        zoomed = true;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (zoomChangedListener != null) zoomChangedListener.onZoomChanged(true);
        invalidate();
        return true;
    }

    public long viewportStartMillis() {
        return zoomed ? viewportStartMillis : 0L;
    }

    public long viewportEndMillis() {
        return zoomed ? viewportEndMillis : 0L;
    }

    public void restoreZoomViewport(long requestedStartMillis, long requestedEndMillis) {
        long[] full = defaultAxis();
        if (!zoomEnabled || full == null) return;
        long span = requestedEndMillis - requestedStartMillis;
        if (span <= 0L || full[1] - full[0] <= span) return;
        long measuredEnd = measuredEndMillis();
        if (measuredEnd <= full[0]) return;
        long maxStart = Math.max(full[0], measuredEnd - span);
        long start = Math.max(full[0], Math.min(requestedStartMillis, maxStart));
        viewportStartMillis = start;
        viewportEndMillis = start + span;
        zoomed = true;
        invalidate();
    }

    private void panBy(float deltaX) {
        long[] full = defaultAxis();
        if (full == null || !zoomed) return;
        long span = viewportEndMillis - viewportStartMillis;
        float width = Math.max(1f, chartRight() - chartLeft());
        long shift = Math.round(-deltaX * span / width);
        long start = viewportStartMillis + shift;
        long measuredEnd = measuredEndMillis();
        long maxStart = Math.max(full[0], measuredEnd - span);
        start = Math.max(full[0], Math.min(start, maxStart));
        viewportStartMillis = start;
        viewportEndMillis = start + span;
        if (zoomChangedListener != null) zoomChangedListener.onZoomChanged(true);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        boolean dark = Ui.isDark(getContext());
        float left = chartLeft();
        float right = chartRight();
        float top = 34f * density;
        float bottom = getHeight() - 32f * density;

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(boldTypeface);
        paint.setTextSize(14f * density);
        paint.setColor(Ui.mainText(dark));
        canvas.drawText(label, left, 20f * density, paint);

        paint.setTypeface(regularTypeface);
        paint.setTextSize(10f * density);
        paint.setColor(Ui.secondaryText(dark));
        String sampleLabel = samples.size() < 2 ? "Building history"
                : samples.size() + " samples";
        if (zoomed) sampleLabel += " · zoom";
        canvas.drawText(sampleLabel, right - paint.measureText(sampleLabel), 20f * density, paint);

        long[] full = defaultAxis();
        long[] axis = visibleAxis();
        if (window == null || full == null || axis == null) {
            drawEmpty(canvas, left, top, dark, density, "Waiting for usage data");
            return;
        }

        drawMeasuredSeries(canvas, axis, left, right, top, bottom, density, dark);
        drawTimeAxis(canvas, axis, left, right, bottom, density, dark);
    }

    private void drawMeasuredSeries(Canvas canvas, long[] axis, float left, float right,
            float top, float bottom, float density, boolean dark) {
        if (samples.isEmpty()) {
            drawEmpty(canvas, left, top, dark, density, "No measured samples yet");
            return;
        }
        path.reset();
        UsageSample before = null;
        boolean started = false;
        for (UsageSample sample : samples) {
            if (sample.observedAtMillis < axis[0]) {
                before = sample;
                continue;
            }
            if (!started && before != null) {
                path.moveTo(x(before.observedAtMillis, axis[0], axis[1], left, right),
                        y(before.usedPercent, top, bottom));
                started = true;
            }
            float sx = x(sample.observedAtMillis, axis[0], axis[1], left, right);
            float sy = y(sample.usedPercent, top, bottom);
            if (!started) {
                path.moveTo(sx, sy);
                started = true;
            } else {
                path.lineTo(sx, sy);
            }
            if (sample.observedAtMillis > axis[1]) break;
        }
        if (!started && before != null) {
            path.moveTo(left, y(before.usedPercent, top, bottom));
            started = true;
        }
        if (!started) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(null);
        paint.setStrokeWidth(3f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(isWeekly() ? WEEKLY_ORANGE : Ui.accent(getContext(), dark));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawTimeAxis(Canvas canvas, long[] axis, float left, float right, float bottom,
            float density, boolean dark) {
        long interval = tickInterval();
        if (interval <= 0L) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * density);
        paint.setColor(Color.argb(dark ? 120 : 90, 128, 128, 128));
        canvas.drawLine(left, bottom + 2f * density, right, bottom + 2f * density, paint);

        long anchor = defaultAxis()[1];
        long stepsBack = Math.max(0L, (anchor - axis[0]) / interval);
        long first = anchor - stepsBack * interval;
        while (first < axis[0]) first += interval;

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(regularTypeface);
        paint.setTextSize((zoomed && isWeekly() ? 8.5f : 9f) * density);
        paint.setColor(Ui.secondaryText(dark));
        SimpleDateFormat format = new SimpleDateFormat(tickPattern(), Locale.getDefault());
        int guard = 0;
        for (long tick = first; tick <= axis[1] && guard++ < 16; tick += interval) {
            float tx = x(tick, axis[0], axis[1], left, right);
            paint.setStrokeWidth(1f * density);
            canvas.drawRect(tx, bottom + 1f * density, tx + 1f * density,
                    bottom + 5f * density, paint);
            String value = format.format(new Date(tick));
            float width = paint.measureText(value);
            float labelX = Math.max(left, Math.min(right - width, tx - width / 2f));
            canvas.drawText(value, labelX, getHeight() - 5f * density, paint);
        }
    }

    private long tickInterval() {
        if (isWeekly()) return zoomed ? WEEKLY_ZOOM_TICK_MS : WEEKLY_DEFAULT_TICK_MS;
        return zoomed ? FIVE_HOUR_ZOOM_TICK_MS : FIVE_HOUR_DEFAULT_TICK_MS;
    }

    private String tickPattern() {
        boolean is24Hour = DateFormat.is24HourFormat(getContext());
        if (isWeekly() && !zoomed) return "EEE d";
        if (isWeekly()) return is24Hour ? "EEE HH:mm" : "EEE h a";
        return is24Hour ? "HH:mm" : "h:mm";
    }

    private long[] defaultAxis() {
        if (window == null || observedAtMillis <= 0L || window.windowSeconds <= 0L) return null;
        long reset = window.effectiveResetAtMillis(observedAtMillis);
        long right = reset > 0L ? reset : System.currentTimeMillis();
        long duration;
        try {
            duration = Math.multiplyExact(window.windowSeconds, 1000L);
        } catch (ArithmeticException exception) {
            return null;
        }
        if (duration <= 0L || right <= duration) return null;
        return new long[]{right - duration, right};
    }

    private long[] visibleAxis() {
        long[] full = defaultAxis();
        if (full == null) return null;
        if (!zoomed || viewportEndMillis <= viewportStartMillis) return full;
        return new long[]{viewportStartMillis, viewportEndMillis};
    }

    private long measuredEndMillis() {
        long[] full = defaultAxis();
        if (full == null || samples.isEmpty()) return Long.MIN_VALUE;
        long latest = Long.MIN_VALUE;
        for (UsageSample sample : samples) {
            if (sample == null) continue;
            latest = Math.max(latest, sample.observedAtMillis);
        }
        if (latest == Long.MIN_VALUE) return latest;
        return Math.max(full[0], Math.min(latest, full[1]));
    }

    private boolean isChartInteractionX(float touchX) {
        return touchX >= chartLeft() && touchX <= chartRight();
    }

    private boolean isMeasuredInteractionX(float touchX) {
        if (!isChartInteractionX(touchX)) return false;
        long[] full = defaultAxis();
        long measuredEnd = measuredEndMillis();
        if (full == null || measuredEnd <= full[0]) return false;
        float measuredRight = x(measuredEnd, full[0], full[1], chartLeft(), chartRight());
        return touchX <= measuredRight;
    }

    private boolean isWeekly() {
        return "Weekly".equalsIgnoreCase(label)
                || (window != null && window.windowSeconds >= TimeUnit.DAYS.toSeconds(6));
    }

    private float chartLeft() {
        return 16f * getResources().getDisplayMetrics().density;
    }

    private float chartRight() {
        return getWidth() - 16f * getResources().getDisplayMetrics().density;
    }

    private void drawEmpty(Canvas canvas, float left, float top, boolean dark, float density,
            String text) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(regularTypeface);
        paint.setTextSize(12f * density);
        paint.setColor(Ui.secondaryText(dark));
        canvas.drawText(text, left, top + 24f * density, paint);
    }

    private static float x(long time, long start, long end, float left, float right) {
        if (end <= start) return left;
        double ratio = Math.max(0d, Math.min(1d,
                (time - start) / (double) (end - start)));
        return left + (float) ratio * (right - left);
    }

    private static float y(double usedPercent, float top, float bottom) {
        return bottom - (float) (Math.max(0d, Math.min(100d, usedPercent)) / 100d)
                * (bottom - top);
    }
}
