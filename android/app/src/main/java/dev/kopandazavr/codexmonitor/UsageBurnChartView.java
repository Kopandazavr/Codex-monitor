package dev.kopandazavr.codexmonitor;

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
    private final Path fillPath = new Path();
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
        String detail = resetLabel(System.currentTimeMillis());
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
        long measuredStart = measuredStartMillis();
        long measuredEnd = measuredEndMillis();
        if (full == null || measuredStart == Long.MAX_VALUE || measuredEnd <= measuredStart) {
            return false;
        }
        long measuredSpan = measuredEnd - measuredStart;
        long zoomSpan = Math.min(
                isWeekly() ? WEEKLY_ZOOM_MS : FIVE_HOUR_ZOOM_MS, measuredSpan);
        if (zoomSpan <= 0L) return false;
        float left = chartLeft();
        float right = chartRight();
        double ratio = Math.max(0d, Math.min(1d,
                (touchX - left) / Math.max(1d, right - left)));
        long center = full[0] + Math.round(ratio * (full[1] - full[0]));
        center = Math.max(measuredStart, Math.min(center, measuredEnd));
        long start = center - zoomSpan / 2L;
        long maxStart = measuredEnd - zoomSpan;
        start = Math.max(measuredStart, Math.min(start, maxStart));
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
        if (!zoomEnabled || defaultAxis() == null) return;
        long measuredStart = measuredStartMillis();
        long measuredEnd = measuredEndMillis();
        if (measuredStart == Long.MAX_VALUE || measuredEnd <= measuredStart) return;
        long requestedSpan = requestedEndMillis - requestedStartMillis;
        if (requestedSpan <= 0L) return;
        long span = Math.min(requestedSpan, measuredEnd - measuredStart);
        long maxStart = measuredEnd - span;
        long start = Math.max(measuredStart, Math.min(requestedStartMillis, maxStart));
        viewportStartMillis = start;
        viewportEndMillis = start + span;
        zoomed = true;
        invalidate();
    }

    private void panBy(float deltaX) {
        if (defaultAxis() == null || !zoomed) return;
        long measuredStart = measuredStartMillis();
        long measuredEnd = measuredEndMillis();
        if (measuredStart == Long.MAX_VALUE || measuredEnd <= measuredStart) return;
        long span = Math.min(viewportEndMillis - viewportStartMillis,
                measuredEnd - measuredStart);
        if (span <= 0L) return;
        float width = Math.max(1f, chartRight() - chartLeft());
        long shift = Math.round(-deltaX * span / width);
        long start = viewportStartMillis + shift;
        long maxStart = measuredEnd - span;
        start = Math.max(measuredStart, Math.min(start, maxStart));
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
        String resetLabel = resetLabel(System.currentTimeMillis());
        if (zoomed) resetLabel += " · zoom";
        canvas.drawText(resetLabel, right - paint.measureText(resetLabel), 20f * density, paint);

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
        fillPath.reset();
        UsageSample before = null;
        boolean started = false;
        float firstX = 0f;
        float lastX = 0f;
        float lastY = 0f;
        for (UsageSample sample : samples) {
            if (sample.observedAtMillis < axis[0]) {
                before = sample;
                continue;
            }
            if (!started && before != null) {
                float bx = x(before.observedAtMillis, axis[0], axis[1], left, right);
                float by = y(plotPercent(before), top, bottom);
                path.moveTo(bx, by);
                fillPath.moveTo(bx, by);
                firstX = bx;
                lastX = bx;
                lastY = by;
                started = true;
            }
            float sx = x(sample.observedAtMillis, axis[0], axis[1], left, right);
            float sy = y(plotPercent(sample), top, bottom);
            if (!started) {
                path.moveTo(sx, sy);
                fillPath.moveTo(sx, sy);
                firstX = sx;
                started = true;
            } else {
                path.lineTo(sx, sy);
                fillPath.lineTo(sx, sy);
            }
            lastX = sx;
            lastY = sy;
            if (sample.observedAtMillis > axis[1]) break;
        }
        if (!started && before != null) {
            float by = y(plotPercent(before), top, bottom);
            path.moveTo(left, by);
            fillPath.moveTo(left, by);
            firstX = left;
            lastX = left;
            lastY = by;
            started = true;
        }
        if (!started) return;

        int seriesColor = isWeekly() ? WEEKLY_ORANGE : Ui.accent(getContext(), dark);
        if (lastX > firstX + 0.5f) {
            fillPath.lineTo(lastX, bottom);
            fillPath.lineTo(firstX, bottom);
            fillPath.close();
            drawStripedFill(canvas, fillPath, firstX, lastX, top, bottom, density,
                    seriesColor, dark);
            // The texture moves independently from the measured geometry.
            postInvalidateDelayed(1000L);
        } else {
            // A first bootstrap pair shares one truthful timestamp. Render that observation as a
            // point rather than inventing horizontal history.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(seriesColor);
            canvas.drawCircle(lastX, lastY, 3f * density, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(null);
        paint.setStrokeWidth(3f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(seriesColor);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawStripedFill(Canvas canvas, Path area, float firstX, float lastX,
            float top, float bottom, float density, int seriesColor, boolean dark) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(dark ? 20 : 16,
                Color.red(seriesColor), Color.green(seriesColor), Color.blue(seriesColor)));
        canvas.drawPath(area, paint);

        float spacing = 14f * density;
        float height = Math.max(1f, bottom - top);
        float phase = (SystemClock.uptimeMillis() % 90_000L) / 90_000f * spacing;
        int save = canvas.save();
        canvas.clipPath(area);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(Color.argb(dark ? 54 : 42,
                Color.red(seriesColor), Color.green(seriesColor), Color.blue(seriesColor)));
        for (float sx = firstX - height - spacing + phase;
                sx <= lastX + spacing; sx += spacing) {
            canvas.drawLine(sx, bottom, sx + height, top, paint);
        }
        canvas.restoreToCount(save);
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

    private long measuredStartMillis() {
        long[] full = defaultAxis();
        if (full == null || samples.isEmpty()) return Long.MAX_VALUE;
        long earliest = Long.MAX_VALUE;
        for (UsageSample sample : samples) {
            if (sample == null) continue;
            earliest = Math.min(earliest, sample.observedAtMillis);
        }
        if (earliest == Long.MAX_VALUE) return earliest;
        return Math.max(full[0], Math.min(earliest, full[1]));
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
        long measuredStart = measuredStartMillis();
        long measuredEnd = measuredEndMillis();
        if (full == null || measuredStart == Long.MAX_VALUE || measuredEnd < measuredStart) {
            return false;
        }
        float measuredLeft = x(measuredStart, full[0], full[1], chartLeft(), chartRight());
        float measuredRight = x(measuredEnd, full[0], full[1], chartLeft(), chartRight());
        return touchX >= measuredLeft && touchX <= measuredRight;
    }

    private String resetLabel(long nowMillis) {
        if (window == null) return "Reset time unavailable";
        long reference = observedAtMillis > 0L ? observedAtMillis : nowMillis;
        String value = UsageFormat.reset(getContext(), window, WidgetOptions.RESET_RELATIVE,
                reference, nowMillis);
        return value == null || value.isEmpty() ? "Reset time unavailable" : value;
    }

    private boolean isWeekly() {
        return "Weekly".equalsIgnoreCase(label)
                || (window != null && window.windowSeconds >= TimeUnit.DAYS.toSeconds(6));
    }

    private double plotPercent(UsageSample sample) {
        if (sample == null) return 0d;
        double used = Math.max(0d, Math.min(100d, sample.usedPercent));
        // Both visible dashboard histories use remaining allowance:
        // 100% available at the top, descending toward 0% as usage is consumed.
        return 100d - used;
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
