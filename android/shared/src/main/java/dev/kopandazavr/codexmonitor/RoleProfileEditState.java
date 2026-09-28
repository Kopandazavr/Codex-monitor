package dev.kopandazavr.codexmonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure transactional Role Settings draft; persistence is owned by RoleProfileStore. */
final class RoleProfileEditState {
    private final String id;
    private final List<String> aliases = new ArrayList<>();
    private final Set<String> calendarAliases = new HashSet<>();
    private String primaryAlias;

    RoleProfileEditState(String id, List<String> aliases, String primaryAlias,
            Set<String> calendarAliases) {
        this.id = id == null ? "" : id;
        if (aliases != null) {
            for (String alias : aliases) {
                String clean = ProjectProfileRules.collapseWhitespace(alias);
                if (!clean.isEmpty() && aliasFor(ProjectProfileRules.normalizeAlias(clean)).isEmpty()) {
                    this.aliases.add(clean);
                }
            }
        }
        if (calendarAliases != null) {
            for (String alias : calendarAliases) {
                String normalized = ProjectProfileRules.normalizeAlias(alias);
                if (!normalized.isEmpty() && !aliasFor(normalized).isEmpty()) {
                    this.calendarAliases.add(normalized);
                }
            }
        }
        this.primaryAlias = aliasFor(ProjectProfileRules.normalizeAlias(primaryAlias));
        if (this.primaryAlias.isEmpty() && !this.aliases.isEmpty()) {
            this.primaryAlias = this.aliases.get(0);
        }
    }

    String id() { return id; }
    List<String> aliases() { return Collections.unmodifiableList(new ArrayList<>(aliases)); }
    Set<String> calendarAliases() {
        return Collections.unmodifiableSet(new HashSet<>(calendarAliases));
    }
    String primaryAlias() { return primaryAlias; }
    boolean isCalendarAlias(String alias) {
        return calendarAliases.contains(ProjectProfileRules.normalizeAlias(alias));
    }
    boolean markCalendarAlias(String alias) {
        String normalized = ProjectProfileRules.normalizeAlias(alias);
        return !normalized.isEmpty() && !aliasFor(normalized).isEmpty()
                && calendarAliases.add(normalized);
    }

    String addAlias(String alias, Map<String,String> externalOwners) {
        String clean = ProjectProfileRules.collapseWhitespace(alias);
        String normalized = ProjectProfileRules.normalizeAlias(clean);
        if (normalized.isEmpty()) return "Alias cannot be empty.";
        if (!aliasFor(normalized).isEmpty()) return "That alias is already known for this role.";
        String owner = externalOwners == null ? null : externalOwners.get(normalized);
        if (owner != null && !owner.isEmpty()) return "That alias already belongs to " + owner + ".";
        aliases.add(clean);
        return "";
    }

    String editAlias(String alias, String replacement, Map<String,String> externalOwners) {
        String oldNormalized = ProjectProfileRules.normalizeAlias(alias);
        String stored = aliasFor(oldNormalized);
        if (stored.isEmpty()) return "Alias not found.";
        String clean = ProjectProfileRules.collapseWhitespace(replacement);
        String normalized = ProjectProfileRules.normalizeAlias(clean);
        if (normalized.isEmpty()) return "Alias cannot be empty.";
        if (normalized.equals(oldNormalized)) return "";
        if (!aliasFor(normalized).isEmpty()) return "That alias is already known for this role.";
        String owner = externalOwners == null ? null : externalOwners.get(normalized);
        if (owner != null && !owner.isEmpty()) return "That alias already belongs to " + owner + ".";
        if (calendarAliases.contains(oldNormalized)) {
            // Calendar aliases remain immutable routing evidence. Editing is copy-on-edit.
            aliases.add(clean);
            return "";
        }
        int index = aliases.indexOf(stored);
        aliases.set(index, clean);
        if (ProjectProfileRules.normalizeAlias(primaryAlias).equals(oldNormalized)) primaryAlias = clean;
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
}
