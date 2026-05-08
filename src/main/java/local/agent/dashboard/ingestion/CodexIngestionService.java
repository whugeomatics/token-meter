package local.agent.dashboard.ingestion;

import local.agent.dashboard.domain.Snapshot;
import local.agent.dashboard.store.UsageStore;
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

public final class CodexIngestionService {
    private final Path sessionsDir;
    private final ZoneId zone;
    private final UsageStore usageStore;

    public CodexIngestionService(Path sessionsDir, ZoneId zone, UsageStore usageStore) {
        this.sessionsDir = sessionsDir;
        this.zone = zone;
        this.usageStore = usageStore;
    }

    public IngestionResult ingest() {
        IngestionResult result = new IngestionResult();
        if (!Files.isDirectory(sessionsDir)) {
            return result;
        }

        List<Path> files;
        try (var stream = Files.walk(sessionsDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            result.addError(IngestionError.general(sessionsDir.toString(), "scan error: " + e.getClass().getSimpleName()));
            return result;
        }

        result.setFilesScanned(files.size());
        for (Path file : files) {
            ingestFile(file, result);
        }
        return result;
    }

    private void ingestFile(Path file, IngestionResult result) {
        try {
            SourceFileState state = SourceFileState.from(file);
            SourceFileRecord current = usageStore.findSourceFile(state);
            if (current != null && current.sameFile(state)) {
                return;
            }
            result.incrementFilesChanged();
            FileIngestion fileResult = readSessionFile(file);
            long sourceFileId = usageStore.upsertSourceFile(state, fileResult.lastLine(), fileResult.lastEventTimestamp(),
                    fileResult.errors().isEmpty() ? "active" : "error", fileResult.lastError());
            for (IngestedUsageEvent event : fileResult.events()) {
                if (usageStore.insertUsageEvent(sourceFileId, event)) {
                    result.incrementEventsInserted();
                } else {
                    result.incrementEventsSkipped();
                }
            }
            result.addErrors(fileResult.errors());
        } catch (Exception e) {
            result.addError(IngestionError.general(file.toString(), "file error: " + e.getClass().getSimpleName()));
        }
    }

    private FileIngestion readSessionFile(Path file) throws IOException {
        FileIngestion result = new FileIngestion();
        String sessionId = fallbackSessionId(file);
        String currentModel = "unknown";
        Snapshot previous = null;
        int lineNumber = 0;

        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) {
                result.markLine(lineNumber);
                continue;
            }
            try {
                String topType = Json.firstString(line, "type").orElse("");
                if ("session_meta".equals(topType)) {
                    sessionId = Json.firstString(line, "id").orElse(sessionId);
                    result.markLine(lineNumber);
                    continue;
                }
                if ("turn_context".equals(topType)) {
                    currentModel = Json.firstString(line, "model").orElse(currentModel);
                    result.markLine(lineNumber);
                    continue;
                }
                if (!"event_msg".equals(topType) || !Json.stringOccurrences(line, "type").contains("token_count")) {
                    result.markLine(lineNumber);
                    continue;
                }

                Optional<String> timestampValue = Json.firstString(line, "timestamp");
                Snapshot cumulative = Snapshot.fromObject(line, "total_token_usage").orElse(null);
                if (timestampValue.isEmpty() || cumulative == null) {
                    result.markLine(lineNumber);
                    continue;
                }

                Instant timestamp = Instant.parse(timestampValue.get());
                Snapshot delta = previous == null ? cumulative : cumulative.minus(previous);
                previous = cumulative;
                result.markLine(lineNumber);
                if (!delta.hasPositiveUsage()) {
                    continue;
                }
                result.addEvent(new IngestedUsageEvent(file.toAbsolutePath().normalize().toString(), lineNumber,
                        sessionId, currentModel, timestamp, timestamp.atZone(zone).toLocalDate(), cumulative, delta));
            } catch (Exception e) {
                result.addError(new IngestionError(file.toString(), lineNumber, "parse error: " + e.getClass().getSimpleName()));
            }
        }
        return result;
    }

    private String fallbackSessionId(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".jsonl") ? name.substring(0, name.length() - ".jsonl".length()) : name;
    }

    private static final class FileIngestion {
        private final List<IngestedUsageEvent> events = new ArrayList<>();
        private final List<IngestionError> errors = new ArrayList<>();
        private int lastLine;
        private Instant lastEventTimestamp;

        List<IngestedUsageEvent> events() {
            return events;
        }

        List<IngestionError> errors() {
            return errors;
        }

        int lastLine() {
            return lastLine;
        }

        Instant lastEventTimestamp() {
            return lastEventTimestamp;
        }

        void markLine(int lineNumber) {
            lastLine = lineNumber;
        }

        void addEvent(IngestedUsageEvent event) {
            events.add(event);
            lastEventTimestamp = event.timestamp();
        }

        void addError(IngestionError error) {
            errors.add(error);
        }

        String lastError() {
            return errors.isEmpty() ? null : errors.get(errors.size() - 1).message();
        }
    }
}
