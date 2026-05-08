package local.token.meter.domain;

import java.time.Instant;

public record UsageEvent(String sessionId, String model, Instant timestamp, Snapshot usage) {
}
