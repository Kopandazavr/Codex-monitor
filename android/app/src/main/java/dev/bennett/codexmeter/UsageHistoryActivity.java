package dev.bennett.codexmeter;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
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
        // Rebuild factual data on foreground resume; charts on this screen are intentionally static.
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
                "Measured usage only.",
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

        UsageBurnChartView chart = new UsageBurnChartView(this);
        chart.setData(label, window, history,
                snapshot == null ? System.currentTimeMillis() : snapshot.fetchedAtMillis, null);
        card.addView(chart, new LinearLayout.LayoutParams(-1, Ui.dp(this, 220)));
        return card;
    }
}
