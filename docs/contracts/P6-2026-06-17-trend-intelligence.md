# P6 Trend Intelligence Contract

## Purpose

This contract defines trend insight data derived from existing usage and upload health facts. It does not define a new ingestion payload.

P6 may extend `/api/team/report` with a `trend_insights` object or add a dedicated team trend endpoint if that keeps the existing report contract cleaner. In either case, the field meanings below are the contract.

## Window Semantics

Trend insights compare a current window with a previous window using the same natural-period semantics already used by Team period comparison:

- `period=day`: current local day vs previous local day.
- `period=week`: current local week vs previous local week.
- `period=month`: current month-to-date vs the equivalent previous period when supported by the existing comparison layer.

All dates use the configured dashboard timezone.

## Response Shape

```json
{
  "trend_insights": {
    "period": "week",
    "range": {
      "current_start": "2026-06-15",
      "current_end": "2026-06-21",
      "previous_start": "2026-06-08",
      "previous_end": "2026-06-14"
    },
    "summary": {
      "total_tokens": { "current": 0, "previous": 0, "delta": 0, "delta_rate": 0.0 },
      "net_total_tokens": { "current": 0, "previous": 0, "delta": 0, "delta_rate": 0.0 },
      "calls": { "current": 0, "previous": 0, "delta": 0, "delta_rate": 0.0 },
      "cache_hit_rate": { "current": 0.0, "previous": 0.0, "delta": 0.0 },
      "reasoning_ratio": { "current": 0.0, "previous": 0.0, "delta": 0.0 }
    },
    "drivers": {
      "users": [],
      "devices": [],
      "models": [],
      "tools": []
    },
    "signals": [],
    "alerts": [],
    "markdown_summary": ""
  }
}
```

## Driver Row

Each driver row explains one dimension value:

```json
{
  "dimension": "user",
  "id": "zhangsan",
  "label": "zhangsan",
  "current_total_tokens": 120000,
  "previous_total_tokens": 80000,
  "delta_total_tokens": 40000,
  "delta_rate": 0.5,
  "share_of_total_delta": 0.42,
  "current_net_total_tokens": 70000,
  "previous_net_total_tokens": 60000,
  "delta_net_total_tokens": 10000
}
```

Rules:

- `delta_rate` is `null` when the previous value is zero and the current value is non-zero.
- `share_of_total_delta` is `null` when total delta is zero.
- Drivers must be sorted by absolute `delta_total_tokens` unless the UI explicitly requests another metric.

## Signal Row

Signals are non-blocking trend observations:

```json
{
  "type": "cache_hit_drop",
  "severity": "warning",
  "title": "Cache hit rate dropped",
  "detail": "Cache hit rate dropped from 77% to 61%.",
  "dimension": "team",
  "id": "all"
}
```

Allowed `type` values for P6:

- `token_spike`
- `token_drop`
- `cache_hit_drop`
- `reasoning_ratio_change`
- `duplicate_upload_increase`
- `rejected_upload_increase`
- `stale_device`

Allowed `severity` values:

- `info`
- `warning`
- `critical`

## Privacy Boundary

Trend insight responses must not contain:

- prompt text;
- response text;
- raw JSONL;
- raw API body;
- transcript text;
- local full source paths;
- admin token;
- device token plaintext;
- token hash.

Driver IDs and labels may use existing team/user/device/model/tool identifiers already visible in Team report responses.
