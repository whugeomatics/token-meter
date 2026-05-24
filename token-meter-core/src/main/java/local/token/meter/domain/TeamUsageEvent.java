package local.token.meter.domain;

import java.time.Instant;
import java.time.LocalDate;

public record TeamUsageEvent(String eventKey, String tool, String sessionId, String model, Instant timestamp,
                             LocalDate localDate, Snapshot usage, String clientUserId, String clientDeviceId,
                             String sourceKind, String sourceQuality) {
    public TeamUsageEvent(String eventKey, String tool, String sessionId, String model, Instant timestamp,
                          LocalDate localDate, Snapshot usage, String clientUserId, String clientDeviceId) {
        this(eventKey, tool, sessionId, model, timestamp, localDate, usage, clientUserId, clientDeviceId, "", "");
    }
}
