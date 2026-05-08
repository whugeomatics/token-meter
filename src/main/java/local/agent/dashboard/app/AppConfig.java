package local.agent.dashboard.app;

import local.agent.dashboard.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record AppConfig(Path sessionsDir, Path dbPath, ZoneId zone, int port,
                        boolean ingestMode, boolean reportMode, boolean teamReportMode,
                        boolean registerDeviceTokenMode, boolean collectTeamMode,
                        boolean createDeviceTokenMode,
                        Map<String, String> reportQuery, Map<String, String> options) {
    public static AppConfig from(String[] args) {
        Path sessionsDir = sessionsDir(args);
        ZoneId zone = zone(args, sessionsDir);
        return new AppConfig(
                sessionsDir,
                dbPath(args),
                zone,
                port(args),
                hasFlag(args, "--ingest"),
                hasFlag(args, "--report"),
                hasFlag(args, "--team-report"),
                hasFlag(args, "--register-device-token"),
                hasFlag(args, "--collect-team"),
                hasFlag(args, "--create-device-token"),
                reportQuery(args),
                options(args)
        );
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> reportQuery(String[] args) {
        Map<String, String> query = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--days=")) {
                query.put("days", arg.substring("--days=".length()));
            } else if (arg.startsWith("--month=")) {
                query.put("month", arg.substring("--month=".length()));
            }
        }
        return query;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int index = arg.indexOf('=');
            if (index > 2) {
                result.put(arg.substring(2, index), arg.substring(index + 1));
            }
        }
        putEnv(result, "device-token", "AGENT_DASHBOARD_DEVICE_TOKEN");
        putEnv(result, "team-id", "AGENT_DASHBOARD_TEAM_ID");
        putEnv(result, "user-id", "AGENT_DASHBOARD_USER_ID");
        putEnv(result, "device-id", "AGENT_DASHBOARD_DEVICE_ID");
        putEnv(result, "device-name", "AGENT_DASHBOARD_DEVICE_NAME");
        putEnv(result, "server-url", "AGENT_DASHBOARD_SERVER_URL");
        putEnv(result, "collector-db", "AGENT_DASHBOARD_COLLECTOR_DB");
        putEnv(result, "batch-size", "AGENT_DASHBOARD_BATCH_SIZE");
        putEnv(result, "admin-token", "AGENT_DASHBOARD_ADMIN_TOKEN");
        return result;
    }

    private static void putEnv(Map<String, String> result, String key, String envName) {
        String value = System.getenv(envName);
        if (!result.containsKey(key) && value != null && !value.isBlank()) {
            result.put(key, value);
        }
    }

    private static Optional<String> option(String[] args, String name) {
        String prefix = name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return Optional.of(arg.substring(prefix.length()));
            }
        }
        return Optional.empty();
    }

    private static Path sessionsDir(String[] args) {
        String override = option(args, "--sessions-dir").orElse(System.getenv("CODEX_SESSIONS_DIR"));
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".codex", "sessions");
    }

    private static Path dbPath(String[] args) {
        String override = option(args, "--db").orElse(System.getenv("AGENT_DASHBOARD_DB"));
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".agent-dashboard", "sqlite");
    }

    public Path collectorDbPath() {
        String override = options.get("collector-db");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String dbOverride = options.get("db");
        if (dbOverride != null && !dbOverride.isBlank()) {
            return Path.of(dbOverride);
        }
        return Path.of(System.getProperty("user.home"), ".agent-dashboard", "collector-sqlite");
    }

    private static ZoneId zone(String[] args, Path sessionsDir) {
        String override = option(args, "--timezone").orElse(System.getenv("DASHBOARD_TIMEZONE"));
        if (override != null && !override.isBlank()) {
            return ZoneId.of(override);
        }
        return detectCodexTimezone(sessionsDir).orElse(ZoneId.systemDefault());
    }

    private static int port(String[] args) {
        Optional<String> option = option(args, "--port");
        if (option.isPresent()) {
            return Integer.parseInt(option.get());
        }
        String value = System.getenv("PORT");
        return value == null || value.isBlank() ? 18080 : Integer.parseInt(value);
    }

    private static Optional<ZoneId> detectCodexTimezone(Path sessionsDir) {
        if (!Files.isDirectory(sessionsDir)) {
            return Optional.empty();
        }
        try (var stream = Files.walk(sessionsDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.reverseOrder())
                    .limit(10)
                    .toList();
            for (Path file : files) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.contains("\"timezone\"")) {
                        continue;
                    }
                    Optional<String> timezone = Json.firstString(line, "timezone");
                    if (timezone.isPresent()) {
                        return Optional.of(ZoneId.of(timezone.get()));
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
