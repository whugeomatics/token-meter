package local.token.meter.ingestion;

import local.token.meter.domain.Snapshot;
import local.token.meter.domain.TeamUsageEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TeamUsagePayloadTest {
    @Test
    void serializesEventToolAndOptionalSourceMetadata() {
        TeamUsageEvent event = new TeamUsageEvent("event-1", "claude-code", "session-1", "claude-sonnet",
                Instant.parse("2026-05-22T00:00:00Z"), LocalDate.parse("2026-05-22"),
                new Snapshot(1, 2, 3, 4, 10), "user-a", "device-a", "otel_metric", "reported");

        String json = TeamUsagePayload.eventsToJson("0.1.0", "user-a", "device-a", List.of(event));

        assertTrue(json.contains("\"tool\":\"claude-code\""));
        assertTrue(json.contains("\"source_kind\":\"otel_metric\""));
        assertTrue(json.contains("\"source_quality\":\"reported\""));
        assertFalse(json.contains("\"tool\":\"codex\",\"session_id\""));
    }
}
