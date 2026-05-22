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

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReportServiceToolTest {
    @TempDir
    Path tempDir;

    @Test
    void localReportCanFilterAndAggregateTools() throws Exception {
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("local.sqlite"));
        store.initialize();
        store.insertLocalUsageEvent("codex", "codex-source", 1, event("codex-1", "codex", "codex-session", 100));
        store.insertLocalUsageEvent("claude-code", "claude-source", 1,
                event("claude-1", "claude-code", "claude-session", 200));

        ReportService service = new ReportService(store, ZoneId.of("UTC"));
        String allJson = service.report(Map.of("days", "1")).toJson();
        String claudeJson = service.report(Map.of("days", "1", "tool", "claude-code")).toJson();
        String codexJson = service.report(Map.of("days", "1", "tool", "codex")).toJson();

        assertTrue(allJson.contains("\"tools\""));
        assertTrue(allJson.contains("\"tool\":\"codex\""));
        assertTrue(allJson.contains("\"tool\":\"claude-code\""));
        assertTrue(claudeJson.contains("\"total_tokens\":200"));
        assertTrue(codexJson.contains("\"total_tokens\":100"));
    }

    @Test
    void localComparisonIncludesToolDeltas() throws Exception {
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("local.sqlite"));
        store.initialize();
        store.insertLocalUsageEvent("claude-code", "claude-source", 1,
                event("claude-today", "claude-code", "session-today", 200));
        store.insertLocalUsageEvent("claude-code", "claude-source", 2,
                new TeamUsageEvent("claude-yesterday", "claude-code", "session-yesterday", "claude-sonnet",
                        Instant.parse("2026-05-21T00:00:00Z"), LocalDate.parse("2026-05-21"),
                        new Snapshot(50, 0, 50, 0, 100), "", ""));

        ReportService service = new ReportService(store, ZoneId.of("UTC"));
        String json = service.report(Map.of("period", "day", "compare", "previous", "tool", "claude-code")).toJson();

        assertTrue(json.contains("\"tools\""));
        assertTrue(json.contains("\"tool\":\"claude-code\""));
        assertTrue(json.contains("\"delta_total_tokens\":100"));
    }

    private static TeamUsageEvent event(String key, String tool, String session, long totalTokens) {
        return new TeamUsageEvent(key, tool, session, "claude-sonnet", Instant.parse("2026-05-22T00:00:00Z"),
                LocalDate.parse("2026-05-22"), new Snapshot(totalTokens / 2, 0, totalTokens / 2, 0, totalTokens),
                "", "");
    }
}
