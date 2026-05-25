package local.token.meter.domain;

import java.time.Instant;

public record StoredTeamUsageEvent(String eventKey, String teamId, String userId, String userDisplayName, String deviceId,
                                   String deviceDisplayName, String tool, String sessionId, String model,
                                   Instant timestamp, Snapshot usage, String sourceKind, String sourceQuality) {
    public StoredTeamUsageEvent(String teamId, String userId, String userDisplayName, String deviceId,
                                String deviceDisplayName, String tool, String sessionId, String model,
                                Instant timestamp, Snapshot usage, String sourceKind, String sourceQuality) {
        this("", teamId, userId, userDisplayName, deviceId, deviceDisplayName, tool, sessionId, model, timestamp,
                usage, sourceKind, sourceQuality);
    }

    public StoredTeamUsageEvent(String teamId, String userId, String userDisplayName, String deviceId,
                                String deviceDisplayName, String tool, String sessionId, String model,
                                Instant timestamp, Snapshot usage) {
        this("", teamId, userId, userDisplayName, deviceId, deviceDisplayName, tool, sessionId, model, timestamp,
                usage, "", "");
    }
}
