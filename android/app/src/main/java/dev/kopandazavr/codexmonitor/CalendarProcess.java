package dev.kopandazavr.codexmonitor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One active long-running process represented by a local calendar watchdog event. */
final class CalendarProcess {
    static final String WATCHDOG_PREFIX = "GPT_WATCHDOG|urgent|";
    static final String METADATA_VERSION = "v1";
    static final long ACTIVE_WORK_WINDOW_MS = 27L * 60_000L;
    private static final Pattern METADATA_PAIR = Pattern.compile(
            "(?is)(?:^|\\s)([a-z0-9_.-]+)\\s*=\\s*(.*?)(?=(?:\\s+[a-z0-9_.-]+\\s*=)|$)");

    final long eventId;
    final long beginMillis;
    final long endMillis;
    final String project;
    final String projectShort;
    final String role;
    final String topic;
    final boolean directSource;

    CalendarProcess(long eventId, long beginMillis, long endMillis,
            String project, String role, String topic) {
        this(eventId, beginMillis, endMillis, project, "", role, topic, false);
    }

    private CalendarProcess(long eventId, long beginMillis, long endMillis,
            String project, String projectShort, String role, String topic, boolean directSource) {
        this.eventId = eventId;
        this.beginMillis = beginMillis;
        this.endMillis = endMillis;
        this.project = clean(project);
        this.projectShort = clean(projectShort);
        this.role = clean(role);
        this.topic = clean(topic);
        this.directSource = directSource;
    }

    static CalendarProcess fromEvent(long eventId, String title, String description,
            long beginMillis, long endMillis) {
        return fromEvent(eventId, title, description, beginMillis, endMillis, false);
    }

    static CalendarProcess fromDirectEvent(long eventId, String title, String description,
            long beginMillis, long endMillis) {
        return fromEvent(eventId, title, description, beginMillis, endMillis, true);
    }

    private static CalendarProcess fromEvent(long eventId, String title, String description,
            long beginMillis, long endMillis, boolean directSource) {
        if (!rejectionReason(title, description, beginMillis, endMillis).isEmpty()) {
            return null;
        }
        Map<String, String> metadata = parseMetadata(description);
        String project = metadata.get("project");
        String projectShort = metadata.get("project_short");
        String role = metadata.get("role");
        String topic = metadata.get("topic");
        return new CalendarProcess(eventId, beginMillis, endMillis,
                project, projectShort, role, topic, directSource);
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

        // Calendar Provider / Google Calendar may preserve line breaks, flatten them to spaces,
        // or surface simple HTML (notably <br>). Normalize those presentation variants before
        // matching canonical key=value boundaries so role/topic survive every supported form.
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
        return nowMillis >= workStartMillis() && nowMillis < beginMillis;
    }

    boolean isWatchdogActive(long nowMillis) {
        return nowMillis >= beginMillis && nowMillis < endMillis;
    }

    boolean isVisibleActive(long nowMillis) {
        return nowMillis >= workStartMillis() && nowMillis < endMillis;
    }

    long remainingMillis(long nowMillis) {
        long target = nowMillis < beginMillis ? beginMillis : endMillis;
        return Math.max(0L, target - nowMillis);
    }

    int remainingPercent(long nowMillis) {
        if (nowMillis < beginMillis) {
            long start = workStartMillis();
            if (nowMillis <= start) return 100;
            long duration = beginMillis - start;
            long remaining = beginMillis - nowMillis;
            if (duration <= 0L) return 0;
            return (int) Math.max(0L, Math.min(100L,
                    Math.round((remaining * 100.0d) / duration)));
        }
        if (nowMillis >= endMillis) return 0;
        long duration = endMillis - beginMillis;
        long remaining = endMillis - nowMillis;
        return (int) Math.max(0L, Math.min(100L,
                Math.round((remaining * 100.0d) / duration)));
    }

    /** Fill amount for the visible process bar: 0 at start and 100 at the timer target. */
    int elapsedPercent(long nowMillis) {
        if (nowMillis < beginMillis) {
            long start = workStartMillis();
            if (nowMillis <= start) return 0;
            long duration = beginMillis - start;
            long elapsed = nowMillis - start;
            if (duration <= 0L) return 100;
            return (int) Math.max(0L, Math.min(100L,
                    Math.round((elapsed * 100.0d) / duration)));
        }
        if (nowMillis >= endMillis) return 100;
        long duration = endMillis - beginMillis;
        long elapsed = nowMillis - beginMillis;
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

    String identity() {
        return eventId + ":" + beginMillis;
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
