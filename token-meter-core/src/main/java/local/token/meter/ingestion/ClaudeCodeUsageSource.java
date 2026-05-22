package local.token.meter.ingestion;

import local.token.meter.domain.Snapshot;
import local.token.meter.domain.TeamUsageEvent;
import local.token.meter.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class ClaudeCodeUsageSource {
    private final Path usagePath;
    private final ZoneId zone;
    private final String userId;
    private final String deviceId;
    private final String sourceKind;
    private final String sourceQuality;

    public ClaudeCodeUsageSource(Path usagePath, ZoneId zone, String userId, String deviceId) {
        this(usagePath, zone, userId, deviceId, "otel_metrics", "official");
    }

    public ClaudeCodeUsageSource(Path usagePath, ZoneId zone, String userId, String deviceId,
                                 String sourceKind, String sourceQuality) {
        this.usagePath = usagePath;
        this.zone = zone;
        this.userId = userId;
        this.deviceId = deviceId;
        this.sourceKind = sourceKind;
        this.sourceQuality = sourceQuality;
    }

    public List<TeamUsageEvent> events() throws IOException {
        return scannedEvents().stream().map(ScannedClaudeCodeUsageEvent::event).toList();
    }

    public List<ScannedClaudeCodeUsageEvent> scannedEvents() throws IOException {
        List<ScannedClaudeCodeUsageEvent> events = new ArrayList<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(usagePath, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            TeamUsageEvent event = parseLine(line);
            if (event != null) {
                events.add(new ScannedClaudeCodeUsageEvent(usagePath.toAbsolutePath().normalize().toString(),
                        lineNumber, event));
            }
        }
        return events;
    }

    private TeamUsageEvent parseLine(String json) {
        try {
            String sessionId = Json.firstString(json, "session_id")
                    .or(() -> Json.firstString(json, "sessionId"))
                    .orElse("");
            String model = Json.firstString(json, "model").orElse("unknown");
            String timestampValue = Json.firstString(json, "timestamp")
                    .or(() -> Json.firstString(json, "time"))
                    .orElse("");
            if (sessionId.isBlank() || timestampValue.isBlank()) {
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
            return new TeamUsageEvent(eventKey(sessionId, model, timestamp, usage), "claude-code", sessionId, model,
                    timestamp, localDate, usage, userId, deviceId, sourceKind, sourceQuality);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String eventKey(String sessionId, String model, Instant timestamp, Snapshot usage) {
        return "claude-code|" + sessionId + "|" + timestamp + "|" + model + "|" + hash(usageKey(usage));
    }

    private String usageKey(Snapshot usage) {
        return usage.inputTokens() + "|" + usage.cachedInputTokens() + "|" + usage.outputTokens() + "|"
                + usage.reasoningOutputTokens() + "|" + usage.totalTokens();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record ScannedClaudeCodeUsageEvent(String sourcePath, int lineNumber, TeamUsageEvent event) {
    }
}
