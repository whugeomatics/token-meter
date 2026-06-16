package local.token.meter.report;

import local.token.meter.domain.Snapshot;
import local.token.meter.domain.TeamUsageEvent;
import local.token.meter.store.SqliteUsageStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReportServiceToolTest {
    @TempDir
    Path tempDir;

    @Test
    void localReportCanFilterAndAggregateTools() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("local.sqlite"));
        store.initialize();
        store.insertLocalUsageEvent("codex", "codex-source", 1,
                event("codex-1", "codex", "codex-session", 100, today));
        store.insertLocalUsageEvent("claude-code", "claude-source", 1,
                event("claude-1", "claude-code", "claude-session", 200, today));

        ReportService service = new ReportService(store, ZoneId.of("UTC"));
        String allJson = service.report(Map.of("days", "1")).toJson();
        String claudeJson = service.report(Map.of("days", "1", "tool", "claude-code")).toJson();
        String codexJson = service.report(Map.of("days", "1", "tool", "codex")).toJson();

        assertTrue(allJson.contains("\"tools\""));
        assertTrue(allJson.contains("\"tool\":\"codex\""));
        assertTrue(allJson.contains("\"tool\":\"claude-code\""));
        assertTrue(allJson.contains("\"tools\":[\"codex\"]"));
        assertTrue(allJson.contains("\"tools\":[\"claude-code\"]"));
        assertTrue(claudeJson.contains("\"total_tokens\":200"));
        assertTrue(codexJson.contains("\"total_tokens\":100"));
    }

    @Test
    void localComparisonIncludesToolDeltas() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        LocalDate yesterday = today.minusDays(1);
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("local.sqlite"));
        store.initialize();
        store.insertLocalUsageEvent("claude-code", "claude-source", 1,
                event("claude-today", "claude-code", "session-today", 200, today));
        store.insertLocalUsageEvent("claude-code", "claude-source", 2,
                new TeamUsageEvent("claude-yesterday", "claude-code", "session-yesterday", "claude-sonnet",
                        Instant.parse(yesterday + "T00:00:00Z"), yesterday,
                        new Snapshot(50, 0, 50, 0, 100), "", ""));

        ReportService service = new ReportService(store, ZoneId.of("UTC"));
        String json = service.report(Map.of("period", "day", "compare", "previous", "tool", "claude-code")).toJson();

        assertTrue(json.contains("\"tools\""));
        assertTrue(json.contains("\"tool\":\"claude-code\""));
        assertTrue(json.contains("\"delta_total_tokens\":100"));
    }

    @Test
    void localSessionsReportPaginatesTenRowsPerPage() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("sessions.sqlite"));
        store.initialize();
        for (int index = 0; index < 55; index++) {
            store.insertLocalUsageEvent("codex", "codex-source", index + 1,
                    event("codex-" + index, "codex", "session-" + index, 100, today,
                            Instant.parse(today + "T00:" + String.format("%02d", index) + ":00Z")));
        }

        ReportService service = new ReportService(store, ZoneId.of("UTC"));
        String firstPage = service.sessions(Map.of("period", "day", "compare", "previous", "page", "1")).toJson();
        String secondPage = service.sessions(Map.of("period", "day", "compare", "previous", "page", "2")).toJson();

        assertTrue(firstPage.contains("\"page_size\":10"));
        assertTrue(firstPage.contains("\"total\":55"));
        assertTrue(firstPage.contains("\"total_pages\":6"));
        assertTrue(firstPage.contains("\"session_id\":\"session-54\""));
        assertFalse(firstPage.contains("\"session_id\":\"session-44\""));
        assertTrue(secondPage.contains("\"page\":2"));
        assertTrue(secondPage.contains("\"session_id\":\"session-44\""));
        assertFalse(secondPage.contains("\"session_id\":\"session-54\""));
    }

    @Test
    void localReportDeduplicatesCallCountWithoutHidingUsageEvents() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        Instant timestamp = Instant.parse(today + "T00:00:00Z");
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("local-calls.sqlite"));
        store.initialize();
        store.insertLocalUsageEvent("claude-code", "source-a", 1,
                event("event-a", "claude-code", "session-a", 100, today, timestamp));
        store.insertLocalUsageEvent("claude-code", "source-b", 1,
                event("event-b", "claude-code", "session-a", 100, today, timestamp));

        String json = new ReportService(store, ZoneId.of("UTC")).report(Map.of("days", "1")).toJson();

        assertTrue(json.contains("\"usage_event_count\":2"));
        assertTrue(json.contains("\"call_count\":1"));
    }

    @Test
    void localSessionsActiveSecondsIgnoreLongIdleGaps() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        Instant first = Instant.parse(today.minusDays(8) + "T10:00:00Z");
        Instant second = Instant.parse(today + "T10:00:00Z");
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("local-active.sqlite"));
        store.initialize();
        store.insertLocalUsageEvent("codex", "source-a", 1,
                event("event-a", "codex", "long-session", 100, today.minusDays(8), first));
        store.insertLocalUsageEvent("codex", "source-a", 2,
                event("event-b", "codex", "long-session", 100, today, second));

        String json = new ReportService(store, ZoneId.of("UTC"))
                .sessions(Map.of("period", "month", "compare", "previous", "page", "1")).toJson();

        assertTrue(json.contains("\"session_id\":\"long-session\""));
        assertTrue(json.contains("\"active_seconds\":0"), json);
        assertFalse(json.contains("\"active_seconds\":691200"), json);
    }

    private static TeamUsageEvent event(String key, String tool, String session, long totalTokens, LocalDate date) {
        return event(key, tool, session, totalTokens, date, Instant.parse(date + "T00:00:00Z"));
    }

    private static TeamUsageEvent event(String key, String tool, String session, long totalTokens, LocalDate date,
                                        Instant timestamp) {
        return new TeamUsageEvent(key, tool, session, "claude-sonnet", timestamp,
                date, new Snapshot(totalTokens / 2, 0, totalTokens / 2, 0, totalTokens),
                "", "");
    }
}
