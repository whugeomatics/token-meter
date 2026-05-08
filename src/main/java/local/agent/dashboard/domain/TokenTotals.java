package local.agent.dashboard.domain;

import java.util.Locale;

public final class TokenTotals {
    public long inputTokens;
    public long cachedInputTokens;
    public long outputTokens;
    public long reasoningOutputTokens;
    public long totalTokens;

    public void add(Snapshot snapshot) {
        inputTokens += snapshot.inputTokens();
        cachedInputTokens += snapshot.cachedInputTokens();
        outputTokens += snapshot.outputTokens();
        reasoningOutputTokens += snapshot.reasoningOutputTokens();
        totalTokens += snapshot.totalTokens();
    }

    public long nonCachedInputTokens() {
        return Math.max(0L, inputTokens - cachedInputTokens);
    }

    public long netTokens() {
        return nonCachedInputTokens() + outputTokens;
    }

    public double cacheHitRate() {
        return inputTokens == 0 ? 0.0d : (double) cachedInputTokens / (double) inputTokens;
    }

    public String jsonFields() {
        return "\"input_tokens\":" + inputTokens
                + ",\"cached_input_tokens\":" + cachedInputTokens
                + ",\"output_tokens\":" + outputTokens
                + ",\"reasoning_output_tokens\":" + reasoningOutputTokens
                + ",\"total_tokens\":" + totalTokens
                + ",\"non_cached_input_tokens\":" + nonCachedInputTokens()
                + ",\"net_tokens\":" + netTokens()
                + ",\"cache_hit_rate\":" + String.format(Locale.ROOT, "%.6f", cacheHitRate());
    }
}
