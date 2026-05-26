package local.token.meter.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TokenTotalsTest {
    @Test
    void derivedMetricsUseCanonicalGrossInputWithoutToolSpecificBranches() {
        TokenTotals totals = new TokenTotals();
        totals.add("claude-code", new Snapshot(16, 5, 7, 2, 25));
        totals.add("codex", new Snapshot(100, 90, 10, 1, 111));

        assertEquals(116, totals.inputTokens);
        assertEquals(95, totals.cachedInputTokens);
        assertEquals(21, totals.netInputTokens());
        assertEquals(41, totals.netTotalTokens());
        assertEquals(41, totals.netTokens());
        assertEquals(0.818965, totals.cacheHitRate(), 0.000001);
    }

    @Test
    void jsonIncludesP5DerivedMetricNamesAndLegacyAliases() {
        TokenTotals totals = new TokenTotals();
        totals.add("codex", new Snapshot(100, 20, 30, 5, 135));

        String json = totals.jsonFields();

        assertTrue(json.contains("\"net_input_tokens\":80"));
        assertTrue(json.contains("\"net_total_tokens\":115"));
        assertTrue(json.contains("\"non_cached_input_tokens\":80"));
        assertTrue(json.contains("\"net_tokens\":115"));
        assertTrue(json.contains("\"cache_hit_rate\":0.200000"));
    }
}
