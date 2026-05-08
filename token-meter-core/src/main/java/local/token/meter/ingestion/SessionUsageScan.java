package local.token.meter.ingestion;

import java.util.List;

public record SessionUsageScan(IngestionResult result, List<IngestedUsageEvent> events) {
}
