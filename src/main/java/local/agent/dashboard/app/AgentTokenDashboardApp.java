package local.agent.dashboard.app;

import local.agent.dashboard.domain.DeviceTokenBinding;
import local.agent.dashboard.domain.ReportQuery;
import local.agent.dashboard.http.DashboardServer;
import local.agent.dashboard.ingestion.CodexIngestionService;
import local.agent.dashboard.ingestion.IngestionResult;
import local.agent.dashboard.ingestion.TeamCollector;
import local.agent.dashboard.ingestion.TeamIngestionService;
import local.agent.dashboard.report.ReportService;
import local.agent.dashboard.report.TeamReportService;
import local.agent.dashboard.store.TeamUsageStore;
import local.agent.dashboard.store.TeamUsageStores;
import local.agent.dashboard.store.UsageStore;
import local.agent.dashboard.store.UsageStores;
import local.agent.dashboard.util.DeviceTokenGenerator;
import local.agent.dashboard.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AgentTokenDashboardApp {
    private AgentTokenDashboardApp() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.from(args);

        if (config.collectTeamMode()) {
            runCollector(config);
            return;
        }

        if (config.createDeviceTokenMode()) {
            TeamUsageStore teamUsageStore = openTeamStore(config);
            String token = createDeviceTokenIfConfigured(config, teamUsageStore);
            System.out.println("{\"status\":\"ok\",\"registered\":true,\"device_token\":\"" + token + "\"}");
            return;
        }

        TeamUsageStore teamUsageStore = openTeamStore(config);
        registerDeviceTokenIfConfigured(config, teamUsageStore);

        if (config.registerDeviceTokenMode()) {
            System.out.println("{\"status\":\"ok\",\"registered\":true}");
            return;
        }

        if (config.ingestMode()) {
            UsageStore usageStore = openUsageStore(config.dbPath());
            IngestionResult result = new CodexIngestionService(config.sessionsDir(), config.zone(), usageStore).ingest();
            System.out.println(result.toJson());
            if (!result.errors().isEmpty()) {
                System.exit(1);
            }
            return;
        }

        UsageStore usageStore = openUsageStore(config.dbPath());
        ReportService reportService = new ReportService(usageStore, config.zone());
        TeamReportService teamReportService = new TeamReportService(teamUsageStore, config.zone());
        if (config.teamReportMode()) {
            ReportQuery query = ReportQuery.from(config.reportQuery(), config.zone());
            System.out.println(teamReportService.report(query).toJson());
            return;
        }

        if (config.options().containsKey("team-ingest-file")) {
            Path payload = Path.of(config.options().get("team-ingest-file"));
            String body = Files.readString(payload, StandardCharsets.UTF_8);
            System.out.println(new TeamIngestionService(teamUsageStore, config.zone())
                    .ingest(config.options().get("device-token"), body).toJson());
            return;
        }

        if (config.reportMode()) {
            ReportQuery query = ReportQuery.from(config.reportQuery(), config.zone());
            System.out.println(reportService.report(query).toJson());
            return;
        }

        IngestionResult startupIngestion = new CodexIngestionService(config.sessionsDir(), config.zone(), usageStore).ingest();
        System.out.println("Startup ingestion: " + startupIngestion.toJson());

        DashboardServer server = new DashboardServer(config.port(), reportService,
                new TeamIngestionService(teamUsageStore, config.zone()), teamReportService,
                teamUsageStore, config.options().get("admin-token"));
        server.start();

        System.out.println("Agent Token Dashboard listening on http://127.0.0.1:" + config.port());
        System.out.println("Codex sessions dir: " + config.sessionsDir());
        System.out.println("Agent Dashboard DB: " + config.dbPath());
        System.out.println("Agent Dashboard Team registry DB: " + TeamUsageStores.resolveTeamRegistryPath(config.dbPath()));
        System.out.println("Agent Dashboard Team event DB pattern: " + TeamUsageStores.resolveTeamDbPath(config.dbPath()));
    }

    private static void runCollector(AppConfig config) throws Exception {
        UsageStore collectorStore = openUsageStore(config.collectorDbPath());
        IngestionResult result = new CodexIngestionService(config.sessionsDir(), config.zone(), collectorStore).ingest();
        if (!result.errors().isEmpty()) {
            System.out.println(result.toJson());
            System.exit(1);
        }
        TeamCollector collector = new TeamCollector(collectorStore, config.zone(), config.options().get("server-url"),
                config.options().get("device-token"), config.options().get("user-id"), config.options().get("device-id"),
                batchSize(config));
        try {
            System.out.println(collector.uploadRecent(reportDays(config)));
        } catch (IOException e) {
            System.out.println("{\"status\":\"error\",\"error_code\":\"collector_upload_failed\",\"message\":\""
                    + Json.escape(e.getMessage()) + "\"}");
            System.exit(1);
        }
    }

    private static UsageStore openUsageStore(Path path) throws Exception {
        UsageStore usageStore = UsageStores.open(path);
        usageStore.initialize();
        return usageStore;
    }

    private static TeamUsageStore openTeamStore(AppConfig config) throws Exception {
        TeamUsageStore teamUsageStore = TeamUsageStores.open(config.dbPath());
        teamUsageStore.initialize();
        return teamUsageStore;
    }

    private static String createDeviceTokenIfConfigured(AppConfig config, TeamUsageStore teamUsageStore) throws Exception {
        String teamId = required(config, "team-id");
        String userId = required(config, "user-id");
        String deviceId = required(config, "device-id");
        String token = DeviceTokenGenerator.generate();
        String deviceName = config.options().getOrDefault("device-name", deviceId);
        teamUsageStore.upsertDeviceToken(token, new DeviceTokenBinding(teamId, userId, deviceId, deviceName, "active"));
        return token;
    }

    private static void registerDeviceTokenIfConfigured(AppConfig config, TeamUsageStore teamUsageStore) throws Exception {
        String token = config.options().get("device-token");
        String teamId = config.options().get("team-id");
        String userId = config.options().get("user-id");
        String deviceId = config.options().get("device-id");
        if (token == null || teamId == null || userId == null || deviceId == null) {
            return;
        }
        String deviceName = config.options().getOrDefault("device-name", deviceId);
        teamUsageStore.upsertDeviceToken(token, new DeviceTokenBinding(teamId, userId, deviceId, deviceName, "active"));
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

    private static String required(AppConfig config, String key) {
        String value = config.options().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

}
