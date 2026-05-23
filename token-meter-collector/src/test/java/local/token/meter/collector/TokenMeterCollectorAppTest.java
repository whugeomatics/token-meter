package local.token.meter.collector;

import local.token.meter.app.AppConfig;
import local.token.meter.ingestion.SessionUsageScanner;
import local.token.meter.ingestion.TeamCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TokenMeterCollectorAppTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultTeamCollectionCollectsCodexAndClaudeCodeWhenBothExist() throws Exception {
        Path codexDir = tempDir.resolve("codex");
        Path claudeDir = tempDir.resolve("claude").resolve("project");
        Files.createDirectories(codexDir);
        Files.createDirectories(claudeDir);
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        Files.writeString(codexDir.resolve("session.jsonl"), ""
                + "{\"timestamp\":\"" + today + "T00:00:00Z\",\"type\":\"session_meta\",\"payload\":{\"id\":\"codex-session\"}}\n"
                + "{\"timestamp\":\"" + today + "T00:00:01Z\",\"type\":\"turn_context\",\"payload\":{\"model\":\"gpt-5-codex\"}}\n"
                + "{\"timestamp\":\"" + today + "T00:00:02Z\",\"type\":\"event_msg\",\"payload\":{\"type\":\"token_count\",\"info\":{\"total_token_usage\":{\"input_tokens\":10,\"cached_input_tokens\":0,\"output_tokens\":5,\"reasoning_output_tokens\":0,\"total_tokens\":15}}}}\n");
        Files.writeString(claudeDir.resolve("session.jsonl"), "{"
                + "\"type\":\"assistant\","
                + "\"sessionId\":\"claude-session\","
                + "\"timestamp\":\"" + today + "T00:00:03Z\","
                + "\"message\":{"
                + "\"id\":\"claude-message\","
                + "\"model\":\"claude-sonnet\","
                + "\"usage\":{\"input_tokens\":20,\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":0,\"output_tokens\":6}"
                + "}"
                + "}\n");

        AppConfig config = AppConfig.from(new String[]{
                "--sessions-dir=" + codexDir,
                "--claude-projects-dir=" + claudeDir,
                "--user-id=user-a",
                "--device-id=device-a"
        });
        TeamCollector collector = new TeamCollector(new SessionUsageScanner(codexDir, config.zone()),
                config.zone(), "http://127.0.0.1:1", "test-token", "user-a", "device-a", 500);
        List<String> tools = TokenMeterCollectorApp.collectAllToolEvents(config, collector,
                        LocalDate.now(config.zone()).minusDays(6), LocalDate.now(config.zone()))
                .stream()
                .map(event -> event.tool())
                .toList();

        assertTrue(tools.contains("codex"));
        assertTrue(tools.contains("claude-code"));
    }

    @Test
    void claudeCodeCollectionDefaultsToUserClaudeProjectsDirectory() throws Exception {
        AppConfig config = AppConfig.from(new String[]{"--collect-claude-code"});

        assertEquals(Path.of(System.getProperty("user.home"), ".claude", "projects").toString(),
                TokenMeterCollectorApp.claudeInput(config, "local"));
        assertEquals("local_jsonl", TokenMeterCollectorApp.claudeSourceKind("local"));
        assertEquals("reported", TokenMeterCollectorApp.claudeSourceQuality("local"));
    }

    @Test
    void claudeCodeCollectionAllowsExplicitProjectsDirectory() throws Exception {
        AppConfig config = AppConfig.from(new String[]{
                "--collect-claude-code",
                "--claude-projects-dir=/tmp/claude-projects"
        });

        assertEquals("/tmp/claude-projects", TokenMeterCollectorApp.claudeInput(config, "local"));
    }

    @Test
    void errorOutputIncludesUploadTimeForDiagnostics() {
        String json = TokenMeterCollectorApp.errorJson(new IOException("collector cannot connect"),
                Instant.parse("2026-05-23T11:58:23Z"));

        assertTrue(json.contains("\"status\":\"error\""));
        assertTrue(json.contains("\"error_code\":\"collector_upload_failed\""));
        assertTrue(json.contains("\"upload_time\":\"2026-05-23T11:58:23Z\""));
        assertTrue(json.contains("\"message\":\"collector cannot connect\""));
    }
}
