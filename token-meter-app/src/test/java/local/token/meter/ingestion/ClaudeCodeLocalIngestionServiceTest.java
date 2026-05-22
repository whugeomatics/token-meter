package local.token.meter.ingestion;

import local.token.meter.report.ReportService;
import local.token.meter.store.SqliteUsageStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClaudeCodeLocalIngestionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void ingestsClaudeCodeFixtureIntoLocalReport() throws Exception {
        Path fixture = tempDir.resolve("claude-usage.jsonl");
        Files.writeString(fixture, "{"
                + "\"session_id\":\"local-claude-session\","
                + "\"model\":\"claude-sonnet\","
                + "\"timestamp\":\"2026-05-22T00:00:00Z\","
                + "\"input_tokens\":10,"
                + "\"cached_input_tokens\":0,"
                + "\"output_tokens\":15,"
                + "\"reasoning_output_tokens\":0,"
                + "\"total_tokens\":25,"
                + "\"prompt\":\"ignored\","
                + "\"response\":\"ignored\""
                + "}\n");
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("local.sqlite"));
        store.initialize();

        IngestionResult result = new ClaudeCodeLocalIngestionService(fixture, ZoneId.of("UTC"), store).ingest();

        assertEquals(1, result.eventsInserted());
        String json = new ReportService(store, ZoneId.of("UTC"))
                .report(Map.of("days", "1", "tool", "claude-code")).toJson();
        assertTrue(json.contains("\"total_tokens\":25"));
        assertTrue(json.contains("\"tool\":\"claude-code\""));
    }
}
