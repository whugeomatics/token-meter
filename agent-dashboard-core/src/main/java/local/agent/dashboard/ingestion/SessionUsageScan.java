package local.agent.dashboard.ingestion;

import java.util.List;

public record SessionUsageScan(IngestionResult result, List<IngestedUsageEvent> events) {
}
