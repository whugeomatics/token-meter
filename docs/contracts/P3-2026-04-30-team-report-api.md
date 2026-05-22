# P3 Team Report API Contract

## 目标

定义 P3 团队 dashboard 使用的只读 report API。

P3 dashboard 必须同时展示团队总用量和每个用户的用量。前端不得自行定义统计口径，必须消费本 contract。


## Endpoint

```text
GET /api/team/report
```

查询参数：

```text
days=<int>
period=<optional-period>
compare=<optional-compare>
team_id=<optional-team-id>
user_id=<optional-user-id>
```

参数规则：

- `days`: 可选，默认 7。允许值为 1、7、30。
- `period`: 可选。允许值为 `day`、`week`、`month`。用于团队自然周期趋势视图。
- `compare`: 可选。`period=<day|week|month>&compare=previous` 返回当前周期和上一同期的对比数据。
- `team_id`: MVP 可选。如果服务端只管理一个团队，可省略。
- `user_id`: 可选。传入时返回该用户明细，同时保留团队总览。

`period=<day|week|month>&compare=previous` 的自然周期口径：

- `day`: current 为今天，previous 为昨天。
- `week`: 周起始日为 Monday。current 为服务端时区下的本周 Monday 到今天，previous 为上周 Monday 到上周同一 weekday。
- `month`: current 为本月 1 日到今天，previous 为上月 1 日到同一 day-of-month；如果上月没有该日期，则 previous 截止到上月月末。
- 例如今天是 2026-05-21，则 week current 为 2026-05-18 到 2026-05-21，previous 为 2026-05-11 到 2026-05-14。
- `team_id` 和 `user_id` 筛选同时应用到 current 和 previous。
- Local `/api/report` 和 Team `/api/team/report` 都支持该模式；旧 `days` / `month` 查询继续兼容。

## Response

```json
{
  "range": {
    "days": 7,
    "timezone": "Asia/Shanghai",
    "start_date": "2026-04-24",
    "end_date": "2026-04-30"
  },
  "summary": {
    "team_id": "team-1",
    "total_tokens": 12000,
    "input_tokens": 9000,
    "output_tokens": 3000,
    "sessions": 20,
    "users": 3,
    "devices": 4,
    "usage_event_count": 80,
    "active_seconds": 7200,
    "avg_tokens_per_session": 600,
    "avg_tokens_per_call": 150,
    "cache_hit_rate": 0.42,
    "reasoning_ratio": 0.08
  },
  "teams": [
    {
      "team_id": "team-1",
      "total_tokens": 12000,
      "sessions": 20,
      "users": 3,
      "devices": 4,
      "usage_event_count": 80,
      "active_seconds": 7200,
      "avg_tokens_per_session": 600,
      "avg_tokens_per_call": 150,
      "cache_hit_rate": 0.42,
      "reasoning_ratio": 0.08,
      "last_upload_at": "2026-04-30T12:35:00+08:00"
    }
  ],
  "users": [
    {
      "team_id": "team-1",
      "user_id": "user-1",
      "display_name": "Alice",
      "total_tokens": 6000,
      "input_tokens": 4500,
      "output_tokens": 1500,
      "sessions": 10,
      "devices": 2,
      "usage_event_count": 40,
      "active_seconds": 3600,
      "avg_tokens_per_session": 600,
      "avg_tokens_per_call": 150,
      "cache_hit_rate": 0.42,
      "reasoning_ratio": 0.08,
      "last_seen_at": "2026-04-30T12:35:00Z"
    }
  ],
  "devices": [
    {
      "team_id": "team-1",
      "device_id": "device-1",
      "user_id": "user-1",
      "display_name": "Alice MacBook Pro",
      "total_tokens": 4000,
      "sessions": 7,
      "usage_event_count": 30,
      "active_seconds": 1800,
      "avg_tokens_per_session": 571.43,
      "avg_tokens_per_call": 133.33,
      "cache_hit_rate": 0.42,
      "reasoning_ratio": 0.08,
      "last_seen_at": "2026-04-30T12:35:00Z"
    }
  ],
  "models": [
    {
      "model": "gpt-5.1-codex",
      "total_tokens": 12000,
      "input_tokens": 9000,
      "output_tokens": 3000,
      "sessions": 20,
      "usage_event_count": 80,
      "active_seconds": 7200,
      "avg_tokens_per_session": 600,
      "avg_tokens_per_call": 150,
      "cache_hit_rate": 0.42,
      "reasoning_ratio": 0.08
    }
  ],
  "team_models": [
    {
      "date": "2026-04-30",
      "team_id": "team-1",
      "user_id": "user-1",
      "display_name": "Alice",
      "model": "gpt-5.1-codex",
      "total_tokens": 2000,
      "input_tokens": 1500,
      "cached_input_tokens": 1000,
      "output_tokens": 500,
      "reasoning_output_tokens": 100,
      "sessions": 2,
      "usage_event_count": 8,
      "avg_tokens_per_session": 1000,
      "avg_tokens_per_call": 250,
      "cache_hit_rate": 0.67,
      "reasoning_ratio": 0.2,
      "started_at": "2026-04-30T20:10:00+08:00",
      "ended_at": "2026-04-30T21:20:00+08:00",
      "active_seconds": 4200
    }
  ],
  "daily": [
    {
      "date": "2026-04-30",
      "total_tokens": 3000,
      "input_tokens": 2200,
      "output_tokens": 800,
      "sessions": 5,
      "users": 2,
      "devices": 2,
      "usage_event_count": 12,
      "active_seconds": 4200,
      "avg_tokens_per_session": 600,
      "avg_tokens_per_call": 250,
      "cache_hit_rate": 0.45,
      "reasoning_ratio": 0.08
    }
  ],
  "user_daily": [
    {
      "date": "2026-04-30",
      "team_id": "team-1",
      "user_id": "user-1",
      "display_name": "Alice",
      "total_tokens": 2000,
      "input_tokens": 1500,
      "output_tokens": 500,
      "sessions": 3,
      "usage_event_count": 8,
      "active_seconds": 4200,
      "avg_tokens_per_session": 666.67,
      "avg_tokens_per_call": 250,
      "cache_hit_rate": 0.45,
      "reasoning_ratio": 0.08
    }
  ],
  "upload_health": [
    {
      "team_id": "team-1",
      "user_id": "user-1",
      "device_id": "device-1",
      "last_upload_at": "2026-04-30T12:35:00+08:00",
      "upload_gap_seconds": 600,
      "health_status": "ok",
      "latest_status": "ok",
      "latest_accepted": 12,
      "latest_duplicate": 0,
      "latest_rejected": 0,
      "recent_uploads": [
        {
          "team_id": "team-1",
          "user_id": "user-1",
          "device_id": "device-1",
          "upload_date": "2026-04-30",
          "upload_time": "2026-04-30T12:35:00+08:00",
          "event_count": 12,
          "accepted": 12,
          "duplicate": 0,
          "rejected": 0,
          "status": "ok",
          "message": ""
        }
      ]
    }
  ],
  "uploads": [
    {
      "team_id": "team-1",
      "user_id": "user-1",
      "device_id": "device-1",
      "upload_date": "2026-04-30",
      "upload_time": "2026-04-30T12:35:00+08:00",
      "event_count": 12,
      "accepted": 12,
      "duplicate": 0,
      "rejected": 0,
      "status": "ok",
      "message": ""
    }
  ]
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
      "total_tokens": 12000,
      "usage_event_count": 80,
      "sessions": 20,
      "users": 3,
      "devices": 4
    },
    "previous": {
      "label": "Previous Week",
      "start_date": "2026-05-11",
      "end_date": "2026-05-14",
      "total_tokens": 9000,
      "usage_event_count": 60,
      "sessions": 18,
      "users": 2,
      "devices": 3
    },
    "delta": {
      "total_tokens": 3000,
      "total_tokens_rate": 0.333333,
      "usage_event_count": 20,
      "sessions": 2,
      "users": 1,
      "devices": 1
    },
    "daily": [
      {
        "day_index": 0,
        "label": "Mon",
        "current_date": "2026-05-18",
        "previous_date": "2026-05-11",
        "current_total_tokens": 1000,
        "previous_total_tokens": 800,
        "delta_total_tokens": 200,
        "delta_total_tokens_rate": 0.25
      }
    ],
    "users": [
      {
        "team_id": "team-1",
        "user_id": "user-1",
        "display_name": "Alice",
        "current_total_tokens": 5000,
        "previous_total_tokens": 3000,
        "delta_total_tokens": 2000,
        "delta_total_tokens_rate": 0.666667
      }
    ],
    "models": [
      {
        "model": "gpt-5.1-codex",
        "current_total_tokens": 5000,
        "previous_total_tokens": 3000,
        "delta_total_tokens": 2000,
        "delta_total_tokens_rate": 0.666667
      }
    ]
  }
}
```

## 展示要求

dashboard 团队视角至少包含：

- 团队总 token、input token、output token。
- 团队 net token（`non_cached_input_tokens + output_tokens`）。
- 团队总 session 数。
- 活跃用户数。
- 活跃设备数。
- team 列表和 team 筛选。
- Team 页面必须使用内部分区 Tab 或等价交互，避免 Daily、Users、Devices、Models、Uploads 全量纵向铺开。
- 用户排行，展示每个用户的 token、session、设备数、最近上报时间。
- 设备排行，展示设备归属到哪个用户。
- 模型聚合。
- Team Models 明细必须按 `date + user_id + model` 聚合，展示日期、用户、模型、token、session 数和该组合下的 session 时间窗口。
- Team Models、Users、Devices 必须展示 `usage_event_count`、`avg_tokens_per_session`、`avg_tokens_per_call`、`cache_hit_rate`、`reasoning_ratio` 和 `active_seconds`。
- Team Models 默认按 `date` 倒序展示，并支持前端排序。
- Upload Health 按 `team_id + user_id + device_id` 聚合，每个设备只展示最近 3 条上报和最后上报距现在的时间差，默认按最久未上报优先展示；不得包含 token。
- `uploads` 只保留兼容和排查用途，服务端最多返回最近 50 条，不应作为默认主视图。
- Daily 视图用于观察按天趋势和异常，建议使用折线趋势图加 daily 明细表；精确数值应在 hover 或表格中展示，图内不要求展示 Y 轴数字。
- 团队按天趋势。
- 用户按天趋势或用户筛选后的按天趋势。

## 统计口径

- token 统计来自服务端已验权并写入的 normalized usage event。
- 包含 token 聚合的对象都会输出 `input_tokens`、`cached_input_tokens`、`output_tokens`、`reasoning_output_tokens`、`total_tokens`、`non_cached_input_tokens`、`net_tokens`、`cache_hit_rate`、`reasoning_ratio`。
- user、device 归属来自 device token 绑定。
- team 维度来自 device token 绑定。
- `sessions` 按 `team_id + user_id + device_id + session_id` 去重。
- `usage_event_count` 统计服务端已验权并写入的 usage event 行数，可近似表示 Codex 模型调用/用量事件次数，不等同于用户提问数。
- `avg_tokens_per_session` 为 `total_tokens / sessions`，当 `sessions = 0` 时为 `0`。
- `avg_tokens_per_call` 为 `total_tokens / usage_event_count`，当 `usage_event_count = 0` 时为 `0`。
- `cache_hit_rate` 为 `cached_input_tokens / input_tokens`，当 `input_tokens = 0` 时为 `0`。
- `reasoning_ratio` 为 `reasoning_output_tokens / output_tokens`，当 `output_tokens = 0` 时为 `0`。
- `active_seconds` 为该聚合 bucket 内每个 session 的活跃间隔之和：同一 session 内按时间排序的相邻 usage event 间隔不超过 30 分钟时计入，超过 30 分钟的空闲间隔不计入；它是日志估算值，不是精确键盘在线时长。
- `upload_health` 按 `team_id + user_id + device_id` 分组。
- `upload_health.recent_uploads` 每组最多 3 条，按 `upload_time` 倒序。
- `upload_health.upload_gap_seconds` 为服务端当前时间到该设备最后一次 `upload_time` 的秒数。
- `upload_health.health_status` 使用最后一次上报状态和时间差计算：最后状态非 `ok` 为 `error`，超过 2 小时为 `stale`，超过 30 分钟为 `warning`，其余为 `ok`。
- `users` 统计查询范围内有 usage event 的用户数。
- `devices` 统计查询范围内有 usage event 的设备数。
- `daily` 使用服务端配置时区聚合。
- `team_models.date` 使用服务端配置时区聚合。
- `started_at`、`ended_at`、`last_seen_at` 使用 response `range.timezone` 格式化，不能直接展示 UTC `Z` 字符串。
- 不包含没有 `token_count` 的 Codex session。

## 与 P1/P2 兼容

P3 新增 `/api/team/report`，并保留 P1/P2 旧查询的兼容行为：

```text
GET /api/report
```

本地视角继续使用 `/api/report`。团队视角使用 `/api/team/report`。当前 dashboard 在两个视角下都默认使用 `period=<day|week|month>&compare=previous`，旧 `days` / `month` 查询仍可用于脚本和兼容入口。

## 隐私

response 不得包含：

- prompt。
- response。
- raw JSONL。
- 本机完整 source path。
- device token 明文。
- token hash。
