package dev.kopandazavr.codexmonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Pure in-memory Project Settings draft. Persistence is owned by ProjectProfileStore. */
final class ProjectProfileEditState {
    private final String id;
    private final List<String> aliases;
    private String primaryAlias;
    private String shortOverride;
    private String iconKey;
    private String colorKey;

    ProjectProfileEditState(String id, List<String> aliases, String primaryAlias,
            String shortOverride, String iconKey, String colorKey) {
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
        this.primaryAlias = canonicalAlias(primaryAlias);
        if (this.primaryAlias.isEmpty() && !this.aliases.isEmpty()) {
            this.primaryAlias = this.aliases.get(0);
        }
        this.shortOverride = ProjectProfileRules.collapseWhitespace(shortOverride);
        this.iconKey = iconKey == null ? "" : iconKey;
        this.colorKey = colorKey == null ? "" : colorKey;
    }

    ProjectProfileEditState copy() {
        return new ProjectProfileEditState(
                id, aliases, primaryAlias, shortOverride, iconKey, colorKey);
    }

    String id() { return id; }
    List<String> aliases() { return Collections.unmodifiableList(new ArrayList<>(aliases)); }
    String primaryAlias() { return primaryAlias; }
    String shortOverride() { return shortOverride; }
    String iconKey() { return iconKey; }
    String colorKey() { return colorKey; }

    void setShortOverride(String value) {
        shortOverride = ProjectProfileRules.collapseWhitespace(value);
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
