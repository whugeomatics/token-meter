package local.token.meter.ingestion;

import local.token.meter.domain.DeviceTokenBinding;
import local.token.meter.domain.Snapshot;
import local.token.meter.domain.TeamIngestResult;
import local.token.meter.domain.TeamUploadRecord;
import local.token.meter.domain.TeamUsageEvent;
import local.token.meter.store.TeamUsageStore;
import local.token.meter.util.Json;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public final class TeamIngestionService {
    private static final int MAX_EVENTS_PER_BATCH = 500;
    private static final int MAX_PAYLOAD_CHARS = 1_048_576;
    private static final List<String> FORBIDDEN_EVENT_FIELDS = List.of(
            "prompt", "response", "raw_api_body", "transcript"
    );

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
        Instant uploadTime = Instant.now();
        String uploadDate = uploadTime.atZone(zone).toLocalDate().toString();
        if (eventObjects.size() > MAX_EVENTS_PER_BATCH) {
            return TeamIngestResult.error("payload_too_large", "too many events in one batch");
        }
        try {
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
            store.insertTeamUpload(new TeamUploadRecord(binding.teamId(), binding.userId(), binding.deviceId(),
                    uploadDate, uploadTime.toString(), eventObjects.size(), accepted, duplicate, rejected,
                    "ok", ""));
            return TeamIngestResult.ok(accepted, duplicate, rejected, eventObjects.size(), binding, uploadDate);
        } catch (SQLException e) {
            recordUploadFailure(binding, uploadDate, uploadTime, eventObjects.size(), "sqlite_error");
            return TeamIngestResult.error("storage_error", "team upload storage failed: " + safeSqliteMessage(e),
                    eventObjects.size(), binding, uploadDate);
        }
    }

    private void recordUploadFailure(DeviceTokenBinding binding, String uploadDate, Instant uploadTime,
                                     int eventCount, String message) {
        try {
            store.insertTeamUpload(new TeamUploadRecord(binding.teamId(), binding.userId(), binding.deviceId(),
                    uploadDate, uploadTime.toString(), eventCount, 0, 0, 0, "error", message));
        } catch (SQLException ignored) {
            // Upload diagnostics are best-effort; never expose tokens or raw payloads.
        }
    }

    private String safeSqliteMessage(SQLException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private TeamUsageEvent parseEvent(String json, String clientUserId, String clientDeviceId) {
        try {
            String eventKey = Json.firstString(json, "event_key").orElse("");
            String tool = Json.firstString(json, "tool").orElse("");
            String sessionId = Json.firstString(json, "session_id").orElse("");
            String model = Json.firstString(json, "model").orElse("unknown");
            String timestampValue = Json.firstString(json, "timestamp").orElse("");
            if (eventKey.isBlank() || !knownTool(tool) || sessionId.isBlank() || timestampValue.isBlank()
                    || hasForbiddenEventField(json)) {
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
            String sourceKind = Json.firstString(json, "source_kind").orElse("");
            String sourceQuality = Json.firstString(json, "source_quality").orElse("");
            return new TeamUsageEvent(eventKey, tool, sessionId, model, timestamp, localDate, usage, clientUserId,
                    clientDeviceId, sourceKind, sourceQuality);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean knownTool(String tool) {
        return "codex".equals(tool) || "claude-code".equals(tool);
    }

    private boolean hasForbiddenEventField(String json) {
        for (String field : FORBIDDEN_EVENT_FIELDS) {
            if (json.contains("\"" + field + "\"")) {
                return true;
            }
        }
        return false;
    }

    public static String eventsToJson(String collectorVersion, String clientUserId, String clientDeviceId,
                                      List<TeamUsageEvent> events) {
        return TeamUsagePayload.eventsToJson(collectorVersion, clientUserId, clientDeviceId, events);
    }
}
