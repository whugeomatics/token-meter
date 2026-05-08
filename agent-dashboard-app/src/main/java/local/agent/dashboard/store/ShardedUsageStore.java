package local.agent.dashboard.store;

import local.agent.dashboard.domain.UsageEvent;
import local.agent.dashboard.domain.ExportedUsageEvent;
import local.agent.dashboard.ingestion.IngestedUsageEvent;
import local.agent.dashboard.ingestion.SourceFileRecord;
import local.agent.dashboard.ingestion.SourceFileState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShardedUsageStore implements UsageStore {
    private static final long SHARD_ID_FACTOR = 1_000_000_000L;

    private final Path rootDir;
    private final Map<YearMonth, SqliteUsageStore> stores = new HashMap<>();
    private final Map<Long, SqliteUsageStore> sourceFileStores = new HashMap<>();

    public ShardedUsageStore(Path rootDir) {
        this.rootDir = rootDir;
    }

    public void initialize() throws Exception {
        Files.createDirectories(rootDir);
    }

    public SourceFileRecord findSourceFile(SourceFileState state) throws SQLException {
        YearMonth shard = shardFor(state);
        SourceFileRecord record = store(shard).findSourceFile(state);
        if (record == null) {
            return null;
        }
        long id = encodeId(shard, record.id());
        sourceFileStores.put(id, store(shard));
        return new SourceFileRecord(id, record.sizeBytes(), record.modifiedAt(), record.fileFingerprint());
    }

    public long upsertSourceFile(SourceFileState state, int lastLine, Instant lastEventTimestamp,
                                 String status, String lastError) throws SQLException {
        YearMonth shard = shardFor(state);
        SqliteUsageStore store = store(shard);
        long localId = store.upsertSourceFile(state, lastLine, lastEventTimestamp, status, lastError);
        long id = encodeId(shard, localId);
        sourceFileStores.put(id, store);
        return id;
    }

    public boolean insertUsageEvent(long sourceFileId, IngestedUsageEvent event) throws SQLException {
        SqliteUsageStore store = sourceFileStores.get(sourceFileId);
        if (store == null) {
            throw new SQLException("unknown source file shard");
        }
        return store.insertUsageEvent(localId(sourceFileId), event);
    }

    public List<UsageEvent> loadEvents(LocalDate startDate, LocalDate endDate) throws SQLException {
        List<UsageEvent> events = new ArrayList<>();
        for (YearMonth shard : existingShards()) {
            events.addAll(store(shard).loadEvents(startDate, endDate));
        }
        return events;
    }

    public List<ExportedUsageEvent> loadExportEvents(LocalDate startDate, LocalDate endDate) throws SQLException {
        List<ExportedUsageEvent> events = new ArrayList<>();
        for (YearMonth shard : existingShards()) {
            events.addAll(store(shard).loadExportEvents(startDate, endDate));
        }
        return events;
    }

    private List<YearMonth> existingShards() throws SQLException {
        if (!Files.isDirectory(rootDir)) {
            return List.of();
        }
        try (var stream = Files.list(rootDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> shardFromDbFileName(path.getFileName().toString()))
                    .filter(month -> month != null)
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (Exception e) {
            throw new SQLException("list usage shards failed", e);
        }
    }

    private SqliteUsageStore store(YearMonth shard) throws SQLException {
        SqliteUsageStore existing = stores.get(shard);
        if (existing != null) {
            return existing;
        }
        try {
            SqliteUsageStore created = new SqliteUsageStore(dbPath(shard));
            created.initialize();
            stores.put(shard, created);
            return created;
        } catch (Exception e) {
            throw new SQLException("initialize shard failed", e);
        }
    }

    private Path dbPath(YearMonth shard) {
        return rootDir.resolve("agent-dashboard-" + shard + ".sqlite");
    }

    private static YearMonth shardFor(SourceFileState state) {
        YearMonth pathMonth = shardFromPath(state.path());
        if (pathMonth != null) {
            return pathMonth;
        }
        return YearMonth.from(Instant.parse(state.modifiedAt()).atZone(java.time.ZoneOffset.UTC));
    }

    private static YearMonth shardFromPath(String sourcePath) {
        Path path = Path.of(sourcePath);
        for (int i = 0; i < path.getNameCount() - 1; i++) {
            String year = path.getName(i).toString();
            String month = path.getName(i + 1).toString();
            if (year.length() == 4 && month.length() == 2 && year.chars().allMatch(Character::isDigit)
                    && month.chars().allMatch(Character::isDigit)) {
                int monthValue = Integer.parseInt(month);
                if (monthValue >= 1 && monthValue <= 12) {
                    return YearMonth.of(Integer.parseInt(year), monthValue);
                }
            }
        }
        return null;
    }

    private static YearMonth shardFromDbFileName(String fileName) {
        String prefix = "agent-dashboard-";
        String suffix = ".sqlite";
        if (!fileName.startsWith(prefix) || !fileName.endsWith(suffix)) {
            return null;
        }
        String value = fileName.substring(prefix.length(), fileName.length() - suffix.length());
        try {
            return YearMonth.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static long encodeId(YearMonth shard, long localId) {
        return ((long) shard.getYear() * 100L + shard.getMonthValue()) * SHARD_ID_FACTOR + localId;
    }

    private static long localId(long encodedId) {
        return encodedId % SHARD_ID_FACTOR;
    }
}
