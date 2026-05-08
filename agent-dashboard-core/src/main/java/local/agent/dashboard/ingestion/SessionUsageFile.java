package local.agent.dashboard.ingestion;

import java.time.Instant;
import java.util.List;

public record SessionUsageFile(List<IngestedUsageEvent> events, List<IngestionError> errors,
                               int lastLine, Instant lastEventTimestamp) {
    public String lastError() {
        return errors.isEmpty() ? null : errors.get(errors.size() - 1).message();
    }
}
