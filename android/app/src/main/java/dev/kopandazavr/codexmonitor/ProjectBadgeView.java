package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Project icon-disc + transparent outlined identity pill. */
final class ProjectBadgeView extends FrameLayout {
    ProjectBadgeView(Context context, ProjectProfileStore.Profile profile, String watchdogShort) {
        super(context);
        int accent = ProjectProfileStore.accentColor(profile);
        setMinimumHeight(Ui.dp(context, 38));
        setFocusable(true);

        TextView pill = Ui.text(context,
                ProjectProfileStore.badgeText(profile, watchdogShort), 11.5f, Color.WHITE);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setMaxLines(2);
        pill.setPadding(Ui.dp(context, 23), Ui.dp(context, 5),
                Ui.dp(context, 10), Ui.dp(context, 5));
        GradientDrawable pillBackground = new GradientDrawable();
        pillBackground.setColor(Color.TRANSPARENT);
        pillBackground.setCornerRadius(Ui.dp(context, 14));
        pillBackground.setStroke(Ui.dp(context, 1.5f), accent);
        pill.setBackground(pillBackground);
        LayoutParams pillParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        pillParams.leftMargin = Ui.dp(context, 17);
        addView(pill, pillParams);

        ImageView icon = new ImageView(context);
        icon.setImageResource(ProjectProfileStore.iconRes(profile));
        icon.setImageTintList(ColorStateList.valueOf(accent));
        icon.setPadding(Ui.dp(context, 8), Ui.dp(context, 8),
                Ui.dp(context, 8), Ui.dp(context, 8));
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(ProjectProfileStore.discColor(profile));
        icon.setBackground(disc);
        icon.setTranslationY(-Ui.dp(context, 2));
        addView(icon, new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36),
                Gravity.START | Gravity.CENTER_VERTICAL));

        setContentDescription("Project settings for "
                + (profile == null ? "project" : profile.primaryAlias));
    }
}
