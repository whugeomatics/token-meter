package local.token.meter.ingestion;

import local.token.meter.domain.Snapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class UsageEventKeys {
    private UsageEventKeys() {
    }

    public static String codex(String sessionId, String sourcePath, int lineNumber, Snapshot cumulative) {
        return "codex|" + sessionId + "|" + shortHash(sourcePath) + "|" + lineNumber + "|"
                + cumulative.totalTokens() + "|" + cumulative.inputTokens() + "|" + cumulative.outputTokens();
    }

    public static String call(String tool, String sessionId, String model, String timestamp, Snapshot usage) {
        return tool + "|" + sessionId + "|" + model + "|" + timestamp + "|" + usage.inputTokens() + "|"
                + usage.cachedInputTokens() + "|" + usage.outputTokens() + "|" + usage.reasoningOutputTokens()
                + "|" + usage.totalTokens();
    }

    static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
