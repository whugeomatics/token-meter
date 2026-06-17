# P6 Incremental Team Collection Acceptance

## Scope

P6 is accepted only when teammate collector upload is incremental by upload cursor instead of repeatedly uploading the full configured lookback window.

## Acceptance Criteria

- Collector stores a local upload cursor with latest successful event timestamp and event key.
- First run uploads eligible Codex and Claude Code events and advances the cursor.
- A second run with no new local events uploads no usage events, records heartbeat, and does not inflate duplicate counts.
- New events after the cursor are uploaded on the next run.
- Events sharing the cursor timestamp are handled with event key tie-breaker and are not skipped.
- Collector downtime longer than the default lookback window does not skip events after the last successful cursor.
- Failed upload does not advance the cursor; a later retry can upload the missed events.
- Server-side `event_key` deduplication remains as defense in depth.
- Existing Local report behavior remains unchanged.
- Existing Team report compatibility remains intact.
- P5 token formulas remain intact.
- No prompt, response, raw JSONL, raw API body, transcript text, local full source path, admin token, device token plaintext, or token hash appears in collector state, payloads, stdout, logs, reports, or exports.

## Verification Commands

```powershell
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd test
```

If the Codex sandbox cannot bind local HTTP ports during manual dashboard verification, record the sandbox limitation here instead of treating it as a product failure.
