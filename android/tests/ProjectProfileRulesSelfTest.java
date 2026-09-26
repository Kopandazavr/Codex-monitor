package dev.kopandazavr.codexmonitor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ProjectProfileRulesSelfTest {
    public static void main(String[] args) {
        assert "codex monitor".equals(ProjectProfileRules.normalizeAlias("  Codex   Monitor "));
        assert "заказы сигарет".equals(ProjectProfileRules.normalizeAlias(" Заказы   сигарет "));
        assert "CM".equals(ProjectProfileRules.automaticAcronym("Codex Monitor"));
        assert "MT".equals(ProjectProfileRules.automaticAcronym("Mira Technical"));
        assert "DM".equals(ProjectProfileRules.automaticAcronym("Data Matrix"));
        assert "WD".equals(ProjectProfileRules.effectiveShort("", "WD", "Watch Dog"));
        assert "LOCAL".equals(ProjectProfileRules.effectiveShort("LOCAL", "WD", "Watch Dog"));
        assert "[CM] Codex Monitor".equals(
                ProjectProfileRules.badgeText("", "", "Codex Monitor"));

        ProjectProfileEditState original = new ProjectProfileEditState(
                "seed:codex-monitor", Collections.singletonList("Codex Monitor"),
                "Codex Monitor", "", "bar_chart", "purple");
        ProjectProfileEditState draft = original.copy();
        Map<String, String> otherOwners = new HashMap<>();

        assert draft.addAlias("Codex Astra", otherOwners).isEmpty();
        assert draft.makePrimary("Codex Astra").isEmpty();
        assert "CA".equals(ProjectProfileRules.fallbackShort("", draft.primaryAlias()));
        assert draft.deleteAlias("Codex Monitor").isEmpty();

        // Deleted ownership is absent from the draft immediately, so same-session re-add works.
        assert draft.addAlias("  Codex   Monitor ", otherOwners).isEmpty();
        assert draft.deleteAlias("Codex Monitor").isEmpty();

        // Explicit override survives later Primary changes and appearance changes stay coherent.
        draft.setShortOverride("LOCAL");
        assert "LOCAL".equals(ProjectProfileRules.effectiveShort(
                draft.shortOverride(), "CM", draft.primaryAlias()));
        draft.setAppearance("heart_filled", "red");
        assert "heart_filled".equals(draft.iconKey());
        assert "red".equals(draft.colorKey());

        // Clearing the explicit Short Name stays empty through later draft actions. Preview may
        // use the automatic Primary acronym, but the override itself must remain empty.
        draft.setShortOverride("");
        assert draft.shortOverride().isEmpty();
        assert "CA".equals(ProjectProfileRules.effectiveShort(
                draft.shortOverride(), "CM", draft.primaryAlias()));
        draft.setAppearance("folder", "purple");
        assert draft.shortOverride().isEmpty();

        // Cancel = discard the draft: original persisted snapshot remains untouched.
        assert "Codex Monitor".equals(original.primaryAlias());
        assert original.aliases().size() == 1;
        assert original.shortOverride().isEmpty();
        assert "bar_chart".equals(original.iconKey());
        assert "purple".equals(original.colorKey());

        otherOwners.put(ProjectProfileRules.normalizeAlias("Other Project"), "Other Project");
        assert draft.addAlias("other   project", otherOwners)
                .equals("That alias already belongs to Other Project.");

        String generated = "project:11111111-1111-4111-8111-111111111111";
        assert ProjectProfileRules.isReclaimableLegacyOrphan(
                generated, Collections.singletonList("GGG"), "GGG", "",
                "folder", "gray", " ggg ");
        assert !ProjectProfileRules.isReclaimableLegacyOrphan(
                "seed:ggg", Collections.singletonList("GGG"), "GGG", "",
                "folder", "gray", "GGG");
        assert !ProjectProfileRules.isReclaimableLegacyOrphan(
                generated, Collections.singletonList("GGG"), "GGG", "G",
                "folder", "gray", "GGG");
        assert !ProjectProfileRules.isReclaimableLegacyOrphan(
                generated, Collections.singletonList("GGG"), "GGG", "",
                "terminal", "gray", "GGG");
        assert !ProjectProfileRules.isReclaimableLegacyOrphan(
                generated, java.util.Arrays.asList("GGG", "Real Project"), "GGG", "",
                "folder", "gray", "GGG");

        System.out.println("Project profile rules + transactional draft PASS");
    }
}
