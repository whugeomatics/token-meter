package local.token.meter.collector;

import local.token.meter.domain.Snapshot;
import local.token.meter.domain.TeamUsageEvent;

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

final class CollectorUsageStore {
    private final Path dbPath;

    CollectorUsageStore(Path dbPath) {
        this.dbPath = dbPath;
    }

    void initialize() throws IOException, SQLException {
        Path parent = dbPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS collector_usage_events (
                      event_key TEXT PRIMARY KEY,
                      tool TEXT NOT NULL,
                      session_id TEXT NOT NULL,
                      model TEXT NOT NULL,
                      event_timestamp TEXT NOT NULL,
                      local_date TEXT NOT NULL,
                      input_tokens INTEGER NOT NULL,
                      cached_input_tokens INTEGER NOT NULL,
                      output_tokens INTEGER NOT NULL,
                      reasoning_output_tokens INTEGER NOT NULL,
                      total_tokens INTEGER NOT NULL,
                      source_kind TEXT NOT NULL,
                      source_quality TEXT NOT NULL,
                      collected_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_collector_usage_events_local_date
                    ON collector_usage_events(local_date)
                    """);
        }
    }

    int insertEvents(List<TeamUsageEvent> events) throws SQLException {
        int inserted = 0;
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT OR IGNORE INTO collector_usage_events(event_key, tool, session_id, model,
                       event_timestamp, local_date, input_tokens, cached_input_tokens, output_tokens,
                       reasoning_output_tokens, total_tokens, source_kind, source_quality, collected_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            for (TeamUsageEvent event : events) {
                statement.setString(1, event.eventKey());
                statement.setString(2, event.tool());
                statement.setString(3, event.sessionId());
                statement.setString(4, event.model());
                statement.setString(5, event.timestamp().toString());
                statement.setString(6, event.localDate().toString());
                statement.setLong(7, event.usage().inputTokens());
                statement.setLong(8, event.usage().cachedInputTokens());
                statement.setLong(9, event.usage().outputTokens());
                statement.setLong(10, event.usage().reasoningOutputTokens());
                statement.setLong(11, event.usage().totalTokens());
                statement.setString(12, event.sourceKind());
                statement.setString(13, event.sourceQuality());
                statement.setString(14, Instant.now().toString());
                inserted += statement.executeUpdate();
            }
        }
        return inserted;
    }

    List<TeamUsageEvent> loadEvents(LocalDate start, LocalDate end, String userId, String deviceId) throws SQLException {
        List<TeamUsageEvent> events = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT event_key, tool, session_id, model, event_timestamp, local_date,
                       input_tokens, cached_input_tokens, output_tokens, reasoning_output_tokens, total_tokens,
                       source_kind, source_quality
                     FROM collector_usage_events
                     WHERE local_date >= ? AND local_date <= ?
                     ORDER BY event_timestamp, event_key
                     """)) {
            statement.setString(1, start.toString());
            statement.setString(2, end.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Snapshot usage = new Snapshot(rs.getLong("input_tokens"), rs.getLong("cached_input_tokens"),
                            rs.getLong("output_tokens"), rs.getLong("reasoning_output_tokens"), rs.getLong("total_tokens"));
                    events.add(new TeamUsageEvent(rs.getString("event_key"), rs.getString("tool"),
                            rs.getString("session_id"), rs.getString("model"),
                            Instant.parse(rs.getString("event_timestamp")),
                            LocalDate.parse(rs.getString("local_date")), usage, userId, deviceId,
                            rs.getString("source_kind"), rs.getString("source_quality")));
                }
            }
        }
        return events;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }
}
