package local.agent.dashboard.domain;

import java.time.Instant;
import java.time.LocalDate;

public record TeamUsageEvent(String eventKey, String tool, String sessionId, String model, Instant timestamp,
                             LocalDate localDate, Snapshot usage, String clientUserId, String clientDeviceId) {
}
