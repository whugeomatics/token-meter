package local.agent.dashboard.store;

import local.agent.dashboard.domain.UsageEvent;
import local.agent.dashboard.domain.ExportedUsageEvent;
import local.agent.dashboard.ingestion.IngestedUsageEvent;
import local.agent.dashboard.ingestion.SourceFileRecord;
import local.agent.dashboard.ingestion.SourceFileState;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface UsageStore {
    void initialize() throws Exception;

    SourceFileRecord findSourceFile(SourceFileState state) throws SQLException;

    long upsertSourceFile(SourceFileState state, int lastLine, java.time.Instant lastEventTimestamp,
                          String status, String lastError) throws SQLException;

    boolean insertUsageEvent(long sourceFileId, IngestedUsageEvent event) throws SQLException;

    List<UsageEvent> loadEvents(LocalDate startDate, LocalDate endDate) throws SQLException;

    List<ExportedUsageEvent> loadExportEvents(LocalDate startDate, LocalDate endDate) throws SQLException;
}
