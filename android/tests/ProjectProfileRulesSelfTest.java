package dev.kopandazavr.codexmonitor;

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
        System.out.println("Project profile rules PASS");
    }
}
