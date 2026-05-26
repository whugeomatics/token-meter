package local.token.meter.ingestion;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LocalIngestionScheduler {
    private static final Logger LOG = Logger.getLogger(LocalIngestionScheduler.class.getName());
    private final LocalIngestionService localIngestionService;
    private final long intervalSeconds;

    public LocalIngestionScheduler(LocalIngestionService localIngestionService, long intervalSeconds) {
        this.localIngestionService = localIngestionService;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        if (intervalSeconds <= 0) {
            LOG.info("Local ingestion scheduler disabled");
            return;
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "token-meter-local-ingestion");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::runOnce, 5, intervalSeconds, TimeUnit.SECONDS);
        LOG.info("Local ingestion scheduler interval seconds: " + intervalSeconds);
    }

    private void runOnce() {
        try {
            IngestionResult result = localIngestionService.ingest();
            LOG.info("Scheduled local ingestion: " + result.toJson());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Scheduled local ingestion failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
