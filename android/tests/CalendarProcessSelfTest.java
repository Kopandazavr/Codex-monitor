package dev.bennett.codexmeter;

/** Focused regression coverage for watchdog metadata, canonical display identity and work timing. */
public final class CalendarProcessSelfTest {
    private CalendarProcessSelfTest() {
    }

    public static void main(String[] args) {
        testMultilineMetadata();
        testFlattenedMetadata();
        testHtmlMetadata();
        testRolePrimaryIdentity();
        testAnotherCanonicalRole();
        testProjectFallbackWithoutRole();
        testUnsupportedMetadataFallsBackSoft();
        testCanonicalWorkWindow();
        System.out.println("CalendarProcess metadata/display/timing self-test passed.");
    }

    private static void testMultilineMetadata() {
        CalendarProcess process = CalendarProcess.fromEvent(
                42L,
                "GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_meter_watchdog=v1\n"
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
                "codex_meter_watchdog=v1 project=Codex Monitor "
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
                "<div>codex_meter_watchdog=v1<br>project=Data Matrix Scanner<br/>"
                        + "role=<b>Developer</b><br />topic=implementation</div>",
                3_000L,
                4_000L);
        require(process != null, "HTML-normalized process parsed");
        require("Data Matrix Scanner".equals(process.project), "HTML project");
        require("Developer".equals(process.role), "HTML role");
        require("implementation".equals(process.topic), "HTML topic");
    }

    private static void testRolePrimaryIdentity() {
        CalendarProcess process = CalendarProcess.fromEvent(
                432L,
                "GPT_WATCHDOG|urgent|Data Matrix Scanner",
                "codex_meter_watchdog=v1 project=Data Matrix Scanner role=Developer topic=build",
                3_000L,
                4_000L);
        require(process != null, "developer process parsed");
        require("Developer — Data Matrix Scanner".equals(process.displayLabel()),
                "role is primary and project is secondary");
    }

    private static void testAnotherCanonicalRole() {
        CalendarProcess process = CalendarProcess.fromEvent(
                433L,
                "GPT_WATCHDOG|urgent|Data Matrix Scanner",
                "codex_meter_watchdog=v1 project=Data Matrix Scanner "
                        + "role=Planning / Review / Acceptance topic=planning",
                3_000L,
                4_000L);
        require(process != null, "planning process parsed");
        require("Planning / Review / Acceptance — Data Matrix Scanner".equals(
                process.displayLabel()), "second role is not hardcoded");
    }

    private static void testProjectFallbackWithoutRole() {
        CalendarProcess process = CalendarProcess.fromEvent(
                434L,
                "GPT_WATCHDOG|urgent|Data Matrix Scanner",
                "codex_meter_watchdog=v1 project=Data Matrix Scanner topic=legacy producer",
                3_000L,
                4_000L);
        require(process != null, "role-less process parsed");
        require("Data Matrix Scanner".equals(process.displayLabel()),
                "project fallback only when role is missing");
    }

    private static void testUnsupportedMetadataFallsBackSoft() {
        CalendarProcess process = CalendarProcess.fromEvent(
                44L,
                "GPT_WATCHDOG|urgent|Title fallback",
                "codex_meter_watchdog=v2 project=Wrong role=Wrong topic=Wrong",
                5_000L,
                6_000L);
        require(process != null, "unsupported metadata still yields title-only watchdog");
        require("Title fallback".equals(process.project), "title fallback project");
        require(process.role.isEmpty(), "unsupported metadata role ignored");
        require(process.topic.isEmpty(), "unsupported metadata topic ignored");
    }

    private static void testCanonicalWorkWindow() {
        long begin = CalendarProcess.ACTIVE_WORK_WINDOW_MS;
        long end = begin + 5L * 60_000L;
        CalendarProcess process = CalendarProcess.fromEvent(
                45L,
                "GPT_WATCHDOG|urgent|Codex Monitor",
                "codex_meter_watchdog=v1 project=Codex Monitor role=Developer topic=implementation",
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
