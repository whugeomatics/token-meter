package local.token.meter.ingestion;

import local.token.meter.domain.TeamUsageEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClaudeCodeUsageSourceTest {
    @TempDir
    Path tempDir;

    @Test
    void readsUsageJsonLinesWithoutPromptOrResponseContent() throws Exception {
        Path fixture = tempDir.resolve("claude-usage.jsonl");
        Files.writeString(fixture, "{"
                + "\"session_id\":\"claude-session\","
                + "\"model\":\"claude-sonnet\","
                + "\"timestamp\":\"2026-05-22T01:02:03Z\","
                + "\"input_tokens\":11,"
                + "\"cached_input_tokens\":2,"
                + "\"output_tokens\":7,"
                + "\"reasoning_output_tokens\":0,"
                + "\"total_tokens\":20,"
                + "\"prompt\":\"must not be copied\","
                + "\"response\":\"must not be copied\""
                + "}\n");

        List<TeamUsageEvent> events = new ClaudeCodeUsageSource(fixture, ZoneId.of("UTC"), "user-a", "device-a")
                .events();

        assertEquals(1, events.size());
        TeamUsageEvent event = events.get(0);
        assertEquals("claude-code", event.tool());
        assertEquals("claude-session", event.sessionId());
        assertEquals(20, event.usage().totalTokens());
        assertEquals("otel_metrics", event.sourceKind());
        assertEquals("official", event.sourceQuality());
    }
}
