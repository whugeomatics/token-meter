package local.token.meter.domain;

import java.time.Instant;

public record UsageEvent(String eventKey, String tool, String sessionId, String model, Instant timestamp, Snapshot usage,
                         String sourceKind, String sourceQuality) {
    public UsageEvent(String tool, String sessionId, String model, Instant timestamp, Snapshot usage,
                      String sourceKind, String sourceQuality) {
        this("", tool, sessionId, model, timestamp, usage, sourceKind, sourceQuality);
    }

    public UsageEvent(String tool, String sessionId, String model, Instant timestamp, Snapshot usage) {
        this("", tool, sessionId, model, timestamp, usage, "", "");
    }

    public UsageEvent(String sessionId, String model, Instant timestamp, Snapshot usage) {
        this("", "codex", sessionId, model, timestamp, usage, "", "");
    }
}
