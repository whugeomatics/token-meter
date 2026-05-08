package local.agent.dashboard.ingestion;

import local.agent.dashboard.store.UsageStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

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
        try {
            files = new SessionUsageScanner(sessionsDir, zone).sessionFiles();
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
            SessionUsageFile fileResult = new SessionUsageScanner(sessionsDir, zone).scanFile(file);
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

}
