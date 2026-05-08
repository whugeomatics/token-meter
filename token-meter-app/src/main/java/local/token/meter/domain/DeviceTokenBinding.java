package local.token.meter.domain;

public record DeviceTokenBinding(String teamId, String userId, String deviceId, String displayName, String status) {
    public boolean active() {
        return "active".equals(status);
    }
}
