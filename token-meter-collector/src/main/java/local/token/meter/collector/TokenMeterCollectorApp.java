package local.token.meter.collector;

import local.token.meter.app.AppConfig;
import local.token.meter.ingestion.SessionUsageScanner;
import local.token.meter.ingestion.ClaudeCodeUsageSource;
import local.token.meter.ingestion.TeamCollector;
import local.token.meter.domain.TeamUsageEvent;
import local.token.meter.util.CliOutput;
import local.token.meter.util.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TokenMeterCollectorApp {
    private TokenMeterCollectorApp() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.from(CollectorEnvFile.withEnvFileDefaults(args, System.getenv()));
        runCollector(config);
    }

    private static void runCollector(AppConfig config) throws Exception {
        Instant uploadTime = Instant.now();
        TeamCollector collector = new TeamCollector(new SessionUsageScanner(config.sessionsDir(), config.zone()),
                config.zone(), config.options().get("server-url"),
                config.options().get("device-token"), config.options().get("user-id"), config.options().get("device-id"),
                batchSize(config));
        try {
            if (config.collectClaudeCodeMode()) {
                CliOutput.writeLine(uploadClaudeCode(config, collector));
            } else {
                CliOutput.writeLine(uploadAllTools(config, collector, uploadTime));
            }
        } catch (IOException | SQLException e) {
            CliOutput.writeLine(errorJson(e, uploadTime, config.zone()));
            System.exit(1);
        }
    }

    static String errorJson(Exception e, Instant uploadTime) {
        return errorJson(e, uploadTime, ZoneId.of("UTC"));
    }

    static String errorJson(Exception e, Instant uploadTime, ZoneId zone) {
        return "{\"status\":\"error\",\"error_code\":\"collector_upload_failed\",\"upload_time\":\""
                + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(uploadTime.atZone(zone))
                + "\",\"message\":\"" + Json.escape(e.getMessage()) + "\"}";
    }

    private static String uploadClaudeCode(AppConfig config, TeamCollector collector) throws IOException, SQLException {
        int days = reportDays(config);
        LocalDate end = LocalDate.now(config.zone());
        LocalDate start = end.minusDays(days - 1L);
        CollectorUsageStore store = collectorStore(config);
        store.insertEvents(collectClaudeCodeEvents(config, start, end));
        return collector.uploadEvents(storedEvents(config, store, start, end), start, end, Instant.now());
    }

    private static String uploadAllTools(AppConfig config, TeamCollector collector, Instant uploadTime) throws IOException, SQLException {
        int days = reportDays(config);
        LocalDate end = LocalDate.now(config.zone());
        LocalDate start = end.minusDays(days - 1L);
        CollectorUsageStore store = collectorStore(config);
        store.insertEvents(collectAllToolEvents(config, collector, start, end));
        return collector.uploadEvents(storedEvents(config, store, start, end), start, end, uploadTime);
    }

    static List<TeamUsageEvent> collectAllToolEvents(AppConfig config, TeamCollector collector,
                                                     LocalDate start, LocalDate end) throws IOException {
        List<TeamUsageEvent> events = new ArrayList<>(collector.collectRecentEvents(start, end));
        events.addAll(collectClaudeCodeEvents(config, start, end));
        return events;
    }

    static List<TeamUsageEvent> collectClaudeCodeEvents(AppConfig config, LocalDate start, LocalDate end)
            throws IOException {
        String sourceMode = config.options().getOrDefault("claude-source", "local");
        String usageFile = claudeInput(config, sourceMode);
        if (usageFile == null || usageFile.isBlank()) {
            throw new IOException("claude input is required");
        }
        var usageSource = new ClaudeCodeUsageSource(Path.of(usageFile), config.zone(),
                config.options().get("user-id"), config.options().get("device-id"),
                claudeSourceKind(sourceMode), claudeSourceQuality(sourceMode));
        return usageSource.events().stream()
                .filter(event -> !event.localDate().isBefore(start) && !event.localDate().isAfter(end))
                .toList();
    }

    static String claudeInput(AppConfig config, String source) throws IOException {
        return switch (source) {
            case "local" -> config.options().getOrDefault("claude-projects-dir",
                    Path.of(System.getProperty("user.home"), ".claude", "projects").toString());
            case "otel", "fixture" -> config.options().getOrDefault("claude-otel-input",
                    config.options().get("claude-code-usage-file"));
            case "hook" -> config.options().getOrDefault("claude-hook-input",
                    config.options().get("claude-code-usage-file"));
            default -> throw new IOException("claude-source must be one of local, otel, hook, or fixture");
        };
    }

    static String claudeSourceKind(String source) {
        return switch (source) {
            case "hook" -> "hook_metadata";
            case "fixture" -> "fixture";
            case "otel" -> "otel_metric";
            default -> "local_jsonl";
        };
    }

    static String claudeSourceQuality(String source) {
        return "hook".equals(source) ? "estimated" : "reported";
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

    private static CollectorUsageStore collectorStore(AppConfig config) throws IOException, SQLException {
        String override = config.options().get("collector-state-db");
        Path path = override == null || override.isBlank()
                ? Path.of(System.getProperty("user.home"), ".token-meter", "sqlite", "token-meter-collector-state.sqlite")
                : Path.of(override);
        CollectorUsageStore store = new CollectorUsageStore(path);
        store.initialize();
        return store;
    }

    private static List<TeamUsageEvent> storedEvents(AppConfig config, CollectorUsageStore store,
                                                     LocalDate start, LocalDate end) throws SQLException {
        return store.loadEvents(start, end, config.options().get("user-id"), config.options().get("device-id"));
    }
}
