# P6 Incremental Team Collection Contract

## Purpose

This contract defines teammate collector incremental upload behavior. It does not define a new Team ingest payload.

P6 keeps `/api/team/ingest` compatible. The collector changes which events it selects for upload and how it advances local upload state.

## Cursor Semantics

The collector stores an upload cursor in its local collector state database.

The cursor must include:

- `last_successful_event_timestamp`: timestamp of the latest event included in the last successful usage upload.
- `last_successful_event_key`: event key of the latest event at that timestamp, used as a deterministic tie-breaker.
- `updated_at`: when the cursor was advanced.

The effective upload range for a normal run is:

- start: exclusive cursor boundary; when no cursor exists, use the configured initial lookback window.
- end: collector run cutoff time captured at run start.

When a cursor exists, collector source scanning must be able to discover events after that cursor even if the collector was not run for longer than the default lookback window. The lookback window is only the bootstrap bound for a device with no upload cursor.

An event is eligible for upload when:

- `event_timestamp > last_successful_event_timestamp`, or
- `event_timestamp == last_successful_event_timestamp && event_key > last_successful_event_key`.

Ordering must be deterministic by `(event_timestamp, event_key)`.

## Success And Failure Rules

- Advance the cursor only after all selected events for the run have been accepted or acknowledged as duplicates by the server.
- Do not advance the cursor when the upload fails due to network error, unauthorized token, invalid response, or rejected events.
- A retry after failure may resend events from the previous cursor. Server-side `event_key` deduplication remains the safety net.
- A run with no new events should still post an empty payload as a heartbeat and record upload health, but it must not move the event cursor.

## Local Ingestion Boundary

P6 does not redefine Local dashboard report ingestion.

- Existing Local Codex ingestion remains checkpointed by `source_files` and idempotent by `usage_events.event_key`.
- Existing Local Claude Code ingestion remains report-compatible and idempotent by `usage_events.event_key`.
- If P6 implementation improves Local Claude Code source checkpointing, that is a reliability cleanup only; it must not change Local report fields, token formulas, or privacy boundaries.

## Collector Output

Collector stdout remains machine-readable JSON and should include:

```json
{
  "status": "ok",
  "events": 2,
  "accepted": 2,
  "duplicate": 0,
  "rejected": 0,
  "incremental": true,
  "cursor_advanced": true,
  "cursor_event_timestamp": "2026-06-17T08:00:00Z",
  "cursor_event_key": "codex|session|..."
}
```

`cursor_event_key` must not include prompt or response text. Existing event keys are usage metadata keys only.

## Upload Health

Upload Health should treat successful empty uploads as device heartbeat. Duplicate counts should no longer grow during normal repeated collector runs without new events.

## Privacy Boundary

Incremental collector state, stdout, payloads, logs, and reports must not contain:

- prompt text;
- response text;
- raw JSONL;
- raw API body;
- transcript text;
- local full source paths;
- admin token;
- device token plaintext;
- token hash.
