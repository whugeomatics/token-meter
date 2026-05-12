package local.token.meter.app;

import local.token.meter.domain.DeviceTokenBinding;
import local.token.meter.domain.ReportQuery;
import local.token.meter.http.DashboardServer;
import local.token.meter.ingestion.CodexIngestionService;
import local.token.meter.ingestion.IngestionResult;
import local.token.meter.ingestion.TeamIngestionService;
import local.token.meter.report.ReportService;
import local.token.meter.report.TeamReportService;
import local.token.meter.store.TeamUsageStore;
import local.token.meter.store.TeamUsageStores;
import local.token.meter.store.UsageStore;
import local.token.meter.store.UsageStores;
import local.token.meter.util.CliOutput;
import local.token.meter.util.DeviceTokenGenerator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class TokenMeterApp {
    public static final String SERVER_BIND_ADDRESS = "0.0.0.0";
    private static final Logger LOG = Logger.getLogger(TokenMeterApp.class.getName());

    private TokenMeterApp() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.from(args);

        if (config.collectTeamMode()) {
            throw new IllegalArgumentException("--collect-team is provided by token-meter-collector");
        }

        if (config.createDeviceTokenMode()) {
            TeamUsageStore teamUsageStore = openTeamStore(config);
            String token = createDeviceTokenIfConfigured(config, teamUsageStore);
            CliOutput.writeLine("{\"status\":\"ok\",\"registered\":true,\"device_token\":\"" + token + "\"}");
            return;
        }

        TeamUsageStore teamUsageStore = openTeamStore(config);
        registerDeviceTokenIfConfigured(config, teamUsageStore);

        if (config.registerDeviceTokenMode()) {
            CliOutput.writeLine("{\"status\":\"ok\",\"registered\":true}");
            return;
        }

        if (config.ingestMode()) {
            UsageStore usageStore = openUsageStore(config.dbPath());
            IngestionResult result = new CodexIngestionService(config.sessionsDir(), config.zone(), usageStore).ingest();
            CliOutput.writeLine(result.toJson());
            if (!result.errors().isEmpty()) {
                System.exit(1);
            }
            return;
        }

        UsageStore usageStore = openUsageStore(config.dbPath());
        ReportService reportService = new ReportService(usageStore, config.zone());
        TeamReportService teamReportService = new TeamReportService(teamUsageStore, config.zone());
        if (config.teamReportMode()) {
            CliOutput.writeLine(teamReportService.report(config.reportQuery()).toJson());
            return;
        }

        if (config.options().containsKey("team-ingest-file")) {
            Path payload = Path.of(config.options().get("team-ingest-file"));
            String body = Files.readString(payload, StandardCharsets.UTF_8);
            CliOutput.writeLine(new TeamIngestionService(teamUsageStore, config.zone())
                    .ingest(config.options().get("device-token"), body).toJson());
            return;
        }

        if (config.reportMode()) {
            ReportQuery query = ReportQuery.from(config.reportQuery(), config.zone());
            CliOutput.writeLine(reportService.report(query).toJson());
            return;
        }

        CodexIngestionService localIngestionService = new CodexIngestionService(config.sessionsDir(), config.zone(), usageStore);
        IngestionResult startupIngestion = localIngestionService.ingest();
        LOG.info("Startup ingestion: " + startupIngestion.toJson());

        DashboardServer server = new DashboardServer(config.port(), reportService, localIngestionService,
                new TeamIngestionService(teamUsageStore, config.zone()), teamReportService,
                teamUsageStore, config.options().get("admin-token"));
        server.start();

        LOG.info("Token Meter listening on http://" + SERVER_BIND_ADDRESS + ":" + config.port());
        LOG.info("Codex sessions dir: " + config.sessionsDir());
        LOG.info("Token Meter DB: " + config.dbPath());
        LOG.info("Token Meter Team registry DB: " + TeamUsageStores.resolveTeamRegistryPath(config.dbPath()));
        LOG.info("Token Meter Team event DB pattern: " + TeamUsageStores.resolveTeamDbPath(config.dbPath()));
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

    private static String required(AppConfig config, String key) {
        String value = config.options().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

}
