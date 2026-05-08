package local.agent.dashboard.collector;

import local.agent.dashboard.app.AppConfig;
import local.agent.dashboard.ingestion.SessionUsageScanner;
import local.agent.dashboard.ingestion.TeamCollector;
import local.agent.dashboard.util.CliOutput;
import local.agent.dashboard.util.Json;

import java.io.IOException;

public final class AgentDashboardCollectorApp {
    private AgentDashboardCollectorApp() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.from(args);
        runCollector(config);
    }

    private static void runCollector(AppConfig config) throws Exception {
        TeamCollector collector = new TeamCollector(new SessionUsageScanner(config.sessionsDir(), config.zone()),
                config.zone(), config.options().get("server-url"),
                config.options().get("device-token"), config.options().get("user-id"), config.options().get("device-id"),
                batchSize(config));
        try {
            CliOutput.writeLine(collector.uploadRecent(reportDays(config)));
        } catch (IOException e) {
            CliOutput.writeLine("{\"status\":\"error\",\"error_code\":\"collector_upload_failed\",\"message\":\""
                    + Json.escape(e.getMessage()) + "\"}");
            System.exit(1);
        }
    }

    private static int reportDays(AppConfig config) {
        String days = config.reportQuery().get("days");
        return days == null || days.isBlank() ? 7 : Integer.parseInt(days);
    }

    private static int batchSize(AppConfig config) {
        String batchSize = config.options().get("batch-size");
        if (batchSize == null || batchSize.isBlank()) {
            return 500;
        }
        return Math.max(1, Math.min(500, Integer.parseInt(batchSize)));
    }
}
