package local.agent.dashboard.ingestion;

import local.agent.dashboard.util.Json;

public record IngestionError(String path, int line, String message) {
    public static IngestionError general(String path, String message) {
        return new IngestionError(path, 0, message);
    }

    public String toJson() {
        return "{"
                + "\"path\":\"" + Json.escape(path) + "\","
                + "\"line\":" + line + ","
                + "\"message\":\"" + Json.escape(message) + "\""
                + "}";
    }
}
