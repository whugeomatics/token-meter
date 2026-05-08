package local.agent.dashboard.store;

import local.agent.dashboard.domain.Snapshot;
import local.agent.dashboard.domain.UsageEvent;
import local.agent.dashboard.domain.ExportedUsageEvent;
import local.agent.dashboard.ingestion.IngestedUsageEvent;
import local.agent.dashboard.ingestion.SourceFileRecord;
import local.agent.dashboard.ingestion.SourceFileState;

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

public final class SqliteUsageStore implements UsageStore {
    private final Path dbPath;
    private final SqlScripts scripts;

    public SqliteUsageStore(Path dbPath) throws IOException {
        this.dbPath = dbPath;
        this.scripts = SqlScripts.load("/db/schema-v1.sql");
    }

    public void initialize() throws SQLException, IOException {
        Path parent = dbPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(scripts.statement("create_schema_migrations"));
            statement.executeUpdate(scripts.statement("create_source_files"));
            statement.executeUpdate(scripts.statement("create_usage_events"));
            statement.executeUpdate(scripts.statement("create_idx_usage_events_local_date"));
            statement.executeUpdate(scripts.statement("create_idx_usage_events_model"));
            statement.executeUpdate(scripts.statement("create_idx_usage_events_session"));
            statement.executeUpdate(scripts.statement("create_idx_usage_events_timestamp"));
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("insert_schema_migration"))) {
            statement.setString(1, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    public SourceFileRecord findSourceFile(SourceFileState state) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("find_source_file"))) {
            statement.setString(1, "codex");
            statement.setString(2, state.path());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new SourceFileRecord(rs.getLong("id"), rs.getLong("size_bytes"),
                        rs.getString("modified_at"), rs.getString("file_fingerprint"));
            }
        }
    }

    public long upsertSourceFile(SourceFileState state, int lastLine, Instant lastEventTimestamp,
                                 String status, String lastError) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("upsert_source_file"))) {
            statement.setString(1, "codex");
            statement.setString(2, state.path());
            statement.setLong(3, state.sizeBytes());
            statement.setString(4, state.modifiedAt());
            statement.setInt(5, lastLine);
            statement.setString(6, lastEventTimestamp == null ? null : lastEventTimestamp.toString());
            statement.setString(7, state.fileFingerprint());
            statement.setString(8, status);
            statement.setString(9, lastError);
            statement.setString(10, Instant.now().toString());
            statement.executeUpdate();
        }
        SourceFileRecord record = findSourceFile(state);
        if (record == null) {
            throw new SQLException("source file upsert did not return a row");
        }
        return record.id();
    }

    public boolean insertUsageEvent(long sourceFileId, IngestedUsageEvent event) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("insert_usage_event"))) {
            statement.setLong(1, sourceFileId);
            statement.setInt(2, event.lineNumber());
            statement.setString(3, event.eventKey());
            statement.setString(4, "codex");
            statement.setString(5, event.sessionId());
            statement.setString(6, event.model());
            statement.setString(7, event.timestamp().toString());
            statement.setString(8, event.localDate().toString());
            statement.setLong(9, event.delta().inputTokens());
            statement.setLong(10, event.delta().cachedInputTokens());
            statement.setLong(11, event.delta().outputTokens());
            statement.setLong(12, event.delta().reasoningOutputTokens());
            statement.setLong(13, event.delta().totalTokens());
            statement.setString(14, Instant.now().toString());
            return statement.executeUpdate() > 0;
        }
    }

    public List<UsageEvent> loadEvents(LocalDate startDate, LocalDate endDate) throws SQLException {
        List<UsageEvent> events = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("load_usage_events"))) {
            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Snapshot usage = new Snapshot(
                            rs.getLong("input_tokens"),
                            rs.getLong("cached_input_tokens"),
                            rs.getLong("output_tokens"),
                            rs.getLong("reasoning_output_tokens"),
                            rs.getLong("total_tokens")
                    );
                    events.add(new UsageEvent(rs.getString("session_id"), rs.getString("model"),
                            Instant.parse(rs.getString("event_timestamp")), usage));
                }
            }
        }
        return events;
    }

    public List<ExportedUsageEvent> loadExportEvents(LocalDate startDate, LocalDate endDate) throws SQLException {
        List<ExportedUsageEvent> events = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(scripts.statement("load_export_usage_events"))) {
            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Snapshot usage = new Snapshot(
                            rs.getLong("input_tokens"),
                            rs.getLong("cached_input_tokens"),
                            rs.getLong("output_tokens"),
                            rs.getLong("reasoning_output_tokens"),
                            rs.getLong("total_tokens")
                    );
                    events.add(new ExportedUsageEvent(rs.getString("event_key"), rs.getString("session_id"),
                            rs.getString("model"), Instant.parse(rs.getString("event_timestamp")), usage));
                }
            }
        }
        return events;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

}
