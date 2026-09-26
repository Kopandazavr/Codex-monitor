package dev.kopandazavr.codexmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
        if (ProjectProfileStore.findById(activity, profileId) == null) return;
        new Controller(activity, profileId, watchdogShort, onChanged).show();
    }

    /**
     * Keeps one dialog/window alive for the whole editing session. Individual edits only rebuild
     * the content tree in place, preserving scroll instead of dismissing/reopening the dialog.
     */
    private static final class Controller {
        private static final int ICON_COLUMNS = 6;

        private final Activity activity;
        private final String profileId;
        private final String watchdogShort;
        private final Runnable onChanged;
        private final boolean dark;
        private final ScrollView scroll;
        private final LinearLayout content;
        private AlertDialog dialog;

        Controller(Activity activity, String profileId, String watchdogShort, Runnable onChanged) {
            this.activity = activity;
            this.profileId = profileId;
            this.watchdogShort = watchdogShort;
            this.onChanged = onChanged;
            this.dark = Ui.isDark(activity);
            this.scroll = new ScrollView(activity);
            this.content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 8),
                    Ui.dp(activity, 20), Ui.dp(activity, 16));
            scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        }

        void show() {
            render(false);
            dialog = new AlertDialog.Builder(activity)
                    .setTitle("Project settings")
                    .setView(scroll)
                    .setNegativeButton("Done", null)
                    .create();
            dialog.show();
        }

        private void render(boolean preserveScroll) {
            int scrollY = preserveScroll ? scroll.getScrollY() : 0;
            ProjectProfileStore.Profile profile = ProjectProfileStore.findById(activity, profileId);
            if (profile == null) {
                if (dialog != null) dialog.dismiss();
                return;
            }
            content.removeAllViews();

            content.addView(sectionTitle(activity, "Preview", dark));
            ProjectBadgeView preview = new ProjectBadgeView(activity, profile, watchdogShort);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-2, -2);
            previewParams.setMargins(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 18));
            content.addView(preview, previewParams);

            addColors(profile);
            addIcons(profile);
            addShortName(profile);
            addAliases(profile);

            if (preserveScroll) {
                scroll.post(() -> scroll.scrollTo(0, scrollY));
            }
        }

        private void addColors(ProjectProfileStore.Profile profile) {
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
                    if (ProjectProfileStore.setAppearance(
                            activity, profile.id, profile.iconKey, colorKey)) {
                        changed();
                    }
                });
                slot.addView(circle, new FrameLayout.LayoutParams(
                        Ui.dp(activity, 28), Ui.dp(activity, 28), Gravity.CENTER));
                colors.addView(slot, new LinearLayout.LayoutParams(0, Ui.dp(activity, 38), 1f));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 16));
            content.addView(colors, params);
        }

        private void addIcons(ProjectProfileStore.Profile profile) {
            content.addView(sectionTitle(activity, "Icon", dark));
            String[] icons = ProjectProfileStore.iconKeys();
            LinearLayout iconGrid = new LinearLayout(activity);
            iconGrid.setOrientation(LinearLayout.VERTICAL);
            for (int start = 0; start < icons.length; start += ICON_COLUMNS) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int column = 0; column < ICON_COLUMNS; column++) {
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
                        icon.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8),
                                Ui.dp(activity, 8), Ui.dp(activity, 8));
                        GradientDrawable bg = new GradientDrawable();
                        bg.setCornerRadius(Ui.dp(activity, 11));
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
                            if (ProjectProfileStore.setAppearance(
                                    activity, profile.id, iconKey, profile.colorKey)) {
                                changed();
                            }
                        });
                        slot.addView(icon, new FrameLayout.LayoutParams(
                                Ui.dp(activity, 42), Ui.dp(activity, 42), Gravity.CENTER));
                    }
                    row.addView(slot, new LinearLayout.LayoutParams(
                            0, Ui.dp(activity, 48), 1f));
                }
                iconGrid.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, -2);
            gridParams.setMargins(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 13));
            content.addView(iconGrid, gridParams);
        }

        private void addShortName(ProjectProfileStore.Profile profile) {
            content.addView(sectionTitle(activity, "Short name override", dark));
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            EditText shortName = new EditText(activity);
            shortName.setSingleLine(true);
            shortName.setText(profile.shortOverride);
            shortName.setHint(ProjectProfileStore.fallbackShort(profile, watchdogShort));
            shortName.setTextColor(Ui.mainText(dark));
            shortName.setHintTextColor(Ui.secondaryText(dark));
            row.addView(shortName, new LinearLayout.LayoutParams(
                    0, Ui.dp(activity, 52), 1f));

            Button commit = Ui.button(activity, "✓", true, dark);
            commit.setTextSize(18);
            commit.setContentDescription("Save short name");
            commit.setVisibility(View.GONE);
            commit.setEnabled(false);
            LinearLayout.LayoutParams commitParams =
                    new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48));
            commitParams.setMargins(Ui.dp(activity, 8), 0, 0, 0);
            row.addView(commit, commitParams);

            String persisted = ProjectProfileRules.collapseWhitespace(profile.shortOverride);
            Runnable updateCommitState = () -> {
                boolean focused = shortName.hasFocus();
                String current = ProjectProfileRules.collapseWhitespace(
                        shortName.getText() == null ? "" : shortName.getText().toString());
                commit.setVisibility(focused ? View.VISIBLE : View.GONE);
                commit.setEnabled(focused && !current.equals(persisted));
            };
            shortName.setOnFocusChangeListener((view, focused) -> updateCommitState.run());
            shortName.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    updateCommitState.run();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            commit.setOnClickListener(view -> {
                if (!commit.isEnabled()) return;
                ProjectProfileStore.setShortOverride(activity, profile.id,
                        shortName.getText() == null ? "" : shortName.getText().toString());
                InputMethodManager keyboard = (InputMethodManager)
                        activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (keyboard != null) {
                    keyboard.hideSoftInputFromWindow(shortName.getWindowToken(), 0);
                }
                shortName.clearFocus();
                commit.setVisibility(View.GONE);
                changed();
            });

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, Ui.dp(activity, 2), 0, Ui.dp(activity, 18));
            content.addView(row, rowParams);
        }

        private void addAliases(ProjectProfileStore.Profile profile) {
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
                        changed();
                    });
                    row.addView(makePrimary,
                            new LinearLayout.LayoutParams(-2, Ui.dp(activity, 38)));
                    Button delete = miniButton(activity, "Delete");
                    delete.setOnClickListener(view -> {
                        String error = ProjectProfileStore.deleteAlias(activity, profile.id, alias);
                        if (!error.isEmpty()) {
                            Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        changed();
                    });
                    LinearLayout.LayoutParams deleteParams =
                            new LinearLayout.LayoutParams(-2, Ui.dp(activity, 38));
                    deleteParams.setMargins(Ui.dp(activity, 6), 0, 0, 0);
                    row.addView(delete, deleteParams);
                }
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.setMargins(0, Ui.dp(activity, 6), 0, 0);
                content.addView(row, rowParams);
            }

            Button addAlias = Ui.button(activity, "Add alias", false, dark);
            addAlias.setTextSize(14);
            addAlias.setOnClickListener(view -> showAddAlias());
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(-1, Ui.dp(activity, 48));
            params.setMargins(0, Ui.dp(activity, 10), 0, 0);
            content.addView(addAlias, params);
        }

        private void showAddAlias() {
            EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setHint("Full project name alias");
            FrameLayout frame = new FrameLayout(activity);
            int pad = Ui.dp(activity, 20);
            frame.setPadding(pad, 0, pad, 0);
            frame.addView(input, new FrameLayout.LayoutParams(-1, Ui.dp(activity, 54)));
            AlertDialog child = new AlertDialog.Builder(activity)
                    .setTitle("Add alias")
                    .setView(frame)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Add", null)
                    .create();
            child.setOnShowListener(ignored -> child.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String error = ProjectProfileStore.addAlias(activity, profileId,
                                input.getText() == null ? "" : input.getText().toString());
                        if (!error.isEmpty()) {
                            input.setError(error);
                            return;
                        }
                        child.dismiss();
                        changed();
                    }));
            child.show();
        }

        private void changed() {
            render(true);
            if (onChanged != null) onChanged.run();
        }
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
}
