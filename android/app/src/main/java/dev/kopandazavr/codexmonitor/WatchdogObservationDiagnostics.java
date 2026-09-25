package dev.kopandazavr.codexmonitor;

import android.content.Context;
import android.content.SharedPreferences;

final class WatchdogObservationDiagnostics {
    private static final String PREFS = "codex_watchdog_observation_diagnostics_v1";
    private WatchdogObservationDiagnostics() {}

    static void logRejected(Context context, String category, long eventId, String source,
            String path, String title, String description, long beginMillis, long endMillis) {
        if (context == null) return;
        String reason = CalendarProcess.rejectionReason(title, description, beginMillis, endMillis);
        String marker = CalendarProcess.markerValueForDiagnostics(description);
        String project = CalendarProcess.metadataValueForDiagnostics(description, "project");
        String projectShort = CalendarProcess.metadataValueForDiagnostics(description, "project_short");
        String role = CalendarProcess.metadataValueForDiagnostics(description, "role");
        String topic = CalendarProcess.metadataValueForDiagnostics(description, "topic");
        String normalized = CalendarProcess.isCanonicalIdentity(project, role)
                ? CalendarProcess.displayIdentity(role, project, projectShort, topic) : "";
        DiagnosticLog.warn(context, category, "watchdog_rejected_metadata",
                "event_id", eventId, "source", source, "observation_path", path,
                "reason", reason,
                "marker_present", !marker.isEmpty(),
                "marker_valid", CalendarProcess.hasSupportedMarker(description),
                "project_present", !project.isEmpty(), "project", project,
                "role_present", !role.isEmpty(), "role", role,
                "project_short_present", !projectShort.isEmpty(), "project_short", projectShort,
                "topic_present", !topic.isEmpty(), "topic", topic,
                "normalized_identity", normalized, "result", "rejected:" + reason);
        MonitorHealthDiagnostics.recordStrictRejection(context, eventId, source, reason);
    }

    static void recordIdentity(Context context, long eventId, String source, String path,
            String description, CalendarProcess process) {
        if (context == null || process == null || eventId <= 0L) return;
        String marker = CalendarProcess.markerValueForDiagnostics(description);
        String project = CalendarProcess.metadataValueForDiagnostics(description, "project");
        String projectShort = CalendarProcess.metadataValueForDiagnostics(description, "project_short");
        String role = CalendarProcess.metadataValueForDiagnostics(description, "role");
        String topic = CalendarProcess.metadataValueForDiagnostics(description, "topic");
        String normalized = process.displayLabel();
        String identity = project + "\n" + projectShort + "\n" + role + "\n" + topic
                + "\n" + normalized;
        String key = "event_" + eventId;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String previous = p.getString(key, "");
        if (identity.equals(previous)) return;
        p.edit().putString(key, identity).apply();
        if (previous == null || previous.isEmpty()) return;
        String[] old = previous.split("\\n", -1);
        DiagnosticLog.warn(context, "calendar_process", "watchdog_identity_changed",
                "event_id", eventId, "source", source, "observation_path", path,
                "marker_present", !marker.isEmpty(),
                "marker_valid", CalendarProcess.hasSupportedMarker(description),
                "project_present", !project.isEmpty(), "project", project,
                "role_present", !role.isEmpty(), "role", role,
                "project_short_present", !projectShort.isEmpty(), "project_short", projectShort,
                "topic_present", !topic.isEmpty(), "topic", topic,
                "previous_normalized_identity", old.length >= 5 ? old[4] : "",
                "normalized_identity", normalized, "result", "identity_changed");
    }
}
