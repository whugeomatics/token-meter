package local.token.meter.ingestion;

import local.token.meter.domain.DeviceTokenBinding;
import local.token.meter.domain.StoredTeamUsageEvent;
import local.token.meter.domain.TeamIngestResult;
import local.token.meter.report.TeamReportService;
import local.token.meter.store.ShardedTeamUsageStore;
import local.token.meter.store.SqliteTeamUsageStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TeamIngestionServiceToolTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsClaudeCodeEventsAndReportCanFilterByTool() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        String timestamp = today + "T00:00:00Z";
        SqliteTeamUsageStore store = new SqliteTeamUsageStore(tempDir.resolve("team.sqlite"));
        store.initialize();
        store.upsertDeviceToken("secret-token",
                new DeviceTokenBinding("team-a", "user-a", "device-a", "Alice", "active"));

        TeamIngestionService ingestion = new TeamIngestionService(store, zone);
        TeamIngestResult result = ingestion.ingest("secret-token", "{"
                + "\"collector_version\":\"0.1.0\","
                + "\"client_user_id\":\"user-a\","
                + "\"client_device_id\":\"device-a\","
                + "\"events\":[{"
                + "\"event_key\":\"claude-code|session-a|" + timestamp + "|100\","
                + "\"tool\":\"claude-code\","
                + "\"session_id\":\"session-a\","
                + "\"model\":\"claude-sonnet\","
                + "\"timestamp\":\"" + timestamp + "\","
                + "\"input_tokens\":40,"
                + "\"cached_input_tokens\":10,"
                + "\"output_tokens\":50,"
                + "\"reasoning_output_tokens\":0,"
                + "\"total_tokens\":100,"
                + "\"source_kind\":\"otel_metric\","
                + "\"source_quality\":\"reported\""
                + "}]"
                + "}");

        assertEquals("ok", result.status());
        assertEquals(1, result.accepted());

        TeamReportService reports = new TeamReportService(store, zone);
        String allJson = reports.report(Map.of("days", "1")).toJson();
        String claudeJson = reports.report(Map.of("days", "1", "tool", "claude-code")).toJson();
        String codexJson = reports.report(Map.of("days", "1", "tool", "codex")).toJson();

        assertTrue(allJson.contains("\"tools\""));
        assertTrue(allJson.contains("\"tool\":\"claude-code\""));
        assertTrue(allJson.contains("\"source_kind\":{\"otel_metric\":1"));
        assertTrue(allJson.contains("\"source_quality\":{\"reported\":1"));
        assertTrue(allJson.contains("\"team_models\""));
        assertTrue(allJson.contains("\"tool\":\"claude-code\",\"model\":\"claude-sonnet\""));
        assertTrue(allJson.contains("\"upload_time\":\"" + today + "T"));
        assertTrue(allJson.contains("+08:00\""));
        assertTrue(claudeJson.contains("\"total_tokens\":100"));
        assertTrue(codexJson.contains("\"total_tokens\":0"));
    }

    @Test
    void appliesCanonicalFallbacksForMissingTotalAndSourceMetadata() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        String timestamp = today + "T00:00:00Z";
        SqliteTeamUsageStore store = new SqliteTeamUsageStore(tempDir.resolve("team-fallback.sqlite"));
        store.initialize();
        store.upsertDeviceToken("secret-token",
                new DeviceTokenBinding("team-a", "user-a", "device-a", "Alice", "active"));

        TeamIngestionService ingestion = new TeamIngestionService(store, zone);
        TeamIngestResult result = ingestion.ingest("secret-token", "{"
                + "\"client_user_id\":\"user-a\","
                + "\"client_device_id\":\"device-a\","
                + "\"events\":[{"
                + "\"event_key\":\"missing-total\","
                + "\"tool\":\"claude-code\","
                + "\"session_id\":\"session-a\","
                + "\"model\":\"claude-sonnet\","
                + "\"timestamp\":\"" + timestamp + "\","
                + "\"input_tokens\":100,"
                + "\"cached_input_tokens\":40,"
                + "\"output_tokens\":10,"
                + "\"reasoning_output_tokens\":3"
                + "}]"
                + "}");

        assertEquals("ok", result.status());
        assertEquals(1, result.accepted());
        List<StoredTeamUsageEvent> events = store.loadTeamEvents(today, today);
        assertEquals(1, events.size());
        assertEquals(113, events.get(0).usage().totalTokens());
        assertEquals("unknown", events.get(0).sourceKind());
        assertEquals("unknown", events.get(0).sourceQuality());
    }

    @Test
    void rejectsUnknownToolsAndForbiddenContentFields() throws Exception {
        SqliteTeamUsageStore store = new SqliteTeamUsageStore(tempDir.resolve("team.sqlite"));
        store.initialize();
        store.upsertDeviceToken("secret-token",
                new DeviceTokenBinding("team-a", "user-a", "device-a", "Alice", "active"));

        TeamIngestionService ingestion = new TeamIngestionService(store, ZoneId.of("UTC"));
        TeamIngestResult result = ingestion.ingest("secret-token", "{"
                + "\"client_user_id\":\"user-a\","
                + "\"client_device_id\":\"device-a\","
                + "\"events\":["
                + eventJson("bad-tool", "\"tool\":\"cursor\"")
                + ","
                + eventJson("bad-content", "\"tool\":\"claude-code\",\"prompt\":\"do not store\"")
                + "]"
                + "}");

        assertEquals("ok", result.status());
        assertEquals(0, result.accepted());
        assertEquals(2, result.rejected());
    }

    @Test
    void teamReportDeduplicatesCallCountWithoutHidingUsageEvents() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        String timestamp = today + "T00:00:00Z";
        SqliteTeamUsageStore store = new SqliteTeamUsageStore(tempDir.resolve("team-calls.sqlite"));
        store.initialize();
        store.upsertDeviceToken("secret-token",
                new DeviceTokenBinding("team-a", "user-a", "device-a", "Alice", "active"));

        TeamIngestionService ingestion = new TeamIngestionService(store, zone);
        TeamIngestResult result = ingestion.ingest("secret-token", "{"
                + "\"client_user_id\":\"user-a\","
                + "\"client_device_id\":\"device-a\","
                + "\"events\":["
                + eventJson("event-a", "\"tool\":\"claude-code\"", timestamp, 100)
                + ","
                + eventJson("event-b", "\"tool\":\"claude-code\"", timestamp, 100)
                + "]"
                + "}");

        assertEquals(2, result.accepted());
        String json = new TeamReportService(store, ZoneId.of("UTC")).report(Map.of("days", "1")).toJson();
        assertTrue(json.contains("\"usage_event_count\":2"));
        assertTrue(json.contains("\"call_count\":1"));
    }

    @Test
    void teamReportActiveSecondsIgnoreLongIdleGapsInSameSession() throws Exception {
        ZoneId zone = ZoneId.of("UTC");
        LocalDate today = LocalDate.now(zone);
        String firstTimestamp = today.minusDays(8) + "T10:00:00Z";
        String secondTimestamp = today + "T10:00:00Z";
        SqliteTeamUsageStore store = new SqliteTeamUsageStore(tempDir.resolve("team-active.sqlite"));
        store.initialize();
        store.upsertDeviceToken("secret-token",
                new DeviceTokenBinding("team-a", "user-a", "device-a", "Alice", "active"));

        TeamIngestionService ingestion = new TeamIngestionService(store, zone);
        TeamIngestResult result = ingestion.ingest("secret-token", "{"
                + "\"client_user_id\":\"user-a\","
                + "\"client_device_id\":\"device-a\","
                + "\"events\":["
                + eventJson("idle-start", "\"tool\":\"codex\"", firstTimestamp, 100)
                + ","
                + eventJson("idle-end", "\"tool\":\"codex\"", secondTimestamp, 100)
                + "]"
                + "}");

        assertEquals(2, result.accepted());
        String json = new TeamReportService(store, zone).report(Map.of("days", "30")).toJson();
        assertTrue(json.contains("\"active_seconds\":0"));
        assertFalse(json.contains("\"active_seconds\":691200"), json);
    }

    @Test
    void shardedTeamStorePreservesEventKeyWhenEnrichingDeviceBinding() throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        String timestamp = today + "T00:00:00Z";
        ShardedTeamUsageStore store = new ShardedTeamUsageStore(tempDir.resolve("team-shards"));
        store.initialize();
        store.upsertDeviceToken("secret-token",
                new DeviceTokenBinding("team-a", "user-a", "device-a", "Alice", "active"));

        TeamIngestionService ingestion = new TeamIngestionService(store, zone);
        TeamIngestResult result = ingestion.ingest("secret-token", "{"
                + "\"client_user_id\":\"user-a\","
                + "\"client_device_id\":\"device-a\","
                + "\"events\":["
                + eventJson("event-a", "\"tool\":\"codex\"", timestamp, 100)
                + "]"
                + "}");

        assertEquals(1, result.accepted());
        List<StoredTeamUsageEvent> events = store.loadTeamEvents(today, today);
        assertEquals(1, events.size());
        assertEquals("event-a", events.get(0).eventKey());
        assertEquals("Alice", events.get(0).userDisplayName());
    }

    private static String eventJson(String key, String toolFields) {
        return eventJson(key, toolFields, "2026-05-22T00:00:00Z", 2);
    }

    private static String eventJson(String key, String toolFields, String timestamp, long totalTokens) {
        return "{"
                + "\"event_key\":\"" + key + "\","
                + toolFields + ","
                + "\"session_id\":\"session-a\","
                + "\"model\":\"model-a\","
                + "\"timestamp\":\"" + timestamp + "\","
                + "\"input_tokens\":1,"
                + "\"cached_input_tokens\":0,"
                + "\"output_tokens\":1,"
                + "\"reasoning_output_tokens\":0,"
                + "\"total_tokens\":" + totalTokens
                + "}";
    }
}
