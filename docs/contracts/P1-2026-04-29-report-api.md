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

兼容扩展：

- `period`: 可选。允许值为 `day`、`week`、`month`。
- `compare`: 可选。当前仅支持 `compare=previous`。

规则：

- 如果同时传入 `days` 和 `month`，后端应返回 400。
- 如果都不传，默认 `days=7`。
- `days=1` 表示今日。
- 日期聚合使用本地时区。
- `period=<day|week|month>&compare=previous` 返回当前自然周期与上一同期的对比数据；该模式用于当前 Local dashboard 的 Day、Week、Month 控件。
- 使用 `period` 时必须同时传 `compare=previous`。

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

当请求 `period=<day|week|month>&compare=previous` 时，response 额外包含：

```json
{
  "comparison": {
    "period": "week",
    "current": {
      "label": "This Week",
      "start_date": "2026-05-18",
      "end_date": "2026-05-21",
      "total_tokens": 0,
      "usage_event_count": 0,
      "sessions": 0
    },
    "previous": {
      "label": "Previous Week",
      "start_date": "2026-05-11",
      "end_date": "2026-05-14",
      "total_tokens": 0,
      "usage_event_count": 0,
      "sessions": 0
    },
    "delta": {
      "total_tokens": 0,
      "total_tokens_rate": 0,
      "usage_event_count": 0,
      "sessions": 0
    },
    "daily": [],
    "models": []
  }
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
- `non_cached_input_tokens`: Codex 使用 `input_tokens - cached_input_tokens`；当来源把 cache token 与普通 input 分开上报导致 `cached_input_tokens > input_tokens` 时使用 `input_tokens`。
- `net_tokens`: `non_cached_input_tokens + output_tokens`。
- `cache_hit_rate`: Codex 使用 `cached_input_tokens / input_tokens`；当来源把 cache token 与普通 input 分开上报导致 `cached_input_tokens > input_tokens` 时使用 `cached_input_tokens / (input_tokens + cached_input_tokens)`。
- `reasoning_ratio`: `reasoning_output_tokens / output_tokens`，当 `output_tokens = 0` 时为 `0`。
- `usage_event_count`: 已入库 usage event 行数，可近似表示 Codex 用量事件次数，不等同于用户提问数。
- `active_seconds`: 对 summary、daily、models 等聚合 bucket，按 session 内不超过 30 分钟的相邻 usage event 间隔累加；超过 30 分钟的空闲间隔不计入。session item 仍展示该 session 内最早 usage event 到最晚 usage event 的时间窗口秒数。
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
