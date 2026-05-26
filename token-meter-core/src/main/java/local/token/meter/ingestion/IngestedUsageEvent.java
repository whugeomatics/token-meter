package local.token.meter.ingestion;

import local.token.meter.domain.Snapshot;

import java.time.Instant;
import java.time.LocalDate;

public record IngestedUsageEvent(String sourcePath, int lineNumber, String sessionId, String model,
                          Instant timestamp, LocalDate localDate, Snapshot cumulative, Snapshot delta) {
    public String eventKey() {
        return UsageEventKeys.codex(sessionId, sourcePath, lineNumber, cumulative);
    }
}
