package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

/** Shared compact account pill + dropdown for Main and onboarding surfaces. */
final class AccountSwitcherView {
    private static final int GROUP_ACCOUNTS = 1;
    private static final int GROUP_ACTIONS = 2;
    private static final int ITEM_ADD_ACCOUNT = 90001;

    interface Listener {
        void onAccountSelected(AccountContainerStore.Account account);
        void onAccountCreated(AccountContainerStore.Account account);
    }

    private AccountSwitcherView() {
    }

    static TextView create(AppCompatActivity activity, boolean dark, Listener listener) {
        AccountContainerStore.ensureInitialized(activity);
        TextView pill = Ui.text(activity, "", 13.5f, Color.WHITE);
        pill.setSingleLine(true);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 6),
                Ui.dp(activity, 10), Ui.dp(activity, 6));
        bindAppearance(activity, pill);
        pill.setOnClickListener(view -> showMenu(activity, pill, dark, listener));
        return pill;
    }

    static void bindAppearance(Context context, TextView pill) {
        AccountContainerStore.Account account = AccountContainerStore.selected(context);
        if (account == null || pill == null) return;
        pill.setText(account.name + "  ▾");
        int accent = AccountContainerStore.accentColor(account);
        pill.setTextColor(contrastForeground(accent));
        GradientDrawable background = new GradientDrawable();
        background.setColor(accent);
        background.setCornerRadius(Ui.dp(context, 18));
        pill.setBackground(background);
        pill.setContentDescription("Account " + account.name + ". Tap to switch or add account.");
    }

    private static void showMenu(AppCompatActivity activity, TextView anchor, boolean dark,
            Listener listener) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        Menu menu = popup.getMenu();
        List<AccountContainerStore.Account> accounts = AccountContainerStore.all(activity);
        String selectedId = AccountContainerStore.selectedId(activity);
        for (int i = 0; i < accounts.size(); i++) {
            AccountContainerStore.Account account = accounts.get(i);
            MenuItem item = menu.add(GROUP_ACCOUNTS, 1000 + i, i, account.name);
            item.setCheckable(true);
            item.setChecked(account.id.equals(selectedId));
        }
        menu.add(GROUP_ACTIONS, ITEM_ADD_ACCOUNT, 1000, "+ Add Account");
        if (Build.VERSION.SDK_INT >= 28) menu.setGroupDividerEnabled(true);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == ITEM_ADD_ACCOUNT) {
                promptCreate(activity, listener);
                return true;
            }
            int index = item.getItemId() - 1000;
            if (index < 0 || index >= accounts.size()) return false;
            AccountContainerStore.Account account = accounts.get(index);
            if (AccountContainerStore.select(activity, account.id)) {
                bindAppearance(activity, anchor);
                if (listener != null) listener.onAccountSelected(account);
            }
            return true;
        });
        popup.show();
    }

    private static void promptCreate(AppCompatActivity activity, Listener listener) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("Account name");
        input.setMaxLines(1);
        int pad = Ui.dp(activity, 20);
        android.widget.FrameLayout box = new android.widget.FrameLayout(activity);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Add Account")
                .setMessage("Name this Codex Monitor account.")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(activity, "Enter an account name.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    AccountContainerStore.Account account =
                            AccountContainerStore.create(activity, name);
                    dialog.dismiss();
                    if (listener != null) listener.onAccountCreated(account);
                }));
        dialog.show();
    }

    private static int contrastForeground(int background) {
        double r = Color.red(background) / 255.0;
        double g = Color.green(background) / 255.0;
        double b = Color.blue(background) / 255.0;
        double luminance = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
        return luminance > 0.42 ? 0xFF202124 : Color.WHITE;
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
