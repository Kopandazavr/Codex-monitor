package dev.kopandazavr.codexmonitor;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.appcompat.content.res.AppCompatResources;

/** Animated percentage fill used by the Figma usage-counter cards. */
public final class UsageWaveView extends View {
    private static final long NORMAL_WAVE_DURATION_MS = 2400L;
    private static final long WARNING_WAVE_DURATION_MS = 950L;
    private static final int WEEKLY_ORANGE = 0xFFFF9800;
    private static final int RESET_LIME = ResetProgress.RESET_LIME;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint refreshStatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint resetRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fillPath = new Path();
    private ValueAnimator animator;
    private Drawable icon;
    private String title = "";
    private String resetTop = "";
    private String resetBottom = "";
    private String pace = "";
    private int percent;
    private int fillPercent;
    private int resetRingPercent = -1;
    private boolean warning;
    private boolean weekly;
    private boolean compactReset;
    private float phase;
    private float phaseOffset;

    public UsageWaveView(Context context) {
        this(context, null);
    }

    public UsageWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        titlePaint.setTypeface(Typeface.create("sec", Typeface.BOLD));
        resetPaint.setTypeface(Typeface.create("sec", Typeface.NORMAL));
        pacePaint.setTypeface(Typeface.create("sec", Typeface.BOLD));
        percentPaint.setTypeface(Typeface.create("sec", Typeface.BOLD));
        refreshStatePaint.setTypeface(Typeface.create("sec", Typeface.NORMAL));
        percentPaint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setUsage(String label, String reset, String paceEstimate, int remainingPercent,
            int iconRes, boolean invertedWave, boolean acceleratedWarning) {
        setUsage(label, reset, paceEstimate, remainingPercent, -1,
                iconRes, invertedWave, acceleratedWarning);
    }

    /** Legacy signature retained for source compatibility; liquid always follows availability. */
    public void setUsage(String label, String reset, String paceEstimate, int remainingPercent,
            int ignoredBarPercent, boolean ignoredDepletedMode, int iconRes, boolean invertedWave,
            boolean acceleratedWarning) {
        setUsage(label, reset, paceEstimate, remainingPercent, -1,
                iconRes, invertedWave, acceleratedWarning);
    }

    public void setUsage(String label, String reset, String paceEstimate, int remainingPercent,
            int resetTimeRemainingPercent, int iconRes, boolean invertedWave,
            boolean acceleratedWarning) {
        title = label;
        percent = Math.max(0, Math.min(100, remainingPercent));
        fillPercent = percent;
        resetRingPercent = resetTimeRemainingPercent < 0 ? -1
                : Math.max(0, Math.min(100, resetTimeRemainingPercent));
        compactReset = "5-hour".equals(label) || "Weekly".equals(label);
        pace = compactReset || paceEstimate == null ? "" : paceEstimate;
        warning = acceleratedWarning && percent > 0;
        weekly = "Weekly".equals(label);
        if (animator != null) {
            animator.setDuration(warning ? WARNING_WAVE_DURATION_MS : NORMAL_WAVE_DURATION_MS);
        }
        phaseOffset = invertedWave ? (float) Math.PI : 0f;
        if (compactReset) {
            // Two primary Home cards use one reset line, without a depletion forecast.
            resetTop = reset == null ? "" : reset;
            resetBottom = "";
        } else if (reset != null && reset.startsWith("Resets in ")) {
            resetTop = "Resets in";
            resetBottom = reset.substring("Resets in ".length());
        } else {
            resetTop = reset == null ? "" : reset;
            resetBottom = "";
        }
        icon = AppCompatResources.getDrawable(getContext(), iconRes);
        String description = label + ", " + percent + " percent available. " + reset;
        if (resetRingPercent >= 0) {
            description += ". Reset cycle progress " + resetRingPercent + " percent";
        }
        if (!pace.isEmpty()) description += ". " + pace.replace("Est.", "Estimated");
        if (warning) description += ". Accelerated usage warning";
        if (ForegroundUsageRefresh.isInFlight()) description += ". Refreshing";
        else if (ForegroundUsageRefresh.isStale()) description += ". Not refreshed";
        setContentDescription(description);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        animator.setDuration(warning ? WARNING_WAVE_DURATION_MS : NORMAL_WAVE_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (Float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        boolean dark = Ui.isDark(getContext());
        float edge = getWidth() * fillPercent / 100f;
        if (fillPercent > 0) {
            float amplitude = (warning ? 10f : 8f) * density;
            fillPath.reset();
            fillPath.moveTo(0, 0);
            fillPath.lineTo(edge, 0);
            int steps = warning ? 36 : 28;
            for (int i = 1; i <= steps; i++) {
                float y = getHeight() * i / (float) steps;
                float envelope = (float) Math.sin(Math.PI * y / getHeight());
                float angle = (float) ((warning ? Math.PI * 4 : Math.PI * 2)
                        * y / getHeight() + phase + phaseOffset);
                float wave = (float) Math.sin(angle);
                float x = edge + amplitude * envelope * wave;
                fillPath.lineTo(x, y);
            }
            fillPath.lineTo(0, getHeight());
            fillPath.close();
            fillPaint.setColor(weekly ? WEEKLY_ORANGE : Ui.accent(getContext(), dark));
            canvas.drawPath(fillPath, fillPaint);
        }

        int foreground = weekly && fillPercent > 0 ? 0xFF000000 : Ui.mainText(dark);
        titlePaint.setColor(foreground);
        titlePaint.setTextSize(20f * density);
        // Reset duration must match title/percent contrast (black light / white dark).
        resetPaint.setColor(foreground);
        resetPaint.setTextSize(13f * density);
        pacePaint.setColor(foreground);
        pacePaint.setTextSize(12.5f * density);
        float titleX = 12f * density;
        float titleBaseline = 34f * density;
        canvas.drawText(title, titleX, titleBaseline, titlePaint);
        drawRefreshState(canvas, density, foreground,
                titleX + titlePaint.measureText(title) + 9f * density, titleBaseline);
        if (!resetTop.isEmpty()) {
            if (compactReset) {
                // Reserve the entire right metrics column, even on narrow devices.
                float left = 12f * density;
                float right = Math.max(left, getWidth() - 90f * density);
                float available = Math.max(0f, right - left);
                float actual = resetPaint.measureText(resetTop);
                if (actual > available && actual > 0f) {
                    resetPaint.setTextSize(Math.max(10f * density,
                            13f * density * available / actual));
                }
                canvas.save();
                canvas.clipRect(left, 52f * density, right, 100f * density);
                canvas.drawText(resetTop, left, 82f * density, resetPaint);
                canvas.restore();
            } else {
                canvas.drawText(resetTop, 12f * density, 67f * density, resetPaint);
            }
            if (!resetBottom.isEmpty()) {
                canvas.drawText(resetBottom, 12f * density, 87f * density, resetPaint);
                if (!pace.isEmpty()) {
                    float paceX = 18f * density
                            + resetPaint.measureText(resetBottom);
                    canvas.drawText("· " + pace, paceX, 87f * density, pacePaint);
                }
            }
        } else if (!pace.isEmpty()) {
            canvas.drawText(pace, 12f * density, 87f * density, pacePaint);
        }
        float rightCenter = getWidth() - 48f * density;
        if (resetRingPercent >= 0) {
            float centerY = 31f * density;
            float radius = 22f * density;
            resetRingPaint.setStyle(Paint.Style.STROKE);
            resetRingPaint.setStrokeWidth(3f * density);
            resetRingPaint.setStrokeCap(Paint.Cap.ROUND);
            resetRingPaint.setColor(Ui.divider(dark));
            canvas.drawCircle(rightCenter, centerY, radius, resetRingPaint);
            if (resetRingPercent > 0) {
                resetRingPaint.setColor(RESET_LIME);
                canvas.drawArc(rightCenter - radius, centerY - radius,
                        rightCenter + radius, centerY + radius,
                        -90f, 360f * resetRingPercent / 100f, false, resetRingPaint);
            }
            resetRingPaint.setStyle(Paint.Style.FILL);
        }
        if (icon != null) {
            int size = Math.round(36f * density);
            int left = Math.round(rightCenter - size / 2f);
            int top = Math.round(13f * density);
            icon.setBounds(left, top, left + size, top + size);
        }
        if (weekly && fillPercent > 0) {
            // Split the glyphs by the ACTUAL animated wave, not the coarse percent
            // threshold. At 80% the right column is on dark background, while at
            // 100% it is orange; a boundary crossing may put one glyph over both.
            canvas.save();
            canvas.clipPath(fillPath);
            drawRightMetrics(canvas, rightCenter, density, 0xFF000000);
            canvas.restore();
            canvas.save();
            canvas.clipOutPath(fillPath);
            drawRightMetrics(canvas, rightCenter, density, Ui.mainText(dark));
            canvas.restore();
        } else {
            drawRightMetrics(canvas, rightCenter, density, foreground);
        }
    }

    private void drawRightMetrics(Canvas canvas, float center, float density, int color) {
        if (icon != null) {
            icon.setTint(color);
            icon.draw(canvas);
        }
        percentPaint.setColor(color);
        percentPaint.setTextSize(22f * density);
        canvas.drawText(percent + "%", center, 85f * density, percentPaint);
    }

    private void drawRefreshState(Canvas canvas, float density, int color,
            float requestedX, float baseline) {
        float reservedRight = getWidth() - 92f * density;
        float x = Math.min(requestedX, reservedRight - 72f * density);
        x = Math.max(12f * density, x);
        refreshStatePaint.setColor(color);
        if (ForegroundUsageRefresh.isInFlight()) {
            refreshStatePaint.setStyle(Paint.Style.STROKE);
            refreshStatePaint.setStrokeWidth(1.8f * density);
            float radius = 6f * density;
            float centerX = x + radius;
            float centerY = baseline - 6.5f * density;
            float rotation = (float) Math.toDegrees(phase) % 360f;
            canvas.drawArc(centerX - radius, centerY - radius,
                    centerX + radius, centerY + radius,
                    rotation, 255f, false, refreshStatePaint);
        } else if (ForegroundUsageRefresh.isStale()) {
            refreshStatePaint.setStyle(Paint.Style.FILL);
            refreshStatePaint.setTextSize(10.5f * density);
            canvas.drawText("Not refreshed", x, baseline, refreshStatePaint);
        }
        refreshStatePaint.setStyle(Paint.Style.FILL);
    }
}
