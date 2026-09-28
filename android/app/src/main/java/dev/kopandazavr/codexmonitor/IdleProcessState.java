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
        final long eventId;
        final long startedMillis;
        final long finishedMillis;

        SessionRecord(String project, String projectShort, String role, String topic,
                long eventId, long startedMillis, long finishedMillis) {
            this.project = clean(project);
            this.projectShort = clean(projectShort);
            this.role = clean(role);
            this.topic = clean(topic);
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
                    json.optLong("event_id", 0L), json.optLong("started", 0L),
                    json.optLong("finished", 0L));
        }
    }

    static final class IdleRole {
        String key;
        final String project;
        final String projectShort;
        final String role;
        final String topic;
        final long lastStartedMillis;
        final long lastFinishedMillis;
        final long eventId;
        final boolean reminderEnabled;
        final long nextReminderAtMillis;

        IdleRole(String key, String project, String projectShort, String role, String topic,
                long lastStartedMillis, long lastFinishedMillis, long eventId,
                boolean reminderEnabled, long nextReminderAtMillis) {
            this.key = clean(key);
            this.project = clean(project);
            this.projectShort = clean(projectShort);
            this.role = clean(role);
            this.topic = clean(topic);
            this.lastStartedMillis = lastStartedMillis;
            this.lastFinishedMillis = lastFinishedMillis;
            this.eventId = eventId;
            this.reminderEnabled = reminderEnabled;
            this.nextReminderAtMillis = nextReminderAtMillis;
        }

        String displayLabel() {
            return CalendarProcess.displayIdentity(role, project, projectShort, topic);
        }
    }

    static List<IdleRole> synchronize(Context context, List<CalendarProcess> active,
            List<CalendarProcess> recentlyFinished, long nowMillis) {
        return synchronize(context, active, recentlyFinished,
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
        if (context == null) return Collections.emptyList();
        Map<String, MutableRole> rows = load(context);
        Set<String> activeKeys = new HashSet<>();

        if (observed != null) {
            for (CalendarProcess process : observed) {
                if (process == null || process.beginMillis <= nowMillis) continue;
                MutableRole row = rowForProcess(context, rows, process);
                if (row != null) rememberObserved(row, process);
            }
        }
        if (active != null) {
            for (CalendarProcess process : active) {
                MutableRole row = rowForProcess(context, rows, process);
                if (row == null) continue;
                activeKeys.add(row.key);
                rememberObserved(row, process);
            }
        }
        if (recentlyFinished != null) {
            for (CalendarProcess process : recentlyFinished) {
                if (process == null || process.beginMillis > nowMillis) continue;
                String key = roleKey(context, process);
                MutableRole row = rows.get(key);
                if (row == null || row.pendingEventId != process.eventId
                        || row.pendingDeadlineMillis <= 0L) continue;
                promoteFinished(row, process.project, process.projectShort, process.role,
                        process.topic, process.eventId, row.pendingStartMillis,
                        row.pendingDeadlineMillis, context);
                clearPending(row);
            }
        }
        for (MutableRole row : rows.values()) {
            if (activeKeys.contains(row.key) || row.pendingDeadlineMillis <= 0L) continue;
            boolean deadlineReached = row.pendingDeadlineMillis <= nowMillis;
            boolean watchedEventDeleted = !deadlineReached && row.pendingEventId > 0L
                    && !CalendarProcessReader.eventExists(
                    context, row.pendingEventId, row.pendingDirectSource);
            if (!deadlineReached && !watchedEventDeleted) continue;
            long finishedAt = watchedEventDeleted ? nowMillis : row.pendingDeadlineMillis;
            promoteFinished(row, row.project, row.projectShort, row.role, row.topic,
                    row.pendingEventId, row.pendingStartMillis, finishedAt, context);
            DiagnosticLog.info(context, "idle_process",
                    watchedEventDeleted ? "watchdog_deleted_before_deadline"
                            : "watchdog_deadline_reached",
                    "event_id", row.pendingEventId, "role", row.role,
                    "deadline", row.pendingDeadlineMillis, "finished_at", finishedAt);
            clearPending(row);
        }
        save(context, rows);
        List<IdleRole> visible = new ArrayList<>();
        for (MutableRole row : rows.values()) {
            if (activeKeys.contains(row.key) || row.lastFinishedMillis <= 0L
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
        RoleProfileStore.Profile profile = RoleProfileStore.resolve(context, process.role);
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
        }
        if (source.pendingDeadlineMillis > target.pendingDeadlineMillis) {
            target.pendingStartMillis=source.pendingStartMillis;
            target.pendingDeadlineMillis=source.pendingDeadlineMillis;
            target.pendingEventId=source.pendingEventId;
            target.pendingDirectSource=source.pendingDirectSource;
            target.project=source.project;target.projectShort=source.projectShort;
            target.role=source.role;target.topic=source.topic;
        }
        for (SessionRecord record : source.history) {
            boolean duplicate=false;
            for (SessionRecord existing : target.history) {
                if (existing.eventId==record.eventId
                        && existing.finishedMillis==record.finishedMillis){duplicate=true;break;}
            }
            if(!duplicate)target.history.add(record);
        }
    }

    private static void clearPending(MutableRole row) {
        row.pendingStartMillis=0L;row.pendingDeadlineMillis=0L;row.pendingEventId=0L;
        row.pendingDirectSource=false;
    }

    private static void rememberObserved(MutableRole row, CalendarProcess process) {
        if (row == null || process == null) return;
        if (row.pendingEventId != 0L && row.pendingEventId != process.eventId
                && process.beginMillis < row.pendingDeadlineMillis) return;
        row.pendingStartMillis = process.workStartMillis();
        row.pendingDeadlineMillis = process.beginMillis;
        row.pendingEventId = process.eventId;
        row.pendingDirectSource = process.directSource;
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

    private static void promoteFinished(MutableRole row, String project, String projectShort,
            String role, String topic, long eventId, long startedMillis, long finishedMillis,
            Context context) {
        if (finishedMillis <= row.lastFinishedMillis) return;
        appendHistory(row, project, projectShort, role, topic, eventId,
                startedMillis, finishedMillis);
        row.project = clean(project);
        row.projectShort = clean(projectShort);
        row.role = clean(role);
        row.topic = clean(topic);
        row.eventId = eventId;
        row.lastStartedMillis = Math.max(0L, Math.min(startedMillis, finishedMillis));
        row.lastFinishedMillis = finishedMillis;
        row.nextReminderAtMillis = row.reminderEnabled
                ? finishedMillis + cadenceMillis(context) : 0L;
    }

    private static void appendHistory(MutableRole row, String project, String projectShort,
            String role, String topic, long eventId, long startedMillis, long finishedMillis) {
        if (row == null || finishedMillis <= 0L) return;
        for (SessionRecord record : row.history) {
            if (record.eventId == eventId && record.finishedMillis == finishedMillis) return;
        }
        row.history.add(new SessionRecord(project, projectShort, role, topic, eventId,
                startedMillis, finishedMillis));
    }

    private static Map<String, MutableRole> load(Context context) {
        Map<String, MutableRole> rows = new HashMap<>();
        if (context == null) return rows;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROWS, "[]");
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
        if (context == null) return;
        JSONArray array = new JSONArray();
        for (MutableRole row : rows.values()) array.put(row.toJson());
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ROWS, array.toString()).apply();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MutableRole {
        final String key;
        String project = "";
        String projectShort = "";
        String role = "";
        String topic = "";
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
            return new IdleRole(key, project, projectShort, role, topic, lastStartedMillis,
                    lastFinishedMillis, eventId, reminderEnabled, nextReminderAtMillis);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("key", key);
                json.put("project", project);
                json.put("project_short", projectShort);
                json.put("role", role);
                json.put("topic", topic);
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
                        row.project, row.projectShort, row.role, row.topic, row.eventId,
                        row.lastStartedMillis, row.lastFinishedMillis));
            }
            return row;
        }
    }
}
