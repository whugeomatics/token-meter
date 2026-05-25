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

final class LocalIngestionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void ingestsCodexAndClaudeIntoOneLocalReport() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        Path codexSessions = tempDir.resolve("codex-sessions");
        Path claudeProjects = tempDir.resolve("claude-projects");
        Path claudeSession = claudeProjects.resolve("project-a").resolve("session.jsonl");
        Files.createDirectories(codexSessions);
        Files.createDirectories(claudeSession.getParent());
        Files.writeString(claudeSession, "{"
                + "\"type\":\"assistant\","
                + "\"sessionId\":\"claude-session\","
                + "\"timestamp\":\"" + today + "T00:00:00Z\","
                + "\"message\":{"
                + "\"model\":\"claude-sonnet\","
                + "\"usage\":{"
                + "\"input_tokens\":10,"
                + "\"cache_creation_input_tokens\":5,"
                + "\"cache_read_input_tokens\":5,"
                + "\"output_tokens\":20"
                + "},"
                + "\"content\":[{\"type\":\"text\",\"text\":\"must not be copied\"}]"
                + "}"
                + "}\n");
        SqliteUsageStore store = new SqliteUsageStore(tempDir.resolve("local.sqlite"));
        store.initialize();

        IngestionResult result = new LocalIngestionService(
                new CodexIngestionService(codexSessions, ZoneId.of("UTC"), store),
                new ClaudeCodeLocalIngestionService(claudeProjects, ZoneId.of("UTC"), store)
        ).ingest();

        assertTrue(result.errors().isEmpty(), result.toJson());
        assertEquals(1, result.eventsInserted(), result.toJson());
        String json = new ReportService(store, ZoneId.of("UTC")).report(Map.of("days", "1")).toJson();
        assertTrue(json.contains("\"tool\":\"claude-code\""));
        assertTrue(json.contains("\"total_tokens\":40"));
        assertTrue(json.contains("\"input_tokens\":20"));
        assertTrue(json.contains("\"cached_input_tokens\":10"));
    }
}
