# Codex Integration Mapping

## 目标

说明 Codex 本地 session usage metadata 如何映射到 P5 canonical usage event。本文只记录字段映射、配置和隐私边界，不改变 Codex 采集实现。

## Source

Codex 来源是用户本机 Codex session JSONL。Local app 和 teammate collector 只读取 usage metadata，不读取或上传 prompt、response、raw JSONL、用户输入或模型输出。

## Canonical Mapping

| Canonical field | Codex mapping |
| --- | --- |
| `tool` | `codex` |
| `provider` | `unknown`，除非来源 metadata 明确提供 |
| `source_kind` | `local_jsonl` |
| `source_quality` | token delta 来自 Codex usage metadata 时为 `reported`；由累计快照差分得到 delta 时为 `derived` |
| `session_id` | Codex session id；缺失时使用 `unknown` |
| `model` | Codex event 中的 model；缺失时使用 `unknown` |
| `timestamp` | usage event timestamp |
| `input_tokens` | Codex input token delta |
| `cached_input_tokens` | Codex cached input token delta；缺失时为 0 |
| `output_tokens` | Codex output token delta |
| `reasoning_output_tokens` | Codex reasoning output token delta；缺失时为 0 |
| `total_tokens` | Codex total token delta；缺失时使用 canonical fallback |

## Event Key

Codex event key 必须稳定，并且不得包含完整本地路径。

推荐格式沿用 P3：

```text
codex|<session_id>|<source_path_hash>|<line_number>|<total_tokens>|<input_tokens>|<output_tokens>
```

## Fallback

- 缺失 model 使用 `unknown`。
- 缺失 session 使用 `unknown`。
- 缺失 cache token 使用 0。
- 缺失 reasoning token 使用 0。
- token 总量为 0 的无效 usage 不生成 event。
- 不得根据 prompt、response、文件大小或字符数估算 token。

## Local 和 Team 配置

Local dashboard 启动和 `/api/ingest` 会读取本机 Codex usage metadata。

Team collector 通过 teammate 本机配置运行：

```text
~/.token-meter/collector.env
```

配置优先级：

```text
CLI 参数 > collector.env > 系统环境变量
```

Windows 默认读取：

```text
%USERPROFILE%\.token-meter\collector.env
```

并兼容旧的 `collector.env.cmd`。

## Privacy Boundary

Codex integration 不得保存或上传：

- prompt。
- response。
- raw JSONL。
- user input。
- model output。
- admin token。
- device token 明文。
- device token hash。
- 完整本地路径。

本地路径只能以 hash 形式参与 event key。
