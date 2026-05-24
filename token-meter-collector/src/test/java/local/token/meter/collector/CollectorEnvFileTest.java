package local.token.meter.collector;

import local.token.meter.app.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CollectorEnvFileTest {
    @TempDir
    Path tempDir;

    @Test
    void readsMissingCollectorOptionsFromEnvFile() throws Exception {
        Path envFile = tempDir.resolve("collector.env");
        Files.writeString(envFile, """
                export TOKEN_METER_SERVER_URL="http://file-server:18080"
                export TOKEN_METER_DEVICE_TOKEN="file-token"
                export TOKEN_METER_USER_ID="file-user"
                export TOKEN_METER_DEVICE_ID="file-device"
                TOKEN_METER_DAYS="14"
                """);

        AppConfig config = AppConfig.from(CollectorEnvFile.withEnvFileDefaults(
                new String[]{"--collect-team", "--collector-env-file=" + envFile}, Map.of()));

        assertEquals("http://file-server:18080", config.options().get("server-url"));
        assertEquals("file-token", config.options().get("device-token"));
        assertEquals("file-user", config.options().get("user-id"));
        assertEquals("file-device", config.options().get("device-id"));
        assertEquals("14", config.reportQuery().get("days"));
    }

    @Test
    void keepsCliOptionsAheadOfEnvFileValues() throws Exception {
        Path envFile = tempDir.resolve("collector.env");
        Files.writeString(envFile, """
                TOKEN_METER_SERVER_URL="http://file-server:18080"
                TOKEN_METER_DEVICE_TOKEN="file-token"
                TOKEN_METER_USER_ID="file-user"
                TOKEN_METER_DEVICE_ID="file-device"
                TOKEN_METER_DAYS="14"
                """);

        AppConfig config = AppConfig.from(CollectorEnvFile.withEnvFileDefaults(new String[]{
                "--collect-team",
                "--collector-env-file=" + envFile,
                "--server-url=http://cli-server:18080",
                "--days=3"
        }, Map.of()));

        assertEquals("http://cli-server:18080", config.options().get("server-url"));
        assertEquals("3", config.reportQuery().get("days"));
        assertEquals("file-token", config.options().get("device-token"));
    }

    @Test
    void envFileValuesBeatSystemEnvironmentValues() throws Exception {
        Path envFile = tempDir.resolve("collector.env");
        Files.writeString(envFile, """
                TOKEN_METER_SERVER_URL="http://file-server:18080"
                TOKEN_METER_DEVICE_TOKEN="file-token"
                """);

        AppConfig config = AppConfig.from(CollectorEnvFile.withEnvFileDefaults(
                new String[]{"--collect-team", "--collector-env-file=" + envFile},
                Map.of(
                        "TOKEN_METER_SERVER_URL", "http://env-server:18080",
                        "TOKEN_METER_DEVICE_TOKEN", "env-token"
                )));

        assertEquals("http://file-server:18080", config.options().get("server-url"));
        assertEquals("file-token", config.options().get("device-token"));
    }

    @Test
    void usesSystemEnvironmentValuesWhenCliAndEnvFileAreMissing() throws Exception {
        AppConfig config = AppConfig.from(CollectorEnvFile.withEnvFileDefaults(new String[]{
                "--collect-team",
                "--collector-env-file=" + tempDir.resolve("missing.env")
        }, Map.of(
                "TOKEN_METER_SERVER_URL", "http://env-server:18080",
                "TOKEN_METER_DEVICE_TOKEN", "env-token",
                "TOKEN_METER_DAYS", "21"
        )));

        assertEquals("http://env-server:18080", config.options().get("server-url"));
        assertEquals("env-token", config.options().get("device-token"));
        assertEquals("21", config.reportQuery().get("days"));
    }

    @Test
    void readsWindowsCmdSetSyntax() throws Exception {
        Path envFile = tempDir.resolve("collector.env.cmd");
        Files.writeString(envFile, """
                @echo off
                set "TOKEN_METER_SERVER_URL=http://windows-server:18080"
                set "TOKEN_METER_DEVICE_TOKEN=windows-token"
                """);

        AppConfig config = AppConfig.from(CollectorEnvFile.withEnvFileDefaults(
                new String[]{"--collect-team", "--collector-env-file=" + envFile}, Map.of()));

        assertEquals("http://windows-server:18080", config.options().get("server-url"));
        assertEquals("windows-token", config.options().get("device-token"));
    }
}
