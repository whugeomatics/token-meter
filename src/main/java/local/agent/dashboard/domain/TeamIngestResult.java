package local.agent.dashboard.domain;

import local.agent.dashboard.util.Json;

import java.time.Instant;

public record TeamIngestResult(String status, int accepted, int duplicate, int rejected, Instant serverTime,
                               String errorCode, String message) {
    public static TeamIngestResult ok(int accepted, int duplicate, int rejected) {
        return new TeamIngestResult("ok", accepted, duplicate, rejected, Instant.now(), null, null);
    }

    public static TeamIngestResult error(String code, String message) {
        return new TeamIngestResult("error", 0, 0, 0, Instant.now(), code, message);
    }

    public String toJson() {
        if ("ok".equals(status)) {
            return "{"
                    + "\"status\":\"ok\","
                    + "\"accepted\":" + accepted + ","
                    + "\"duplicate\":" + duplicate + ","
                    + "\"rejected\":" + rejected + ","
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
