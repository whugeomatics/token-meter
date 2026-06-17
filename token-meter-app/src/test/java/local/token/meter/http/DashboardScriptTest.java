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

    @Test
    void dashboardShowsContinuousCollectionNotice() throws Exception {
        String script = DashboardPage.resource("/static/app.js");
        String html = DashboardPage.resource("/static/index.html");

        assertTrue(html.contains("Reports show the latest stored snapshot"));
        assertTrue(script.contains("hasUsage(report)"));
        assertTrue(script.contains("No usage data collected for the current filters yet."));
        assertTrue(script.contains("renderPeriodComparison(comparison, hasData)"));
        assertTrue(script.contains("renderDaily(report.daily || [], 'localDailyChart', 'localDailyStatus', 'localDailyBody', hasData)"));
        assertTrue(script.contains("renderDaily(report.daily || [], 'teamDailyChart', 'teamDailyStatus', 'teamDailyBody', hasData)"));
        assertTrue(script.contains("setTrendEmpty"));
        assertFalse(script.contains("Data is being generated"));
        assertFalse(script.contains("renderIngestionStatus"));
        assertFalse(script.contains("/api/ingest/status"));
    }

    @Test
    void uploadHealthRecentUploadsColumnUsesTextAlignment() throws Exception {
        String script = DashboardPage.resource("/static/app.js");
        String html = DashboardPage.resource("/static/index.html");
        String css = DashboardPage.resource("/static/app.css");

        assertTrue(html.contains("class=\"recent-uploads\""));
        assertTrue(script.contains("class=\"recent-uploads\""));
        assertTrue(css.contains(".recent-uploads"));
        assertTrue(css.contains("text-align: left"));
        assertTrue(html.contains("Latest Upload"));
        assertTrue(script.contains("rows.slice(0, 1)"));
        assertFalse(script.contains("rows.slice(0, 3)"));
    }

    @Test
    void teamDashboardDefaultsToWeekPeriod() throws Exception {
        String script = DashboardPage.resource("/static/app.js");

        assertTrue(script.contains("localPeriod: 'day', teamPeriod: 'week'"));
        assertFalse(script.contains("localPeriod: 'day', teamPeriod: 'day'"));
    }
}
