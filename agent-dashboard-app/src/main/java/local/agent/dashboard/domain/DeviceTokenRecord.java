package local.agent.dashboard.domain;

public record DeviceTokenRecord(long tokenId, String tokenPreview, boolean tokenRecoverable,
                                String teamId, String userId, String deviceId, String displayName,
                                String status, String createdAt, String lastSeenAt) {
}
