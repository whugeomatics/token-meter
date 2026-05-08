package local.agent.dashboard.domain;

import java.time.Instant;

public record StoredTeamUsageEvent(String teamId, String userId, String userDisplayName, String deviceId,
                                   String deviceDisplayName, String tool, String sessionId, String model,
                                   Instant timestamp, Snapshot usage) {
}
