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
        assertEquals("local_jsonl", event.sourceKind());
        assertEquals("reported", event.sourceQuality());
    }

    @Test
    void readsClaudeCodeProjectJsonlUsageWithoutContent() throws Exception {
        Path fixture = tempDir.resolve("project").resolve("session.jsonl");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, "{"
                + "\"type\":\"assistant\","
                + "\"sessionId\":\"claude-local-session\","
                + "\"timestamp\":\"2026-05-22T01:02:03Z\","
                + "\"message\":{"
                + "\"id\":\"msg_1\","
                + "\"type\":\"message\","
                + "\"role\":\"assistant\","
                + "\"model\":\"claude-sonnet\","
                + "\"usage\":{"
                + "\"input_tokens\":11,"
                + "\"cache_creation_input_tokens\":3,"
                + "\"cache_read_input_tokens\":2,"
                + "\"output_tokens\":7"
                + "},"
                + "\"content\":[{\"type\":\"text\",\"text\":\"must not be copied\"}]"
                + "}"
                + "}\n");

        List<TeamUsageEvent> events = new ClaudeCodeUsageSource(tempDir.resolve("project"), ZoneId.of("UTC"),
                "user-a", "device-a").events();

        assertEquals(1, events.size());
        TeamUsageEvent event = events.get(0);
        assertEquals("claude-code", event.tool());
        assertEquals("claude-local-session", event.sessionId());
        assertEquals("claude-sonnet", event.model());
        assertEquals(11, event.usage().inputTokens());
        assertEquals(5, event.usage().cachedInputTokens());
        assertEquals(7, event.usage().outputTokens());
        assertEquals(23, event.usage().totalTokens());
        assertEquals("local_jsonl", event.sourceKind());
        assertEquals("reported", event.sourceQuality());
    }

    @Test
    void deduplicatesRepeatedClaudeCodeRowsForTheSameMessage() throws Exception {
        Path fixture = tempDir.resolve("project").resolve("session.jsonl");
        Files.createDirectories(fixture.getParent());
        String first = claudeProjectLine("uuid-1", "parent-1", "msg-1", "2026-05-22T01:02:03.100Z");
        String duplicate = claudeProjectLine("uuid-2", "uuid-1", "msg-1", "2026-05-22T01:02:03.300Z");
        Files.writeString(fixture, first + "\n" + duplicate + "\n");

        List<TeamUsageEvent> events = new ClaudeCodeUsageSource(tempDir.resolve("project"), ZoneId.of("UTC"),
                "user-a", "device-a").events();

        assertEquals(1, events.size());
        assertEquals(23, events.get(0).usage().totalTokens());
    }

    private static String claudeProjectLine(String uuid, String parentUuid, String messageId, String timestamp) {
        return "{"
                + "\"type\":\"assistant\","
                + "\"uuid\":\"" + uuid + "\","
                + "\"parentUuid\":\"" + parentUuid + "\","
                + "\"sessionId\":\"claude-local-session\","
                + "\"timestamp\":\"" + timestamp + "\","
                + "\"message\":{"
                + "\"id\":\"" + messageId + "\","
                + "\"type\":\"message\","
                + "\"role\":\"assistant\","
                + "\"model\":\"claude-sonnet\","
                + "\"usage\":{"
                + "\"input_tokens\":11,"
                + "\"cache_creation_input_tokens\":3,"
                + "\"cache_read_input_tokens\":2,"
                + "\"output_tokens\":7"
                + "},"
                + "\"content\":[{\"type\":\"text\",\"text\":\"must not be copied\"}]"
                + "}"
                + "}";
    }
}
