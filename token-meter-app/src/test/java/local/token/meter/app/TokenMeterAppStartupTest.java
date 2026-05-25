package local.token.meter.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class TokenMeterAppStartupTest {
    @Test
    void serverStartupDoesNotRunLocalIngestion() throws Exception {
        String source = Files.readString(Path.of("src/main/java/local/token/meter/app/TokenMeterApp.java"));

        assertFalse(source.contains("Startup ingestion"), "dashboard startup should not block on local ingestion");
        assertFalse(source.contains("startupIngestion"), "dashboard startup should only open stores and start the server");
        assertFalse(source.contains("localIngestionService.ingest();"), "dashboard startup should not run ingestion inline");
    }

    @Test
    void serverStartupStartsProgramManagedLocalIngestionScheduler() throws Exception {
        String source = Files.readString(Path.of("src/main/java/local/token/meter/app/TokenMeterApp.java"));

        org.junit.jupiter.api.Assertions.assertTrue(source.contains("LocalIngestionScheduler"));
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("localIngestIntervalSeconds()"));
    }
}
