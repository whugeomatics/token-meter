package local.token.meter.collector;

import local.token.meter.app.AppConfig;
import local.token.meter.ingestion.SessionUsageScanner;
import local.token.meter.ingestion.ClaudeCodeUsageSource;
import local.token.meter.ingestion.TeamCollector;
import local.token.meter.util.CliOutput;
import local.token.meter.util.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

public final class TokenMeterCollectorApp {
    private TokenMeterCollectorApp() {
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
            if (config.collectClaudeCodeMode()) {
                CliOutput.writeLine(uploadClaudeCode(config, collector));
            } else {
                CliOutput.writeLine(collector.uploadRecent(reportDays(config)));
            }
        } catch (IOException e) {
            CliOutput.writeLine("{\"status\":\"error\",\"error_code\":\"collector_upload_failed\",\"message\":\""
                    + Json.escape(e.getMessage()) + "\"}");
            System.exit(1);
        }
    }

    private static String uploadClaudeCode(AppConfig config, TeamCollector collector) throws IOException {
        String sourceMode = config.options().getOrDefault("claude-source", "otel");
        String usageFile = claudeInput(config, sourceMode);
        if (usageFile == null || usageFile.isBlank()) {
            throw new IOException("claude-otel-input or claude-hook-input is required");
        }
        int days = reportDays(config);
        LocalDate end = LocalDate.now(config.zone());
        LocalDate start = end.minusDays(days - 1L);
        var usageSource = new ClaudeCodeUsageSource(Path.of(usageFile), config.zone(),
                config.options().get("user-id"), config.options().get("device-id"),
                claudeSourceKind(sourceMode), claudeSourceQuality(sourceMode));
        var events = usageSource.events().stream()
                .filter(event -> !event.localDate().isBefore(start) && !event.localDate().isAfter(end))
                .toList();
        return collector.uploadEvents(events, start, end, Instant.now());
    }

    private static String claudeInput(AppConfig config, String source) throws IOException {
        return switch (source) {
            case "otel", "fixture" -> config.options().getOrDefault("claude-otel-input",
                    config.options().get("claude-code-usage-file"));
            case "hook" -> config.options().getOrDefault("claude-hook-input",
                    config.options().get("claude-code-usage-file"));
            default -> throw new IOException("claude-source must be one of otel, hook, or fixture");
        };
    }

    private static String claudeSourceKind(String source) {
        return "hook".equals(source) ? "hook_metadata" : "otel_metrics";
    }

    private static String claudeSourceQuality(String source) {
        return "hook".equals(source) ? "metadata_only" : "official";
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
