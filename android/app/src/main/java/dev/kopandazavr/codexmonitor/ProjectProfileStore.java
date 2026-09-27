package dev.kopandazavr.codexmonitor;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Durable local project identity registry. Never writes watchdog Calendar metadata. */
final class ProjectProfileStore {
    private static final String PREFS = "codex_project_profiles_v1";
    private static final String KEY_PROFILES = "profiles_json";
    private static final String KEY_ROUTES = "incoming_routes_json";

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
        final Set<String> calendarAliases;

        Profile(String id, List<String> aliases, String primaryAlias, String shortOverride,
                String iconKey, String colorKey, Set<String> calendarAliases) {
            this.id = id;
            this.aliases = Collections.unmodifiableList(new ArrayList<>(aliases));
            this.primaryAlias = primaryAlias;
            this.shortOverride = shortOverride;
            this.iconKey = iconKey;
            this.colorKey = colorKey;
            this.calendarAliases = Collections.unmodifiableSet(new HashSet<>(calendarAliases));
        }

        boolean isCalendarAlias(String alias) {
            return calendarAliases.contains(ProjectProfileRules.normalizeAlias(alias));
        }
    }

    static final class EditSession {
        private final String profileId;
        private final ProjectProfileEditState state;
        private final Map<String, String> externalOwners;
        private final Map<String, String> externalShortOwners;

        EditSession(String profileId, ProjectProfileEditState state,
                Map<String, String> externalOwners, Map<String, String> externalShortOwners) {
            this.profileId = profileId;
            this.state = state;
            this.externalOwners = new HashMap<>(externalOwners);
            this.externalShortOwners = new HashMap<>(externalShortOwners);
        }

        Profile profile() {
            return new Profile(profileId, state.aliases(), state.primaryAlias(),
                    state.shortOverride(), state.iconKey(), state.colorKey(),
                    state.calendarAliases());
        }

        boolean setAppearance(String iconKey, String colorKey) {
            if (!contains(ICON_KEYS, iconKey) || !contains(COLOR_KEYS, colorKey)) return false;
            state.setAppearance(iconKey, colorKey);
            return true;
        }

        void setShortOverride(String value) {
            state.setShortOverride(value);
        }

        String shortOverrideError() {
            String normalized = ProjectProfileRules.normalizeShort(state.shortOverride());
            if (normalized.isEmpty()) return "";
            String owner = externalShortOwners.get(normalized);
            return owner == null || owner.isEmpty()
                    ? "" : "That Short Name already belongs to " + owner + ".";
        }

        boolean isCalendarAlias(String alias) { return state.isCalendarAlias(alias); }
        String addAlias(String alias) { return state.addAlias(alias, externalOwners); }
        String editAlias(String alias, String replacement) {
            return state.editAlias(alias, replacement, externalOwners);
        }
        String makePrimary(String alias) { return state.makePrimary(alias); }
        String deleteAlias(String alias) { return state.deleteAlias(alias); }
        String commit(Context context) { return commitEdit(context, this); }
    }

    private ProjectProfileStore() {}

    static synchronized Profile resolve(Context context, String incomingProject) {
        String raw = ProjectProfileRules.collapseWhitespace(incomingProject);
        if (raw.isEmpty()) return null;
        String normalized = ProjectProfileRules.normalizeAlias(raw);
        List<MutableProfile> profiles = loadAndSeed(context);
        Map<String, String> routes = loadRoutes(context);

        MutableProfile aliasOwner = findAliasOwner(profiles, normalized);
        MutableProfile recovered = legacySeedTarget(profiles, aliasOwner, normalized);
        if (recovered != null) {
            String orphanId = aliasOwner.id;
            profiles.remove(aliasOwner);
            removeRoutesOwnedBy(routes, orphanId);
            if (!recovered.hasAlias(normalized)) recovered.aliases.add(raw);
            recovered.calendarAliases.add(normalized);
            routes.put(normalized, recovered.id);
            save(context, profiles, routes);
            DiagnosticLog.info(context, "project_profile", "legacy_orphan_reconciled",
                    "alias", raw, "profile_id", recovered.id);
            return recovered.freeze();
        }

        if (aliasOwner != null) {
            boolean changed = aliasOwner.calendarAliases.add(normalized);
            if (!aliasOwner.id.equals(routes.get(normalized))) {
                routes.put(normalized, aliasOwner.id);
                changed = true;
            }
            if (changed) save(context, profiles, routes);
            return aliasOwner.freeze();
        }

        MutableProfile routed = findMutable(profiles, routes.get(normalized));
        if (routed != null) {
            boolean changed = false;
            if (!routed.hasAlias(normalized)) {
                routed.aliases.add(raw);
                changed = true;
            }
            changed |= routed.calendarAliases.add(normalized);
            if (changed) save(context, profiles, routes);
            return routed.freeze();
        }
        routes.remove(normalized);

        MutableProfile created = new MutableProfile(
                "project:" + UUID.randomUUID(), raw, "", "folder", COLOR_GRAY);
        created.aliases.add(raw);
        created.calendarAliases.add(normalized);
        profiles.add(created);
        routes.put(normalized, created.id);
        save(context, profiles, routes);
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

    static String effectiveShort(Profile profile) {
        return profile == null ? "" : ProjectProfileRules.effectiveShort(
                profile.shortOverride, "", profile.primaryAlias);
    }

    static synchronized EditSession beginEdit(Context context, String id) {
        List<MutableProfile> profiles = loadAndSeed(context);
        MutableProfile target = findMutable(profiles, id);
        if (target == null) return null;
        ProjectProfileEditState state = new ProjectProfileEditState(
                target.id, target.aliases, target.primaryAlias, target.shortOverride,
                target.iconKey, target.colorKey, target.calendarAliases);
        Map<String, String> routes = loadRoutes(context);
        Map<String, String> externalOwners = externalAliasOwners(profiles, target.id, true);
        omitReclaimableStaleGhostOwners(externalOwners, profiles, routes, target.id);
        return new EditSession(target.id, state, externalOwners,
                externalShortOwners(profiles, target.id));
    }

    private static synchronized String commitEdit(Context context, EditSession session) {
        if (session == null || !session.state.ownsPrimary() || session.state.aliases().isEmpty()) {
            return "Project profile is incomplete.";
        }
        if (!contains(ICON_KEYS, session.state.iconKey())
                || !contains(COLOR_KEYS, session.state.colorKey())) {
            return "Project appearance is invalid.";
        }
        List<MutableProfile> profiles = loadAndSeed(context);
        MutableProfile target = findMutable(profiles, session.profileId);
        if (target == null) return "Project profile not found.";
        Map<String, String> currentOwners =
                externalAliasOwners(profiles, session.profileId, false);
        Map<String, String> routes = loadRoutes(context);
        String shortKey = ProjectProfileRules.normalizeShort(session.state.shortOverride());
        if (!shortKey.isEmpty()) {
            String shortOwner = externalShortOwners(profiles, session.profileId).get(shortKey);
            if (shortOwner != null && !shortOwner.isEmpty()) {
                return "That Short Name already belongs to " + shortOwner + ".";
            }
        }
        for (String alias : session.state.aliases()) {
            String normalized = ProjectProfileRules.normalizeAlias(alias);
            String owner = currentOwners.get(normalized);
            if (owner == null || owner.isEmpty()) continue;

            MutableProfile externalOwner = findAliasOwner(profiles, normalized);
            if (!isReclaimableStaleGhost(externalOwner, routes, normalized)) {
                return "That alias already belongs to " + owner + ".";
            }

            String orphanId = externalOwner.id;
            profiles.remove(externalOwner);
            removeRoutesOwnedBy(routes, orphanId);
            routes.put(normalized, session.profileId);
            DiagnosticLog.info(context, "project_profile", "legacy_orphan_reclaimed",
                    "alias", alias, "profile_id", session.profileId);
        }
        MutableProfile replacement = new MutableProfile(
                session.profileId, session.state.primaryAlias(), session.state.shortOverride(),
                session.state.iconKey(), session.state.colorKey());
        replacement.aliases.addAll(session.state.aliases());
        replacement.calendarAliases.addAll(session.state.calendarAliases());
        for (String calendarAlias : target.calendarAliases) {
            if (replacement.hasAlias(calendarAlias)) replacement.calendarAliases.add(calendarAlias);
        }
        int index = profiles.indexOf(target);
        profiles.set(index, replacement);
        save(context, profiles, routes);
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

    static int surfaceTintColor(Profile profile) {
        // Opaque same-hue identity surface at roughly 40% of the bright outline intensity.
        int accent = accentColor(profile);
        int red = Math.round(((accent >> 16) & 0xFF) * 0.40f);
        int green = Math.round(((accent >> 8) & 0xFF) * 0.40f);
        int blue = Math.round((accent & 0xFF) * 0.40f);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    static int discColor(Profile profile) { return surfaceTintColor(profile); }

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

    private static Map<String, String> loadRoutes(Context context) {
        Map<String, String> routes = new HashMap<>();
        if (context == null) return routes;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ROUTES, "{}");
        try {
            JSONObject json = new JSONObject(raw == null ? "{}" : raw);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String rawKey = keys.next();
                String key = ProjectProfileRules.normalizeAlias(rawKey);
                String id = json.optString(rawKey, "");
                if (!key.isEmpty() && !id.isEmpty()) routes.put(key, id);
            }
        } catch (JSONException exception) {
            DiagnosticLog.warn(context, "project_profile", "routes_parse_failed",
                    "error", exception.getClass().getSimpleName());
        }
        return routes;
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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PROFILES, profilesJson(profiles)).apply();
    }

    private static void save(Context context, List<MutableProfile> profiles,
            Map<String, String> routes) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROFILES, profilesJson(profiles))
                .putString(KEY_ROUTES, routesJson(routes))
                .apply();
    }

    private static String profilesJson(List<MutableProfile> profiles) {
        JSONArray array = new JSONArray();
        for (MutableProfile profile : profiles) array.put(profile.toJson());
        return array.toString();
    }

    private static String routesJson(Map<String, String> routes) {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> entry : routes.entrySet()) {
            try { json.put(entry.getKey(), entry.getValue()); } catch (JSONException ignored) {}
        }
        return json.toString();
    }

    private static void bindRoute(Context context, Map<String, String> routes,
            String normalized, String profileId) {
        if (profileId.equals(routes.get(normalized))) return;
        routes.put(normalized, profileId);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ROUTES, routesJson(routes)).apply();
    }

    private static Map<String, String> externalShortOwners(
            List<MutableProfile> profiles, String excludedId) {
        Map<String, String> owners = new HashMap<>();
        for (MutableProfile profile : profiles) {
            if (profile.id.equals(excludedId)) continue;
            String normalized = ProjectProfileRules.normalizeShort(profile.shortOverride);
            if (!normalized.isEmpty()) owners.put(normalized, profile.primaryAlias);
        }
        return owners;
    }

    private static void omitReclaimableStaleGhostOwners(Map<String, String> owners,
            List<MutableProfile> profiles, Map<String, String> routes, String excludedId) {
        for (MutableProfile profile : profiles) {
            if (profile.id.equals(excludedId)) continue;
            for (String alias : profile.aliases) {
                String normalized = ProjectProfileRules.normalizeAlias(alias);
                if (isReclaimableStaleGhost(profile, routes, normalized)) {
                    owners.remove(normalized);
                }
            }
        }
    }

    private static Map<String, String> externalAliasOwners(
            List<MutableProfile> profiles, String excludedId,
            boolean omitReclaimableLegacyOrphans) {
        Map<String, String> owners = new HashMap<>();
        for (MutableProfile profile : profiles) {
            if (profile.id.equals(excludedId)) continue;
            for (String alias : profile.aliases) {
                String normalized = ProjectProfileRules.normalizeAlias(alias);
                if (omitReclaimableLegacyOrphans
                        && isReclaimableLegacyOrphan(profile, normalized)) {
                    continue;
                }
                owners.put(normalized, profile.primaryAlias);
            }
        }
        return owners;
    }

    private static void removeRoutesOwnedBy(Map<String, String> routes, String profileId) {
        Iterator<Map.Entry<String, String>> iterator = routes.entrySet().iterator();
        while (iterator.hasNext()) {
            if (profileId.equals(iterator.next().getValue())) iterator.remove();
        }
    }

    private static boolean isReclaimableStaleGhost(
            MutableProfile profile, Map<String, String> routes, String normalized) {
        if (profile == null) return false;
        boolean hasStableRoute = false;
        for (String ownerId : routes.values()) {
            if (profile.id.equals(ownerId)) {
                hasStableRoute = true;
                break;
            }
        }
        boolean calendarObserved = !profile.calendarAliases.isEmpty();
        if (hasStableRoute || calendarObserved) return false;
        return isReclaimableLegacyOrphan(profile, normalized)
                || ProjectProfileRules.isReclaimableUnroutedLegacyGhost(
                profile.id, profile.aliases, profile.primaryAlias, profile.shortOverride,
                profile.iconKey, profile.colorKey, normalized, false, false);
    }

    private static MutableProfile findAliasOwner(
            List<MutableProfile> profiles, String normalized) {
        for (MutableProfile profile : profiles) if (profile.hasAlias(normalized)) return profile;
        return null;
    }

    private static MutableProfile legacySeedTarget(List<MutableProfile> profiles,
            MutableProfile owner, String normalized) {
        if (!isReclaimableLegacyOrphan(owner, normalized)) return null;
        String seedId = seedIdForCanonicalAlias(normalized);
        if (seedId.isEmpty()) return null;
        MutableProfile seed = findMutable(profiles, seedId);
        return seed == owner ? null : seed;
    }

    private static boolean isReclaimableLegacyOrphan(
            MutableProfile profile, String normalized) {
        return profile != null && ProjectProfileRules.isReclaimableLegacyOrphan(
                profile.id, profile.aliases, profile.primaryAlias, profile.shortOverride,
                profile.iconKey, profile.colorKey, normalized);
    }

    private static String seedIdForCanonicalAlias(String normalized) {
        if (ProjectProfileRules.normalizeAlias("Data Matrix").equals(normalized)) return "seed:data-matrix";
        if (ProjectProfileRules.normalizeAlias("Codex Monitor").equals(normalized)) return "seed:codex-monitor";
        if (ProjectProfileRules.normalizeAlias("Заказы сигарет").equals(normalized)) return "seed:orders-cigarettes";
        if (ProjectProfileRules.normalizeAlias("Mira Technical").equals(normalized)) return "seed:mira-technical";
        if (ProjectProfileRules.normalizeAlias("Написание книг про ии будущего").equals(normalized)) return "seed:ai-books-future";
        if (ProjectProfileRules.normalizeAlias("Mira Universe").equals(normalized)) return "seed:mira-universe";
        return "";
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
        final Set<String> calendarAliases = new HashSet<>();

        MutableProfile(String id, String primaryAlias, String shortOverride,
                String iconKey, String colorKey) {
            this.id = id == null ? "" : id;
            this.primaryAlias = ProjectProfileRules.collapseWhitespace(primaryAlias);
            this.shortOverride = ProjectProfileRules.normalizeShort(shortOverride);
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
            return new Profile(id, aliases, primaryAlias, shortOverride, iconKey, colorKey,
                    calendarAliases);
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
                JSONArray calendarArray = new JSONArray();
                for (String alias : calendarAliases) calendarArray.put(alias);
                json.put("calendar_aliases", calendarArray);
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
            JSONArray calendarArray = json.optJSONArray("calendar_aliases");
            if (calendarArray != null) {
                for (int i = 0; i < calendarArray.length(); i++) {
                    String normalized = ProjectProfileRules.normalizeAlias(
                            calendarArray.optString(i, ""));
                    if (!normalized.isEmpty() && !profile.aliasFor(normalized).isEmpty()) {
                        profile.calendarAliases.add(normalized);
                    }
                }
            }
            String primary = profile.aliasFor(
                    ProjectProfileRules.normalizeAlias(profile.primaryAlias));
            if (!primary.isEmpty()) profile.primaryAlias = primary;
            return profile;
        }
    }
}
