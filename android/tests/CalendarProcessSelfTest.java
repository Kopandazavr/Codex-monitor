package dev.kopandazavr.codexmonitor;

/** Focused regression coverage for strict watchdog metadata, display identity and work timing. */
public final class CalendarProcessSelfTest {
    private CalendarProcessSelfTest() {
    }

    public static void main(String[] args) {
        testMultilineMetadata();
        testFlattenedMetadata();
        testHtmlMetadata();
        testCompactProjectIdentity();
        testFullProjectIdentityWithoutShortName();
        testAnotherCanonicalRole();
        testMissingRoleRejected();
        testMissingProjectRejected();
        testUnsupportedMetadataRejected();
        testOptionalFieldsMayBeMissing();
        testDirectSourceProvenance();
        testCanonicalWorkWindow();
        System.out.println("CalendarProcess strict metadata/display/timing self-test passed.");
    }

    private static void testMultilineMetadata() {
        CalendarProcess process = CalendarProcess.fromEvent(
                42L,
                "GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_monitor_watchdog=v1\n"
                        + "project=Codex Monitor\n"
                        + "role=Developer\n"
                        + "topic=phone acceptance cleanup",
                1_000L,
                2_000L);
        require(process != null, "multiline process parsed");
        require("Codex Monitor".equals(process.project), "multiline project");
        require("Developer".equals(process.role), "multiline role");
        require("phone acceptance cleanup".equals(process.topic), "multiline topic");
    }

    private static void testFlattenedMetadata() {
        CalendarProcess process = CalendarProcess.fromEvent(
                43L,
                "GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_monitor_watchdog=v1 project=Codex Monitor "
                        + "role=Planning / Review / Acceptance "
                        + "topic=bounded scope planning",
                3_000L,
                4_000L);
        require(process != null, "flattened process parsed");
        require("Codex Monitor".equals(process.project), "flattened project");
        require("Planning / Review / Acceptance".equals(process.role), "flattened role");
        require("bounded scope planning".equals(process.topic), "flattened topic");
    }

    private static void testHtmlMetadata() {
        CalendarProcess process = CalendarProcess.fromEvent(
                431L,
                "GPT_WATCHDOG|urgent|Data Matrix Scanner",
                "<div>codex_monitor_watchdog=v1<br>project=Data Matrix Scanner<br/>"
                        + "role=<b>Developer</b><br />topic=implementation</div>",
                3_000L,
                4_000L);
        require(process != null, "HTML-normalized process parsed");
        require("Data Matrix Scanner".equals(process.project), "HTML project");
        require("Developer".equals(process.role), "HTML role");
        require("implementation".equals(process.topic), "HTML topic");
    }

    private static void testCompactProjectIdentity() {
        CalendarProcess process = CalendarProcess.fromEvent(
                432L,
                "GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_monitor_watchdog=v1 project=Codex Monitor project_short=CM "
                        + "role=Main Agent topic=implementation",
                3_000L,
                4_000L);
        require(process != null, "compact project process parsed");
        require("CM".equals(process.projectShort), "compact project metadata parsed");
        require("CM — Main Agent".equals(process.displayLabel()),
                "compact project precedes canonical role");
    }

    private static void testFullProjectIdentityWithoutShortName() {
        CalendarProcess process = CalendarProcess.fromEvent(
                4321L,
                "GPT_WATCHDOG|urgent|Data Matrix Scanner",
                "codex_monitor_watchdog=v1 project=Data Matrix Scanner role=Developer",
                3_000L,
                4_000L);
        require(process != null, "full-project process parsed");
        require("Data Matrix Scanner — Developer".equals(process.displayLabel()),
                "full project is used when project_short is absent");
    }

    private static void testAnotherCanonicalRole() {
        CalendarProcess process = CalendarProcess.fromEvent(
                433L,
                "GPT_WATCHDOG|urgent|Data Matrix Scanner",
                "codex_monitor_watchdog=v1 project=Data Matrix Scanner "
                        + "role=Planning / Review / Acceptance topic=planning",
                3_000L,
                4_000L);
        require(process != null, "planning process parsed");
        require("Data Matrix Scanner — Planning / Review / Acceptance".equals(
                process.displayLabel()), "second role is not hardcoded");
    }

    private static void testMissingRoleRejected() {
        String title = "GPT_WATCHDOG|urgent|Data Matrix Scanner";
        String description = "codex_monitor_watchdog=v1 project=Data Matrix Scanner topic=x";
        require(CalendarProcess.fromEvent(434L, title, description, 3_000L, 4_000L) == null,
                "role-less watchdog rejected");
        require("missing_or_invalid_role".equals(
                CalendarProcess.rejectionReason(title, description, 3_000L, 4_000L)),
                "missing role has explicit rejection reason");
    }

    private static void testMissingProjectRejected() {
        String title = "GPT_WATCHDOG|urgent|Title Must Not Be Project Fallback";
        String description = "codex_monitor_watchdog=v1 role=Main Agent topic=x";
        require(CalendarProcess.fromEvent(435L, title, description, 3_000L, 4_000L) == null,
                "project-less watchdog rejected despite title suffix");
        require("missing_or_invalid_project".equals(
                CalendarProcess.rejectionReason(title, description, 3_000L, 4_000L)),
                "missing project has explicit rejection reason");
    }

    private static void testUnsupportedMetadataRejected() {
        String title = "GPT_WATCHDOG|urgent|Codex Monitor";
        String description = "codex_monitor_watchdog=v2 project=Codex Monitor role=Main Agent";
        require(CalendarProcess.fromEvent(44L, title, description, 5_000L, 6_000L) == null,
                "unsupported watchdog metadata rejected");
        require("missing_or_invalid_marker".equals(
                CalendarProcess.rejectionReason(title, description, 5_000L, 6_000L)),
                "unsupported marker has explicit rejection reason");
    }

    private static void testOptionalFieldsMayBeMissing() {
        CalendarProcess process = CalendarProcess.fromEvent(
                441L,
                "GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_monitor_watchdog=v1 project=Codex Monitor role=Main Agent",
                5_000L,
                6_000L);
        require(process != null, "project_short/topic are optional");
        require(process.projectShort.isEmpty(), "project_short may be absent");
        require(process.topic.isEmpty(), "topic may be absent");
    }

    private static void testDirectSourceProvenance() {
        CalendarProcess provider = CalendarProcess.fromEvent(
                451L,
                "GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_monitor_watchdog=v1 project=Codex Monitor role=Developer",
                10_000L,
                20_000L);
        CalendarProcess direct = CalendarProcess.fromDirectEvent(
                452L,
                "GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_monitor_watchdog=v1 project=Codex Monitor role=Developer",
                10_000L,
                20_000L);
        require(provider != null && !provider.directSource,
                "provider watchdog keeps provider provenance");
        require(direct != null && direct.directSource,
                "direct watchdog keeps direct provenance");
    }

    private static void testCanonicalWorkWindow() {
        long begin = CalendarProcess.ACTIVE_WORK_WINDOW_MS;
        long end = begin + 5L * 60_000L;
        CalendarProcess process = CalendarProcess.fromEvent(
                45L,
                "GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_monitor_watchdog=v1 project=Codex Monitor role=Developer topic=implementation",
                begin,
                end);
        require(process != null, "timed process parsed");
        require(process.workStartMillis() == 0L, "work starts 27 minutes before BEGIN");
        require(process.isVisibleActive(1L), "future watchdog is visible during work interval");
        require(process.isWorkRunning(1L), "pre-BEGIN phase is work-running");
        require(process.remainingPercent(0L) == 100, "work starts at 100 percent remaining");
        require(process.remainingPercent(begin / 2L) == 50, "work midpoint is 50 percent remaining");
        require(process.remainingMillis(begin / 2L) == begin / 2L,
                "work remaining targets BEGIN");
        require(process.isWatchdogActive(begin), "BEGIN enters watchdog window");
        require(process.remainingPercent(begin) == 100,
                "watchdog fallback window retains its own progress semantics");
        require(process.remainingMillis(begin) == end - begin,
                "post-BEGIN remaining targets END");
        require(!process.isVisibleActive(end), "process is no longer active at END");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
