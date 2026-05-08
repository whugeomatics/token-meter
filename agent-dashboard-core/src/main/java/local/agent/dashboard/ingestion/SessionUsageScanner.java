package local.agent.dashboard.ingestion;

import local.agent.dashboard.domain.Snapshot;
import local.agent.dashboard.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class SessionUsageScanner {
    private final Path sessionsDir;
    private final ZoneId zone;

    public SessionUsageScanner(Path sessionsDir, ZoneId zone) {
        this.sessionsDir = sessionsDir;
        this.zone = zone;
    }

    public SessionUsageScan scan() {
        IngestionResult result = new IngestionResult();
        List<IngestedUsageEvent> events = new ArrayList<>();
        if (!Files.isDirectory(sessionsDir)) {
            return new SessionUsageScan(result, events);
        }

        List<Path> files;
        try {
            files = sessionFiles();
        } catch (IOException e) {
            result.addError(IngestionError.general(sessionsDir.toString(), "scan error: " + e.getClass().getSimpleName()));
            return new SessionUsageScan(result, events);
        }

        result.setFilesScanned(files.size());
        for (Path file : files) {
            try {
                SessionUsageFile fileResult = scanFile(file);
                result.incrementFilesChanged();
                for (IngestedUsageEvent event : fileResult.events()) {
                    events.add(event);
                    result.incrementEventsInserted();
                }
                result.addErrors(fileResult.errors());
            } catch (Exception e) {
                result.addError(IngestionError.general(file.toString(), "file error: " + e.getClass().getSimpleName()));
            }
        }
        return new SessionUsageScan(result, events);
    }

    public List<Path> sessionFiles() throws IOException {
        try (var stream = Files.walk(sessionsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    public SessionUsageFile scanFile(Path file) throws IOException {
        List<IngestedUsageEvent> events = new ArrayList<>();
        List<IngestionError> errors = new ArrayList<>();
        String sessionId = fallbackSessionId(file);
        String currentModel = "unknown";
        Snapshot previous = null;
        int lineNumber = 0;
        Instant lastEventTimestamp = null;

        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            try {
                String topType = Json.firstString(line, "type").orElse("");
                if ("session_meta".equals(topType)) {
                    sessionId = Json.firstString(line, "id").orElse(sessionId);
                    continue;
                }
                if ("turn_context".equals(topType)) {
                    currentModel = Json.firstString(line, "model").orElse(currentModel);
                    continue;
                }
                if (!"event_msg".equals(topType) || !Json.stringOccurrences(line, "type").contains("token_count")) {
                    continue;
                }

                Optional<String> timestampValue = Json.firstString(line, "timestamp");
                Snapshot cumulative = Snapshot.fromObject(line, "total_token_usage").orElse(null);
                if (timestampValue.isEmpty() || cumulative == null) {
                    continue;
                }

                Instant timestamp = Instant.parse(timestampValue.get());
                Snapshot delta = previous == null ? cumulative : cumulative.minus(previous);
                previous = cumulative;
                if (!delta.hasPositiveUsage()) {
                    continue;
                }
                events.add(new IngestedUsageEvent(file.toAbsolutePath().normalize().toString(), lineNumber,
                        sessionId, currentModel, timestamp, timestamp.atZone(zone).toLocalDate(), cumulative, delta));
                lastEventTimestamp = timestamp;
            } catch (Exception e) {
                errors.add(new IngestionError(file.toString(), lineNumber, "parse error: " + e.getClass().getSimpleName()));
            }
        }
        return new SessionUsageFile(events, errors, lineNumber, lastEventTimestamp);
    }

    private String fallbackSessionId(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
    }
}
