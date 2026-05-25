# Claude Code Integration Mapping

## 目标

说明 Claude Code usage metadata 如何映射到 P5 canonical usage event。本文只记录字段映射、配置和隐私边界，不读取或上传 Claude prompt、response、raw API body 或 transcript 正文。

## Source

P4/P5 默认读取用户主目录下 Claude Code 本地项目日志：

```text
<user.home>/.claude/projects/**/*.jsonl
```

P4 也保留 OTel 和 hook metadata fixture 支持。Hook 只允许作为受限 metadata 来源，不读取 transcript 正文。

## Canonical Mapping

| Canonical field | Claude Code mapping |
| --- | --- |
| `tool` | `claude-code` |
| `provider` | `anthropic`，如果来源无法可靠确认则为 `unknown` |
| `source_kind` | `local_jsonl`、`otel_metric`、`otel_log`、`hook_metadata` 或 `fixture` |
| `source_quality` | 来源明确上报 token 时为 `reported`；total 由 usage metadata 相加得到时为 `derived`；session/timestamp 等非 token 维度推导时为 `estimated` |
| `session_id` | Claude Code session id；缺失时使用稳定 source identity 或 `unknown` |
| `model` | `message.model` 或 OTel model metadata；缺失时使用 `unknown` |
| `timestamp` | Claude Code event timestamp |
| `input_tokens` | Claude Code `input_tokens + cache_creation_input_tokens + cache_read_input_tokens` |
| `cached_input_tokens` | `cache_creation_input_tokens + cache_read_input_tokens` |
| `output_tokens` | Claude Code `output_tokens` |
| `reasoning_output_tokens` | Claude Code 来源缺失时为 0 |
| `total_tokens` | 来源上报 total；缺失时使用 canonical fallback |

## Event Key

Claude Code event key 必须稳定，并且不得包含完整本地路径。

推荐格式沿用 P4：

```text
claude-code|<source_kind>|<session_id>|<source_identity>|<model>|<token_fingerprint>
```

`local_jsonl` 来源优先使用 Claude `message.id` 的 hash 作为 `source_identity`，避免同一 assistant message 的重复 JSONL 行被重复统计。

## Fallback

- 缺失 model 使用 `unknown`。
- 缺失 session 优先使用稳定 source identity，仍缺失时使用 `unknown`。
- 缺失 cache token 使用 0。
- 缺失 reasoning token 使用 0。
- 缺失 total 时使用 canonical fallback；因为 canonical `input_tokens` 已包含 cached input，不再额外相加 cache token。
- token 总量为 0 的无效 usage 不生成 event。
- 不得根据 prompt、response、raw transcript、raw API body、文件大小或字符数估算 token。

## Local 和 Team 配置

Local dashboard 启动和 `/api/ingest` 默认同时采集 Codex 与 Claude Code。

Team collector 默认一次运行同时采集 Codex 与 Claude Code。`--collect-claude-code` 仅作为旧脚本兼容入口保留，不是正常 teammate 流程要求。

teammate 本机配置文件：

```text
~/.token-meter/collector.env
```

配置优先级：

```text
CLI 参数 > collector.env > 系统环境变量
```

## Privacy Boundary

Claude Code integration 不得保存或上传：

- prompt。
- response。
- raw API body。
- raw transcript。
- raw JSONL 行。
- user input。
- model output。
- tool input payload。
- tool output payload。
- admin token。
- device token 明文。
- device token hash。
- 完整本地路径。

本地路径只能以 hash 形式参与 event key 或 source identity。
