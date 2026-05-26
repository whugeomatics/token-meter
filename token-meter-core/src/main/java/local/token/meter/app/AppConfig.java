package local.token.meter.app;

import local.token.meter.util.Json;

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

public record AppConfig(Path sessionsDir, Path dbPath, ZoneId zone, int port, String bindHost,
                        boolean ingestMode, boolean reportMode, boolean teamReportMode,
                        boolean registerDeviceTokenMode, boolean collectTeamMode,
                        boolean createDeviceTokenMode, boolean collectClaudeCodeMode,
                        Map<String, String> reportQuery, Map<String, String> options) {
    public static AppConfig from(String[] args) {
        return from(args, System.getenv());
    }

    public static AppConfig from(String[] args, Map<String, String> env) {
        Path sessionsDir = sessionsDir(args, env);
        ZoneId zone = zone(args, sessionsDir, env);
        return new AppConfig(
                sessionsDir,
                dbPath(args, env),
                zone,
                port(args, env),
                bindHost(args, env),
                hasFlag(args, "--ingest"),
                hasFlag(args, "--report"),
                hasFlag(args, "--team-report"),
                hasFlag(args, "--register-device-token"),
                hasFlag(args, "--collect-team"),
                hasFlag(args, "--create-device-token"),
                hasFlag(args, "--collect-claude-code"),
                reportQuery(args),
                options(args, env)
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
            } else if (arg.startsWith("--period=")) {
                query.put("period", arg.substring("--period=".length()));
            } else if (arg.startsWith("--compare=")) {
                query.put("compare", arg.substring("--compare=".length()));
            } else if (arg.startsWith("--team-id=")) {
                query.put("team_id", arg.substring("--team-id=".length()));
            } else if (arg.startsWith("--user-id=")) {
                query.put("user_id", arg.substring("--user-id=".length()));
            } else if (arg.startsWith("--tool=")) {
                query.put("tool", arg.substring("--tool=".length()));
            }
        }
        return query;
    }

    private static Map<String, String> options(String[] args, Map<String, String> env) {
        Map<String, String> result = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int index = arg.indexOf('=');
            if (index > 2) {
                String value = arg.substring(index + 1);
                if (!value.isBlank()) {
                    result.put(arg.substring(2, index), value);
                }
            }
        }
        putEnv(result, "device-token", "TOKEN_METER_DEVICE_TOKEN", env);
        putEnv(result, "team-id", "TOKEN_METER_TEAM_ID", env);
        putEnv(result, "user-id", "TOKEN_METER_USER_ID", env);
        putEnv(result, "device-id", "TOKEN_METER_DEVICE_ID", env);
        putEnv(result, "device-name", "TOKEN_METER_DEVICE_NAME", env);
        putEnv(result, "server-url", "TOKEN_METER_SERVER_URL", env);
        putEnv(result, "batch-size", "TOKEN_METER_BATCH_SIZE", env);
        putEnv(result, "collector-state-db", "TOKEN_METER_COLLECTOR_STATE_DB", env);
        putEnv(result, "admin-token", "TOKEN_METER_ADMIN_TOKEN", env);
        putEnv(result, "claude-code-usage-file", "CLAUDE_CODE_USAGE_FILE", env);
        putEnv(result, "claude-source", "TOKEN_METER_CLAUDE_SOURCE", env);
        putEnv(result, "claude-projects-dir", "TOKEN_METER_CLAUDE_PROJECTS_DIR", env);
        putEnv(result, "claude-otel-input", "TOKEN_METER_CLAUDE_OTEL_INPUT", env);
        putEnv(result, "claude-hook-input", "TOKEN_METER_CLAUDE_HOOK_INPUT", env);
        putEnv(result, "local-ingest-interval-seconds", "TOKEN_METER_LOCAL_INGEST_INTERVAL_SECONDS", env);
        return result;
    }

    private static void putEnv(Map<String, String> result, String key, String envName, Map<String, String> env) {
        String value = env.get(envName);
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

    private static Path sessionsDir(String[] args, Map<String, String> env) {
        String override = option(args, "--sessions-dir").filter(value -> !value.isBlank()).orElse(env.get("CODEX_SESSIONS_DIR"));
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".codex", "sessions");
    }

    private static Path dbPath(String[] args, Map<String, String> env) {
        String override = option(args, "--db").filter(value -> !value.isBlank()).orElse(env.get("TOKEN_METER_DB"));
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".token-meter", "sqlite");
    }

    private static ZoneId zone(String[] args, Path sessionsDir, Map<String, String> env) {
        String override = option(args, "--timezone").filter(value -> !value.isBlank()).orElse(env.get("DASHBOARD_TIMEZONE"));
        if (override != null && !override.isBlank()) {
            return ZoneId.of(override);
        }
        return detectCodexTimezone(sessionsDir).orElse(ZoneId.systemDefault());
    }

    private static int port(String[] args, Map<String, String> env) {
        Optional<String> option = option(args, "--port").filter(value -> !value.isBlank());
        if (option.isPresent()) {
            return Integer.parseInt(option.get());
        }
        String value = env.get("PORT");
        return value == null || value.isBlank() ? 18080 : Integer.parseInt(value);
    }

    private static String bindHost(String[] args, Map<String, String> env) {
        String value = option(args, "--bind").filter(item -> !item.isBlank()).orElse(env.get("TOKEN_METER_BIND"));
        return value == null || value.isBlank() ? "127.0.0.1" : value;
    }

    public long localIngestIntervalSeconds() {
        String value = options.get("local-ingest-interval-seconds");
        if (value == null || value.isBlank()) {
            return 300;
        }
        long seconds = Long.parseLong(value);
        if (seconds < 0) {
            throw new IllegalArgumentException("local-ingest-interval-seconds must be >= 0");
        }
        return seconds;
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
