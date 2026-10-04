package dev.kopandazavr.codexmonitor;

import android.content.Context;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Durable pending watchdog instances, separated from role-level settings/history. */
final class WatchdogInstanceState {
    private static final String PREFS = "codex_watchdog_instances_v1";
    private static final String KEY_PENDING = "pending_json";

    static final class PendingInstance {
        final String storageKey;
        final String roleKey;
        final String instanceIdentity;
        final String instanceId;
        final String project;
        final String projectShort;
        final String role;
        final String topic;
        final long eventId;
        final long startedMillis;
        final long deadlineMillis;
        final boolean directSource;

        PendingInstance(String storageKey, String roleKey, String instanceIdentity,
                String instanceId, String project, String projectShort, String role, String topic,
                long eventId, long startedMillis, long deadlineMillis, boolean directSource) {
            this.storageKey = clean(storageKey);
            this.roleKey = clean(roleKey);
            this.instanceIdentity = clean(instanceIdentity);
            this.instanceId = clean(instanceId);
            this.project = clean(project);
            this.projectShort = clean(projectShort);
            this.role = clean(role);
            this.topic = clean(topic);
            this.eventId = eventId;
            this.startedMillis = Math.max(0L, startedMillis);
            this.deadlineMillis = Math.max(0L, deadlineMillis);
            this.directSource = directSource;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("storage_key", storageKey);
                json.put("role_key", roleKey);
                json.put("instance_identity", instanceIdentity);
                json.put("instance_id", instanceId);
                json.put("project", project);
                json.put("project_short", projectShort);
                json.put("role", role);
                json.put("topic", topic);
                json.put("event_id", eventId);
                json.put("started", startedMillis);
                json.put("deadline", deadlineMillis);
                json.put("direct", directSource);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static PendingInstance fromJson(JSONObject json) {
            if (json == null) return null;
            String storageKey = clean(json.optString("storage_key", ""));
            String roleKey = clean(json.optString("role_key", ""));
            String identity = clean(json.optString("instance_identity", ""));
            if (storageKey.isEmpty() || roleKey.isEmpty() || identity.isEmpty()) return null;
            return new PendingInstance(storageKey, roleKey, identity,
                    json.optString("instance_id", ""),
                    json.optString("project", ""), json.optString("project_short", ""),
                    json.optString("role", ""), json.optString("topic", ""),
                    json.optLong("event_id", 0L), json.optLong("started", 0L),
                    json.optLong("deadline", 0L), json.optBoolean("direct", false));
        }
    }

    private WatchdogInstanceState() {
    }

    static String storageKey(String roleKey, CalendarProcess process) {
        if (process == null) return "";
        return storageKey(roleKey, process.instanceIdentity());
    }

    static String storageKey(String roleKey, String instanceIdentity) {
        String role = clean(roleKey);
        String instance = clean(instanceIdentity);
        return role.isEmpty() || instance.isEmpty() ? "" : role + "\u001f" + instance;
    }

    static PendingInstance fromProcess(String roleKey, CalendarProcess process) {
        if (process == null) return null;
        String identity = process.instanceIdentity();
        String key = storageKey(roleKey, identity);
        if (key.isEmpty()) return null;
        return new PendingInstance(key, roleKey, identity, process.instanceId,
                process.project, process.projectShort, process.role, process.topic,
                process.eventId, process.workStartMillis(), process.beginMillis,
                process.directSource);
    }

    static PendingInstance legacy(String roleKey, String project, String projectShort,
            String role, String topic, long eventId, long startedMillis, long deadlineMillis,
            boolean directSource) {
        if (eventId <= 0L || clean(roleKey).isEmpty()) return null;
        String identity = "legacy-event:" + eventId;
        String key = storageKey(roleKey, identity);
        return new PendingInstance(key, roleKey, identity, "", project, projectShort, role, topic,
                eventId, startedMillis, deadlineMillis, directSource);
    }

    static void remember(Map<String, PendingInstance> pending, String roleKey,
            CalendarProcess process) {
        if (pending == null) return;
        PendingInstance value = fromProcess(roleKey, process);
        if (value != null) pending.put(value.storageKey, value);
    }

    static Map<String, PendingInstance> load(Context context) {
        return load(context, AccountContainerStore.selectedId(context));
    }

    static Map<String, PendingInstance> load(Context context, String containerId) {
        Map<String, PendingInstance> pending = new LinkedHashMap<>();
        if (context == null) return pending;
        android.content.SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String scoped = key(containerId);
        String raw = prefs.contains(scoped)
                ? prefs.getString(scoped, "[]")
                : AccountContainerStore.isLegacyOwner(context, containerId)
                        ? prefs.getString(KEY_PENDING, "[]") : "[]";
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                PendingInstance item = PendingInstance.fromJson(array.optJSONObject(i));
                if (item != null && item.deadlineMillis > 0L) {
                    pending.put(item.storageKey, item);
                }
            }
        } catch (JSONException exception) {
            DiagnosticLog.warn(context, "idle_process", "instance_state_parse_failed",
                    "error", exception.getClass().getSimpleName());
        }
        return pending;
    }

    static void save(Context context, Map<String, PendingInstance> pending) {
        save(context, AccountContainerStore.selectedId(context), pending);
    }

    static void save(Context context, String containerId,
            Map<String, PendingInstance> pending) {
        if (context == null) return;
        JSONArray array = new JSONArray();
        if (pending != null) {
            for (PendingInstance item : pending.values()) {
                if (item != null && item.deadlineMillis > 0L) array.put(item.toJson());
            }
        }
        android.content.SharedPreferences.Editor editor =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(key(containerId), array.toString());
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            editor.remove(KEY_PENDING);
        }
        editor.apply();
    }

    static void clearContainer(Context context, String containerId) {
        if (context == null || containerId == null || containerId.trim().isEmpty()) return;
        android.content.SharedPreferences.Editor editor =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .remove(key(containerId));
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            editor.remove(KEY_PENDING);
        }
        editor.apply();
    }

    private static String key(String containerId) {
        String id = containerId == null ? "" : containerId.trim();
        return KEY_PENDING + "::" + id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
