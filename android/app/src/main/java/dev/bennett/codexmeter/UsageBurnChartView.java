package dev.bennett.codexmeter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.format.DateFormat;
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
    private static final long FIVE_HOUR_ZOOM_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long WEEKLY_ZOOM_MS = TimeUnit.DAYS.toMillis(1);
    private static final long FIVE_HOUR_ZOOM_TICK_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long FIVE_HOUR_DEFAULT_TICK_MS = TimeUnit.HOURS.toMillis(1);
    private static final long WEEKLY_ZOOM_TICK_MS = TimeUnit.HOURS.toMillis(4);
    private static final long WEEKLY_DEFAULT_TICK_MS = TimeUnit.DAYS.toMillis(1);
    // Same orange used by the Weekly dashboard fill.
    private static final int WEEKLY_ORANGE = 0xFFFF9800;

    public interface OnScrubListener {
        void onScrub(long timeMillis, double usedPercent, boolean historicalWindow);

        void onScrubEnd();
    }

    public interface OnZoomChangedListener {
        void onZoomChanged(boolean zoomed);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF bubbleRect = new RectF();
    private final Typeface regularTypeface = Typeface.create("sec", Typeface.NORMAL);
    private final Typeface boldTypeface = Typeface.create("sec", Typeface.BOLD);
    private final int touchSlop;

    private String label = "";
    private UsageWindow window;
    private List<UsageSample> samples = Collections.emptyList();
    private long observedAtMillis;
    private boolean scrubEnabled;
    private boolean zoomEnabled;
    private boolean zoomed;
    private long viewportStartMillis;
    private long viewportEndMillis;
    private OnScrubListener scrubListener;
    private OnZoomChangedListener zoomChangedListener;
    private boolean scrubbing;
    private long scrubTimeMillis;
    private double scrubPercent = -1d;
    private long lastHapticBucket = Long.MIN_VALUE;
    private float downX;
    private float lastX;
    private boolean moved;

    public UsageBurnChartView(Context context) {
        this(context, null);
    }

    public UsageBurnChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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
        scrubbing = false;
        String detail = samples.size() < 2 ? "Building measured history"
                : samples.size() + " measured samples";
        if (zoomEnabled) detail += ". Tap to zoom and drag to inspect";
        setContentDescription(this.label + " measured usage chart. " + detail + ".");
        if (wasZoomed && zoomChangedListener != null) zoomChangedListener.onZoomChanged(false);
        invalidate();
    }

    public void setScrubEnabled(boolean enabled) {
        scrubEnabled = enabled;
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
        scrubbing = false;
        lastHapticBucket = Long.MIN_VALUE;
        if (scrubListener != null) scrubListener.onScrubEnd();
        if (zoomChangedListener != null) zoomChangedListener.onZoomChanged(false);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
    }

    public void setOnScrubListener(OnScrubListener listener) {
        scrubListener = listener;
    }

    public void setOnZoomChangedListener(OnZoomChangedListener listener) {
        zoomChangedListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!scrubEnabled && !zoomEnabled) return super.onTouchEvent(event);
        long[] axis = visibleAxis();
        if (axis == null || samples.isEmpty()) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = event.getX();
                lastX = downX;
                moved = false;
                if (scrubEnabled) updateScrub(event.getX(), axis);
                return true;
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                if (Math.abs(x - downX) > touchSlop) moved = true;
                if (zoomEnabled && zoomed && moved) {
                    if (scrubbing) {
                        scrubbing = false;
                        if (scrubListener != null) scrubListener.onScrubEnd();
                    }
                    panBy(x - lastX);
                    lastX = x;
                } else if (scrubEnabled) {
                    updateScrub(x, axis);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (zoomEnabled && !moved && !zoomed) {
                    zoomAt(event.getX());
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

    private void finishTouch() {
        getParent().requestDisallowInterceptTouchEvent(false);
        scrubbing = false;
        lastHapticBucket = Long.MIN_VALUE;
        if (scrubListener != null) scrubListener.onScrubEnd();
        invalidate();
    }

    private void zoomAt(float touchX) {
        long[] full = defaultAxis();
        if (full == null) return;
        long zoomSpan = isWeekly() ? WEEKLY_ZOOM_MS : FIVE_HOUR_ZOOM_MS;
        long fullSpan = full[1] - full[0];
        if (fullSpan <= zoomSpan) return;
        float left = chartLeft();
        float right = chartRight();
        double ratio = Math.max(0d, Math.min(1d,
                (touchX - left) / Math.max(1d, right - left)));
        long center = full[0] + Math.round(ratio * fullSpan);
        long start = center - zoomSpan / 2L;
        start = Math.max(full[0], Math.min(start, full[1] - zoomSpan));
        viewportStartMillis = start;
        viewportEndMillis = start + zoomSpan;
        zoomed = true;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (zoomChangedListener != null) zoomChangedListener.onZoomChanged(true);
        invalidate();
    }

    private void panBy(float deltaX) {
        long[] full = defaultAxis();
        if (full == null || !zoomed) return;
        long span = viewportEndMillis - viewportStartMillis;
        float width = Math.max(1f, chartRight() - chartLeft());
        long shift = Math.round(-deltaX * span / width);
        long start = viewportStartMillis + shift;
        start = Math.max(full[0], Math.min(start, full[1] - span));
        viewportStartMillis = start;
        viewportEndMillis = start + span;
        invalidate();
    }

    private void updateScrub(float touchX, long[] axis) {
        float left = chartLeft();
        float right = chartRight();
        double ratio = Math.max(0d, Math.min(1d,
                (touchX - left) / Math.max(1d, right - left)));
        long time = axis[0] + Math.round(ratio * (axis[1] - axis[0]));
        long first = samples.get(0).observedAtMillis;
        long last = samples.get(samples.size() - 1).observedAtMillis;
        long clamped = Math.max(Math.max(first, axis[0]), Math.min(Math.min(last, axis[1]), time));
        double percent = UsageStats.usedPercentAt(samples, clamped);
        if (percent < 0d) percent = samples.get(samples.size() - 1).usedPercent;
        boolean changed = !scrubbing || clamped != scrubTimeMillis;
        scrubbing = true;
        scrubTimeMillis = clamped;
        scrubPercent = percent;
        long bucketSize = Math.max(1L, (axis[1] - axis[0]) / 24L);
        long bucket = (clamped - axis[0]) / bucketSize;
        if (bucket != lastHapticBucket) {
            lastHapticBucket = bucket;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        if (changed && scrubListener != null) {
            scrubListener.onScrub(clamped, percent, false);
        }
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

        if (scrubbing && scrubPercent >= 0d) {
            drawScrub(canvas, axis, left, right, top, bottom, density, dark);
        }
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
        long right = reset > observedAtMillis ? reset : observedAtMillis;
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

    private void drawScrub(Canvas canvas, long[] axis, float left, float right, float top,
            float bottom, float density, boolean dark) {
        float scrubX = x(scrubTimeMillis, axis[0], axis[1], left, right);
        float scrubY = y(scrubPercent, top, bottom);
        int series = isWeekly() ? WEEKLY_ORANGE : Ui.accent(getContext(), dark);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(Color.argb(dark ? 150 : 125,
                Color.red(series), Color.green(series), Color.blue(series)));
        canvas.drawLine(scrubX, top, scrubX, bottom, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.cardColor(getContext(), dark));
        canvas.drawCircle(scrubX, scrubY, 6f * density, paint);
        paint.setColor(series);
        canvas.drawCircle(scrubX, scrubY, 4f * density, paint);

        String bubble = scrubTimeLabel() + " · " + Math.round(scrubPercent) + "%";
        paint.setTypeface(regularTypeface);
        paint.setTextSize(11f * density);
        float textWidth = paint.measureText(bubble);
        float padding = 8f * density;
        float bubbleLeft = Math.max(left,
                Math.min(right - textWidth - padding * 2f,
                        scrubX - textWidth / 2f - padding));
        float bubbleTop = top - 30f * density;
        bubbleRect.set(bubbleLeft, bubbleTop,
                bubbleLeft + textWidth + padding * 2f,
                bubbleTop + 22f * density);
        paint.setColor(Ui.controlSurface(getContext(), dark));
        canvas.drawRoundRect(bubbleRect, 11f * density, 11f * density, paint);
        paint.setColor(Ui.mainText(dark));
        canvas.drawText(bubble, bubbleLeft + padding, bubbleTop + 15f * density, paint);
    }

    private String scrubTimeLabel() {
        boolean is24Hour = DateFormat.is24HourFormat(getContext());
        String pattern = isWeekly()
                ? (is24Hour ? "EEE HH:mm" : "EEE h:mm a")
                : (is24Hour ? "HH:mm" : "h:mm a");
        return new SimpleDateFormat(pattern, Locale.getDefault())
                .format(new Date(scrubTimeMillis));
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
