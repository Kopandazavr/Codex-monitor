package dev.kopandazavr.codexmonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure in-memory Project Settings draft. Persistence is owned by ProjectProfileStore. */
final class ProjectProfileEditState {
    private final String id;
    private final List<String> aliases;
    private final Set<String> calendarAliases;
    private String primaryAlias;
    private String shortOverride;
    private String iconKey;
    private String colorKey;

    ProjectProfileEditState(String id, List<String> aliases, String primaryAlias,
            String shortOverride, String iconKey, String colorKey) {
        this(id, aliases, primaryAlias, shortOverride, iconKey, colorKey,
                Collections.emptySet());
    }

    ProjectProfileEditState(String id, List<String> aliases, String primaryAlias,
            String shortOverride, String iconKey, String colorKey,
            Set<String> calendarAliases) {
        this.id = id == null ? "" : id;
        this.aliases = new ArrayList<>();
        if (aliases != null) {
            for (String alias : aliases) {
                String clean = ProjectProfileRules.collapseWhitespace(alias);
                if (!clean.isEmpty()
                        && aliasFor(ProjectProfileRules.normalizeAlias(clean)).isEmpty()) {
                    this.aliases.add(clean);
                }
            }
        }
        this.calendarAliases = new HashSet<>();
        if (calendarAliases != null) {
            for (String alias : calendarAliases) {
                String normalized = ProjectProfileRules.normalizeAlias(alias);
                if (!normalized.isEmpty() && !aliasFor(normalized).isEmpty()) {
                    this.calendarAliases.add(normalized);
                }
            }
        }
        this.primaryAlias = canonicalAlias(primaryAlias);
        if (this.primaryAlias.isEmpty() && !this.aliases.isEmpty()) {
            this.primaryAlias = this.aliases.get(0);
        }
        this.shortOverride = ProjectProfileRules.normalizeShort(shortOverride);
        this.iconKey = iconKey == null ? "" : iconKey;
        this.colorKey = colorKey == null ? "" : colorKey;
    }

    ProjectProfileEditState copy() {
        return new ProjectProfileEditState(
                id, aliases, primaryAlias, shortOverride, iconKey, colorKey, calendarAliases);
    }

    String id() { return id; }
    List<String> aliases() { return Collections.unmodifiableList(new ArrayList<>(aliases)); }
    Set<String> calendarAliases() {
        return Collections.unmodifiableSet(new HashSet<>(calendarAliases));
    }
    boolean isCalendarAlias(String alias) {
        return calendarAliases.contains(ProjectProfileRules.normalizeAlias(alias));
    }
    boolean markCalendarAlias(String alias) {
        String normalized = ProjectProfileRules.normalizeAlias(alias);
        return !normalized.isEmpty() && !aliasFor(normalized).isEmpty()
                && calendarAliases.add(normalized);
    }
    String primaryAlias() { return primaryAlias; }
    String shortOverride() { return shortOverride; }
    String iconKey() { return iconKey; }
    String colorKey() { return colorKey; }

    void setShortOverride(String value) {
        shortOverride = ProjectProfileRules.normalizeShort(value);
    }

    void setAppearance(String icon, String color) {
        iconKey = icon == null ? "" : icon;
        colorKey = color == null ? "" : color;
    }

    String addAlias(String alias, Map<String, String> externalOwners) {
        String clean = ProjectProfileRules.collapseWhitespace(alias);
        String normalized = ProjectProfileRules.normalizeAlias(clean);
        if (normalized.isEmpty()) return "Alias cannot be empty.";
        if (!aliasFor(normalized).isEmpty()) {
            return "That alias is already known for this project.";
        }
        String owner = externalOwners == null ? null : externalOwners.get(normalized);
        if (owner != null && !owner.isEmpty()) {
            return "That alias already belongs to " + owner + ".";
        }
        aliases.add(clean);
        return "";
    }

    String editAlias(String alias, String replacement, Map<String, String> externalOwners) {
        String oldNormalized = ProjectProfileRules.normalizeAlias(alias);
        String stored = aliasFor(oldNormalized);
        if (stored.isEmpty()) return "Alias not found.";
        String clean = ProjectProfileRules.collapseWhitespace(replacement);
        String normalized = ProjectProfileRules.normalizeAlias(clean);
        if (normalized.isEmpty()) return "Alias cannot be empty.";
        if (normalized.equals(oldNormalized)) return "";
        if (!aliasFor(normalized).isEmpty()) {
            return "That alias is already known for this project.";
        }
        String owner = externalOwners == null ? null : externalOwners.get(normalized);
        if (owner != null && !owner.isEmpty()) {
            return "That alias already belongs to " + owner + ".";
        }
        if (calendarAliases.contains(oldNormalized)) {
            // Calendar aliases are immutable routing evidence. Editing is copy-on-edit.
            aliases.add(clean);
            return "";
        }
        int index = aliases.indexOf(stored);
        aliases.set(index, clean);
        if (ProjectProfileRules.normalizeAlias(primaryAlias).equals(oldNormalized)) {
            primaryAlias = clean;
        }
        return "";
    }

    String makePrimary(String alias) {
        String stored = aliasFor(ProjectProfileRules.normalizeAlias(alias));
        if (stored.isEmpty()) return "Alias not found.";
        primaryAlias = stored;
        return "";
    }

    String deleteAlias(String alias) {
        String normalized = ProjectProfileRules.normalizeAlias(alias);
        String stored = aliasFor(normalized);
        if (stored.isEmpty()) return "Alias not found.";
        if (ProjectProfileRules.normalizeAlias(primaryAlias).equals(normalized)) {
            return "Make another alias Primary before deleting this one.";
        }
        if (aliases.size() <= 1) return "The last alias cannot be deleted.";
        aliases.remove(stored);
        calendarAliases.remove(normalized);
        return "";
    }

    boolean ownsPrimary() {
        return !aliasFor(ProjectProfileRules.normalizeAlias(primaryAlias)).isEmpty();
    }

    private String aliasFor(String normalized) {
        if (normalized == null || normalized.isEmpty()) return "";
        for (String alias : aliases) {
            if (ProjectProfileRules.normalizeAlias(alias).equals(normalized)) return alias;
        }
        return "";
    }

    private String canonicalAlias(String alias) {
        return aliasFor(ProjectProfileRules.normalizeAlias(alias));
    }
}
