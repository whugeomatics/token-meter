package local.token.meter.ingestion;

import local.token.meter.report.ReportService;
import local.token.meter.store.SqliteUsageStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClaudeCodeLocalIngestionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void ingestsClaudeCodeFixtureIntoLocalReport() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        Path fixture = tempDir.resolve("claude-usage.jsonl");
        Files.writeString(fixture, "{"
                + "\"session_id\":\"local-claude-session\","
                + "\"model\":\"claude-sonnet\","
                + "\"timestamp\":\"" + today + "T00:00:00Z\","
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

        assertEquals(1, result.eventsInserted(), result.toJson());
        String json = new ReportService(store, ZoneId.of("UTC"))
                .report(Map.of("days", "1", "tool", "claude-code")).toJson();
        assertTrue(json.contains("\"total_tokens\":25"));
        assertTrue(json.contains("\"tool\":\"claude-code\""));
    }

    @Test
    void ingestsClaudeCodeProjectDirectoryIntoLocalReport() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        Path session = tempDir.resolve("claude-projects").resolve("project-a").resolve("session.jsonl");
        Files.createDirectories(session.getParent());
        Files.writeString(session, "{"
                + "\"type\":\"assistant\","
                + "\"sessionId\":\"local-claude-session\","
                + "\"timestamp\":\"" + today + "T00:00:00Z\","
                + "\"message\":{"
                + "\"id\":\"msg_1\","
                + "\"type\":\"message\","
                + "\"role\":\"assistant\","
                + "\"model\":\"claude-sonnet\","
                + "\"usage\":{"
                + "\"input_tokens\":10,"
                + "\"cache_creation_input_tokens\":4,"
                + "\"cache_read_input_tokens\":6,"
                + "\"output_tokens\":15"
                + "},"
                + "\"content\":[{\"type\":\"text\",\"text\":\"must not be copied\"}]"
                + "}"
                + "}\n");
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("local.sqlite"));
        store.initialize();

        IngestionResult result = new ClaudeCodeLocalIngestionService(tempDir.resolve("claude-projects"),
                ZoneId.of("UTC"), store).ingest();

        assertTrue(result.errors().isEmpty(), result.toJson());
        assertEquals(1, result.eventsInserted(), result.toJson());
        String json = new ReportService(store, ZoneId.of("UTC"))
                .report(Map.of("days", "1", "tool", "claude-code")).toJson();
        assertTrue(json.contains("\"total_tokens\":35"));
        assertTrue(json.contains("\"input_tokens\":20"));
        assertTrue(json.contains("\"cached_input_tokens\":10"));
        assertTrue(json.contains("\"net_input_tokens\":10"));
        assertTrue(json.contains("\"source_kind\":{\"local_jsonl\":1"));
        assertTrue(json.contains("\"source_quality\":{\"reported\":1"));
        assertTrue(json.contains("\"tool\":\"claude-code\""));
    }
}
