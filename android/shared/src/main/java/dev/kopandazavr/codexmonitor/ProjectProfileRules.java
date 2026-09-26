package dev.kopandazavr.codexmonitor;

import java.util.Locale;

/** Pure normalization and short-name rules shared by the durable project-profile registry. */
final class ProjectProfileRules {
    private ProjectProfileRules() {}

    static String collapseWhitespace(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    static String normalizeAlias(String value) {
        return collapseWhitespace(value).toLowerCase(Locale.ROOT);
    }

    static String automaticAcronym(String fullName) {
        String clean = collapseWhitespace(fullName);
        if (clean.isEmpty()) return "";
        StringBuilder acronym = new StringBuilder();
        for (String word : clean.split(" ")) {
            if (word.isEmpty()) continue;
            acronym.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
        }
        return acronym.toString();
    }

    static String fallbackShort(String watchdogShort, String primaryAlias) {
        String watchdog = collapseWhitespace(watchdogShort);
        return watchdog.isEmpty() ? automaticAcronym(primaryAlias) : watchdog;
    }

    static String effectiveShort(String localOverride, String watchdogShort, String primaryAlias) {
        String local = collapseWhitespace(localOverride);
        return local.isEmpty() ? fallbackShort(watchdogShort, primaryAlias) : local;
    }

    static String badgeText(String localOverride, String watchdogShort, String primaryAlias) {
        String full = collapseWhitespace(primaryAlias);
        String shortName = effectiveShort(localOverride, watchdogShort, full);
        return shortName.isEmpty() ? full : "[" + shortName + "] " + full;
    }
}
