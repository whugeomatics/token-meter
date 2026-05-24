# P4 Tool Usage Report Extension Contract

## 目标

扩展 P3 `/api/team/report`，让 Team dashboard 可以横向展示 Codex 和 Claude Code 的用量。

P4 不破坏 P3 response 既有字段，只新增 tool 维度字段和可选筛选参数。

## Query Extension

`GET /api/team/report` 新增可选参数：

```text
tool=<optional-tool>
```

规则：

- `tool` 可选。
- 允许值：`codex`、`claude-code`。
- 缺省时返回所有工具聚合。
- `tool` 筛选必须同时应用到 current 和 previous period comparison。
- `team_id`、`user_id`、`period`、`compare` 行为沿用 P3 contract。

## Response Extension

response 顶层新增：

```json
{
  "tools": [
    {
      "tool": "codex",
      "total_tokens": 12000,
      "input_tokens": 9000,
      "cached_input_tokens": 1000,
      "output_tokens": 3000,
      "reasoning_output_tokens": 0,
      "sessions": 20,
      "users": 3,
      "devices": 4,
      "usage_event_count": 80,
      "active_seconds": 7200,
      "source_quality": {
        "reported": 80,
        "derived": 0,
        "estimated": 0
      }
    }
  ]
}
```

字段规则：

- `tools`: 按 `total_tokens` 降序。
- `tool`: 工具名，P4 支持 `codex` 和 `claude-code`。
- token 字段沿用 P3 `TokenTotals` 口径。
- `source_quality`: 可选但推荐，用于展示 Claude Code 字段来源质量。

## Comparison Extension

当请求：

```text
period=<day|week|month>&compare=previous
```

`comparison` 中新增：

```json
{
  "tools": [
    {
      "tool": "claude-code",
      "current_total_tokens": 3000,
      "previous_total_tokens": 1000,
      "delta_total_tokens": 2000,
      "delta_total_tokens_rate": 2.0
    }
  ]
}
```

规则：

- `comparison.tools` 覆盖 current 和 previous 中出现过的 tool。
- 排序按 `abs(delta_total_tokens)` 降序。
- `tool=<value>` 筛选时，`comparison.tools` 可以只返回该 tool。

## Backward Compatibility

P4 不允许移除或重命名 P3 字段：

- `range`。
- `summary`。
- `teams`。
- `users`。
- `devices`。
- `models`。
- `team_models`。
- `daily`。
- `user_daily`。
- `upload_health`。
- `uploads`。
- `comparison` 中已有字段。

旧 P3 客户端忽略新增 `tools` 字段时仍应可工作。

## Dashboard 要求

P4 Team dashboard 至少支持：

- All Tools 总览。
- Codex tool 过滤。
- Claude Code tool 过滤。
- tool 维度排行。
- 日、周、月 period comparison 下的 tool delta。
