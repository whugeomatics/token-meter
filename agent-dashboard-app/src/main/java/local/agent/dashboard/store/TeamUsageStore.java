package local.agent.dashboard.store;

import local.agent.dashboard.domain.DeviceTokenBinding;
import local.agent.dashboard.domain.DeviceTokenRecord;
import local.agent.dashboard.domain.StoredTeamUsageEvent;
import local.agent.dashboard.domain.TeamUploadRecord;
import local.agent.dashboard.domain.TeamUsageEvent;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface TeamUsageStore {
    void initialize() throws Exception;

    void upsertDeviceToken(String token, DeviceTokenBinding binding) throws SQLException;

    DeviceTokenBinding findDeviceToken(String token) throws SQLException;

    List<DeviceTokenRecord> listDeviceTokens() throws SQLException;

    String getDeviceTokenSecret(long tokenId) throws SQLException;

    boolean deleteDeviceToken(long tokenId) throws SQLException;

    DeviceTokenBinding findDeviceBinding(String teamId, String userId, String deviceId) throws SQLException;

    void updateDeviceTokenSeen(String token) throws SQLException;

    void insertTeamUpload(TeamUploadRecord upload) throws SQLException;

    boolean insertTeamUsageEvent(DeviceTokenBinding binding, TeamUsageEvent event) throws SQLException;

    List<StoredTeamUsageEvent> loadTeamEvents(LocalDate startDate, LocalDate endDate) throws SQLException;

    List<TeamUploadRecord> loadTeamUploads(LocalDate startDate, LocalDate endDate) throws SQLException;
}
