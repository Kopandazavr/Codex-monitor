package dev.bennett.codexmeter;

import androidx.appcompat.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Factual local usage history. Raw historical windows remain stored, while the user-facing
 * visualization intentionally shows only measured points from the current 5-hour and Weekly windows.
 */
public final class UsageHistoryActivity extends AppCompatActivity {
    private LinearLayout content;
    private boolean dark;

    @Override
    protected void onCreate(Bundle state) {
        Ui.applySelectedTheme(this);
        super.onCreate(state);
        dark = Ui.isDark(this);
        content = Ui.installPage(this, "Usage history", true).content;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuilding is deliberate: zoom/pan is ephemeral and resets on every foreground resume.
        render();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void render() {
        content.removeAllViews();
        UsageSnapshot snapshot = AppPreferences.loadSnapshot(this);
        UsageHistory five = AppPreferences.loadUsageHistory(this, UsageHistory.FIVE_HOUR);
        UsageHistory weekly = AppPreferences.loadUsageHistory(this, UsageHistory.WEEKLY);

        LinearLayout intro = Ui.card(this, dark);
        intro.addView(Ui.text(this,
                "Measured usage only. Tap a chart to zoom; drag inside a zoomed chart to pan.",
                13, Ui.secondaryText(dark)));
        content.addView(intro);
        Ui.addSpacer(content, 20);

        boolean hasCharts = false;
        UsageWindow fiveWindow = snapshot == null ? null : snapshot.fiveHour;
        if (fiveWindow != null && snapshot.fetchedAtMillis > 0L) {
            addWindowSection("5-hour", fiveWindow, snapshot, five);
            hasCharts = true;
        }
        UsageWindow weeklyWindow = snapshot == null ? null : snapshot.weekly;
        if (weeklyWindow != null && snapshot.fetchedAtMillis > 0L) {
            addWindowSection("Weekly", weeklyWindow, snapshot, weekly);
            hasCharts = true;
        }

        if (!hasCharts) {
            LinearLayout waiting = Ui.card(this, dark);
            waiting.addView(Ui.text(this,
                    "Charts appear once OpenAI reports your 5-hour or Weekly usage window. "
                            + "Refresh usage from the dashboard to check again.",
                    13, Ui.secondaryText(dark)));
            content.addView(waiting);
            Ui.addSpacer(content, 20);
        }

        Button clear = Ui.button(this, "Clear local history", false, dark);
        clear.setEnabled(!five.samples.isEmpty() || !weekly.samples.isEmpty()
                || !AppPreferences.loadUsageHistory(this, UsageHistory.MONTHLY).samples.isEmpty());
        clear.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Clear usage history?")
                .setMessage("This removes every locally stored usage sample. Your latest "
                        + "allowance and account sign-in stay intact.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    AppPreferences.clearUsageHistory(this);
                    render();
                })
                .show());
        content.addView(clear, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
    }

    private void addWindowSection(String label, UsageWindow window, UsageSnapshot snapshot,
            UsageHistory history) {
        content.addView(Ui.separator(this, label + " window"));
        content.addView(buildChartCard(label, window, snapshot, history));
        Ui.addSpacer(content, 20);
    }

    private LinearLayout buildChartCard(String label, UsageWindow window, UsageSnapshot snapshot,
            UsageHistory history) {
        LinearLayout card = Ui.card(this, dark);
        card.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 10));

        FrameLayout chartFrame = new FrameLayout(this);
        UsageBurnChartView chart = new UsageBurnChartView(this);
        chart.setScrubEnabled(true);
        chart.setZoomEnabled(true);
        chart.setData(label, window, history,
                snapshot == null ? System.currentTimeMillis() : snapshot.fetchedAtMillis, null);
        chartFrame.addView(chart, new FrameLayout.LayoutParams(-1, Ui.dp(this, 220)));

        TextView zoomOut = Ui.text(this, "−", 28, Ui.mainText(dark));
        zoomOut.setGravity(Gravity.CENTER);
        zoomOut.setContentDescription("Zoom Out");
        zoomOut.setClickable(true);
        zoomOut.setFocusable(true);
        GradientDrawable zoomBackground = new GradientDrawable();
        zoomBackground.setShape(GradientDrawable.OVAL);
        zoomBackground.setColor(Ui.controlSurface(this, dark));
        zoomBackground.setStroke(Ui.dp(this, 1), Ui.divider(dark));
        zoomOut.setBackground(zoomBackground);
        FrameLayout.LayoutParams zoomParams =
                new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48),
                        Gravity.TOP | Gravity.END);
        zoomParams.setMargins(0, Ui.dp(this, 30), Ui.dp(this, 8), 0);
        chartFrame.addView(zoomOut, zoomParams);
        zoomOut.setVisibility(View.GONE);
        zoomOut.setOnClickListener(view -> chart.zoomOut());
        card.addView(chartFrame, new LinearLayout.LayoutParams(-1, Ui.dp(this, 220)));

        TextView detail = Ui.text(this,
                "Tap to zoom · drag to inspect measured points",
                12, Ui.secondaryText(dark));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        card.addView(detail, detailParams);

        chart.setOnZoomChangedListener(zoomed -> {
            zoomOut.setVisibility(zoomed ? View.VISIBLE : View.GONE);
            detail.setText(zoomed
                    ? "Drag horizontally to pan · hold a point to inspect"
                    : "Tap to zoom · drag to inspect measured points");
        });
        chart.setOnScrubListener(new UsageBurnChartView.OnScrubListener() {
            @Override
            public void onScrub(long timeMillis, double usedPercent, boolean historicalWindow) {
                String moment = UsageFormat.absolute(UsageHistoryActivity.this, timeMillis,
                        System.currentTimeMillis());
                detail.setTextColor(Ui.mainText(dark));
                detail.setText(moment + " — " + Math.round(usedPercent) + "% used");
            }

            @Override
            public void onScrubEnd() {
                detail.setTextColor(Ui.secondaryText(dark));
                detail.setText(chart.isZoomed()
                        ? "Drag horizontally to pan · hold a point to inspect"
                        : "Tap to zoom · drag to inspect measured points");
            }
        });
        return card;
    }
}
