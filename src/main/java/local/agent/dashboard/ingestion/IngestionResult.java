package local.agent.dashboard.ingestion;

import local.agent.dashboard.util.Json;

import java.util.ArrayList;
import java.util.List;

public final class IngestionResult {
    private int filesScanned;
    private int filesChanged;
    private int eventsInserted;
    private int eventsSkipped;
    private final List<IngestionError> errors = new ArrayList<>();

    public List<IngestionError> errors() {
        return errors;
    }

    public void setFilesScanned(int filesScanned) {
        this.filesScanned = filesScanned;
    }

    public void incrementFilesChanged() {
        filesChanged++;
    }

    public void incrementEventsInserted() {
        eventsInserted++;
    }

    public void incrementEventsSkipped() {
        eventsSkipped++;
    }

    public void addError(IngestionError error) {
        errors.add(error);
    }

    public void addErrors(List<IngestionError> newErrors) {
        errors.addAll(newErrors);
    }

    public String toJson() {
        return "{"
                + "\"status\":\"" + (errors.isEmpty() ? "ok" : "error") + "\","
                + "\"files_scanned\":" + filesScanned + ","
                + "\"files_changed\":" + filesChanged + ","
                + "\"events_inserted\":" + eventsInserted + ","
                + "\"events_skipped\":" + eventsSkipped + ","
                + "\"errors\":" + Json.array(errors, IngestionError::toJson)
                + "}";
    }
}
