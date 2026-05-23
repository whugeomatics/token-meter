package local.token.meter.ingestion;

import local.token.meter.ingestion.ClaudeCodeUsageSource.ScannedClaudeCodeUsageEvent;
import local.token.meter.store.UsageStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

public final class ClaudeCodeLocalIngestionService {
    private final Path usagePath;
    private final ZoneId zone;
    private final UsageStore store;

    public ClaudeCodeLocalIngestionService(Path usagePath, ZoneId zone, UsageStore store) {
        this.usagePath = usagePath;
        this.zone = zone;
        this.store = store;
    }

    public IngestionResult ingest() {
        IngestionResult result = new IngestionResult();
        if (!Files.isRegularFile(usagePath) && !Files.isDirectory(usagePath)) {
            return result;
        }
        result.setFilesScanned(1);
        try {
            List<ScannedClaudeCodeUsageEvent> events = new ClaudeCodeUsageSource(usagePath, zone, "", "").scannedEvents();
            if (!events.isEmpty()) {
                result.incrementFilesChanged();
            }
            for (ScannedClaudeCodeUsageEvent scanned : events) {
                if (store.insertLocalUsageEvent(scanned.event().tool(), scanned.sourcePath(), scanned.lineNumber(),
                        scanned.event())) {
                    result.incrementEventsInserted();
                } else {
                    result.incrementEventsSkipped();
                }
            }
        } catch (Exception e) {
            result.addError(IngestionError.general(usagePath.toString(), "file error: " + e.getClass().getSimpleName()));
        }
        return result;
    }
}
