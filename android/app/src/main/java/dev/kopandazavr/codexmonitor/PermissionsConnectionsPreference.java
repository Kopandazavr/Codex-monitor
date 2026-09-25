package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/** Settings row whose readiness summary is rendered as the shared red/yellow/green status pill. */
public final class PermissionsConnectionsPreference extends Preference {
    public PermissionsConnectionsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (summary == null) return;

        boolean dark = Ui.isDark(getContext());
        int status = SetupReadiness.overallStatus(getContext());
        int foreground;
        int background;
        if (status == SetupReadiness.STATUS_REQUIRED_MISSING) {
            foreground = dark ? 0xFFFF7B73 : 0xFFD32F2F;
            background = dark ? 0x443D1010 : 0x22D32F2F;
        } else if (status == SetupReadiness.STATUS_RECOMMENDED_MISSING) {
            foreground = dark ? 0xFFFFD54F : 0xFF9A6A00;
            background = dark ? 0x443D3210 : 0x22FFC107;
        } else {
            foreground = dark ? 0xFF6EDC8C : 0xFF16843D;
            background = dark ? 0x4420442A : 0x2216843D;
        }

        GradientDrawable pill = new GradientDrawable();
        pill.setColor(background);
        pill.setCornerRadius(dp(16));
        summary.setBackground(pill);
        summary.setTextColor(foreground);
        summary.setPadding(dp(8), dp(3), dp(8), dp(3));
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }
}
