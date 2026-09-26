package dev.kopandazavr.codexmonitor;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Durable local project identity registry. Never writes watchdog Calendar metadata. */
final class ProjectProfileStore {
    private static final String PREFS = "codex_project_profiles_v1";
    private static final String KEY_PROFILES = "profiles_json";

    static final String COLOR_GRAY = "gray";
    static final String COLOR_RED = "red";
    static final String COLOR_ORANGE = "orange";
    static final String COLOR_YELLOW = "yellow";
    static final String COLOR_GREEN = "green";
    static final String COLOR_BLUE = "blue";
    static final String COLOR_PURPLE = "purple";
    static final String COLOR_PINK = "pink";

    private static final String[] COLOR_KEYS = {
            COLOR_GRAY, COLOR_RED, COLOR_ORANGE, COLOR_YELLOW,
            COLOR_GREEN, COLOR_BLUE, COLOR_PURPLE, COLOR_PINK
    };
    private static final String[] ICON_KEYS = {
            "popcorn", "stethoscope", "paw", "flask",
            "heart", "heart_filled", "graduation_cap", "terminal",
            "dumbbell", "brain", "folder", "dollar_circle",
            "book", "pencil", "music", "atom",
            "notebook", "globe", "plane", "bar_chart"
    };

    static final class Profile {
        final String id;
        final List<String> aliases;
        final String primaryAlias;
        final String shortOverride;
        final String iconKey;
        final String colorKey;

        Profile(String id, List<String> aliases, String primaryAlias, String shortOverride,
                String iconKey, String colorKey) {
            this.id = id;
            this.aliases = Collections.unmodifiableList(new ArrayList<>(aliases));
            this.primaryAlias = primaryAlias;
            this.shortOverride = shortOverride;
            this.iconKey = iconKey;
            this.colorKey = colorKey;
        }
    }

    private ProjectProfileStore() {}

    static synchronized Profile resolve(Context context, String incomingProject) {
        List<MutableProfile> profiles = loadAndSeed(context);
        String normalized = ProjectProfileRules.normalizeAlias(incomingProject);
        for (MutableProfile profile : profiles) {
            if (profile.hasAlias(normalized)) return profile.freeze();
        }
        String raw = ProjectProfileRules.collapseWhitespace(incomingProject);
        if (raw.isEmpty()) return null;
        MutableProfile created = new MutableProfile(
                "project:" + UUID.randomUUID(), raw, "", "folder", COLOR_GRAY);
        created.aliases.add(raw);
        profiles.add(created);
        save(context, profiles);
        return created.freeze();
    }

    static synchronized Profile findById(Context context, String id) {
        for (MutableProfile profile : loadAndSeed(context)) {
            if (profile.id.equals(id)) return profile.freeze();
        }
        return null;
    }

    static String fallbackShort(Profile profile, String watchdogShort) {
        return profile == null ? "" : ProjectProfileRules.fallbackShort(
                watchdogShort, profile.primaryAlias);
    }

    static String badgeText(Profile profile, String watchdogShort) {
        return profile == null ? "" : ProjectProfileRules.badgeText(
                profile.shortOverride, watchdogShort, profile.primaryAlias);
    }

    static synchronized boolean setShortOverride(Context context, String id, String value) {
        List<MutableProfile> profiles = loadAndSeed(context);
        MutableProfile profile = findMutable(profiles, id);
        if (profile == null) return false;
        profile.shortOverride = ProjectProfileRules.collapseWhitespace(value);
        save(context, profiles);
        return true;
    }

    static synchronized boolean setAppearance(Context context, String id,
            String iconKey, String colorKey) {
        if (!contains(ICON_KEYS, iconKey) || !contains(COLOR_KEYS, colorKey)) return false;
        List<MutableProfile> profiles = loadAndSeed(context);
        MutableProfile profile = findMutable(profiles, id);
        if (profile == null) return false;
        profile.iconKey = iconKey;
        profile.colorKey = colorKey;
        save(context, profiles);
        return true;
    }

    static synchronized String addAlias(Context context, String id, String alias) {
        String clean = ProjectProfileRules.collapseWhitespace(alias);
        String normalized = ProjectProfileRules.normalizeAlias(clean);
        if (normalized.isEmpty()) return "Alias cannot be empty.";
        List<MutableProfile> profiles = loadAndSeed(context);
        MutableProfile target = findMutable(profiles, id);
        if (target == null) return "Project profile not found.";
        for (MutableProfile profile : profiles) {
            if (!profile.hasAlias(normalized)) continue;
            return profile.id.equals(id)
                    ? "That alias is already known for this project."
                    : "That alias already belongs to " + profile.primaryAlias + ".";
        }
        target.aliases.add(clean);
        save(context, profiles);
        return "";
    }

    static synchronized String makePrimary(Context context, String id, String alias) {
        String normalized = ProjectProfileRules.normalizeAlias(alias);
        List<MutableProfile> profiles = loadAndSeed(context);
        MutableProfile target = findMutable(profiles, id);
        if (target == null) return "Project profile not found.";
        String stored = target.aliasFor(normalized);
        if (stored.isEmpty()) return "Alias not found.";
        target.primaryAlias = stored;
        save(context, profiles);
        return "";
    }

    static synchronized String deleteAlias(Context context, String id, String alias) {
        String normalized = ProjectProfileRules.normalizeAlias(alias);
        List<MutableProfile> profiles = loadAndSeed(context);
        MutableProfile target = findMutable(profiles, id);
        if (target == null) return "Project profile not found.";
        String stored = target.aliasFor(normalized);
        if (stored.isEmpty()) return "Alias not found.";
        if (ProjectProfileRules.normalizeAlias(target.primaryAlias).equals(normalized)) {
            return "Make another alias Primary before deleting this one.";
        }
        if (target.aliases.size() <= 1) return "The last alias cannot be deleted.";
        target.aliases.remove(stored);
        save(context, profiles);
        return "";
    }

    static String[] colorKeys() { return COLOR_KEYS.clone(); }
    static String[] iconKeys() { return ICON_KEYS.clone(); }

    static int accentColor(Profile profile) {
        return accentColor(profile == null ? COLOR_GRAY : profile.colorKey);
    }

    static int accentColor(String colorKey) {
        switch (colorKey) {
            case COLOR_RED: return 0xFFE65248;
            case COLOR_ORANGE: return 0xFFDF8247;
            case COLOR_YELLOW: return 0xFFEDC75C;
            case COLOR_GREEN: return 0xFF6EB363;
            case COLOR_BLUE: return 0xFF4D81EF;
            case COLOR_PURPLE: return 0xFF8155E6;
            case COLOR_PINK: return 0xFFE07EAD;
            default: return 0xFFAFAFAF;
        }
    }

    static int discColor(Profile profile) {
        String colorKey = profile == null ? COLOR_GRAY : profile.colorKey;
        switch (colorKey) {
            case COLOR_RED: return 0xFF2E0F0D;
            case COLOR_ORANGE: return 0xFF2C1A0E;
            case COLOR_YELLOW: return 0xFF2F2712;
            case COLOR_GREEN: return 0xFF162415;
            case COLOR_BLUE: return 0xFF0F1A2E;
            case COLOR_PURPLE: return 0xFF19102D;
            case COLOR_PINK: return 0xFF2C1522;
            default: return 0xFF242424;
        }
    }

    static int iconRes(Profile profile) {
        return iconRes(profile == null ? "folder" : profile.iconKey);
    }

    static int iconRes(String iconKey) {
        switch (iconKey) {
            case "popcorn": return R.drawable.ic_project_popcorn;
            case "stethoscope": return R.drawable.ic_project_stethoscope;
            case "paw": return R.drawable.ic_project_paw;
            case "flask": return R.drawable.ic_project_flask;
            case "heart": return R.drawable.ic_project_heart;
            case "heart_filled": return R.drawable.ic_project_heart_filled;
            case "graduation_cap": return R.drawable.ic_project_graduation_cap;
            case "terminal": return R.drawable.ic_project_terminal;
            case "dumbbell": return R.drawable.ic_project_dumbbell;
            case "brain": return R.drawable.ic_project_brain;
            case "dollar_circle": return R.drawable.ic_project_dollar_circle;
            case "book": return R.drawable.ic_project_book;
            case "pencil": return R.drawable.ic_project_pencil;
            case "music": return R.drawable.ic_project_music;
            case "atom": return R.drawable.ic_project_atom;
            case "notebook": return R.drawable.ic_project_notebook;
            case "globe": return R.drawable.ic_project_globe;
            case "plane": return R.drawable.ic_project_plane;
            case "bar_chart": return R.drawable.ic_project_bar_chart;
            default: return R.drawable.ic_project_folder;
        }
    }

    static String readableIconName(String iconKey) {
        if ("heart_filled".equals(iconKey)) return "Heart filled";
        if ("graduation_cap".equals(iconKey)) return "Graduation cap";
        if ("dollar_circle".equals(iconKey)) return "Dollar circle";
        if ("bar_chart".equals(iconKey)) return "Bar chart";
        String clean = iconKey == null ? "" : iconKey.replace('_', ' ');
        return clean.isEmpty() ? "Project icon"
                : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private static List<MutableProfile> loadAndSeed(Context context) {
        List<MutableProfile> profiles = load(context);
        boolean changed = false;
        changed |= addSeed(profiles, "seed:data-matrix", "Data Matrix",
                "DM", "terminal", COLOR_GREEN);
        changed |= addSeed(profiles, "seed:codex-monitor", "Codex Monitor",
                "", "bar_chart", COLOR_PURPLE);
        changed |= addSeed(profiles, "seed:orders-cigarettes", "Заказы сигарет",
                "", "notebook", COLOR_YELLOW);
        changed |= addSeed(profiles, "seed:mira-technical", "Mira Technical",
                "", "terminal", COLOR_BLUE);
        changed |= addSeed(profiles, "seed:ai-books-future",
                "Написание книг про ии будущего", "", "pencil", COLOR_ORANGE);
        changed |= addSeed(profiles, "seed:mira-universe", "Mira Universe",
                "", "heart", COLOR_RED);
        if (changed) save(context, profiles);
        return profiles;
    }

    private static boolean addSeed(List<MutableProfile> profiles, String id, String primary,
            String shortOverride, String icon, String color) {
        String normalized = ProjectProfileRules.normalizeAlias(primary);
        for (MutableProfile profile : profiles) {
            if (profile.id.equals(id) || profile.hasAlias(normalized)) return false;
        }
        MutableProfile profile = new MutableProfile(id, primary, shortOverride, icon, color);
        profile.aliases.add(primary);
        profiles.add(profile);
        return true;
    }

    private static List<MutableProfile> load(Context context) {
        List<MutableProfile> profiles = new ArrayList<>();
        if (context == null) return profiles;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PROFILES, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) continue;
                MutableProfile profile = MutableProfile.fromJson(json);
                if (!profile.id.isEmpty() && !profile.primaryAlias.isEmpty()
                        && !profile.aliases.isEmpty()) profiles.add(profile);
            }
        } catch (JSONException exception) {
            DiagnosticLog.warn(context, "project_profile", "registry_parse_failed",
                    "error", exception.getClass().getSimpleName());
        }
        return profiles;
    }

    private static void save(Context context, List<MutableProfile> profiles) {
        if (context == null) return;
        JSONArray array = new JSONArray();
        for (MutableProfile profile : profiles) array.put(profile.toJson());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PROFILES, array.toString()).apply();
    }

    private static MutableProfile findMutable(List<MutableProfile> profiles, String id) {
        String clean = id == null ? "" : id;
        for (MutableProfile profile : profiles) if (profile.id.equals(clean)) return profile;
        return null;
    }

    private static boolean contains(String[] values, String target) {
        if (target == null) return false;
        for (String value : values) if (value.equals(target)) return true;
        return false;
    }

    private static final class MutableProfile {
        final String id;
        final List<String> aliases = new ArrayList<>();
        String primaryAlias;
        String shortOverride;
        String iconKey;
        String colorKey;

        MutableProfile(String id, String primaryAlias, String shortOverride,
                String iconKey, String colorKey) {
            this.id = id == null ? "" : id;
            this.primaryAlias = ProjectProfileRules.collapseWhitespace(primaryAlias);
            this.shortOverride = ProjectProfileRules.collapseWhitespace(shortOverride);
            this.iconKey = contains(ICON_KEYS, iconKey) ? iconKey : "folder";
            this.colorKey = contains(COLOR_KEYS, colorKey) ? colorKey : COLOR_GRAY;
        }

        boolean hasAlias(String normalized) { return !aliasFor(normalized).isEmpty(); }

        String aliasFor(String normalized) {
            for (String alias : aliases) {
                if (ProjectProfileRules.normalizeAlias(alias).equals(normalized)) return alias;
            }
            return "";
        }

        Profile freeze() {
            return new Profile(id, aliases, primaryAlias, shortOverride, iconKey, colorKey);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("primary", primaryAlias);
                json.put("short_override", shortOverride);
                json.put("icon", iconKey);
                json.put("color", colorKey);
                JSONArray aliasArray = new JSONArray();
                for (String alias : aliases) aliasArray.put(alias);
                json.put("aliases", aliasArray);
            } catch (JSONException ignored) {}
            return json;
        }

        static MutableProfile fromJson(JSONObject json) {
            MutableProfile profile = new MutableProfile(
                    json.optString("id", ""), json.optString("primary", ""),
                    json.optString("short_override", ""), json.optString("icon", "folder"),
                    json.optString("color", COLOR_GRAY));
            JSONArray aliasArray = json.optJSONArray("aliases");
            if (aliasArray != null) {
                for (int i = 0; i < aliasArray.length(); i++) {
                    String alias = ProjectProfileRules.collapseWhitespace(aliasArray.optString(i, ""));
                    if (!alias.isEmpty() && profile.aliasFor(
                            ProjectProfileRules.normalizeAlias(alias)).isEmpty()) {
                        profile.aliases.add(alias);
                    }
                }
            }
            if (profile.aliases.isEmpty() && !profile.primaryAlias.isEmpty()) {
                profile.aliases.add(profile.primaryAlias);
            }
            String primary = profile.aliasFor(
                    ProjectProfileRules.normalizeAlias(profile.primaryAlias));
            if (!primary.isEmpty()) profile.primaryAlias = primary;
            return profile;
        }
    }
}
