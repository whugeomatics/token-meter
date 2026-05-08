package local.token.meter.store;

import local.token.meter.domain.UsageEvent;
import local.token.meter.domain.ExportedUsageEvent;
import local.token.meter.ingestion.IngestedUsageEvent;
import local.token.meter.ingestion.SourceFileRecord;
import local.token.meter.ingestion.SourceFileState;

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
