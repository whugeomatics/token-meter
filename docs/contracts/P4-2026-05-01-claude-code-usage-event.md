# P4 Claude Code Usage Event Contract

## 目标

定义 teammate collector 从 Claude Code usage metadata 生成并上传的标准化 usage event。

P4 event 必须能进入 P3 `/api/team/ingest` 链路，并在 `/api/team/report` 中以 `tool=claude-code` 聚合。P4 不上传 prompt、response、raw API body 或 transcript 正文。

## Event 字段

单条 event：

```json
{
  "event_key": "claude-code|local_jsonl|session|source-identity-hash|model|token-fingerprint",
  "tool": "claude-code",
  "session_id": "session-id",
  "model": "claude-sonnet-4-5",
  "timestamp": "2026-05-01T12:34:56Z",
  "input_tokens": 100,
  "cached_input_tokens": 20,
  "output_tokens": 30,
  "reasoning_output_tokens": 0,
  "total_tokens": 130,
  "source_kind": "otel_metric",
  "source_quality": "reported",
  "source_event_key": "optional-source-event-key",
  "client_user_id": "optional-user-id",
  "client_device_id": "optional-device-id"
}
```

字段规则：

- `event_key`: 必填。服务端对同一 `team_id + user_id + device_id + event_key` 去重。
- `tool`: 必填，P4 固定为 `claude-code`。
- `session_id`: 必填。优先使用 Claude Code session id；缺失时使用稳定的 collector-generated session id，并将 `source_quality` 标为 `estimated`。
- `model`: 必填。缺失时使用 `unknown`。
- `timestamp`: 必填。事件时间，ISO-8601 UTC。
- `input_tokens`: 必填，非负整数。缺失时为 0。
- `cached_input_tokens`: 必填，非负整数。缺失时为 0。Claude Code 本地 JSONL 来源使用 `cache_read_input_tokens + cache_creation_input_tokens`。
- `output_tokens`: 必填，非负整数。缺失时为 0。
- `reasoning_output_tokens`: 必填，非负整数。Claude Code 来源缺失时为 0。
- `total_tokens`: 必填，非负整数。优先使用来源上报总量；缺失时使用 `input_tokens + cached_input_tokens + output_tokens + reasoning_output_tokens`。
- `source_kind`: 必填。允许值：`local_jsonl`、`otel_metric`、`otel_log`、`hook_metadata`、`fixture`。
- `source_quality`: 必填。允许值：`reported`、`derived`、`estimated`。
- `source_event_key`: 可选。保存本地来源事件键或 metric identity，不包含完整本地路径。
- `client_user_id`: 可选，只用于服务端一致性校验，不决定归属。
- `client_device_id`: 可选，只用于服务端一致性校验，不决定归属。

## 不允许字段

event 不得包含：

- prompt。
- response。
- raw API body。
- raw transcript。
- user input。
- model output。
- tool input payload。
- tool output payload。
- admin token。
- device token 明文。
- device token hash。

## Event Key

`event_key` 必须稳定，且不包含完整本地路径。

推荐格式：

```text
claude-code|<source_kind>|<session_id>|<source_identity>|<model>|<token_fingerprint>
```

说明：

- `local_jsonl` 来源优先使用 Claude `message.id` 的 hash 作为 source identity；缺失时才使用来源事件时间，避免同一 assistant message 的重复 JSONL 行被重复统计。
- `source_identity` 使用来源自身稳定身份；如果来源只有累计指标，使用 metric export timestamp。
- `token_fingerprint` 使用 token 数值、metric name 和 source identity 生成，不使用 prompt 或 response。
- 如果来源包含本地文件路径，只能上传路径 hash。

## 来源质量

- `reported`: token 数来自 Claude Code telemetry 明确上报字段。
- `derived`: total tokens 由已上报 input/output/cache/reasoning 字段相加得到。
- `estimated`: session id 或 timestamp 等非 token 维度由 collector 推导；token 不得根据正文、文件大小或 transcript 内容估算。

## 服务端写入规则

服务端写入中央库时继续补充：

```text
team_id
user_id
device_id
received_at
```

这些字段来自 P3 device token binding 和服务端接收时间，不信任客户端 payload。

## 聚合维度

P4 至少支持：

- date。
- team。
- user。
- device。
- tool。
- model。
- session。
- source_kind。
- source_quality。
