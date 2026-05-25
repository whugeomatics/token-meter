package local.token.meter.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DashboardScriptTest {
    @Test
    void localDashboardDoesNotTriggerIngestionOnPageLoad() throws Exception {
        String script = DashboardPage.resource("/static/app.js");

        assertFalse(script.contains("fetch('/api/ingest'"), "Local dashboard should read SQLite reports only");
    }

    @Test
    void localSessionsTabUsesPaginatedSessionsEndpoint() throws Exception {
        String script = DashboardPage.resource("/static/app.js");
        String html = DashboardPage.resource("/static/index.html");

        assertTrue(script.contains("/api/report/sessions?"));
        assertTrue(script.contains("page_size=${encodeURIComponent(state.localSessionsPageSize)}"));
        assertTrue(html.contains("id=\"localSessionsPrev\""));
        assertTrue(html.contains("id=\"localSessionsNext\""));
        assertFalse(script.contains("renderSessions(report.sessions || [])"));
    }
}
