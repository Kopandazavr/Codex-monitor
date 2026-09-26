package dev.kopandazavr.codexmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Project-local appearance, short-name and alias editor. */
final class ProjectSettingsDialog {
    private ProjectSettingsDialog() {}

    static void show(Activity activity, String profileId, String watchdogShort, Runnable onChanged) {
        if (activity == null || activity.isFinishing()) return;
        ProjectProfileStore.Profile profile = ProjectProfileStore.findById(activity, profileId);
        if (profile == null) return;
        boolean dark = Ui.isDark(activity);
        AlertDialog[] holder = new AlertDialog[1];

        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8),
                Ui.dp(activity, 20), Ui.dp(activity, 16));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(sectionTitle(activity, "Preview", dark));
        ProjectBadgeView preview = new ProjectBadgeView(activity, profile, watchdogShort);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-2, -2);
        previewParams.setMargins(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 18));
        content.addView(preview, previewParams);

        content.addView(sectionTitle(activity, "Color", dark));
        LinearLayout colors = new LinearLayout(activity);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setGravity(Gravity.CENTER_VERTICAL);
        for (String colorKey : ProjectProfileStore.colorKeys()) {
            FrameLayout slot = new FrameLayout(activity);
            View circle = new View(activity);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(ProjectProfileStore.accentColor(colorKey));
            if (colorKey.equals(profile.colorKey)) {
                bg.setStroke(Ui.dp(activity, 2), Ui.mainText(dark));
            }
            circle.setBackground(bg);
            circle.setContentDescription(colorKey + " project color");
            circle.setClickable(true);
            circle.setFocusable(true);
            circle.setOnClickListener(view -> {
                ProjectProfileStore.setAppearance(activity, profile.id, profile.iconKey, colorKey);
                reopen(holder[0], activity, profile.id, watchdogShort, onChanged);
            });
            slot.addView(circle, new FrameLayout.LayoutParams(
                    Ui.dp(activity, 28), Ui.dp(activity, 28), Gravity.CENTER));
            colors.addView(slot, new LinearLayout.LayoutParams(0, Ui.dp(activity, 38), 1f));
        }
        LinearLayout.LayoutParams colorsParams = new LinearLayout.LayoutParams(-1, -2);
        colorsParams.setMargins(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 16));
        content.addView(colors, colorsParams);

        content.addView(sectionTitle(activity, "Icon", dark));
        String[] icons = ProjectProfileStore.iconKeys();
        LinearLayout iconGrid = new LinearLayout(activity);
        iconGrid.setOrientation(LinearLayout.VERTICAL);
        for (int start = 0; start < icons.length; start += 4) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 4; column++) {
                int index = start + column;
                FrameLayout slot = new FrameLayout(activity);
                if (index < icons.length) {
                    String iconKey = icons[index];
                    ImageView icon = new ImageView(activity);
                    icon.setImageResource(ProjectProfileStore.iconRes(iconKey));
                    icon.setImageTintList(ColorStateList.valueOf(
                            iconKey.equals(profile.iconKey)
                                    ? ProjectProfileStore.accentColor(profile)
                                    : Ui.mainText(dark)));
                    icon.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 10),
                            Ui.dp(activity, 10), Ui.dp(activity, 10));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(Ui.dp(activity, 12));
                    bg.setColor(Ui.controlSurface(activity, dark));
                    bg.setStroke(Ui.dp(activity, iconKey.equals(profile.iconKey) ? 2 : 1),
                            iconKey.equals(profile.iconKey)
                                    ? ProjectProfileStore.accentColor(profile)
                                    : Ui.divider(dark));
                    icon.setBackground(bg);
                    icon.setContentDescription(ProjectProfileStore.readableIconName(iconKey));
                    icon.setClickable(true);
                    icon.setFocusable(true);
                    icon.setOnClickListener(view -> {
                        ProjectProfileStore.setAppearance(
                                activity, profile.id, iconKey, profile.colorKey);
                        reopen(holder[0], activity, profile.id, watchdogShort, onChanged);
                    });
                    slot.addView(icon, new FrameLayout.LayoutParams(
                            Ui.dp(activity, 46), Ui.dp(activity, 46), Gravity.CENTER));
                }
                row.addView(slot, new LinearLayout.LayoutParams(0, Ui.dp(activity, 54), 1f));
            }
            iconGrid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, -2);
        gridParams.setMargins(0, Ui.dp(activity, 5), 0, Ui.dp(activity, 14));
        content.addView(iconGrid, gridParams);

        content.addView(sectionTitle(activity, "Short name override", dark));
        EditText shortName = new EditText(activity);
        shortName.setSingleLine(true);
        shortName.setText(profile.shortOverride);
        shortName.setHint(ProjectProfileStore.fallbackShort(profile, watchdogShort));
        shortName.setTextColor(Ui.mainText(dark));
        shortName.setHintTextColor(Ui.secondaryText(dark));
        content.addView(shortName, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 52)));

        Button saveShort = Ui.button(activity, "Save short name", false, dark);
        saveShort.setTextSize(14);
        saveShort.setOnClickListener(view -> {
            ProjectProfileStore.setShortOverride(activity, profile.id,
                    shortName.getText() == null ? "" : shortName.getText().toString());
            reopen(holder[0], activity, profile.id, watchdogShort, onChanged);
        });
        LinearLayout.LayoutParams saveShortParams =
                new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48));
        saveShortParams.setMargins(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 18));
        content.addView(saveShort, saveShortParams);

        content.addView(sectionTitle(activity, "Known aliases", dark));
        for (String alias : profile.aliases) {
            boolean primary = ProjectProfileRules.normalizeAlias(alias).equals(
                    ProjectProfileRules.normalizeAlias(profile.primaryAlias));
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView aliasText = Ui.text(activity,
                    alias + (primary ? " · Primary" : ""), 13.5f, Ui.mainText(dark));
            row.addView(aliasText, new LinearLayout.LayoutParams(0, -2, 1f));
            if (!primary) {
                Button makePrimary = miniButton(activity, "Make Primary");
                makePrimary.setOnClickListener(view -> {
                    String error = ProjectProfileStore.makePrimary(activity, profile.id, alias);
                    if (!error.isEmpty()) {
                        Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    reopen(holder[0], activity, profile.id, watchdogShort, onChanged);
                });
                row.addView(makePrimary, new LinearLayout.LayoutParams(-2, Ui.dp(activity, 38)));
                Button delete = miniButton(activity, "Delete");
                delete.setOnClickListener(view -> {
                    String error = ProjectProfileStore.deleteAlias(activity, profile.id, alias);
                    if (!error.isEmpty()) {
                        Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    reopen(holder[0], activity, profile.id, watchdogShort, onChanged);
                });
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                        -2, Ui.dp(activity, 38));
                dp.setMargins(Ui.dp(activity, 6), 0, 0, 0);
                row.addView(delete, dp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.setMargins(0, Ui.dp(activity, 6), 0, 0);
            content.addView(row, rp);
        }

        Button addAlias = Ui.button(activity, "Add alias", false, dark);
        addAlias.setTextSize(14);
        addAlias.setOnClickListener(view -> showAddAlias(
                activity, holder[0], profile.id, watchdogShort, onChanged));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48));
        ap.setMargins(0, Ui.dp(activity, 10), 0, 0);
        content.addView(addAlias, ap);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Project settings")
                .setView(scroll)
                .setNegativeButton("Done", null)
                .create();
        holder[0] = dialog;
        dialog.show();
    }

    private static void showAddAlias(Activity activity, AlertDialog parent, String profileId,
            String watchdogShort, Runnable onChanged) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("Full project name alias");
        FrameLayout frame = new FrameLayout(activity);
        int pad = Ui.dp(activity, 20);
        frame.setPadding(pad, 0, pad, 0);
        frame.addView(input, new FrameLayout.LayoutParams(-1, Ui.dp(activity, 54)));
        new AlertDialog.Builder(activity)
                .setTitle("Add alias")
                .setView(frame)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    String error = ProjectProfileStore.addAlias(activity, profileId,
                            input.getText() == null ? "" : input.getText().toString());
                    if (!error.isEmpty()) {
                        Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    reopen(parent, activity, profileId, watchdogShort, onChanged);
                })
                .show();
    }

    private static TextView sectionTitle(Activity activity, String text, boolean dark) {
        TextView title = Ui.text(activity, text, 13.0f, Ui.secondaryText(dark));
        title.setTypeface(Ui.mediumTypeface(activity));
        return title;
    }

    private static Button miniButton(Activity activity, String text) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(11);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(Ui.dp(activity, 8), 0, Ui.dp(activity, 8), 0);
        return button;
    }

    private static void reopen(AlertDialog current, Activity activity, String profileId,
            String watchdogShort, Runnable onChanged) {
        if (current != null) current.dismiss();
        if (onChanged != null) onChanged.run();
        show(activity, profileId, watchdogShort, onChanged);
    }
}
