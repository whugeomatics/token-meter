package local.token.meter.domain;

import java.time.Instant;

public record UsageEvent(String tool, String sessionId, String model, Instant timestamp, Snapshot usage) {
    public UsageEvent(String sessionId, String model, Instant timestamp, Snapshot usage) {
        this("codex", sessionId, model, timestamp, usage);
    }
}
