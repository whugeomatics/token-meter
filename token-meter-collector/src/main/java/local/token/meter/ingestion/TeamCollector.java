package local.token.meter.ingestion;

import local.token.meter.domain.TeamUsageEvent;
import local.token.meter.util.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;

public final class TeamCollector {
    private final SessionUsageScanner scanner;
    private final ZoneId zone;
    private final String serverUrl;
    private final String token;
    private final String userId;
    private final String deviceId;
    private final int batchSize;

    public TeamCollector(SessionUsageScanner scanner, ZoneId zone, String serverUrl, String token, String userId, String deviceId,
                         int batchSize) {
        this.scanner = scanner;
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
        Instant uploadTime = Instant.now();
        return uploadEvents(collectRecentEvents(start, end), start, end, uploadTime);
    }

    public List<TeamUsageEvent> collectRecentEvents(LocalDate start, LocalDate end) throws IOException {
        SessionUsageScan scan = scanner.scan();
        if (!scan.result().errors().isEmpty()) {
            throw new IOException("collector session scan failed: " + scan.result().toJson());
        }
        List<TeamUsageEvent> events = new ArrayList<>();
        for (IngestedUsageEvent event : scan.events()) {
            LocalDate eventDate = event.timestamp().atZone(zone).toLocalDate();
            if (eventDate.isBefore(start) || eventDate.isAfter(end)) {
                continue;
            }
            events.add(new TeamUsageEvent(teamEventKey(event), "codex", event.sessionId(), event.model(), event.timestamp(),
                    eventDate, event.delta(), userId, deviceId));
        }
        return events;
    }

    public String uploadEvents(List<TeamUsageEvent> events, LocalDate start, LocalDate end, Instant uploadTime)
            throws IOException {
        int accepted = 0;
        int duplicate = 0;
        int rejected = 0;
        int batches = 0;
        for (int index = 0; index < events.size(); index += batchSize) {
            int endIndex = Math.min(events.size(), index + batchSize);
            String payload = TeamUsagePayload.eventsToJson("0.1.0", userId, deviceId, events.subList(index, endIndex));
            String response = post(payload);
            accepted += Json.longValue(response, "accepted").orElse(0L).intValue();
            duplicate += Json.longValue(response, "duplicate").orElse(0L).intValue();
            rejected += Json.longValue(response, "rejected").orElse(0L).intValue();
            batches++;
        }
        if (events.isEmpty()) {
            String payload = TeamUsagePayload.eventsToJson("0.1.0", userId, deviceId, events);
            String response = post(payload);
            accepted += Json.longValue(response, "accepted").orElse(0L).intValue();
            duplicate += Json.longValue(response, "duplicate").orElse(0L).intValue();
            rejected += Json.longValue(response, "rejected").orElse(0L).intValue();
            batches = 1;
        }
        return "{\"status\":\"ok\",\"events\":" + events.size() + ",\"batches\":" + batches
                + ",\"accepted\":" + accepted + ",\"duplicate\":" + duplicate + ",\"rejected\":" + rejected
                + ",\"upload_time\":\"" + uploadTime + "\""
                + ",\"client_user_id\":\"" + Json.escape(userId) + "\""
                + ",\"client_device_id\":\"" + Json.escape(deviceId) + "\""
                + ",\"server_url\":\"" + Json.escape(safeEndpoint()) + "\""
                + ",\"start_date\":\"" + start + "\",\"end_date\":\"" + end + "\"}";
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
                    + ". Start the dashboard server first, or check TOKEN_METER_SERVER_URL and port.", e);
        }
        int status = connection.getResponseCode();
        byte[] response = (status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream()).readAllBytes();
        String body = new String(response, StandardCharsets.UTF_8);
        if (status >= 400) {
            throw new IOException(uploadFailureMessage(status, body, userId, deviceId, endpoint));
        }
        return body;
    }

    public static String uploadFailureMessage(int status, String body, String userId, String deviceId, String endpoint) {
        return "team upload failed: HTTP " + status + " " + sanitize(body)
                + " client_user_id=" + userId + " client_device_id=" + deviceId
                + " server_url=" + endpoint
                + uploadFailureHint(status, body);
    }

    private static String uploadFailureHint(int status, String body) {
        if (status != 401) {
            return "";
        }
        String normalized = body == null ? "" : body.toLowerCase();
        if (normalized.contains("unknown device token")) {
            return " hint=unknown device token. The collector may still be using an old TOKEN_METER_DEVICE_TOKEN; copy the current teammate .env from admin.html, update the teammate environment, restart the collector, and verify the dashboard server is using the same database where the token was created.";
        }
        if (normalized.contains("missing device token")) {
            return " hint=missing device token. Set TOKEN_METER_DEVICE_TOKEN from the teammate .env generated in admin.html, then restart the collector.";
        }
        return " hint=unauthorized. Verify TOKEN_METER_USER_ID, TOKEN_METER_DEVICE_ID, TOKEN_METER_DEVICE_TOKEN, and the dashboard server database match the token created in admin.html.";
    }

    private String safeEndpoint() {
        if (serverUrl == null || serverUrl.isBlank()) {
            return "";
        }
        return serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    }

    private static String sanitize(String body) {
        return Json.escape(body);
    }

    private String teamEventKey(IngestedUsageEvent event) {
        return "codex|" + event.sessionId() + "|" + sourceHash(event.sourcePath()) + "|" + event.lineNumber() + "|"
                + event.cumulative().totalTokens() + "|" + event.cumulative().inputTokens() + "|"
                + event.cumulative().outputTokens();
    }

    private String sourceHash(String sourcePath) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sourcePath.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
