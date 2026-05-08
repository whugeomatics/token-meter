package local.agent.dashboard.http;

import local.agent.dashboard.domain.DeviceTokenBinding;
import local.agent.dashboard.domain.DeviceTokenRecord;
import local.agent.dashboard.store.TeamUsageStore;
import local.agent.dashboard.util.DeviceTokenGenerator;
import local.agent.dashboard.util.Json;

import java.sql.SQLException;
import java.util.List;

public final class AdminService {
    private final TeamUsageStore store;

    public AdminService(TeamUsageStore store) {
        this.store = store;
    }

    public String listDeviceTokens() throws SQLException {
        List<DeviceTokenRecord> tokens = store.listDeviceTokens();
        return "{\"status\":\"ok\",\"device_tokens\":" + Json.array(tokens, this::tokenJson) + "}";
    }

    public String createDeviceToken(String body) throws SQLException {
        String teamId = Json.firstString(body, "team_id").orElse("");
        String userId = Json.firstString(body, "user_id").orElse("");
        String deviceId = Json.firstString(body, "device_id").orElse("");
        String displayName = Json.firstString(body, "device_name")
                .or(() -> Json.firstString(body, "display_name")).orElse(deviceId);
        if (teamId.isBlank() || userId.isBlank() || deviceId.isBlank()) {
            throw new BadRequestException("team_id, user_id and device_id are required");
        }
        String token = DeviceTokenGenerator.generate();
        DeviceTokenBinding binding = new DeviceTokenBinding(teamId, userId, deviceId, displayName, "active");
        store.upsertDeviceToken(token, binding);
        return "{\"status\":\"ok\",\"device_token\":\"" + Json.escape(token) + "\",\"binding\":"
                + bindingJson(binding) + "}";
    }

    public String getDeviceTokenSecret(long tokenId) throws SQLException {
        String token = store.getDeviceTokenSecret(tokenId);
        if (token == null || token.isBlank()) {
            throw new BadRequestException("device token is not recoverable");
        }
        return "{\"status\":\"ok\",\"device_token\":\"" + Json.escape(token) + "\"}";
    }

    public String deleteDeviceToken(long tokenId) throws SQLException {
        if (!store.deleteDeviceToken(tokenId)) {
            throw new BadRequestException("device token binding not found");
        }
        return "{\"status\":\"ok\",\"deleted\":true}";
    }

    private String tokenJson(DeviceTokenRecord token) {
        return "{\"token_id\":" + token.tokenId() + ",\"token_preview\":\"" + Json.escape(token.tokenPreview())
                + "\",\"token_recoverable\":" + token.tokenRecoverable()
                + ",\"team_id\":\"" + Json.escape(token.teamId()) + "\",\"user_id\":\"" + Json.escape(token.userId())
                + "\",\"device_id\":\"" + Json.escape(token.deviceId()) + "\",\"display_name\":\""
                + Json.escape(token.displayName()) + "\",\"status\":\"" + Json.escape(token.status())
                + "\",\"created_at\":\"" + Json.escape(token.createdAt()) + "\",\"last_seen_at\":\""
                + Json.escape(token.lastSeenAt()) + "\"}";
    }

    private String bindingJson(DeviceTokenBinding binding) {
        return "{\"team_id\":\"" + Json.escape(binding.teamId()) + "\",\"user_id\":\""
                + Json.escape(binding.userId()) + "\",\"device_id\":\"" + Json.escape(binding.deviceId())
                + "\",\"display_name\":\"" + Json.escape(binding.displayName()) + "\",\"status\":\""
                + Json.escape(binding.status()) + "\"}";
    }
}
