package local.agent.dashboard.store;

import local.agent.dashboard.domain.DeviceTokenBinding;
import local.agent.dashboard.domain.DeviceTokenRecord;
import local.agent.dashboard.domain.Snapshot;
import local.agent.dashboard.domain.StoredTeamUsageEvent;
import local.agent.dashboard.domain.TeamUploadRecord;
import local.agent.dashboard.domain.TeamUsageEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class SqliteTeamUsageStore implements TeamUsageStore {
    private final Path dbPath;
    private final SqlScripts scripts;
    private final boolean tokenTables;
    private final boolean eventTables;
    private final boolean uploadTables;

    public SqliteTeamUsageStore(Path dbPath) throws IOException {
        this(dbPath, true, true, true);
    }

    public SqliteTeamUsageStore(Path dbPath, boolean tokenTables, boolean eventTables, boolean uploadTables) throws IOException {
        this.dbPath = dbPath;
        this.scripts = SqlScripts.load("/db/schema-v1.sql");
        this.tokenTables = tokenTables;
        this.eventTables = eventTables;
        this.uploadTables = uploadTables;
    }

    public void initialize() throws Exception {
        Path parent = dbPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(scripts.statement("create_schema_migrations"));
            if (tokenTables) {
                statement.executeUpdate(scripts.statement("create_device_tokens"));
                addTokenSecretColumn(statement);
            }
            if (eventTables) {
                statement.executeUpdate(scripts.statement("create_team_usage_events"));
                statement.executeUpdate(scripts.statement("create_idx_team_usage_events_local_date"));
                statement.executeUpdate(scripts.statement("create_idx_team_usage_events_user"));
                statement.executeUpdate(scripts.statement("create_idx_team_usage_events_device"));
                statement.executeUpdate(scripts.statement("create_idx_team_usage_events_model"));
            }
            if (uploadTables) {
                statement.executeUpdate(scripts.statement("create_team_uploads"));
                statement.executeUpdate(scripts.statement("create_idx_team_uploads_date"));
                statement.executeUpdate(scripts.statement("create_idx_team_uploads_team_user"));
            }
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("insert_schema_migration"))) {
            statement.setString(1, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    public void upsertDeviceToken(String token, DeviceTokenBinding binding) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("upsert_device_token"))) {
            statement.setString(1, TokenHasher.sha256(token));
            statement.setString(2, token);
            statement.setString(3, binding.teamId());
            statement.setString(4, binding.userId());
            statement.setString(5, binding.deviceId());
            statement.setString(6, binding.displayName());
            statement.setString(7, binding.status());
            statement.setString(8, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    public DeviceTokenBinding findDeviceToken(String token) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("find_device_token"))) {
            statement.setString(1, TokenHasher.sha256(token));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new DeviceTokenBinding(rs.getString("team_id"), rs.getString("user_id"),
                        rs.getString("device_id"), rs.getString("display_name"), rs.getString("status"));
            }
        }
    }

    public List<DeviceTokenRecord> listDeviceTokens() throws SQLException {
        List<DeviceTokenRecord> tokens = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("list_device_tokens"));
            ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String tokenSecret = rs.getString("token_secret");
                tokens.add(new DeviceTokenRecord(rs.getLong("token_id"), preview(tokenSecret),
                        tokenSecret != null && !tokenSecret.isBlank(), rs.getString("team_id"),
                        rs.getString("user_id"), rs.getString("device_id"), rs.getString("display_name"),
                        rs.getString("status"), rs.getString("created_at"), rs.getString("last_seen_at")));
            }
        }
        return tokens;
    }

    public String getDeviceTokenSecret(long tokenId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("get_device_token_secret"))) {
            statement.setLong(1, tokenId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString("token_secret");
            }
        }
    }

    public boolean deleteDeviceToken(long tokenId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("delete_device_token"))) {
            statement.setLong(1, tokenId);
            return statement.executeUpdate() > 0;
        }
    }

    public DeviceTokenBinding findDeviceBinding(String teamId, String userId, String deviceId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("find_device_binding"))) {
            statement.setString(1, teamId);
            statement.setString(2, userId);
            statement.setString(3, deviceId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new DeviceTokenBinding(rs.getString("team_id"), rs.getString("user_id"),
                        rs.getString("device_id"), rs.getString("display_name"), rs.getString("status"));
            }
        }
    }

    public void updateDeviceTokenSeen(String token) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("update_device_token_seen"))) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, TokenHasher.sha256(token));
            statement.executeUpdate();
        }
    }

    public void insertTeamUpload(TeamUploadRecord upload) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("insert_team_upload"))) {
            statement.setString(1, upload.teamId());
            statement.setString(2, upload.userId());
            statement.setString(3, upload.deviceId());
            statement.setString(4, upload.uploadDate());
            statement.setString(5, upload.uploadTime());
            statement.setInt(6, upload.eventCount());
            statement.setInt(7, upload.accepted());
            statement.setInt(8, upload.duplicate());
            statement.setInt(9, upload.rejected());
            statement.setString(10, upload.status());
            statement.setString(11, upload.message());
            statement.executeUpdate();
        }
    }

    public boolean insertTeamUsageEvent(DeviceTokenBinding binding, TeamUsageEvent event) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("insert_team_usage_event"))) {
            statement.setString(1, binding.teamId());
            statement.setString(2, binding.userId());
            statement.setString(3, binding.deviceId());
            statement.setString(4, event.eventKey());
            statement.setString(5, event.tool());
            statement.setString(6, event.sessionId());
            statement.setString(7, event.model());
            statement.setString(8, event.timestamp().toString());
            statement.setString(9, event.localDate().toString());
            statement.setLong(10, event.usage().inputTokens());
            statement.setLong(11, event.usage().cachedInputTokens());
            statement.setLong(12, event.usage().outputTokens());
            statement.setLong(13, event.usage().reasoningOutputTokens());
            statement.setLong(14, event.usage().totalTokens());
            statement.setString(15, Instant.now().toString());
            return statement.executeUpdate() > 0;
        }
    }

    public List<StoredTeamUsageEvent> loadTeamEvents(LocalDate startDate, LocalDate endDate) throws SQLException {
        List<StoredTeamUsageEvent> events = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement(
                     tokenTables ? "load_team_usage_events" : "load_team_usage_events_plain"))) {
            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Snapshot usage = new Snapshot(rs.getLong("input_tokens"), rs.getLong("cached_input_tokens"),
                            rs.getLong("output_tokens"), rs.getLong("reasoning_output_tokens"), rs.getLong("total_tokens"));
                    String displayName = rs.getString("display_name");
                    events.add(new StoredTeamUsageEvent(rs.getString("team_id"), rs.getString("user_id"),
                            displayName == null || displayName.isBlank() ? rs.getString("user_id") : displayName,
                            rs.getString("device_id"), displayName, rs.getString("tool"), rs.getString("session_id"),
                            rs.getString("model"), Instant.parse(rs.getString("event_timestamp")), usage));
                }
            }
        }
        return events;
    }

    public List<TeamUploadRecord> loadTeamUploads(LocalDate startDate, LocalDate endDate) throws SQLException {
        List<TeamUploadRecord> uploads = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("load_team_uploads"))) {
            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    uploads.add(new TeamUploadRecord(rs.getString("team_id"), rs.getString("user_id"),
                            rs.getString("device_id"), rs.getString("upload_date"), rs.getString("upload_time"),
                            rs.getInt("event_count"), rs.getInt("accepted"), rs.getInt("duplicate"),
                            rs.getInt("rejected"), rs.getString("status"), rs.getString("message")));
                }
            }
        }
        return uploads;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    private void addTokenSecretColumn(Statement statement) throws SQLException {
        try {
            statement.executeUpdate(scripts.statement("alter_device_tokens_add_token_secret"));
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                throw e;
            }
        }
    }

    private String preview(String token) {
        if (token == null || token.isBlank()) {
            return "unrecoverable";
        }
        if (token.length() <= 12) {
            return token.charAt(0) + "****" + token.charAt(token.length() - 1);
        }
        return token.substring(0, 6) + "****" + token.substring(token.length() - 4);
    }
}
