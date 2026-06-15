# P5 Unified CLI Usage Metrics Review

## Goal

Review whether the P5 implementation matches the unified CLI usage metrics contract, with focus on token semantics, fallback behavior, privacy boundaries, and Local/Team report consistency.

## Review Scope

- `docs/contracts/P5-2026-05-24-unified-cli-usage-metrics.md`
- `docs/integrations/codex.md`
- `docs/integrations/claude-code.md`
- `docs/acceptance/P5-2026-05-24-unified-cli-usage-metrics.md`
- `token-meter-core/src/main/java/local/token/meter/domain/Snapshot.java`
- `token-meter-core/src/main/java/local/token/meter/domain/TeamUsageEvent.java`
- `token-meter-core/src/main/java/local/token/meter/ingestion/ClaudeCodeUsageSource.java`
- `token-meter-core/src/main/java/local/token/meter/ingestion/TeamUsagePayload.java`
- `token-meter-app/src/main/java/local/token/meter/domain/TokenTotals.java`
- `token-meter-app/src/main/java/local/token/meter/ingestion/TeamIngestionService.java`
- `token-meter-app/src/main/java/local/token/meter/report/ReportService.java`
- `token-meter-app/src/main/java/local/token/meter/report/TeamReportService.java`
- `token-meter-app/src/main/resources/db/schema-v1.sql`

## Conclusion

Status: follow-up required before P5 can be treated as fully closed.

The implementation has the main P5 report-layer derived metric model in place: `TokenTotals` computes `net_input_tokens`, `net_total_tokens`, and clamped cache rate once, and both Local and Team report services use that shared type. Codex and Claude Code collection also carry `source_kind` and `source_quality` through storage and report summaries/tools.

However, the review found contract mismatches in fallback and privacy handling. These should be fixed before changing the project status to "P5 complete".

## Findings

### P1: Claude flat usage fallback double-counts cached input tokens

`ClaudeCodeUsageSource.usageFromFlatLine` falls back to:

```text
input + cached + output + reasoning
```

when `total_tokens` is missing.

The P5 contract says `input_tokens` already includes identifiable cached input, so missing `total_tokens` must fall back to:

```text
input + output + reasoning
```

Impact: flat Claude Code fixture/telemetry-style lines that omit `total_tokens` overstate historical `total_tokens`, comparison totals, averages, and dashboard totals whenever `cached_input_tokens > 0`.

Evidence:

- `token-meter-core/src/main/java/local/token/meter/ingestion/ClaudeCodeUsageSource.java:146`
- `token-meter-core/src/main/java/local/token/meter/ingestion/ClaudeCodeUsageSource.java:151`
- `docs/contracts/P5-2026-05-24-unified-cli-usage-metrics.md` fallback rules

Recommended fix:

- Change the flat-line fallback to `input + output + reasoning`.
- Add a unit test where flat usage has `input_tokens=100`, `cached_input_tokens=40`, `output_tokens=10`, no `total_tokens`, and expected total is `110`.

### P1: Team ingestion does not apply canonical total fallback

`TeamIngestionService.parseEvent` reads missing `total_tokens` as `0` instead of applying the P5 fallback formula.

Impact: a valid canonical event with positive input/output tokens but missing `total_tokens` is accepted and stored with `total_tokens=0`. This makes historical `total_tokens`, comparison totals, averages, and ranking inconsistent with Local source parsing and with the P5 contract.

Evidence:

- `token-meter-app/src/main/java/local/token/meter/ingestion/TeamIngestionService.java:127`
- `token-meter-app/src/main/java/local/token/meter/ingestion/TeamIngestionService.java:132`
- `docs/contracts/P5-2026-05-24-unified-cli-usage-metrics.md` fallback rules

Recommended fix:

- Parse token facts into locals first.
- Use `Json.longValue(json, "total_tokens").orElse(input + output + reasoning)`.
- Add a team ingestion test for missing `total_tokens`.

### P1: Local SQLite still stores full source paths despite the P5 privacy boundary

The P5 privacy boundary says DB must not contain complete local paths. The local store schema still persists `source_files.path`, keyed by `UNIQUE(tool, path)`, and app/Claude local ingestion pass normalized absolute source paths into local storage.

Impact: Local SQLite can contain full Codex/Claude source file paths. That contradicts the P5 privacy acceptance text and can expose usernames, project names, and filesystem layout to anyone with DB access.

Evidence:

- `token-meter-app/src/main/resources/db/schema-v1.sql:8`
- `token-meter-app/src/main/resources/db/schema-v1.sql:11`
- `token-meter-app/src/main/resources/db/schema-v1.sql:20`
- `token-meter-app/src/main/resources/db/schema-v1.sql:224`
- `docs/contracts/P5-2026-05-24-unified-cli-usage-metrics.md` privacy boundary

Recommended fix:

- Decide whether P5 intends to forbid complete local paths in all DB tables or only in canonical event/team payload/report surfaces.
- If DB paths are forbidden, migrate `source_files.path` to a non-reversible source identity plus non-sensitive display label, and keep incremental scan identity outside report/export surfaces.
- If local DB path storage is intentionally retained for incremental ingestion, amend P5 contract and acceptance to narrow the privacy claim. Do not leave the current contradiction.

### P2: Team ingestion accepts missing source metadata even though P5 marks it required

P5 says `source_kind` and `source_quality` are required canonical facts. `TeamIngestionService` accepts both as blank and later reports them as `unknown`.

Impact: current collector payloads include these fields, so normal flows are not broken. But `/api/team/ingest` does not enforce the P5 canonical event requirement, which weakens source mapping guarantees for future collectors or hand-built payloads.

Evidence:

- `token-meter-app/src/main/java/local/token/meter/ingestion/TeamIngestionService.java:138`
- `token-meter-app/src/main/java/local/token/meter/ingestion/TeamIngestionService.java:139`
- `docs/contracts/P5-2026-05-24-unified-cli-usage-metrics.md` canonical usage event field rules

Recommended fix:

- Either reject blank `source_kind`/`source_quality`, or explicitly define the server-side fallback to `unknown` in the P5 contract.

## Passing Checks

Token derived metrics:

- `net_input_tokens` is computed as per-event `max(input_tokens - cached_input_tokens, 0)` before aggregation.
- `net_total_tokens` is computed as `net_input_tokens + output_tokens + reasoning_output_tokens`.
- Cache rate is `cached_input_tokens / input_tokens` and clamped to 1.0 when cached exceeds input.
- Local and Team report services both aggregate through `TokenTotals`.

Source dimensions:

- Local and Team usage tables include `source_kind` and `source_quality`.
- Local and Team reports expose source counts in summary and tools sections.

Claude project JSONL mapping:

- Claude Code project JSONL mapping correctly adds `cache_creation_input_tokens + cache_read_input_tokens` into canonical `input_tokens`.
- Missing Claude reasoning tokens default to `0`.
- Missing Claude `total_tokens` in project JSONL falls back to `input + output + reasoning`.

Privacy-positive behavior:

- Team upload payload generation only emits normalized event fields.
- Team ingestion rejects some high-risk forbidden fields (`prompt`, `response`, `raw_api_body`, `transcript`).
- Team report/export queries do not include local source paths.

## Suggested Closeout

1. Fix the two token fallback issues.
2. Resolve the local DB path privacy contradiction by either code migration or contract clarification.
3. Re-run targeted fallback/privacy tests and full `mvn test`.
4. Update P5 acceptance after the follow-up fixes.
5. Only then mark P5 as complete.
