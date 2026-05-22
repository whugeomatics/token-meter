package local.token.meter.ingestion;

import local.token.meter.domain.TeamUsageEvent;
import local.token.meter.util.Json;

import java.util.ArrayList;
import java.util.List;

public final class TeamUsagePayload {
    private TeamUsagePayload() {
    }

    public static String eventsToJson(String collectorVersion, String clientUserId, String clientDeviceId,
                                      List<TeamUsageEvent> events) {
        List<String> rows = new ArrayList<>();
        for (TeamUsageEvent event : events) {
            rows.add("{"
                    + "\"event_key\":\"" + Json.escape(event.eventKey()) + "\","
                    + "\"tool\":\"" + Json.escape(event.tool()) + "\","
                    + "\"session_id\":\"" + Json.escape(event.sessionId()) + "\","
                    + "\"model\":\"" + Json.escape(event.model()) + "\","
                    + "\"timestamp\":\"" + event.timestamp() + "\","
                    + "\"input_tokens\":" + event.usage().inputTokens() + ","
                    + "\"cached_input_tokens\":" + event.usage().cachedInputTokens() + ","
                    + "\"output_tokens\":" + event.usage().outputTokens() + ","
                    + "\"reasoning_output_tokens\":" + event.usage().reasoningOutputTokens() + ","
                    + "\"total_tokens\":" + event.usage().totalTokens()
                    + sourceMetadata(event)
                    + "}");
        }
        return "{"
                + "\"collector_version\":\"" + Json.escape(collectorVersion) + "\","
                + "\"client_user_id\":\"" + Json.escape(clientUserId) + "\","
                + "\"client_device_id\":\"" + Json.escape(clientDeviceId) + "\","
                + "\"events\":[" + String.join(",", rows) + "]"
                + "}";
    }

    private static String sourceMetadata(TeamUsageEvent event) {
        StringBuilder out = new StringBuilder();
        if (event.sourceKind() != null && !event.sourceKind().isBlank()) {
            out.append(",\"source_kind\":\"").append(Json.escape(event.sourceKind())).append('"');
        }
        if (event.sourceQuality() != null && !event.sourceQuality().isBlank()) {
            out.append(",\"source_quality\":\"").append(Json.escape(event.sourceQuality())).append('"');
        }
        return out.toString();
    }
}
