package local.agent.dashboard.ingestion;

import local.agent.dashboard.domain.Snapshot;

import java.time.Instant;
import java.time.LocalDate;

public record IngestedUsageEvent(String sourcePath, int lineNumber, String sessionId, String model,
                          Instant timestamp, LocalDate localDate, Snapshot cumulative, Snapshot delta) {
    public String eventKey() {
        return "codex|" + sessionId + "|" + sourcePath + "|" + lineNumber + "|"
                + cumulative.totalTokens() + "|" + cumulative.inputTokens() + "|" + cumulative.outputTokens();
    }
}
