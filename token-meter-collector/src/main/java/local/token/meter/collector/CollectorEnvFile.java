package local.token.meter.collector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CollectorEnvFile {
    private static final Map<String, String> ENV_TO_ARG = Map.ofEntries(
            Map.entry("TOKEN_METER_SERVER_URL", "server-url"),
            Map.entry("TOKEN_METER_DEVICE_TOKEN", "device-token"),
            Map.entry("TOKEN_METER_USER_ID", "user-id"),
            Map.entry("TOKEN_METER_DEVICE_ID", "device-id"),
            Map.entry("TOKEN_METER_BATCH_SIZE", "batch-size"),
            Map.entry("TOKEN_METER_COLLECTOR_STATE_DB", "collector-state-db"),
            Map.entry("TOKEN_METER_DAYS", "days"),
            Map.entry("CODEX_SESSIONS_DIR", "sessions-dir"),
            Map.entry("TOKEN_METER_CLAUDE_SOURCE", "claude-source"),
            Map.entry("TOKEN_METER_CLAUDE_PROJECTS_DIR", "claude-projects-dir"),
            Map.entry("TOKEN_METER_CLAUDE_OTEL_INPUT", "claude-otel-input"),
            Map.entry("TOKEN_METER_CLAUDE_HOOK_INPUT", "claude-hook-input"),
            Map.entry("CLAUDE_CODE_USAGE_FILE", "claude-code-usage-file")
    );

    private CollectorEnvFile() {
    }

    static String[] withEnvFileDefaults(String[] args, Map<String, String> env) throws IOException {
        Map<String, String> fileValues = readFile(envFilePath(args, env));
        List<String> merged = new ArrayList<>(List.of(args));
        for (Map.Entry<String, String> entry : ENV_TO_ARG.entrySet()) {
            String value = fileValues.get(entry.getKey());
            if (value == null || value.isBlank()) {
                value = env.get(entry.getKey());
            }
            String argName = entry.getValue();
            if (value != null && !value.isBlank() && !hasNonBlankArg(args, argName)) {
                merged.add("--" + argName + "=" + value);
            }
        }
        return merged.toArray(String[]::new);
    }

    private static Path envFilePath(String[] args, Map<String, String> env) {
        Optional<String> cliPath = option(args, "--collector-env-file");
        if (cliPath.isPresent() && !cliPath.get().isBlank()) {
            return Path.of(cliPath.get());
        }
        String envPath = env.get("TOKEN_METER_COLLECTOR_ENV");
        if (envPath != null && !envPath.isBlank()) {
            return Path.of(envPath);
        }
        Path defaultPath = Path.of(System.getProperty("user.home"), ".token-meter", "collector.env");
        Path windowsTaskPath = Path.of(System.getProperty("user.home"), ".token-meter", "collector.env.cmd");
        if (!Files.isRegularFile(defaultPath) && Files.isRegularFile(windowsTaskPath)) {
            return windowsTaskPath;
        }
        return defaultPath;
    }

    private static Map<String, String> readFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            parseLine(line).ifPresent(entry -> values.put(entry.key(), entry.value()));
        }
        return values;
    }

    private static Optional<Entry> parseLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return Optional.empty();
        }
        if (trimmed.startsWith("export ")) {
            trimmed = trimmed.substring("export ".length()).trim();
        } else if (trimmed.startsWith("set ")) {
            trimmed = trimmed.substring("set ".length()).trim();
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        int index = trimmed.indexOf('=');
        if (index <= 0) {
            return Optional.empty();
        }
        String key = trimmed.substring(0, index).trim();
        String value = unquote(trimmed.substring(index + 1).trim());
        if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return Optional.empty();
        }
        return Optional.of(new Entry(key, value));
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean hasNonBlankArg(String[] args, String name) {
        String prefix = "--" + name + "=";
        for (String arg : args) {
            if (arg.startsWith(prefix) && !arg.substring(prefix.length()).isBlank()) {
                return true;
            }
        }
        return false;
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

    private record Entry(String key, String value) {
    }
}
