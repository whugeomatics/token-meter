package local.agent.dashboard.domain;

import local.agent.dashboard.util.Json;

import java.util.Optional;

public record Snapshot(long inputTokens, long cachedInputTokens, long outputTokens,
                long reasoningOutputTokens, long totalTokens) {
    public static Optional<Snapshot> fromObject(String json, String objectName) {
        Optional<String> section = Json.objectSection(json, objectName);
        if (section.isEmpty()) {
            return Optional.empty();
        }
        String value = section.get();
        return Optional.of(new Snapshot(
                Json.longValue(value, "input_tokens").orElse(0L),
                Json.longValue(value, "cached_input_tokens").orElse(0L),
                Json.longValue(value, "output_tokens").orElse(0L),
                Json.longValue(value, "reasoning_output_tokens").orElse(0L),
                Json.longValue(value, "total_tokens").orElse(0L)
        ));
    }

    public Snapshot minus(Snapshot previous) {
        return new Snapshot(
                Math.max(0L, inputTokens - previous.inputTokens),
                Math.max(0L, cachedInputTokens - previous.cachedInputTokens),
                Math.max(0L, outputTokens - previous.outputTokens),
                Math.max(0L, reasoningOutputTokens - previous.reasoningOutputTokens),
                Math.max(0L, totalTokens - previous.totalTokens)
        );
    }

    public boolean hasPositiveUsage() {
        return inputTokens > 0 || cachedInputTokens > 0 || outputTokens > 0
                || reasoningOutputTokens > 0 || totalTokens > 0;
    }
}
