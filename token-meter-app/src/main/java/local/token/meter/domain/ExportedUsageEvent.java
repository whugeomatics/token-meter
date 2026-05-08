package local.token.meter.domain;

import java.time.Instant;

public record ExportedUsageEvent(String eventKey, String sessionId, String model, Instant timestamp, Snapshot usage) {
}
