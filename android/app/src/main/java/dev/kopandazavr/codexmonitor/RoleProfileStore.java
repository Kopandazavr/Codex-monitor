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

/** Durable local role identity registry, independent from any one Calendar/display alias. */
final class RoleProfileStore {
    private static final String PREFS = "codex_role_profiles_v1";
    private static final String KEY_PROFILES = "profiles_json";
    private static final String KEY_ROUTES = "incoming_routes_json";

    static final class Profile {
        final String id;
        final List<String> aliases;
        final String primaryAlias;
        final Set<String> calendarAliases;

        Profile(String id, List<String> aliases, String primaryAlias, Set<String> calendarAliases) {
            this.id = id;
            this.aliases = Collections.unmodifiableList(new ArrayList<>(aliases));
            this.primaryAlias = primaryAlias;
            this.calendarAliases = Collections.unmodifiableSet(new HashSet<>(calendarAliases));
        }
        boolean isCalendarAlias(String alias) {
            return calendarAliases.contains(ProjectProfileRules.normalizeAlias(alias));
        }
    }

    static final class EditSession {
        private final String profileId;
        private final RoleProfileEditState state;
        private final Map<String,String> externalOwners;
        EditSession(String profileId, RoleProfileEditState state, Map<String,String> externalOwners) {
            this.profileId = profileId;
            this.state = state;
            this.externalOwners = new HashMap<>(externalOwners);
        }
        Profile profile() {
            return new Profile(profileId, state.aliases(), state.primaryAlias(), state.calendarAliases());
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

    private RoleProfileStore() {}

    static synchronized Profile resolve(Context context, String incomingRole) {
        String raw = ProjectProfileRules.collapseWhitespace(incomingRole);
        if (context == null || raw.isEmpty()) return null;
        String normalized = ProjectProfileRules.normalizeAlias(raw);
        List<MutableProfile> profiles = load(context);
        Map<String,String> routes = loadRoutes(context);

        MutableProfile aliasOwner = findAliasOwner(profiles, normalized);
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

        MutableProfile created = new MutableProfile("role-profile:" + UUID.randomUUID(), raw);
        created.aliases.add(raw);
        created.calendarAliases.add(normalized);
        profiles.add(created);
        routes.put(normalized, created.id);
        save(context, profiles, routes);
        return created.freeze();
    }

    static synchronized Profile findById(Context context, String id) {
        if (context == null) return null;
        MutableProfile profile = findMutable(load(context), id);
        return profile == null ? null : profile.freeze();
    }

    /** Exact local alias lookup without changing Calendar provenance. */
    static synchronized Profile findByAlias(Context context, String alias) {
        if (context == null) return null;
        MutableProfile profile = findAliasOwner(load(context), ProjectProfileRules.normalizeAlias(alias));
        return profile == null ? null : profile.freeze();
    }

    static String displayName(Context context, String incomingRole) {
        Profile profile = resolve(context, incomingRole);
        return profile == null || profile.primaryAlias.isEmpty()
                ? ProjectProfileRules.collapseWhitespace(incomingRole) : profile.primaryAlias;
    }

    static String displayNameById(Context context, String profileId, String fallbackRole) {
        Profile profile = findById(context, profileId);
        if (profile != null && !profile.primaryAlias.isEmpty()) return profile.primaryAlias;
        return displayName(context, fallbackRole);
    }

    static synchronized EditSession beginEdit(Context context, String id) {
        if (context == null) return null;
        List<MutableProfile> profiles = load(context);
        MutableProfile target = findMutable(profiles, id);
        if (target == null) return null;
        return new EditSession(target.id,
                new RoleProfileEditState(target.id, target.aliases, target.primaryAlias,
                        target.calendarAliases),
                externalOwners(profiles, target.id));
    }

    private static synchronized String commitEdit(Context context, EditSession session) {
        if (context == null || session == null || !session.state.ownsPrimary()
                || session.state.aliases().isEmpty()) return "Role profile is incomplete.";
        List<MutableProfile> profiles = load(context);
        MutableProfile target = findMutable(profiles, session.profileId);
        if (target == null) return "Role profile not found.";
        Map<String,String> owners = externalOwners(profiles, session.profileId);
        for (String alias : session.state.aliases()) {
            String owner = owners.get(ProjectProfileRules.normalizeAlias(alias));
            if (owner != null && !owner.isEmpty()) return "That alias already belongs to " + owner + ".";
        }

        MutableProfile replacement = new MutableProfile(session.profileId, session.state.primaryAlias());
        replacement.aliases.addAll(session.state.aliases());
        replacement.calendarAliases.addAll(session.state.calendarAliases());
        for (String calendarAlias : target.calendarAliases) {
            if (replacement.hasAlias(calendarAlias)) replacement.calendarAliases.add(calendarAlias);
        }
        profiles.set(profiles.indexOf(target), replacement);

        Map<String,String> routes = loadRoutes(context);
        Set<String> kept = new HashSet<>();
        for (String alias : replacement.aliases) kept.add(ProjectProfileRules.normalizeAlias(alias));
        Iterator<Map.Entry<String,String>> iterator = routes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String,String> entry = iterator.next();
            if (session.profileId.equals(entry.getValue()) && !kept.contains(entry.getKey())) {
                iterator.remove();
            }
        }
        for (String alias : replacement.calendarAliases) routes.put(alias, replacement.id);
        save(context, profiles, routes);
        return "";
    }

    private static Map<String,String> externalOwners(List<MutableProfile> profiles, String excludedId) {
        Map<String,String> owners = new HashMap<>();
        for (MutableProfile profile : profiles) {
            if (profile.id.equals(excludedId)) continue;
            for (String alias : profile.aliases) {
                owners.put(ProjectProfileRules.normalizeAlias(alias), profile.primaryAlias);
            }
        }
        return owners;
    }

    private static List<MutableProfile> load(Context context) {
        List<MutableProfile> profiles = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PROFILES, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i=0;i<array.length();i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) continue;
                MutableProfile profile = MutableProfile.fromJson(json);
                if (!profile.id.isEmpty() && !profile.primaryAlias.isEmpty()
                        && !profile.aliases.isEmpty()) profiles.add(profile);
            }
        } catch (JSONException exception) {
            DiagnosticLog.warn(context, "role_profile", "registry_parse_failed",
                    "error", exception.getClass().getSimpleName());
        }
        return profiles;
    }

    private static Map<String,String> loadRoutes(Context context) {
        Map<String,String> routes = new HashMap<>();
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
            DiagnosticLog.warn(context, "role_profile", "routes_parse_failed",
                    "error", exception.getClass().getSimpleName());
        }
        return routes;
    }

    private static void save(Context context, List<MutableProfile> profiles, Map<String,String> routes) {
        JSONArray profileArray = new JSONArray();
        for (MutableProfile profile : profiles) profileArray.put(profile.toJson());
        JSONObject routeJson = new JSONObject();
        for (Map.Entry<String,String> entry : routes.entrySet()) {
            try { routeJson.put(entry.getKey(), entry.getValue()); } catch (JSONException ignored) {}
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PROFILES, profileArray.toString())
                .putString(KEY_ROUTES, routeJson.toString()).apply();
    }

    private static MutableProfile findAliasOwner(List<MutableProfile> profiles, String normalized) {
        if (normalized == null || normalized.isEmpty()) return null;
        for (MutableProfile profile : profiles) if (profile.hasAlias(normalized)) return profile;
        return null;
    }

    private static MutableProfile findMutable(List<MutableProfile> profiles, String id) {
        if (id == null || id.isEmpty()) return null;
        for (MutableProfile profile : profiles) if (profile.id.equals(id)) return profile;
        return null;
    }

    private static final class MutableProfile {
        final String id;
        final List<String> aliases = new ArrayList<>();
        final Set<String> calendarAliases = new HashSet<>();
        String primaryAlias;

        MutableProfile(String id, String primaryAlias) {
            this.id = id == null ? "" : id;
            this.primaryAlias = ProjectProfileRules.collapseWhitespace(primaryAlias);
        }
        boolean hasAlias(String normalized) {
            for (String alias : aliases) {
                if (ProjectProfileRules.normalizeAlias(alias).equals(normalized)) return true;
            }
            return false;
        }
        Profile freeze() { return new Profile(id, aliases, primaryAlias, calendarAliases); }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("primary", primaryAlias);
                JSONArray a = new JSONArray(); for (String alias : aliases) a.put(alias);
                json.put("aliases", a);
                JSONArray c = new JSONArray(); for (String alias : calendarAliases) c.put(alias);
                json.put("calendar_aliases", c);
            } catch (JSONException ignored) {}
            return json;
        }

        static MutableProfile fromJson(JSONObject json) {
            MutableProfile p = new MutableProfile(json.optString("id",""), json.optString("primary",""));
            JSONArray aliases = json.optJSONArray("aliases");
            if (aliases != null) {
                for (int i=0;i<aliases.length();i++) {
                    String alias = ProjectProfileRules.collapseWhitespace(aliases.optString(i,""));
                    if (!alias.isEmpty() && !p.hasAlias(ProjectProfileRules.normalizeAlias(alias))) {
                        p.aliases.add(alias);
                    }
                }
            }
            if (p.aliases.isEmpty() && !p.primaryAlias.isEmpty()) p.aliases.add(p.primaryAlias);
            String primaryKey = ProjectProfileRules.normalizeAlias(p.primaryAlias);
            String canonical = "";
            for (String alias : p.aliases) {
                if (ProjectProfileRules.normalizeAlias(alias).equals(primaryKey)) {
                    canonical = alias; break;
                }
            }
            p.primaryAlias = canonical.isEmpty() && !p.aliases.isEmpty() ? p.aliases.get(0) : canonical;
            JSONArray calendar = json.optJSONArray("calendar_aliases");
            if (calendar != null) {
                for (int i=0;i<calendar.length();i++) {
                    String key = ProjectProfileRules.normalizeAlias(calendar.optString(i,""));
                    if (!key.isEmpty() && p.hasAlias(key)) p.calendarAliases.add(key);
                }
            }
            return p;
        }
    }
}
