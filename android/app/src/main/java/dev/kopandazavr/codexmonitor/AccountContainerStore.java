package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Durable local account-container registry. Project/role identity remains app-global. */
final class AccountContainerStore {
    private static final String PREFS = "codex_account_containers_v1";
    private static final String KEY_ACCOUNTS = "accounts_json";
    private static final String KEY_SELECTED = "selected_id";
    private static final String KEY_LEGACY_OWNER = "legacy_owner_id";

    private static final String[] COLOR_KEYS = {
            "blue", "green", "purple", "orange", "pink", "cyan", "yellow", "red"
    };

    static final class Account {
        final String id;
        final String name;
        final String colorKey;
        final long createdAtMillis;

        Account(String id, String name, String colorKey, long createdAtMillis) {
            this.id = clean(id);
            this.name = cleanName(name);
            this.colorKey = validColor(colorKey) ? colorKey : COLOR_KEYS[0];
            this.createdAtMillis = Math.max(0L, createdAtMillis);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("name", name);
                json.put("color", colorKey);
                json.put("created_at", createdAtMillis);
            } catch (Exception ignored) {
            }
            return json;
        }

        static Account fromJson(JSONObject json) {
            if (json == null) return null;
            Account account = new Account(
                    json.optString("id", ""),
                    json.optString("name", ""),
                    json.optString("color", COLOR_KEYS[0]),
                    json.optLong("created_at", 0L));
            return account.id.isEmpty() || account.name.isEmpty() ? null : account;
        }
    }

    private AccountContainerStore() {
    }

    static synchronized void ensureInitialized(Context context) {
        SharedPreferences prefs = prefs(context);
        List<Account> accounts = loadRaw(prefs);
        if (!accounts.isEmpty()) {
            String selected = clean(prefs.getString(KEY_SELECTED, ""));
            if (find(accounts, selected) == null) {
                prefs.edit().putString(KEY_SELECTED, accounts.get(0).id).apply();
            }
            return;
        }

        long now = System.currentTimeMillis();
        Account main = new Account("account:" + UUID.randomUUID(), "Main", COLOR_KEYS[0], now);
        ArrayList<Account> seeded = new ArrayList<>();
        seeded.add(main);
        saveAccounts(prefs, seeded);
        prefs.edit()
                .putString(KEY_SELECTED, main.id)
                .putString(KEY_LEGACY_OWNER, main.id)
                .commit();
    }

    static synchronized List<Account> all(Context context) {
        ensureInitialized(context);
        return Collections.unmodifiableList(new ArrayList<>(loadRaw(prefs(context))));
    }

    static synchronized Account selected(Context context) {
        ensureInitialized(context);
        List<Account> accounts = loadRaw(prefs(context));
        Account selected = find(accounts, prefs(context).getString(KEY_SELECTED, ""));
        return selected == null ? accounts.get(0) : selected;
    }

    static synchronized String selectedId(Context context) {
        Account account = selected(context);
        return account == null ? "" : account.id;
    }

    static synchronized Account find(Context context, String id) {
        ensureInitialized(context);
        return find(loadRaw(prefs(context)), id);
    }

    static synchronized boolean select(Context context, String id) {
        ensureInitialized(context);
        if (find(loadRaw(prefs(context)), id) == null) return false;
        return prefs(context).edit().putString(KEY_SELECTED, clean(id)).commit();
    }

    static synchronized Account create(Context context, String requestedName) {
        ensureInitialized(context);
        SharedPreferences prefs = prefs(context);
        ArrayList<Account> accounts = new ArrayList<>(loadRaw(prefs));
        String name = uniqueName(accounts, requestedName);
        String color = nextColor(accounts);
        Account account = new Account("account:" + UUID.randomUUID(), name, color,
                System.currentTimeMillis());
        accounts.add(account);
        saveAccounts(prefs, accounts);
        prefs.edit().putString(KEY_SELECTED, account.id).commit();
        return account;
    }

    static synchronized boolean rename(Context context, String id, String requestedName) {
        ensureInitialized(context);
        ArrayList<Account> accounts = new ArrayList<>(loadRaw(prefs(context)));
        int index = indexOf(accounts, id);
        if (index < 0) return false;
        String base = cleanName(requestedName);
        if (base.isEmpty()) return false;
        String name = uniqueNameExcept(accounts, base, clean(id));
        Account old = accounts.get(index);
        accounts.set(index, new Account(old.id, name, old.colorKey, old.createdAtMillis));
        saveAccounts(prefs(context), accounts);
        return true;
    }

    static synchronized boolean setColor(Context context, String id, String colorKey) {
        ensureInitialized(context);
        if (!validColor(colorKey)) return false;
        ArrayList<Account> accounts = new ArrayList<>(loadRaw(prefs(context)));
        int index = indexOf(accounts, id);
        if (index < 0) return false;
        Account old = accounts.get(index);
        accounts.set(index, new Account(old.id, old.name, colorKey, old.createdAtMillis));
        saveAccounts(prefs(context), accounts);
        return true;
    }

    static synchronized boolean remove(Context context, String id) {
        ensureInitialized(context);
        SharedPreferences prefs = prefs(context);
        ArrayList<Account> accounts = new ArrayList<>(loadRaw(prefs));
        if (accounts.size() <= 1) return false;
        int index = indexOf(accounts, id);
        if (index < 0) return false;
        String removed = accounts.remove(index).id;
        saveAccounts(prefs, accounts);
        if (removed.equals(clean(prefs.getString(KEY_SELECTED, "")))) {
            prefs.edit().putString(KEY_SELECTED, accounts.get(0).id).commit();
        }
        return true;
    }

    static synchronized boolean isLegacyOwner(Context context, String id) {
        ensureInitialized(context);
        return clean(id).equals(clean(prefs(context).getString(KEY_LEGACY_OWNER, "")));
    }

    static int accentColor(Account account) {
        return accentColor(account == null ? COLOR_KEYS[0] : account.colorKey);
    }

    static int accentColor(String key) {
        switch (key) {
            case "green": return 0xFF6EB363;
            case "purple": return 0xFF8155E6;
            case "orange": return 0xFFDF8247;
            case "pink": return 0xFFE07EAD;
            case "cyan": return 0xFF4FAFC3;
            case "yellow": return 0xFFEDC75C;
            case "red": return 0xFFE65248;
            default: return 0xFF4D81EF;
        }
    }

    static String[] colorKeys() {
        return COLOR_KEYS.clone();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<Account> loadRaw(SharedPreferences prefs) {
        ArrayList<Account> accounts = new ArrayList<>();
        String raw = prefs.getString(KEY_ACCOUNTS, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                Account account = Account.fromJson(array.optJSONObject(i));
                if (account != null && find(accounts, account.id) == null) accounts.add(account);
            }
        } catch (Exception ignored) {
        }
        return accounts;
    }

    private static void saveAccounts(SharedPreferences prefs, List<Account> accounts) {
        JSONArray array = new JSONArray();
        for (Account account : accounts) array.put(account.toJson());
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).commit();
    }

    private static Account find(List<Account> accounts, String id) {
        String target = clean(id);
        for (Account account : accounts) if (account.id.equals(target)) return account;
        return null;
    }

    private static int indexOf(List<Account> accounts, String id) {
        String target = clean(id);
        for (int i = 0; i < accounts.size(); i++) if (accounts.get(i).id.equals(target)) return i;
        return -1;
    }

    private static String nextColor(List<Account> accounts) {
        for (String color : COLOR_KEYS) {
            boolean used = false;
            for (Account account : accounts) {
                if (color.equals(account.colorKey)) {
                    used = true;
                    break;
                }
            }
            if (!used) return color;
        }
        return COLOR_KEYS[accounts.size() % COLOR_KEYS.length];
    }

    private static String uniqueName(List<Account> accounts, String requested) {
        String base = cleanName(requested);
        if (base.isEmpty()) base = "Account";
        return uniqueNameExcept(accounts, base, "");
    }

    private static String uniqueNameExcept(List<Account> accounts, String base, String excludedId) {
        String candidate = base;
        int suffix = 2;
        while (nameUsed(accounts, candidate, excludedId)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private static boolean nameUsed(List<Account> accounts, String name, String excludedId) {
        for (Account account : accounts) {
            if (!account.id.equals(excludedId) && account.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static String cleanName(String value) {
        String clean = clean(value).replaceAll("\\s+", " ");
        if (clean.length() > 32) clean = clean.substring(0, 32).trim();
        return clean;
    }

    private static boolean validColor(String value) {
        for (String color : COLOR_KEYS) if (color.equals(value)) return true;
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
