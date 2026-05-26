package local.token.meter.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AppConfigLocalIngestionTest {
    @Test
    void localIngestionIntervalDefaultsToFiveMinutes() {
        AppConfig config = AppConfig.from(new String[0]);

        assertEquals(300, config.localIngestIntervalSeconds());
    }

    @Test
    void localIngestionIntervalCanBeDisabled() {
        AppConfig config = AppConfig.from(new String[]{"--local-ingest-interval-seconds=0"});

        assertEquals(0, config.localIngestIntervalSeconds());
    }

    @Test
    void blankCliOptionDoesNotOverrideSystemEnvironmentOption() {
        AppConfig config = AppConfig.from(new String[]{"--server-url="},
                Map.of("TOKEN_METER_SERVER_URL", "http://env-server:18080"));

        assertEquals("http://env-server:18080", config.options().get("server-url"));
    }
}
