package local.agent.dashboard.ingestion;

import local.agent.dashboard.domain.ExportedUsageEvent;
import local.agent.dashboard.domain.ReportQuery;
import local.agent.dashboard.domain.TeamUsageEvent;
import local.agent.dashboard.store.UsageStore;
import local.agent.dashboard.util.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class TeamCollector {
    private final UsageStore localStore;
    private final ZoneId zone;
    private final String serverUrl;
    private final String token;
    private final String userId;
    private final String deviceId;
    private final int batchSize;

    public TeamCollector(UsageStore localStore, ZoneId zone, String serverUrl, String token, String userId, String deviceId,
                         int batchSize) {
        this.localStore = localStore;
        this.zone = zone;
        this.serverUrl = serverUrl;
        this.token = token;
        this.userId = userId;
        this.deviceId = deviceId;
        this.batchSize = Math.max(1, Math.min(500, batchSize));
    }

    public String uploadRecent(int days) throws Exception {
        LocalDate end = LocalDate.now(zone);
        LocalDate start = end.minusDays(days - 1L);
        List<TeamUsageEvent> events = new ArrayList<>();
        for (ExportedUsageEvent event : localStore.loadExportEvents(start, end)) {
            events.add(new TeamUsageEvent(event.eventKey(), "codex", event.sessionId(), event.model(), event.timestamp(),
                    event.timestamp().atZone(zone).toLocalDate(), event.usage(), userId, deviceId));
        }
        int accepted = 0;
        int duplicate = 0;
        int rejected = 0;
        int batches = 0;
        for (int index = 0; index < events.size(); index += batchSize) {
            int endIndex = Math.min(events.size(), index + batchSize);
            String payload = TeamIngestionService.eventsToJson("0.1.0", userId, deviceId, events.subList(index, endIndex));
            String response = post(payload);
            accepted += Json.longValue(response, "accepted").orElse(0L).intValue();
            duplicate += Json.longValue(response, "duplicate").orElse(0L).intValue();
            rejected += Json.longValue(response, "rejected").orElse(0L).intValue();
            batches++;
        }
        if (events.isEmpty()) {
            String payload = TeamIngestionService.eventsToJson("0.1.0", userId, deviceId, events);
            String response = post(payload);
            accepted += Json.longValue(response, "accepted").orElse(0L).intValue();
            duplicate += Json.longValue(response, "duplicate").orElse(0L).intValue();
            rejected += Json.longValue(response, "rejected").orElse(0L).intValue();
            batches = 1;
        }
        return "{\"status\":\"ok\",\"events\":" + events.size() + ",\"batches\":" + batches
                + ",\"accepted\":" + accepted + ",\"duplicate\":" + duplicate + ",\"rejected\":" + rejected + "}";
    }

    private String post(String payload) throws IOException {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IOException("server-url is required");
        }
        if (token == null || token.isBlank()) {
            throw new IOException("device-token is required");
        }
        String endpoint = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint + "/api/team/ingest").toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        } catch (ConnectException e) {
            throw new IOException("collector cannot connect to " + endpoint
                    + ". Start the dashboard server first, or check AGENT_DASHBOARD_SERVER_URL and port.", e);
        }
        int status = connection.getResponseCode();
        byte[] response = (status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream()).readAllBytes();
        String body = new String(response, StandardCharsets.UTF_8);
        if (status >= 400) {
            throw new IOException("team upload failed: HTTP " + status + " " + sanitize(body));
        }
        return body;
    }

    private String sanitize(String body) {
        return Json.escape(body);
    }
}
