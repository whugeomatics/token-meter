package local.token.meter.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TokenTotalsTest {
    @Test
    void codexCacheHitRateUsesInputTokensBecauseCachedTokensAreIncludedInInput() {
        TokenTotals totals = new TokenTotals();
        totals.add("codex", new Snapshot(100, 90, 10, 0, 110));

        assertEquals(10, totals.nonCachedInputTokens());
        assertEquals(20, totals.netTokens());
        assertEquals(0.9, totals.cacheHitRate(), 0.000001);
    }

    @Test
    void claudeCacheHitRateUsesTotalInputWhenCachedTokensAreSeparate() {
        TokenTotals totals = new TokenTotals();
        totals.add("claude-code", new Snapshot(100, 191, 10, 0, 301));

        assertEquals(100, totals.nonCachedInputTokens());
        assertEquals(110, totals.netTokens());
        assertEquals(0.656357, totals.cacheHitRate(), 0.000001);
    }
}
