package dev.kopandazavr.codexmonitor;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Durable per-role lifecycle state derived from calendar-backed watchdog processes. */
final class IdleProcessState {
    private static final String PREFS = "codex_idle_process_state_v1";
    private static final String KEY_ROWS = "rows_json";
    static final int DEFAULT_CADENCE_MINUTES = 5;

    private IdleProcessState() {
    }

    static final class SessionRecord {
        final String project;
        final String projectShort;
        final String role;
        final String topic;
        final String instanceId;
        final long eventId;
        final long startedMillis;
        final long finishedMillis;

        SessionRecord(String project, String projectShort, String role, String topic,
                String instanceId, long eventId, long startedMillis, long finishedMillis) {
            this.project = clean(project);
            this.projectShort = clean(projectShort);
            this.role = clean(role);
            this.topic = clean(topic);
            this.instanceId = clean(instanceId);
            this.eventId = eventId;
            this.startedMillis = Math.max(0L, Math.min(startedMillis, finishedMillis));
            this.finishedMillis = Math.max(0L, finishedMillis);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("project", project);
                json.put("project_short", projectShort);
                json.put("role", role);
                json.put("topic", topic);
                json.put("instance_id", instanceId);
                json.put("event_id", eventId);
                json.put("started", startedMillis);
                json.put("finished", finishedMillis);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static SessionRecord fromJson(JSONObject json) {
            return new SessionRecord(
                    json.optString("project", ""), json.optString("project_short", ""),
                    json.optString("role", ""), json.optString("topic", ""),
                    json.optString("instance_id", ""), json.optLong("event_id", 0L),
                    json.optLong("started", 0L),
                    json.optLong("finished", 0L));
        }
    }

    static final class IdleRole {
        final String key;
        final String project;
        final String projectShort;
        final String role;
        final String topic;
        final String instanceId;
        final long lastStartedMillis;
        final long lastFinishedMillis;
        final long eventId;
        final boolean reminderEnabled;
        final long nextReminderAtMillis;

        IdleRole(String key, String project, String projectShort, String role, String topic,
                String instanceId, long lastStartedMillis, long lastFinishedMillis, long eventId,
                boolean reminderEnabled, long nextReminderAtMillis) {
            this.key = clean(key);
            this.project = clean(project);
            this.projectShort = clean(projectShort);
            this.role = clean(role);
            this.topic = clean(topic);
            this.instanceId = clean(instanceId);
            this.lastStartedMillis = lastStartedMillis;
            this.lastFinishedMillis = lastFinishedMillis;
            this.eventId = eventId;
            this.reminderEnabled = reminderEnabled;
            this.nextReminderAtMillis = nextReminderAtMillis;
        }

        String displayLabel() {
            return CalendarProcess.displayIdentity(role, project, projectShort, topic);
        }

        String completionIdentity() {
            return instanceId.isEmpty() ? "legacy-event:" + eventId : "instance:" + instanceId;
        }
    }

    static List<IdleRole> synchronize(Context context, List<CalendarProcess> active,
            List<CalendarProcess> recentlyFinished, long nowMillis) {
        return synchronize(context, AccountContainerStore.selectedId(context),
                active, recentlyFinished, mergeObserved(active, recentlyFinished), nowMillis);
    }

    static List<IdleRole> synchronize(Context context, String containerId,
            List<CalendarProcess> active, List<CalendarProcess> recentlyFinished,
            long nowMillis) {
        return synchronize(context, containerId, active, recentlyFinished,
                mergeObserved(active, recentlyFinished), nowMillis);
    }

    /**
     * Synchronizes durable role state with every locally observed watchdog, not only active ones.
     * Recording future watchdog metadata here is what preserves role/topic continuity when a known
     * event is deleted before BEGIN and therefore never appears in the active list.
     */
    static List<IdleRole> synchronize(Context context, List<CalendarProcess> active,
            List<CalendarProcess> recentlyFinished, List<CalendarProcess> observed,
            long nowMillis) {
        return synchronize(context, AccountContainerStore.selectedId(context),
                active, recentlyFinished, observed, nowMillis);
    }

    static List<IdleRole> synchronize(Context context, String containerId,
            List<CalendarProcess> active, List<CalendarProcess> recentlyFinished,
            List<CalendarProcess> observed, long nowMillis) {
        if (context == null) return Collections.emptyList();
        Map<String, MutableRole> rows = load(context, containerId);
        Map<String, WatchdogInstanceState.PendingInstance> pending =
                WatchdogInstanceState.load(context, containerId);
        Set<String> activeRoleKeys = new HashSet<>();
        Set<String> activeInstanceKeys = new HashSet<>();

        // One-time bridge from the pre-2.38 single pending slot into the instance store.
        for (MutableRole row : rows.values()) {
            if (row.pendingEventId <= 0L || row.pendingDeadlineMillis <= 0L) continue;
            WatchdogInstanceState.PendingInstance legacy = WatchdogInstanceState.legacy(
                    row.key, row.project, row.projectShort, row.role, row.topic,
                    row.pendingEventId, row.pendingStartMillis, row.pendingDeadlineMillis,
                    row.pendingDirectSource);
            if (legacy != null && !pending.containsKey(legacy.storageKey)) {
                pending.put(legacy.storageKey, legacy);
            }
            clearLegacyPending(row);
        }

        if (observed != null) {
            for (CalendarProcess process : observed) {
                if (process == null || process.beginMillis <= nowMillis) continue;
                MutableRole row = rowForProcess(context, rows, process);
                if (row == null) continue;
                rememberObserved(row, process);
                WatchdogInstanceState.remember(pending, row.key, process);
            }
        }
        if (active != null) {
            for (CalendarProcess process : active) {
                MutableRole row = rowForProcess(context, rows, process);
                if (row == null) continue;
                rememberObserved(row, process);
                WatchdogInstanceState.remember(pending, row.key, process);
                activeRoleKeys.add(row.key);
                activeInstanceKeys.add(WatchdogInstanceState.storageKey(row.key, process));
            }
        }
        if (recentlyFinished != null) {
            for (CalendarProcess process : recentlyFinished) {
                if (process == null || process.beginMillis > nowMillis) continue;
                String roleKey = roleKey(context, process);
                String instanceKey = WatchdogInstanceState.storageKey(roleKey, process);
                WatchdogInstanceState.PendingInstance instance = pending.get(instanceKey);
                MutableRole row = rows.get(roleKey);
                if (row == null || instance == null || instance.deadlineMillis <= 0L) continue;
                promoteFinished(row, instance, instance.deadlineMillis, context);
                pending.remove(instanceKey);
            }
        }

        List<WatchdogInstanceState.PendingInstance> pendingSnapshot =
                new ArrayList<>(pending.values());
        for (WatchdogInstanceState.PendingInstance instance : pendingSnapshot) {
            if (instance == null || instance.deadlineMillis <= 0L
                    || activeInstanceKeys.contains(instance.storageKey)) continue;
            MutableRole row = rows.get(instance.roleKey);
            if (row == null) {
                pending.remove(instance.storageKey);
                continue;
            }
            boolean deadlineReached = instance.deadlineMillis <= nowMillis;
            boolean watchedEventDeleted = !deadlineReached && instance.eventId > 0L
                    && !CalendarProcessReader.eventExists(
                    context, containerId, instance.eventId, instance.directSource);
            if (!deadlineReached && !watchedEventDeleted) continue;
            long finishedAt = watchedEventDeleted ? nowMillis : instance.deadlineMillis;
            promoteFinished(row, instance, finishedAt, context);
            DiagnosticLog.info(context, "idle_process",
                    watchedEventDeleted ? "watchdog_deleted_before_deadline"
                            : "watchdog_deadline_reached",
                    "event_id", instance.eventId,
                    "instance_id", instance.instanceId,
                    "role", instance.role,
                    "deadline", instance.deadlineMillis,
                    "finished_at", finishedAt);
            pending.remove(instance.storageKey);
        }

        WatchdogInstanceState.save(context, containerId, pending);
        save(context, containerId, rows);
        List<IdleRole> visible = new ArrayList<>();
        for (MutableRole row : rows.values()) {
            if (activeRoleKeys.contains(row.key) || row.lastFinishedMillis <= 0L
                    || row.dismissedThroughMillis >= row.lastFinishedMillis) continue;
            visible.add(row.freeze());
        }
        visible.sort(Comparator.comparingLong((IdleRole row) -> row.lastFinishedMillis).reversed());
        return visible;
    }

    static IdleRole find(Context context, String key) {
        MutableRole row = load(context).get(clean(key));
        return row == null || row.lastFinishedMillis <= 0L ? null : row.freeze();
    }

    static IdleRole findCompletion(Context context, String key, String instanceId,
            long eventId, long finishedMillis) {
        MutableRole row = load(context).get(clean(key));
        if (row == null || finishedMillis <= 0L) return null;
        String wantedInstance = clean(instanceId);
        for (SessionRecord record : row.history) {
            boolean identityMatch = !wantedInstance.isEmpty()
                    ? wantedInstance.equals(record.instanceId)
                    : record.eventId == eventId;
            if (identityMatch && record.finishedMillis == finishedMillis) {
                return row.freeze(record);
            }
        }
        return null;
    }

    static List<IdleRole> recentCompletions(Context context, long nowMillis, long maxAgeMillis) {
        List<IdleRole> result = new ArrayList<>();
        if (context == null) return result;
        long cutoff = Math.max(0L, nowMillis - Math.max(0L, maxAgeMillis));
        for (MutableRole row : load(context).values()) {
            for (SessionRecord record : row.history) {
                if (record.finishedMillis < cutoff || record.finishedMillis > nowMillis) continue;
                result.add(row.freeze(record));
            }
        }
        result.sort(Comparator.comparingLong(
                (IdleRole item) -> item.lastFinishedMillis).reversed());
        return result;
    }

    static List<SessionRecord> history(Context context, String key) {
        MutableRole row = load(context).get(clean(key));
        if (row == null || row.history.isEmpty()) return Collections.emptyList();
        List<SessionRecord> newestFirst = new ArrayList<>(row.history);
        newestFirst.sort(Comparator.comparingLong(
                (SessionRecord record) -> record.finishedMillis).reversed());
        return Collections.unmodifiableList(newestFirst);
    }

    static boolean isReminderEnabled(Context context, String key) {
        MutableRole row = load(context).get(clean(key));
        return row != null && row.reminderEnabled;
    }

    static void dismiss(Context context, String key, long finishedMillis) {
        Map<String, MutableRole> rows = load(context);
        MutableRole row = rows.get(clean(key));
        if (row == null) return;
        row.dismissedThroughMillis = Math.max(row.dismissedThroughMillis, finishedMillis);
        save(context, rows);
    }

    static boolean toggleReminder(Context context, String key, long nowMillis) {
        Map<String, MutableRole> rows = load(context);
        MutableRole row = rows.get(clean(key));
        if (row == null) return false;
        row.reminderEnabled = !row.reminderEnabled;
        row.nextReminderAtMillis = row.reminderEnabled ? nowMillis + cadenceMillis(context) : 0L;
        save(context, rows);
        return row.reminderEnabled;
    }

    static void setNextReminderAt(Context context, String key, long whenMillis) {
        Map<String, MutableRole> rows = load(context);
        MutableRole row = rows.get(clean(key));
        if (row == null) return;
        row.nextReminderAtMillis = Math.max(0L, whenMillis);
        save(context, rows);
    }

    static int cadenceMinutes(Context context) {
        return DEFAULT_CADENCE_MINUTES;
    }

    static void setCadenceMinutes(Context context, int ignoredMinutes, long nowMillis) {
        Map<String, MutableRole> rows = load(context);
        long next = nowMillis + DEFAULT_CADENCE_MINUTES * 60_000L;
        for (MutableRole row : rows.values()) {
            if (row.reminderEnabled && row.lastFinishedMillis > 0L) row.nextReminderAtMillis = next;
        }
        save(context, rows);
    }

    static long cadenceMillis(Context context) {
        return DEFAULT_CADENCE_MINUTES * 60_000L;
    }

    static String roleKey(Context context, CalendarProcess process) {
        if (process == null
                || !CalendarProcess.isCanonicalIdentity(process.project, process.role)) return "";
        RoleProfileStore.Profile profile = RoleProfileStore.resolve(
                context, process.role, process.roleIcon);
        return profile == null ? roleKey(process) : profile.id;
    }

    /** Legacy 2.28 raw-role key, retained only for non-destructive migration. */
    static String roleKey(CalendarProcess process) {
        if (process == null
                || !CalendarProcess.isCanonicalIdentity(process.project, process.role)) return "";
        return "role:" + clean(process.role);
    }

    static boolean isRoleActive(Context context, List<CalendarProcess> active, String key) {
        if (active == null) return false;
        for (CalendarProcess process : active) {
            if (clean(key).equals(roleKey(context, process))) return true;
        }
        return false;
    }

    private static MutableRole rowForProcess(Context context, Map<String, MutableRole> rows,
            CalendarProcess process) {
        String stableKey = roleKey(context, process);
        if (stableKey.isEmpty()) return null;
        MutableRole target = rows.get(stableKey);
        if (target == null) target = new MutableRole(stableKey);
        List<String> migrations = new ArrayList<>();
        for (Map.Entry<String, MutableRole> entry : rows.entrySet()) {
            if (stableKey.equals(entry.getKey())) continue;
            RoleProfileStore.Profile profile =
                    RoleProfileStore.findByAlias(context, entry.getValue().role);
            if (profile != null && stableKey.equals(profile.id)) migrations.add(entry.getKey());
        }
        String directLegacy = roleKey(process);
        if (!directLegacy.isEmpty() && rows.containsKey(directLegacy)
                && !migrations.contains(directLegacy)) migrations.add(directLegacy);
        for (String oldKey : migrations) {
            MutableRole legacy = rows.remove(oldKey);
            if (legacy != null) mergeMutable(target, legacy);
        }
        target.key = stableKey;
        rows.put(stableKey, target);
        return target;
    }

    private static void mergeMutable(MutableRole target, MutableRole source) {
        if (target == null || source == null || target == source) return;
        target.dismissedThroughMillis = Math.max(target.dismissedThroughMillis,
                source.dismissedThroughMillis);
        target.reminderEnabled |= source.reminderEnabled;
        target.nextReminderAtMillis = Math.max(target.nextReminderAtMillis,
                source.nextReminderAtMillis);
        if (source.lastFinishedMillis > target.lastFinishedMillis) {
            target.project=source.project;target.projectShort=source.projectShort;
            target.role=source.role;target.topic=source.topic;
            target.lastStartedMillis=source.lastStartedMillis;
            target.lastFinishedMillis=source.lastFinishedMillis;target.eventId=source.eventId;
            target.lastInstanceId=source.lastInstanceId;
        }
        for (SessionRecord record : source.history) {
            boolean duplicate=false;
            for (SessionRecord existing : target.history) {
                boolean sameInstance = !record.instanceId.isEmpty()
                        ? record.instanceId.equals(existing.instanceId)
                        : existing.eventId == record.eventId;
                if (sameInstance && existing.finishedMillis==record.finishedMillis) {
                    duplicate=true;break;
                }
            }
            if(!duplicate)target.history.add(record);
        }
    }

    private static void clearLegacyPending(MutableRole row) {
        row.pendingStartMillis=0L;row.pendingDeadlineMillis=0L;row.pendingEventId=0L;
        row.pendingDirectSource=false;
    }

    private static void rememberObserved(MutableRole row, CalendarProcess process) {
        if (row == null || process == null) return;
        row.project = clean(process.project);
        row.projectShort = clean(process.projectShort);
        row.role = clean(process.role);
        row.topic = clean(process.topic);
    }

    private static List<CalendarProcess> mergeObserved(List<CalendarProcess> active,
            List<CalendarProcess> recentlyFinished) {
        List<CalendarProcess> merged = new ArrayList<>();
        if (active != null) merged.addAll(active);
        if (recentlyFinished != null) merged.addAll(recentlyFinished);
        return merged;
    }

    private static void promoteFinished(MutableRole row,
            WatchdogInstanceState.PendingInstance instance, long finishedMillis,
            Context context) {
        if (row == null || instance == null || finishedMillis <= 0L) return;
        appendHistory(row, instance.project, instance.projectShort, instance.role, instance.topic,
                instance.instanceId, instance.eventId, instance.startedMillis, finishedMillis);
        if (finishedMillis <= row.lastFinishedMillis) return;
        row.project = clean(instance.project);
        row.projectShort = clean(instance.projectShort);
        row.role = clean(instance.role);
        row.topic = clean(instance.topic);
        row.lastInstanceId = clean(instance.instanceId);
        row.eventId = instance.eventId;
        row.lastStartedMillis = Math.max(0L,
                Math.min(instance.startedMillis, finishedMillis));
        row.lastFinishedMillis = finishedMillis;
        row.nextReminderAtMillis = row.reminderEnabled
                ? finishedMillis + cadenceMillis(context) : 0L;
    }

    private static void appendHistory(MutableRole row, String project, String projectShort,
            String role, String topic, String instanceId, long eventId,
            long startedMillis, long finishedMillis) {
        if (row == null || finishedMillis <= 0L) return;
        String cleanInstance = clean(instanceId);
        for (SessionRecord record : row.history) {
            boolean sameInstance = !cleanInstance.isEmpty()
                    ? cleanInstance.equals(record.instanceId) : record.eventId == eventId;
            if (sameInstance && record.finishedMillis == finishedMillis) return;
        }
        row.history.add(new SessionRecord(project, projectShort, role, topic, cleanInstance,
                eventId, startedMillis, finishedMillis));
    }

    private static Map<String, MutableRole> load(Context context) {
        return load(context, AccountContainerStore.selectedId(context));
    }

    private static Map<String, MutableRole> load(Context context, String containerId) {
        Map<String, MutableRole> rows = new HashMap<>();
        if (context == null) return rows;
        android.content.SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String scopedKey = rowsKey(containerId);
        String raw = prefs.contains(scopedKey)
                ? prefs.getString(scopedKey, "[]")
                : AccountContainerStore.isLegacyOwner(context, containerId)
                        ? prefs.getString(KEY_ROWS, "[]") : "[]";
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) continue;
                MutableRole row = MutableRole.fromJson(json);
                if (row.key.isEmpty()) continue;
                if (!CalendarProcess.isCanonicalIdentity(row.project, row.role)) {
                    DiagnosticLog.warn(context, "idle_process",
                            "watchdog_idle_state_rejected",
                            "key", row.key,
                            "reason", "missing_project_or_role");
                    continue;
                }
                rows.put(row.key, row);
            }
        } catch (JSONException exception) {
            DiagnosticLog.warn(context, "idle_process", "state_parse_failed",
                    "error", exception.getClass().getSimpleName());
        }
        return rows;
    }

    private static void save(Context context, Map<String, MutableRole> rows) {
        save(context, AccountContainerStore.selectedId(context), rows);
    }

    private static void save(Context context, String containerId,
            Map<String, MutableRole> rows) {
        if (context == null) return;
        JSONArray array = new JSONArray();
        for (MutableRole row : rows.values()) array.put(row.toJson());
        android.content.SharedPreferences.Editor editor =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(rowsKey(containerId), array.toString());
        if (AccountContainerStore.isLegacyOwner(context, containerId)) {
            editor.remove(KEY_ROWS);
        }
        editor.apply();
    }

    private static String rowsKey(String containerId) {
        String id = containerId == null ? "" : containerId.trim();
        return KEY_ROWS + "::" + id.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MutableRole {
        String key;
        String project = "";
        String projectShort = "";
        String role = "";
        String topic = "";
        String lastInstanceId = "";
        long lastStartedMillis;
        long lastFinishedMillis;
        long eventId;
        long dismissedThroughMillis;
        boolean reminderEnabled;
        long nextReminderAtMillis;
        long pendingStartMillis;
        long pendingDeadlineMillis;
        long pendingEventId;
        boolean pendingDirectSource;
        final List<SessionRecord> history = new ArrayList<>();

        MutableRole(String key) { this.key = clean(key); }

        IdleRole freeze() {
            return new IdleRole(key, project, projectShort, role, topic, lastInstanceId,
                    lastStartedMillis, lastFinishedMillis, eventId,
                    reminderEnabled, nextReminderAtMillis);
        }

        IdleRole freeze(SessionRecord record) {
            return new IdleRole(key, record.project, record.projectShort, record.role,
                    record.topic, record.instanceId, record.startedMillis, record.finishedMillis,
                    record.eventId, reminderEnabled, nextReminderAtMillis);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("key", key);
                json.put("project", project);
                json.put("project_short", projectShort);
                json.put("role", role);
                json.put("topic", topic);
                json.put("last_instance_id", lastInstanceId);
                json.put("last_started", lastStartedMillis);
                json.put("last_finished", lastFinishedMillis);
                json.put("event_id", eventId);
                json.put("dismissed_through", dismissedThroughMillis);
                json.put("reminder", reminderEnabled);
                json.put("next_reminder", nextReminderAtMillis);
                json.put("pending_start", pendingStartMillis);
                json.put("pending_deadline", pendingDeadlineMillis);
                json.put("pending_event", pendingEventId);
                json.put("pending_direct", pendingDirectSource);
                JSONArray historyArray = new JSONArray();
                for (SessionRecord record : history) historyArray.put(record.toJson());
                json.put("history", historyArray);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static MutableRole fromJson(JSONObject json) {
            MutableRole row = new MutableRole(json.optString("key", ""));
            row.project = clean(json.optString("project", ""));
            row.projectShort = clean(json.optString("project_short", ""));
            row.role = clean(json.optString("role", ""));
            row.topic = clean(json.optString("topic", ""));
            row.lastInstanceId = clean(json.optString("last_instance_id", ""));
            row.lastStartedMillis = json.optLong("last_started", 0L);
            row.lastFinishedMillis = json.optLong("last_finished", 0L);
            row.eventId = json.optLong("event_id", 0L);
            row.dismissedThroughMillis = json.optLong("dismissed_through", 0L);
            row.reminderEnabled = json.optBoolean("reminder", false);
            row.nextReminderAtMillis = json.optLong("next_reminder", 0L);
            row.pendingStartMillis = json.optLong("pending_start", 0L);
            if (json.has("pending_deadline")) {
                row.pendingDeadlineMillis = json.optLong("pending_deadline", 0L);
            } else {
                long legacyCarrierEnd = json.optLong("pending_end", 0L);
                row.pendingDeadlineMillis = legacyCarrierEnd <= 0L ? 0L
                        : Math.max(row.pendingStartMillis, legacyCarrierEnd - 60_000L);
            }
            row.pendingEventId = json.optLong("pending_event", 0L);
            row.pendingDirectSource = json.optBoolean("pending_direct", false);
            JSONArray historyArray = json.optJSONArray("history");
            if (historyArray != null) {
                for (int i = 0; i < historyArray.length(); i++) {
                    JSONObject item = historyArray.optJSONObject(i);
                    if (item == null) continue;
                    SessionRecord record = SessionRecord.fromJson(item);
                    if (record.finishedMillis > 0L) row.history.add(record);
                }
            }
            if (row.history.isEmpty() && row.lastFinishedMillis > 0L) {
                // One-time non-destructive migration of the pre-2.28 latest-session fields.
                row.history.add(new SessionRecord(
                        row.project, row.projectShort, row.role, row.topic, row.lastInstanceId,
                        row.eventId, row.lastStartedMillis, row.lastFinishedMillis));
            }
            return row;
        }
    }
}
