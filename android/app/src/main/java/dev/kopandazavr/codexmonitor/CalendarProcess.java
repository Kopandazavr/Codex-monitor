package dev.kopandazavr.codexmonitor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One active long-running process represented by a local calendar watchdog deadline marker. */
final class CalendarProcess {
    static final String WATCHDOG_PREFIX = "GPT_WATCHDOG|urgent|";
    static final String METADATA_VERSION = "v1";
    static final long ACTIVE_WORK_WINDOW_MS = 27L * 60_000L;
    private static final Pattern METADATA_PAIR = Pattern.compile(
            "(?is)(?:^|\\s)([a-z0-9_.-]+)\\s*=\\s*(.*?)(?=(?:\\s+[a-z0-9_.-]+\\s*=)|$)");

    final long eventId;
    final long beginMillis;
    final long endMillis;
    final long providerUpdatedMillis;
    final String project;
    final String projectShort;
    final String role;
    final String roleIcon;
    final String topic;
    final String instanceId;
    final boolean directSource;

    CalendarProcess(long eventId, long beginMillis, long endMillis,
            String project, String role, String topic) {
        this(eventId, beginMillis, endMillis, 0L, project, "", role, "", topic, "", false);
    }

    private CalendarProcess(long eventId, long beginMillis, long endMillis,
            long providerUpdatedMillis, String project, String projectShort,
            String role, String roleIcon, String topic, String instanceId, boolean directSource) {
        this.eventId = eventId;
        this.beginMillis = beginMillis;
        this.endMillis = endMillis;
        this.providerUpdatedMillis = Math.max(0L, providerUpdatedMillis);
        this.project = clean(project);
        this.projectShort = clean(projectShort);
        this.role = clean(role);
        this.roleIcon = clean(roleIcon);
        this.topic = clean(topic);
        this.instanceId = clean(instanceId);
        this.directSource = directSource;
    }

    static CalendarProcess fromEvent(long eventId, String title, String description,
            long beginMillis, long endMillis) {
        return fromEvent(eventId, title, description, beginMillis, endMillis, 0L, false);
    }

    static CalendarProcess fromDirectEvent(long eventId, String title, String description,
            long beginMillis, long endMillis) {
        return fromDirectEvent(eventId, title, description, beginMillis, endMillis, 0L);
    }

    static CalendarProcess fromDirectEvent(long eventId, String title, String description,
            long beginMillis, long endMillis, long providerUpdatedMillis) {
        return fromEvent(eventId, title, description, beginMillis, endMillis,
                providerUpdatedMillis, true);
    }

    private static CalendarProcess fromEvent(long eventId, String title, String description,
            long beginMillis, long endMillis, long providerUpdatedMillis, boolean directSource) {
        if (!rejectionReason(title, description, beginMillis, endMillis).isEmpty()) {
            return null;
        }
        Map<String, String> metadata = parseMetadata(description);
        String project = metadata.get("project");
        String projectShort = metadata.get("project_short");
        String role = metadata.get("role");
        String roleIcon = metadata.get("role_icon");
        String topic = metadata.get("topic");
        String instanceId = metadata.get("instance_id");
        return new CalendarProcess(eventId, beginMillis, endMillis, providerUpdatedMillis,
                project, projectShort, role, roleIcon, topic, instanceId, directSource);
    }

    static String rejectionReason(String title, String description,
            long beginMillis, long endMillis) {
        if (title == null || !title.startsWith(WATCHDOG_PREFIX)) return "not_watchdog";
        if (beginMillis <= 0L || endMillis <= beginMillis) return "invalid_time";
        if (!hasSupportedMarker(description)) return "missing_or_invalid_marker";
        Map<String, String> metadata = parseMetadata(description);
        if (!hasCanonicalProject(metadata.get("project"))) return "missing_or_invalid_project";
        if (!hasCanonicalRole(metadata.get("role"))) return "missing_or_invalid_role";
        return "";
    }

    static boolean isCanonicalIdentity(String project, String role) {
        return hasCanonicalProject(project) && hasCanonicalRole(role);
    }

    static boolean hasCanonicalProject(String project) {
        return hasCanonicalValue(project);
    }

    static boolean hasSupportedMarker(String description) {
        if (description == null || description.trim().isEmpty()) return false;
        Matcher matcher = METADATA_PAIR.matcher(normalizeMetadata(description));
        while (matcher.find()) {
            String key = clean(matcher.group(1)).toLowerCase(Locale.ROOT);
            String value = clean(matcher.group(2));
            if (("codex_monitor_watchdog".equals(key) || "codex_meter_watchdog".equals(key))
                    && METADATA_VERSION.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    static String metadataValueForDiagnostics(String description, String requestedKey) {
        String target = clean(requestedKey).toLowerCase(Locale.ROOT);
        if (target.isEmpty() || description == null || description.trim().isEmpty()) return "";
        Matcher matcher = METADATA_PAIR.matcher(normalizeMetadata(description));
        while (matcher.find()) {
            String key = clean(matcher.group(1)).toLowerCase(Locale.ROOT);
            if (target.equals(key)) return clean(matcher.group(2));
        }
        return "";
    }

    static String markerValueForDiagnostics(String description) {
        String value = metadataValueForDiagnostics(description, "codex_monitor_watchdog");
        return value.isEmpty()
                ? metadataValueForDiagnostics(description, "codex_meter_watchdog") : value;
    }

    static Map<String, String> parseMetadata(String description) {
        Map<String, String> values = new LinkedHashMap<>();
        if (description == null || description.trim().isEmpty()) return values;
        Matcher matcher = METADATA_PAIR.matcher(normalizeMetadata(description));
        boolean supported = false;
        while (matcher.find()) {
            String key = clean(matcher.group(1)).toLowerCase(Locale.ROOT);
            String value = clean(matcher.group(2));
            if ("codex_monitor_watchdog".equals(key) || "codex_meter_watchdog".equals(key)) {
                supported = METADATA_VERSION.equalsIgnoreCase(value);
            } else if (!value.isEmpty()) {
                values.put(key, value);
            }
        }
        return supported ? values : new LinkedHashMap<>();
    }

    long workStartMillis() {
        return Math.max(0L, beginMillis - ACTIVE_WORK_WINDOW_MS);
    }

    boolean isWorkRunning(long nowMillis) {
        return isVisibleActive(nowMillis);
    }

    /** BEGIN is the only lifecycle deadline; Calendar END is carrier-only metadata. */
    boolean isWatchdogActive(long nowMillis) {
        return isVisibleActive(nowMillis);
    }

    boolean isVisibleActive(long nowMillis) {
        return nowMillis >= workStartMillis() && nowMillis < beginMillis;
    }

    long remainingMillis(long nowMillis) {
        return Math.max(0L, beginMillis - nowMillis);
    }

    int remainingPercent(long nowMillis) {
        long start = workStartMillis();
        if (nowMillis <= start) return 100;
        if (nowMillis >= beginMillis) return 0;
        long duration = beginMillis - start;
        long remaining = beginMillis - nowMillis;
        if (duration <= 0L) return 0;
        return (int) Math.max(0L, Math.min(100L,
                Math.round((remaining * 100.0d) / duration)));
    }

    /** Fill amount for the visible process bar: 0 at work start and 100 at BEGIN. */
    int elapsedPercent(long nowMillis) {
        long start = workStartMillis();
        if (nowMillis <= start) return 0;
        if (nowMillis >= beginMillis) return 100;
        long duration = beginMillis - start;
        long elapsed = nowMillis - start;
        if (duration <= 0L) return 100;
        return (int) Math.max(0L, Math.min(100L,
                Math.round((elapsed * 100.0d) / duration)));
    }

    String displayLabel() {
        return displayIdentity(role, project, projectShort, topic);
    }

    static String displayIdentity(String role, String project, String topic) {
        return displayIdentity(role, project, "", topic);
    }

    static String displayIdentity(String role, String project, String projectShort, String topic) {
        String cleanRole = clean(role);
        String cleanProject = clean(project);
        String compactProject = valueOr(projectShort, cleanProject);
        if (hasCanonicalRole(cleanRole)) {
            if (!compactProject.isEmpty() && !compactProject.equalsIgnoreCase(cleanRole)) {
                return compactProject + " — " + cleanRole;
            }
            return cleanRole;
        }
        if (!compactProject.isEmpty()) return compactProject;
        String cleanTopic = clean(topic);
        return cleanTopic.isEmpty() ? "Active process" : cleanTopic;
    }

    static boolean hasCanonicalRole(String role) {
        return hasCanonicalValue(role);
    }

    private static boolean hasCanonicalValue(String value) {
        String cleanValue = clean(value);
        return !cleanValue.isEmpty()
                && !"unknown".equalsIgnoreCase(cleanValue)
                && !"null".equalsIgnoreCase(cleanValue)
                && !"none".equalsIgnoreCase(cleanValue)
                && !"n/a".equalsIgnoreCase(cleanValue)
                && !"-".equals(cleanValue);
    }

    /**
     * Stable logical identity for a watchdog. Explicit instance_id remains authoritative.
     * Legacy watchdogs use semantic watchdog fields rather than provider event ids, so the
     * Local Provider and Direct API representations of one event remain equivalent.
     */
    String instanceIdentity() {
        if (!instanceId.isEmpty()) return "instance:" + instanceId;
        return logicalLegacyIdentity();
    }

    String logicalLegacyIdentity() {
        return "legacy-semantic:" + semanticIdentityString();
    }

    String semanticIdentityString() {
        return clean(project) + "\u001f"
                + clean(projectShort) + "\u001f"
                + clean(role) + "\u001f"
                + clean(topic) + "\u001f"
                + workStartMillis() + "\u001f"
                + beginMillis;
    }

    String identity() {
        return instanceIdentity();
    }

    private static String normalizeMetadata(String description) {
        return description
                .replace('\r', ' ')
                .replaceAll("(?is)<br\\s*/?>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ");
    }

    private static String valueOr(String preferred, String fallback) {
        String cleanPreferred = clean(preferred);
        return cleanPreferred.isEmpty() ? clean(fallback) : cleanPreferred;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
