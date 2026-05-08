package local.token.meter.domain;

public record TeamUploadRecord(String teamId, String userId, String deviceId, String uploadDate, String uploadTime,
                               int eventCount, int accepted, int duplicate, int rejected,
                               String status, String message) {
}
