package local.token.meter.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class DashboardScriptTest {
    @Test
    void localDashboardDoesNotTriggerIngestionOnPageLoad() throws Exception {
        String script = DashboardPage.resource("/static/app.js");

        assertFalse(script.contains("fetch('/api/ingest'"), "Local dashboard should read SQLite reports only");
    }
}
