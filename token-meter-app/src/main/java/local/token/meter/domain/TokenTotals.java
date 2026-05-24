package local.token.meter.domain;

import java.util.Locale;

public final class TokenTotals {
    public long inputTokens;
    public long cachedInputTokens;
    public long outputTokens;
    public long reasoningOutputTokens;
    public long totalTokens;
    private long nonCachedInputTokens;
    private long cacheHitRateDenominatorTokens;

    public void add(Snapshot snapshot) {
        add("codex", snapshot);
    }

    public void add(String tool, Snapshot snapshot) {
        inputTokens += snapshot.inputTokens();
        cachedInputTokens += snapshot.cachedInputTokens();
        outputTokens += snapshot.outputTokens();
        reasoningOutputTokens += snapshot.reasoningOutputTokens();
        totalTokens += snapshot.totalTokens();
        if ("claude-code".equals(tool) && snapshot.cachedInputTokens() > snapshot.inputTokens()) {
            nonCachedInputTokens += snapshot.inputTokens();
            cacheHitRateDenominatorTokens += snapshot.inputTokens() + snapshot.cachedInputTokens();
            return;
        }
        nonCachedInputTokens += Math.max(0L, snapshot.inputTokens() - snapshot.cachedInputTokens());
        cacheHitRateDenominatorTokens += snapshot.inputTokens();
    }

    public long nonCachedInputTokens() {
        return nonCachedInputTokens;
    }

    public long netTokens() {
        return nonCachedInputTokens() + outputTokens;
    }

    public double cacheHitRate() {
        return cacheHitRateDenominatorTokens == 0L ? 0.0d
                : (double) cachedInputTokens / (double) cacheHitRateDenominatorTokens;
    }

    public double reasoningRatio() {
        return outputTokens == 0 ? 0.0d : (double) reasoningOutputTokens / (double) outputTokens;
    }

    public String jsonFields() {
        return "\"input_tokens\":" + inputTokens
                + ",\"cached_input_tokens\":" + cachedInputTokens
                + ",\"output_tokens\":" + outputTokens
                + ",\"reasoning_output_tokens\":" + reasoningOutputTokens
                + ",\"total_tokens\":" + totalTokens
                + ",\"non_cached_input_tokens\":" + nonCachedInputTokens()
                + ",\"net_tokens\":" + netTokens()
                + ",\"cache_hit_rate\":" + String.format(Locale.ROOT, "%.6f", cacheHitRate())
                + ",\"reasoning_ratio\":" + String.format(Locale.ROOT, "%.6f", reasoningRatio());
    }
}
