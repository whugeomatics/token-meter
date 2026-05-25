# Unified CLI Usage Metrics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Codex and Claude Code conform to the P5 unified CLI usage metrics contract while preserving P1-P4 API compatibility.

**Architecture:** Normalize source-specific usage into canonical event facts before storage. Keep `source_kind` and `source_quality` as event facts in SQLite. Keep `net_input_tokens`, `net_total_tokens`, and `cache_rate` as report-layer derived metrics, with no tool-specific branch in the formula.

**Tech Stack:** Java 17, JUnit 5, SQLite schema SQL resources, static dashboard JavaScript.

---

## File Structure

- Modify `token-meter-core/src/main/java/local/token/meter/ingestion/ClaudeCodeUsageSource.java`: normalize Claude Code project JSONL raw usage to gross canonical input.
- Modify `token-meter-app/src/main/java/local/token/meter/domain/TokenTotals.java`: remove Claude-specific cache denominator logic and expose P5 derived metric aliases.
- Modify `token-meter-app/src/main/resources/db/schema-v1.sql`: add `source_kind` and `source_quality` columns to local and team usage event tables, with idempotent alter statements and select/insert updates.
- Modify `token-meter-app/src/main/java/local/token/meter/store/SqliteUsageStore.java`: apply local event schema migration and persist/load source metadata.
- Modify `token-meter-app/src/main/java/local/token/meter/store/SqliteTeamUsageStore.java`: apply team event schema migration and persist/load source metadata.
- Modify `token-meter-app/src/main/java/local/token/meter/domain/UsageEvent.java`: carry source metadata for Local report aggregation.
- Modify `token-meter-app/src/main/java/local/token/meter/domain/StoredTeamUsageEvent.java`: carry source metadata for Team report aggregation.
- Modify tests under `token-meter-app/src/test/java` and `token-meter-collector/src/test/java`: add failing coverage first, then implement.
- Modify `docs/milestones/P5-unified-cli-usage-metrics/P5-2026-05-24-tasks.md` and `docs/acceptance/P5-2026-05-24-unified-cli-usage-metrics.md` after verification.

---

### Task 1: Normalize Claude Code Project JSONL To Gross Input

**Files:**
- Modify: `token-meter-collector/src/test/java/local/token/meter/ingestion/ClaudeCodeUsageSourceTest.java`
- Modify: `token-meter-app/src/test/java/local/token/meter/ingestion/ClaudeCodeLocalIngestionServiceTest.java`
- Modify: `token-meter-app/src/test/java/local/token/meter/ingestion/LocalIngestionServiceTest.java`
- Modify: `token-meter-core/src/main/java/local/token/meter/ingestion/ClaudeCodeUsageSource.java`

- [ ] **Step 1: Write the failing collector test assertions**

In `ClaudeCodeUsageSourceTest.readsClaudeCodeProjectJsonlUsageWithoutContent`, change expected input and total:

```java
assertEquals(16, event.usage().inputTokens());
assertEquals(5, event.usage().cachedInputTokens());
assertEquals(7, event.usage().outputTokens());
assertEquals(23, event.usage().totalTokens());
```

In `deduplicatesRepeatedClaudeCodeRowsForTheSameMessage`, keep total at 23 and add:

```java
assertEquals(16, events.get(0).usage().inputTokens());
assertEquals(5, events.get(0).usage().cachedInputTokens());
```

- [ ] **Step 2: Write failing local ingestion assertions**

In `ClaudeCodeLocalIngestionServiceTest.ingestsClaudeCodeProjectDirectoryIntoLocalReport`, add:

```java
assertTrue(json.contains("\"input_tokens\":20"));
assertTrue(json.contains("\"net_input_tokens\":10"));
```

In `LocalIngestionServiceTest.ingestsCodexAndClaudeIntoOneLocalReport`, add:

```java
assertTrue(json.contains("\"input_tokens\":20"));
assertTrue(json.contains("\"cached_input_tokens\":10"));
```

- [ ] **Step 3: Run targeted tests and verify red**

Run:

```bash
mvn -pl token-meter-collector -Dtest=ClaudeCodeUsageSourceTest test
mvn -pl token-meter-app -Dtest=ClaudeCodeLocalIngestionServiceTest,LocalIngestionServiceTest test
```

Expected: failures showing current `input_tokens` is 11 or 10 instead of gross input 16 or 20, and `net_input_tokens` is missing.

- [ ] **Step 4: Implement minimal gross-input normalization**

In `ClaudeCodeUsageSource.usageFromClaudeUsage`, replace the method body with:

```java
private Snapshot usageFromClaudeUsage(String json) {
    long rawInput = Json.longValue(json, "input_tokens").orElse(0L);
    long cacheCreation = Json.longValue(json, "cache_creation_input_tokens").orElse(0L);
    long cacheRead = Json.longValue(json, "cache_read_input_tokens").orElse(0L);
    long cached = cacheCreation + cacheRead;
    long input = rawInput + cached;
    long output = Json.longValue(json, "output_tokens").orElse(0L);
    long reasoning = Json.longValue(json, "reasoning_output_tokens").orElse(0L);
    long total = Json.longValue(json, "total_tokens").orElse(input + output + reasoning);
    return new Snapshot(input, cached, output, reasoning, total);
}
```

Do not change `usageFromFlatLine` in this task; flat lines already use canonical field names.

- [ ] **Step 5: Run targeted tests and verify green**

Run:

```bash
mvn -pl token-meter-collector -Dtest=ClaudeCodeUsageSourceTest test
mvn -pl token-meter-app -Dtest=ClaudeCodeLocalIngestionServiceTest,LocalIngestionServiceTest test
```

Expected: Task 1 tests pass except assertions depending on `net_input_tokens`, which are completed in Task 2.

---

### Task 2: Centralize P5 Report Derived Metrics

**Files:**
- Modify: `token-meter-app/src/test/java/local/token/meter/domain/TokenTotalsTest.java`
- Modify: `token-meter-app/src/main/java/local/token/meter/domain/TokenTotals.java`

- [ ] **Step 1: Replace tool-specific TokenTotals tests with P5 formula tests**

Replace `TokenTotalsTest` contents with:

```java
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
```

- [ ] **Step 2: Run TokenTotals tests and verify red**

Run:

```bash
mvn -pl token-meter-app -Dtest=TokenTotalsTest test
```

Expected: fail because `netInputTokens()` and `netTotalTokens()` do not exist and JSON lacks P5 names.

- [ ] **Step 3: Implement P5 formulas in TokenTotals**

In `TokenTotals`, remove `cacheHitRateDenominatorTokens` and replace `add`, derived methods, and JSON fields with:

```java
public void add(String tool, Snapshot snapshot) {
    inputTokens += snapshot.inputTokens();
    cachedInputTokens += snapshot.cachedInputTokens();
    outputTokens += snapshot.outputTokens();
    reasoningOutputTokens += snapshot.reasoningOutputTokens();
    totalTokens += snapshot.totalTokens();
    nonCachedInputTokens += Math.max(0L, snapshot.inputTokens() - snapshot.cachedInputTokens());
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
```

In `jsonFields()`, include P5 names before legacy aliases:

```java
+ ",\"net_input_tokens\":" + netInputTokens()
+ ",\"net_total_tokens\":" + netTotalTokens()
+ ",\"non_cached_input_tokens\":" + nonCachedInputTokens()
+ ",\"net_tokens\":" + netTokens()
```

- [ ] **Step 4: Run Task 1 and Task 2 targeted tests**

Run:

```bash
mvn -pl token-meter-app -Dtest=TokenTotalsTest,ClaudeCodeLocalIngestionServiceTest,LocalIngestionServiceTest test
mvn -pl token-meter-collector -Dtest=ClaudeCodeUsageSourceTest test
```

Expected: all listed tests pass.

---

### Task 3: Persist Source Metadata Facts In Local And Team SQLite

**Files:**
- Modify: `token-meter-app/src/main/resources/db/schema-v1.sql`
- Modify: `token-meter-app/src/main/java/local/token/meter/domain/UsageEvent.java`
- Modify: `token-meter-app/src/main/java/local/token/meter/domain/StoredTeamUsageEvent.java`
- Modify: `token-meter-app/src/main/java/local/token/meter/store/SqliteUsageStore.java`
- Modify: `token-meter-app/src/main/java/local/token/meter/store/SqliteTeamUsageStore.java`
- Modify: `token-meter-app/src/test/java/local/token/meter/ingestion/ClaudeCodeLocalIngestionServiceTest.java`
- Modify: `token-meter-app/src/test/java/local/token/meter/ingestion/TeamIngestionServiceToolTest.java`

- [ ] **Step 1: Write failing local source metadata assertion**

In `ClaudeCodeLocalIngestionServiceTest.ingestsClaudeCodeProjectDirectoryIntoLocalReport`, add:

```java
assertTrue(json.contains("\"source_quality\":{\"reported\":1"));
```

- [ ] **Step 2: Write failing team source metadata assertion**

In `TeamIngestionServiceToolTest`, in the test that ingests a `claude-code` event with `source_quality`, assert the team report includes the source quality count:

```java
assertTrue(allJson.contains("\"source_quality\":{\"reported\":1"));
```

- [ ] **Step 3: Run targeted tests and verify red**

Run:

```bash
mvn -pl token-meter-app -Dtest=ClaudeCodeLocalIngestionServiceTest,TeamIngestionServiceToolTest test
```

Expected: fail because report JSON does not include persisted source quality counts.

- [ ] **Step 4: Add schema columns and migrations**

In `schema-v1.sql`, add columns to `usage_events`:

```sql
  source_kind TEXT NOT NULL DEFAULT '',
  source_quality TEXT NOT NULL DEFAULT '',
```

Add columns to `team_usage_events`:

```sql
  source_kind TEXT NOT NULL DEFAULT '',
  source_quality TEXT NOT NULL DEFAULT '',
```

Add named alter statements:

```sql
-- name: alter_usage_events_add_source_kind
ALTER TABLE usage_events ADD COLUMN source_kind TEXT NOT NULL DEFAULT '';

-- name: alter_usage_events_add_source_quality
ALTER TABLE usage_events ADD COLUMN source_quality TEXT NOT NULL DEFAULT '';

-- name: alter_team_usage_events_add_source_kind
ALTER TABLE team_usage_events ADD COLUMN source_kind TEXT NOT NULL DEFAULT '';

-- name: alter_team_usage_events_add_source_quality
ALTER TABLE team_usage_events ADD COLUMN source_quality TEXT NOT NULL DEFAULT '';
```

Update insert statements to include `source_kind, source_quality` and two extra placeholders. Update load statements to select both fields.

- [ ] **Step 5: Carry source metadata in domain records**

Change `UsageEvent` to:

```java
public record UsageEvent(String tool, String sessionId, String model, Instant timestamp, Snapshot usage,
                         String sourceKind, String sourceQuality) {
    public UsageEvent(String tool, String sessionId, String model, Instant timestamp, Snapshot usage) {
        this(tool, sessionId, model, timestamp, usage, "", "");
    }

    public UsageEvent(String sessionId, String model, Instant timestamp, Snapshot usage) {
        this("codex", sessionId, model, timestamp, usage, "", "");
    }
}
```

Change `StoredTeamUsageEvent` to:

```java
public record StoredTeamUsageEvent(String teamId, String userId, String userDisplayName, String deviceId,
                                   String deviceDisplayName, String tool, String sessionId, String model,
                                   Instant timestamp, Snapshot usage, String sourceKind, String sourceQuality) {
    public StoredTeamUsageEvent(String teamId, String userId, String userDisplayName, String deviceId,
                                String deviceDisplayName, String tool, String sessionId, String model,
                                Instant timestamp, Snapshot usage) {
        this(teamId, userId, userDisplayName, deviceId, deviceDisplayName, tool, sessionId, model, timestamp,
                usage, "", "");
    }
}
```

- [ ] **Step 6: Persist and load source metadata**

In `SqliteUsageStore.initialize`, after creating `usage_events`, call a helper that applies the two local alter statements and ignores duplicate-column errors.

In `insertUsageEvent`, pass:

```java
statement.setString(14, "local_jsonl");
statement.setString(15, "derived");
statement.setString(16, Instant.now().toString());
```

In `insertLocalUsageEvent`, pass:

```java
statement.setString(14, event.sourceKind());
statement.setString(15, event.sourceQuality());
statement.setString(16, Instant.now().toString());
```

In `loadEvents`, construct `UsageEvent` with `rs.getString("source_kind")` and `rs.getString("source_quality")`.

In `SqliteTeamUsageStore.initialize`, after creating `team_usage_events`, apply the two team alter statements. In `insertTeamUsageEvent`, pass `event.sourceKind()` and `event.sourceQuality()` before `received_at`. In `loadTeamEvents`, construct `StoredTeamUsageEvent` with loaded source metadata.

- [ ] **Step 7: Add source quality counts to report buckets**

Add a source quality count map to Local and Team tool/daily/model/session/summary aggregators only where straightforward. Minimum required for P5 acceptance: include source quality counts in summary and tools JSON.

For each bucket with `TokenTotals`, add:

```java
final Map<String, Integer> sourceQualityCounts = new HashMap<>();

void addSourceQuality(String sourceQuality) {
    String value = sourceQuality == null || sourceQuality.isBlank() ? "unknown" : sourceQuality;
    sourceQualityCounts.merge(value, 1, Integer::sum);
}
```

When adding an event, call `addSourceQuality(event.sourceQuality())`.

Emit JSON:

```java
",\"source_quality\":" + sourceQualityJson(bucket.sourceQualityCounts)
```

Add helper:

```java
private static String sourceQualityJson(Map<String, Integer> counts) {
    if (counts.isEmpty()) {
        return "{\"unknown\":0}";
    }
    return "{" + counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> "\"" + Json.escape(entry.getKey()) + "\":" + entry.getValue())
            .reduce((left, right) -> left + "," + right)
            .orElse("") + "}";
}
```

- [ ] **Step 8: Run targeted tests and verify green**

Run:

```bash
mvn -pl token-meter-app -Dtest=ClaudeCodeLocalIngestionServiceTest,TeamIngestionServiceToolTest,ReportServiceToolTest test
```

Expected: all targeted tests pass and source quality counts appear in report JSON.

---

### Task 4: P5 Regression And Documentation Closeout

**Files:**
- Modify: `docs/milestones/P5-unified-cli-usage-metrics/P5-2026-05-24-tasks.md`
- Modify: `docs/acceptance/P5-2026-05-24-unified-cli-usage-metrics.md`

- [ ] **Step 1: Run full Java tests**

Run:

```bash
mvn test
```

Expected: build success. If sandbox or environment blocks Maven, record the exact limitation in acceptance.

- [ ] **Step 2: Run package build**

Run:

```bash
mvn -DskipTests package
```

Expected: build success.

- [ ] **Step 3: Run JS syntax checks**

Run:

```bash
node --check token-meter-app/src/main/resources/static/app.js
node --check token-meter-app/src/main/resources/static/admin.js
```

Expected: both commands pass.

- [ ] **Step 4: Run collector packaging**

Run:

```bash
sh scripts/P3-2026-05-01-package-collector.sh all
```

Expected: `dist/token-meter-collector-mac-linux/token-meter-collector.jar` and `dist/token-meter-collector-windows/token-meter-collector.jar` are generated.

- [ ] **Step 5: Update P5 tasks and acceptance**

In `P5-2026-05-24-tasks.md`, mark completed items through verification.

In `P5-2026-05-24-unified-cli-usage-metrics.md`, change verified sections from `结果：待验证` to `结果：通过` only for checks proven by commands. Add command output summaries under “本轮已执行”.

- [ ] **Step 6: Commit**

Use an English commit message:

```bash
git add AGENTS.md README.md README.zh-CN.md docs token-meter-core token-meter-app token-meter-collector
git commit -m "feat(metrics): unify cli usage metrics"
```

Only commit if the user asks for a commit or if the current workflow explicitly proceeds to branch completion.

---

## Self-Review

Spec coverage:

- Canonical gross input: Task 1.
- Report-derived metrics with no tool branch: Task 2.
- Event/DB source facts: Task 3.
- Local/Team consistency and regression: Tasks 2, 3, and 4.
- Privacy boundaries: preserved by source mapping tests and no raw payload/path additions.

Placeholder scan:

- No `TBD`, `TODO`, or unspecified implementation steps remain.

Type consistency:

- `sourceKind` and `sourceQuality` use existing Java camelCase accessor names and SQL snake_case column names.
- `netInputTokens()` and `netTotalTokens()` are new Java methods; JSON uses `net_input_tokens` and `net_total_tokens`.
