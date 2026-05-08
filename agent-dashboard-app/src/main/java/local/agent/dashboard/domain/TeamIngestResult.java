package local.agent.dashboard.domain;

import local.agent.dashboard.util.Json;

import java.time.Instant;

public record TeamIngestResult(String status, int accepted, int duplicate, int rejected, int eventCount,
                               String teamId, String userId, String deviceId, String uploadDate,
                               Instant serverTime, String errorCode, String message) {
    public static TeamIngestResult ok(int accepted, int duplicate, int rejected, int eventCount,
                                      DeviceTokenBinding binding, String uploadDate) {
        return new TeamIngestResult("ok", accepted, duplicate, rejected, eventCount, binding.teamId(),
                binding.userId(), binding.deviceId(), uploadDate, Instant.now(), null, null);
    }

    public static TeamIngestResult error(String code, String message) {
        return new TeamIngestResult("error", 0, 0, 0, 0, "", "", "", "", Instant.now(), code, message);
    }

    public static TeamIngestResult error(String code, String message, int eventCount,
                                         DeviceTokenBinding binding, String uploadDate) {
        return new TeamIngestResult("error", 0, 0, 0, eventCount, binding.teamId(), binding.userId(),
                binding.deviceId(), uploadDate, Instant.now(), code, message);
    }

    public String toJson() {
        if ("ok".equals(status)) {
            return "{"
                    + "\"status\":\"ok\","
                    + "\"accepted\":" + accepted + ","
                    + "\"duplicate\":" + duplicate + ","
                    + "\"rejected\":" + rejected + ","
                    + "\"event_count\":" + eventCount + ","
                    + "\"team_id\":\"" + Json.escape(teamId) + "\","
                    + "\"user_id\":\"" + Json.escape(userId) + "\","
                    + "\"device_id\":\"" + Json.escape(deviceId) + "\","
                    + "\"upload_date\":\"" + Json.escape(uploadDate) + "\","
                    + "\"server_time\":\"" + serverTime + "\""
                    + "}";
        }
        return "{"
                + "\"status\":\"error\","
                + "\"error_code\":\"" + Json.escape(errorCode) + "\","
                + "\"message\":\"" + Json.escape(message) + "\""
                + "}";
    }
}
