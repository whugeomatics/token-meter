package local.agent.dashboard.ingestion;

import local.agent.dashboard.domain.TeamUsageEvent;
import local.agent.dashboard.util.Json;

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
