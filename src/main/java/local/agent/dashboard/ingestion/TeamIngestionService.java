package local.agent.dashboard.ingestion;

import local.agent.dashboard.domain.DeviceTokenBinding;
import local.agent.dashboard.domain.Snapshot;
import local.agent.dashboard.domain.TeamIngestResult;
import local.agent.dashboard.domain.TeamUsageEvent;
import local.agent.dashboard.store.TeamUsageStore;
import local.agent.dashboard.util.Json;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TeamIngestionService {
    private static final int MAX_EVENTS_PER_BATCH = 500;
    private static final int MAX_PAYLOAD_CHARS = 1_048_576;

    private final TeamUsageStore store;
    private final ZoneId zone;

    public TeamIngestionService(TeamUsageStore store, ZoneId zone) {
        this.store = store;
        this.zone = zone;
    }

    public TeamIngestResult ingest(String token, String payload) throws SQLException {
        if (payload != null && payload.length() > MAX_PAYLOAD_CHARS) {
            return TeamIngestResult.error("payload_too_large", "payload exceeds 1 MB");
        }
        if (token == null || token.isBlank()) {
            return TeamIngestResult.error("unauthorized", "missing device token");
        }
        DeviceTokenBinding binding = store.findDeviceToken(token);
        if (binding == null) {
            return TeamIngestResult.error("unauthorized", "unknown device token");
        }
        if (!binding.active()) {
            return TeamIngestResult.error("forbidden", "device token is not active");
        }

        String clientUserId = Json.firstString(payload, "client_user_id").orElse("");
        String clientDeviceId = Json.firstString(payload, "client_device_id").orElse("");
        if (!clientUserId.isBlank() && !binding.userId().equals(clientUserId)) {
            return TeamIngestResult.error("identity_conflict", "client user does not match device token");
        }
        if (!clientDeviceId.isBlank() && !binding.deviceId().equals(clientDeviceId)) {
            return TeamIngestResult.error("identity_conflict", "client device does not match device token");
        }

        Optional<String> eventsJson = Json.arraySection(payload, "events");
        if (eventsJson.isEmpty()) {
            return TeamIngestResult.error("invalid_payload", "events array is required");
        }

        int accepted = 0;
        int duplicate = 0;
        int rejected = 0;
        List<String> eventObjects = Json.objectElements(eventsJson.get());
        if (eventObjects.size() > MAX_EVENTS_PER_BATCH) {
            return TeamIngestResult.error("payload_too_large", "too many events in one batch");
        }
        for (String eventJson : eventObjects) {
            TeamUsageEvent event = parseEvent(eventJson, clientUserId, clientDeviceId);
            if (event == null) {
                rejected++;
                continue;
            }
            if (store.insertTeamUsageEvent(binding, event)) {
                accepted++;
            } else {
                duplicate++;
            }
        }
        store.updateDeviceTokenSeen(token);
        return TeamIngestResult.ok(accepted, duplicate, rejected);
    }

    private TeamUsageEvent parseEvent(String json, String clientUserId, String clientDeviceId) {
        try {
            String eventKey = Json.firstString(json, "event_key").orElse("");
            String tool = Json.firstString(json, "tool").orElse("");
            String sessionId = Json.firstString(json, "session_id").orElse("");
            String model = Json.firstString(json, "model").orElse("unknown");
            String timestampValue = Json.firstString(json, "timestamp").orElse("");
            if (eventKey.isBlank() || !"codex".equals(tool) || sessionId.isBlank() || timestampValue.isBlank()) {
                return null;
            }
            Instant timestamp = Instant.parse(timestampValue);
            Snapshot usage = new Snapshot(
                    Json.longValue(json, "input_tokens").orElse(0L),
                    Json.longValue(json, "cached_input_tokens").orElse(0L),
                    Json.longValue(json, "output_tokens").orElse(0L),
                    Json.longValue(json, "reasoning_output_tokens").orElse(0L),
                    Json.longValue(json, "total_tokens").orElse(0L)
            );
            if (!usage.hasPositiveUsage()) {
                return null;
            }
            LocalDate localDate = timestamp.atZone(zone).toLocalDate();
            return new TeamUsageEvent(eventKey, tool, sessionId, model, timestamp, localDate, usage, clientUserId, clientDeviceId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String eventsToJson(String collectorVersion, String clientUserId, String clientDeviceId,
                                      List<TeamUsageEvent> events) {
        List<String> rows = new ArrayList<>();
        for (TeamUsageEvent event : events) {
            rows.add("{"
                    + "\"event_key\":\"" + Json.escape(event.eventKey()) + "\","
                    + "\"tool\":\"codex\","
                    + "\"session_id\":\"" + Json.escape(event.sessionId()) + "\","
                    + "\"model\":\"" + Json.escape(event.model()) + "\","
                    + "\"timestamp\":\"" + event.timestamp() + "\","
                    + "\"input_tokens\":" + event.usage().inputTokens() + ","
                    + "\"cached_input_tokens\":" + event.usage().cachedInputTokens() + ","
                    + "\"output_tokens\":" + event.usage().outputTokens() + ","
                    + "\"reasoning_output_tokens\":" + event.usage().reasoningOutputTokens() + ","
                    + "\"total_tokens\":" + event.usage().totalTokens()
                    + "}");
        }
        return "{"
                + "\"collector_version\":\"" + Json.escape(collectorVersion) + "\","
                + "\"client_user_id\":\"" + Json.escape(clientUserId) + "\","
                + "\"client_device_id\":\"" + Json.escape(clientDeviceId) + "\","
                + "\"events\":[" + String.join(",", rows) + "]"
                + "}";
    }
}
