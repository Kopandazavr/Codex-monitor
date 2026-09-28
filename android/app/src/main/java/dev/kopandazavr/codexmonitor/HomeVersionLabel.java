package dev.kopandazavr.codexmonitor;

import android.app.Activity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import dev.oneuiproject.oneui.layout.ToolbarLayout;

/** Applies the accepted Diagnostics expanded/collapsed build-identity contract to Home. */
final class HomeVersionLabel {
    private static final String HOME_TITLE = "Codex Monitor";

    private HomeVersionLabel() {}

    static void apply(Activity activity) {
        if (!(activity instanceof MainActivity)) return;
        ToolbarLayout toolbar = activity.findViewById(R.id.toolbar_layout);
        if (toolbar == null) return;

        SpannableString expandedSubtitle = new SpannableString(
                "Version " + BuildConfig.VERSION_NAME + " · Build " + BuildConfig.VERSION_CODE);
        expandedSubtitle.setSpan(new ForegroundColorSpan(Ui.secondaryText(Ui.isDark(activity))),
                0, expandedSubtitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        SpannableString collapsedSubtitle = new SpannableString(buildIdentity());
        collapsedSubtitle.setSpan(new ForegroundColorSpan(Ui.secondaryText(Ui.isDark(activity))),
                0, collapsedSubtitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        collapsedSubtitle.setSpan(new RelativeSizeSpan(0.88f),
                0, collapsedSubtitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        toolbar.setTitle(HOME_TITLE, HOME_TITLE);
        toolbar.setSubtitle(expandedSubtitle);
        toolbar.setCollapsedSubtitle(collapsedSubtitle);
    }

    static String buildIdentity() {
        return BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
    }
}
