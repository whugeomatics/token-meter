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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class ClaudeCodeUsageSource {
    private final Path usagePath;
    private final ZoneId zone;
    private final String userId;
    private final String deviceId;
    private final String sourceKind;
    private final String sourceQuality;

    public ClaudeCodeUsageSource(Path usagePath, ZoneId zone, String userId, String deviceId) {
        this(usagePath, zone, userId, deviceId, "local_jsonl", "reported");
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
        Set<String> eventKeys = new HashSet<>();
        for (Path file : usageFiles()) {
            scanFile(file, events, eventKeys);
        }
        return events;
    }

    private List<Path> usageFiles() throws IOException {
        if (Files.isRegularFile(usagePath)) {
            return List.of(usagePath);
        }
        if (!Files.isDirectory(usagePath)) {
            return List.of();
        }
        try (var stream = Files.walk(usagePath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted()
                    .toList();
        }
    }

    private void scanFile(Path file, List<ScannedClaudeCodeUsageEvent> events, Set<String> eventKeys)
            throws IOException {
        int lineNumber = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            TeamUsageEvent event = parseLine(line);
            if (event != null && eventKeys.add(event.eventKey())) {
                events.add(new ScannedClaudeCodeUsageEvent(file.toAbsolutePath().normalize().toString(),
                        lineNumber, event));
            }
        }
    }

    private TeamUsageEvent parseLine(String json) {
        try {
            TeamUsageEvent localEvent = parseClaudeProjectLine(json);
            if (localEvent != null) {
                return localEvent;
            }
            return parseFlatUsageLine(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private TeamUsageEvent parseClaudeProjectLine(String json) {
        String sessionId = Json.firstString(json, "sessionId").orElse("");
        String timestampValue = Json.firstString(json, "timestamp").orElse("");
        var message = Json.objectSection(json, "message");
        if (sessionId.isBlank() || timestampValue.isBlank() || message.isEmpty()) {
            return null;
        }
        var usageJson = Json.objectSection(message.get(), "usage");
        if (usageJson.isEmpty()) {
            return null;
        }
        String messageId = Json.firstString(message.get(), "id").orElse("");
        String model = Json.firstString(message.get(), "model").orElse("unknown");
        Instant timestamp = Instant.parse(timestampValue);
        Snapshot usage = usageFromClaudeUsage(usageJson.get());
        if (!usage.hasPositiveUsage()) {
            return null;
        }
        LocalDate localDate = timestamp.atZone(zone).toLocalDate();
        String sourceIdentity = messageId.isBlank() ? timestamp.toString() : messageId;
        return new TeamUsageEvent(eventKey(sessionId, model, sourceIdentity, usage), "claude-code", sessionId, model,
                timestamp, localDate, usage, userId, deviceId, sourceKind, sourceQuality);
    }

    private TeamUsageEvent parseFlatUsageLine(String json) {
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
        Snapshot usage = usageFromFlatLine(json);
        if (!usage.hasPositiveUsage()) {
            return null;
        }
        LocalDate localDate = timestamp.atZone(zone).toLocalDate();
        return new TeamUsageEvent(eventKey(sessionId, model, timestamp.toString(), usage), "claude-code", sessionId,
                model, timestamp, localDate, usage, userId, deviceId, sourceKind, sourceQuality);
    }

    private Snapshot usageFromFlatLine(String json) {
        long input = Json.longValue(json, "input_tokens").orElse(0L);
        long cached = Json.longValue(json, "cached_input_tokens").orElse(0L);
        long output = Json.longValue(json, "output_tokens").orElse(0L);
        long reasoning = Json.longValue(json, "reasoning_output_tokens").orElse(0L);
        long total = Json.longValue(json, "total_tokens").orElse(input + cached + output + reasoning);
        return new Snapshot(input, cached, output, reasoning, total);
    }

    private Snapshot usageFromClaudeUsage(String json) {
        long rawInput = Json.longValue(json, "input_tokens").orElse(0L);
        long cacheCreation = Json.longValue(json, "cache_creation_input_tokens").orElse(0L);
        long cacheRead = Json.longValue(json, "cache_read_input_tokens").orElse(0L);
        long cached = cacheCreation + cacheRead;
        long input = rawInput + cached;
        long output = Json.longValue(json, "output_tokens").orElse(0L);
        long reasoning = Json.longValue(json, "reasoning_output_tokens").orElse(0L);
        long total = Json.longValue(json, "total_tokens").orElse(input + output + reasoning);
        return new Snapshot(input, cached, output, reasoning, total);
    }

    private String eventKey(String sessionId, String model, String sourceIdentity, Snapshot usage) {
        return "claude-code|" + sourceKind + "|" + sessionId + "|" + hash(sourceIdentity) + "|" + model + "|"
                + hash(usageKey(usage));
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
