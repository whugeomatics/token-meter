# P5 Unified CLI Usage Metrics Contract

## 目标

定义 Token Meter 的统一 CLI usage metrics schema。P5 先覆盖 Codex 和 Claude Code，并要求 schema 不依赖任何单一工具的私有字段，未来 CLI 只需新增 source mapping 即可进入同一套 Local/Team report。

## Canonical Usage Event

单条 canonical event 表示一次可去重的 usage delta 或 usage record。

```json
{
  "event_key": "tool|source-kind|session|source-identity|token-fingerprint",
  "tool": "codex",
  "provider": "unknown",
  "source_kind": "local_jsonl",
  "source_quality": "reported",
  "session_id": "session-id",
  "model": "gpt-5.1-codex",
  "timestamp": "2026-05-24T12:34:56Z",
  "input_tokens": 100,
  "cached_input_tokens": 20,
  "output_tokens": 30,
  "reasoning_output_tokens": 0,
  "total_tokens": 130,
  "source_event_key": "optional-source-event-key",
  "client_user_id": "optional-user-id",
  "client_device_id": "optional-device-id"
}
```

字段规则：

- `event_key`: 必填。服务端对同一 `team_id + user_id + device_id + event_key` 去重。
- `tool`: 必填。CLI 工具标识，例如 `codex`、`claude-code`。未来工具必须新增稳定 tool id。
- `provider`: 可选。模型服务商标识，例如 `openai`、`anthropic`、`unknown`。P5 不把 provider 作为 dashboard 主过滤维度。
- `source_kind`: 必填。来源类型，例如 `local_jsonl`、`otel_metric`、`hook_metadata`、`gateway`、`fixture`。
- `source_quality`: 必填。允许值：`reported`、`derived`、`estimated`。
- `session_id`: 必填。缺失时使用稳定 source identity 或 `unknown`，并按规则降低 `source_quality`。
- `model`: 必填。缺失时使用 `unknown`。
- `timestamp`: 必填。事件时间，ISO-8601 UTC。
- `input_tokens`: 必填，非负整数。表示输入 token 总量，必须包含可识别的 cache creation/read input token。
- `cached_input_tokens`: 必填，非负整数。表示可从 input 中识别出的 cache hit 或 cache write/read input token。
- `output_tokens`: 必填，非负整数。表示模型输出 token。
- `reasoning_output_tokens`: 必填，非负整数。来源缺失时为 0。
- `total_tokens`: 必填，非负整数。优先使用来源上报总量；缺失时按 canonical fallback 计算。
- `source_event_key`: 可选。不包含完整本地路径、prompt、response、raw payload 或 token。
- `client_user_id`: 可选，只用于服务端一致性校验，不决定归属。
- `client_device_id`: 可选，只用于服务端一致性校验，不决定归属。

## Source Quality

- `reported`: token 字段来自来源工具明确上报的 usage metadata。
- `derived`: token 字段由来源工具上报的非正文 usage metadata 相加或相减得到。
- `estimated`: 非 token 维度由 collector 推导；token 不得根据正文、文件大小或 transcript 内容估算。

如果同一 event 中不同字段质量不同，event `source_quality` 使用最低可信等级：

```text
reported > derived > estimated
```

## Fallback Rules

- 缺失 `model` 时使用 `unknown`。
- 缺失 `session_id` 时优先使用来源稳定 identity；仍缺失时使用 `unknown`。
- 缺失 `cached_input_tokens` 时使用 0。
- 缺失 `reasoning_output_tokens` 时使用 0。
- 缺失 `total_tokens` 时使用 `input_tokens + output_tokens + reasoning_output_tokens`。因为 `input_tokens` 已包含可识别 cached input，fallback 不再额外相加 `cached_input_tokens`。
- 如果 `input_tokens + cached_input_tokens + output_tokens + reasoning_output_tokens + total_tokens` 全部为 0，不生成 usage event。
- 不得根据 prompt、response、raw transcript、raw API body、文件大小或字符数估算 token。

## Derived Report Metrics

以下字段不作为新的历史事实写入 DB，由 report 层按统一公式派生：

```text
net_input_tokens = max(input_tokens - cached_input_tokens, 0)
net_total_tokens = net_input_tokens + output_tokens + reasoning_output_tokens
cache_rate = cached_input_tokens / input_tokens
```

规则：

- `input_tokens` 为 0 时，`cache_rate` 为 0。
- `cached_input_tokens` 大于 `input_tokens` 时，`net_input_tokens` 按 0 处理，`cache_rate` 可以按 report 展示策略 clamp 到 1.0。
- summary、daily、models、sessions、tools 和 comparison 必须使用同一公式。
- Local `/api/report` 和 Team `/api/team/report` 对同名派生字段必须保持相同语义。

## Aggregation Dimensions

P5 要求 Local 和 Team 至少支持以下聚合维度：

- date。
- tool。
- model。
- session。
- source_kind。
- source_quality。

Team report 额外支持：

- team。
- user。
- device。

`provider` 是可选维度。P5 contract 允许 event 携带 provider，但不要求 Local/Team report 提供 provider filter 或 provider ranking。

## Tool Mapping Requirements

每个 CLI 工具接入必须提供 source mapping 文档，说明：

- 工具来源路径或 endpoint。
- 原始 usage 字段到 canonical 字段的映射。
- event key 稳定性规则。
- 缺失字段 fallback。
- `source_kind` 和 `source_quality` 判定。
- 禁止采集和禁止上传字段。
- 验证 fixture 或真实样本的脱敏方式。

P5 覆盖：

- `docs/integrations/codex.md`
- `docs/integrations/claude-code.md`

## Privacy Boundary

canonical event、payload、DB、report、stdout 和日志不得包含：

- prompt。
- response。
- raw JSONL。
- raw API body。
- raw transcript。
- user input。
- model output。
- tool input payload。
- tool output payload。
- admin token。
- device token 明文。
- device token hash。
- 完整本地路径。

如果需要关联本地来源，只能使用 hash 或来源自身稳定 id。
