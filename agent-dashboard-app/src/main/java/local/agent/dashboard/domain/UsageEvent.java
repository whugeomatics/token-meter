package local.agent.dashboard.domain;

import java.time.Instant;

public record UsageEvent(String sessionId, String model, Instant timestamp, Snapshot usage) {
}
