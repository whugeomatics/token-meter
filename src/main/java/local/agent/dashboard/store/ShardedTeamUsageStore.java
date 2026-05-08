package local.agent.dashboard.store;

import local.agent.dashboard.domain.DeviceTokenBinding;
import local.agent.dashboard.domain.DeviceTokenRecord;
import local.agent.dashboard.domain.StoredTeamUsageEvent;
import local.agent.dashboard.domain.TeamUsageEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShardedTeamUsageStore implements TeamUsageStore {
    private final Path rootDir;
    private final SqliteTeamUsageStore registry;
    private final Map<YearMonth, SqliteTeamUsageStore> stores = new HashMap<>();

    public ShardedTeamUsageStore(Path rootDir) throws java.io.IOException {
        this.rootDir = rootDir;
        this.registry = new SqliteTeamUsageStore(rootDir.resolve("agent-dashboard-team-registry.sqlite"));
    }

    public void initialize() throws Exception {
        Files.createDirectories(rootDir);
        registry.initialize();
    }

    public void upsertDeviceToken(String token, DeviceTokenBinding binding) throws SQLException {
        registry.upsertDeviceToken(token, binding);
    }

    public DeviceTokenBinding findDeviceToken(String token) throws SQLException {
        return registry.findDeviceToken(token);
    }

    public List<DeviceTokenRecord> listDeviceTokens() throws SQLException {
        return registry.listDeviceTokens();
    }

    public String getDeviceTokenSecret(long tokenId) throws SQLException {
        return registry.getDeviceTokenSecret(tokenId);
    }

    public boolean deleteDeviceToken(long tokenId) throws SQLException {
        return registry.deleteDeviceToken(tokenId);
    }

    public DeviceTokenBinding findDeviceBinding(String teamId, String userId, String deviceId) throws SQLException {
        return registry.findDeviceBinding(teamId, userId, deviceId);
    }

    public void updateDeviceTokenSeen(String token) throws SQLException {
        registry.updateDeviceTokenSeen(token);
    }

    public boolean insertTeamUsageEvent(DeviceTokenBinding binding, TeamUsageEvent event) throws SQLException {
        return store(YearMonth.from(event.localDate())).insertTeamUsageEvent(binding, event);
    }

    public List<StoredTeamUsageEvent> loadTeamEvents(LocalDate startDate, LocalDate endDate) throws SQLException {
        List<StoredTeamUsageEvent> events = new ArrayList<>();
        YearMonth month = YearMonth.from(startDate);
        YearMonth endMonth = YearMonth.from(endDate);
        while (!month.isAfter(endMonth)) {
            Path path = dbPath(month);
            if (Files.isRegularFile(path)) {
                for (StoredTeamUsageEvent event : store(month).loadTeamEvents(startDate, endDate)) {
                    events.add(enrich(event));
                }
            }
            month = month.plusMonths(1);
        }
        return events;
    }

    private StoredTeamUsageEvent enrich(StoredTeamUsageEvent event) throws SQLException {
        DeviceTokenBinding binding = registry.findDeviceBinding(event.teamId(), event.userId(), event.deviceId());
        if (binding == null) {
            return event;
        }
        String displayName = binding.displayName();
        return new StoredTeamUsageEvent(event.teamId(), event.userId(),
                displayName == null || displayName.isBlank() ? event.userId() : displayName,
                event.deviceId(), displayName, event.tool(), event.sessionId(), event.model(),
                event.timestamp(), event.usage());
    }

    private SqliteTeamUsageStore store(YearMonth shard) throws SQLException {
        SqliteTeamUsageStore existing = stores.get(shard);
        if (existing != null) {
            return existing;
        }
        try {
            SqliteTeamUsageStore created = new SqliteTeamUsageStore(dbPath(shard));
            created.initialize();
            stores.put(shard, created);
            return created;
        } catch (Exception e) {
            throw new SQLException("initialize team shard failed", e);
        }
    }

    private Path dbPath(YearMonth shard) {
        return rootDir.resolve("agent-dashboard-team-" + shard + ".sqlite");
    }
}
