package local.token.meter.domain;

import java.util.Locale;

public final class TokenTotals {
    public long inputTokens;
    public long cachedInputTokens;
    public long outputTokens;
    public long reasoningOutputTokens;
    public long totalTokens;
    private long nonCachedInputTokens;

    public void add(Snapshot snapshot) {
        add("codex", snapshot);
    }

    public void add(String tool, Snapshot snapshot) {
        inputTokens += snapshot.inputTokens();
        cachedInputTokens += snapshot.cachedInputTokens();
        outputTokens += snapshot.outputTokens();
        reasoningOutputTokens += snapshot.reasoningOutputTokens();
        totalTokens += snapshot.totalTokens();
        nonCachedInputTokens += Math.max(0L, snapshot.inputTokens() - snapshot.cachedInputTokens());
    }

    public long nonCachedInputTokens() {
        return nonCachedInputTokens;
    }

    public long netInputTokens() {
        return nonCachedInputTokens();
    }

    public long netTotalTokens() {
        return netInputTokens() + outputTokens + reasoningOutputTokens;
    }

    public long netTokens() {
        return netTotalTokens();
    }

    public double cacheHitRate() {
        if (inputTokens == 0L) {
            return 0.0d;
        }
        return Math.min(1.0d, (double) cachedInputTokens / (double) inputTokens);
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
                + ",\"net_input_tokens\":" + netInputTokens()
                + ",\"net_total_tokens\":" + netTotalTokens()
                + ",\"non_cached_input_tokens\":" + nonCachedInputTokens()
                + ",\"net_tokens\":" + netTokens()
                + ",\"cache_hit_rate\":" + String.format(Locale.ROOT, "%.6f", cacheHitRate())
                + ",\"reasoning_ratio\":" + String.format(Locale.ROOT, "%.6f", reasoningRatio());
    }
}
