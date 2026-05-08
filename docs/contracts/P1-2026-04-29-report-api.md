# P1 Report API Contract

## 目标

定义 Codex Dashboard MVP 的最小 API 契约，避免前端和后端各自定义统计口径。

## Endpoint

```text
GET /api/report
```

## 查询参数

二选一：

- `days`: 正整数。支持 `1`、`7`、`30`。
- `month`: `YYYY-MM` 格式，例如 `2026-04`。

规则：

- 如果同时传入 `days` 和 `month`，后端应返回 400。
- 如果都不传，默认 `days=7`。
- `days=1` 表示今日。
- 日期聚合使用本地时区。

## Response

成功：

```json
{
  "range": {
    "kind": "days",
    "start_date": "2026-04-23",
    "end_date": "2026-04-29",
    "timezone": "Asia/Shanghai"
  },
  "summary": {
    "input_tokens": 0,
    "cached_input_tokens": 0,
    "output_tokens": 0,
    "reasoning_output_tokens": 0,
    "total_tokens": 0,
    "non_cached_input_tokens": 0,
    "net_tokens": 0,
    "cache_hit_rate": 0,
    "reasoning_ratio": 0,
    "usage_event_count": 0,
    "active_seconds": 0,
    "avg_tokens_per_session": 0,
    "avg_tokens_per_call": 0
  },
  "daily": [],
  "models": [],
  "sessions": []
}
```

错误：

```json
{
  "error": {
    "code": "invalid_query",
    "message": "days and month cannot be used together"
  }
}
```

## 字段定义

### range

- `kind`: `days` 或 `month`。
- `start_date`: 本地日期，闭区间开始。
- `end_date`: 本地日期，闭区间结束。
- `timezone`: IANA timezone 名称。

### summary

- `input_tokens`: 输入 token，包含 cached input。
- `cached_input_tokens`: 缓存命中的输入 token。
- `output_tokens`: 输出 token。
- `reasoning_output_tokens`: 推理输出 token。
- `total_tokens`: Codex `total_token_usage` 相邻累计快照 delta 之和。
- `non_cached_input_tokens`: `input_tokens - cached_input_tokens`。
- `net_tokens`: `non_cached_input_tokens + output_tokens`。
- `cache_hit_rate`: `cached_input_tokens / input_tokens`，当 `input_tokens = 0` 时为 `0`。
- `reasoning_ratio`: `reasoning_output_tokens / output_tokens`，当 `output_tokens = 0` 时为 `0`。
- `usage_event_count`: 已入库 usage event 行数，可近似表示 Codex 用量事件次数，不等同于用户提问数。
- `active_seconds`: 聚合范围内最早 usage event 到最晚 usage event 的时间窗口秒数。
- `avg_tokens_per_session`: `total_tokens / session_count`，当 session 数为 `0` 时为 `0`。
- `avg_tokens_per_call`: `total_tokens / usage_event_count`，当 `usage_event_count = 0` 时为 `0`。

### daily item

```json
{
  "date": "2026-04-29",
  "input_tokens": 0,
  "cached_input_tokens": 0,
  "output_tokens": 0,
  "reasoning_output_tokens": 0,
  "total_tokens": 0,
  "non_cached_input_tokens": 0,
  "net_tokens": 0,
  "cache_hit_rate": 0,
  "reasoning_ratio": 0,
  "session_count": 0,
  "usage_event_count": 0,
  "active_seconds": 0,
  "avg_tokens_per_session": 0,
  "avg_tokens_per_call": 0
}
```

排序：`daily` 按 `date` 升序。

### model item

```json
{
  "model": "gpt-5.4",
  "input_tokens": 0,
  "cached_input_tokens": 0,
  "output_tokens": 0,
  "reasoning_output_tokens": 0,
  "total_tokens": 0,
  "non_cached_input_tokens": 0,
  "net_tokens": 0,
  "cache_hit_rate": 0,
  "session_count": 0,
  "usage_event_count": 0,
  "avg_tokens_per_session": 0,
  "avg_tokens_per_call": 0,
  "active_seconds": 0
}
```

排序：默认按 `total_tokens` 降序。无法归因的模型使用 `unknown`。

### session item

```json
{
  "session_id": "string",
  "started_at": "2026-04-29T12:00:00+08:00",
  "ended_at": "2026-04-29T12:10:00+08:00",
  "active_seconds": 600,
  "models": ["gpt-5.4"],
  "usage_event_count": 0,
  "avg_tokens_per_call": 0,
  "input_tokens": 0,
  "cached_input_tokens": 0,
  "output_tokens": 0,
  "reasoning_output_tokens": 0,
  "total_tokens": 0,
  "non_cached_input_tokens": 0,
  "net_tokens": 0,
  "cache_hit_rate": 0
}
```

排序：默认按 `started_at` 降序。

## 空数据行为

如果时间范围内没有 Codex usage：

- 返回 200。
- `summary` 所有数值为 0。
- `daily`、`models`、`sessions` 返回空数组或补零数组。
- 页面展示空态，不报错。

## P1 不包含

- 分页。
- 费用字段。
- provider 字段。
- tool 字段。
- prompt 或 response 字段。
- SQLite cursor 或 offset。
