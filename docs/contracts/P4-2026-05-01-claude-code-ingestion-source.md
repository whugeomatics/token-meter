# P4 Claude Code Ingestion Source Contract

## 目标

定义 teammate collector 如何从 Claude Code 获取 usage metadata，以及哪些来源可以使用、哪些来源禁止使用。

P4 本地统计默认读取 Claude Code 本地 JSONL usage metadata；team collector 也可以读取同一路径并上传标准化 event。官方 OpenTelemetry usage monitoring 仍是组织级 telemetry 首选来源。Hook/transcript 只作为受限补充来源，不读取正文。

## 来源优先级

### 1. Local Claude Code JSONL usage metadata

本地 dashboard 和 teammate collector 默认读取：

```text
<user.home>/.claude/projects/**/*.jsonl
```

该路径在 macOS/Linux 和 Windows 上都通过 JVM `user.home` 解析，不硬编码平台路径。

允许读取：

- `sessionId`。
- `timestamp`。
- `message.id`，仅用于稳定去重，不作为正文或用户内容处理。
- `message.model`。
- `message.usage.input_tokens`。
- `message.usage.cache_creation_input_tokens`。
- `message.usage.cache_read_input_tokens`。
- `message.usage.output_tokens`。
- `message.usage.reasoning_output_tokens`（如果存在）。

同一 Claude Code assistant message 可能在本地 JSONL 中出现多行。collector 必须按 `message.id`、session、model 和 token 指纹生成稳定 `event_key` 并在本次扫描内去重；`message.id` 缺失时才回退到 timestamp。

禁止解析、存储或上传：

- `message.content`。
- prompt。
- response。
- tool input。
- tool output。
- transcript 正文。

### 2. OpenTelemetry usage metadata

优先使用 Claude Code OpenTelemetry 输出的 usage、cost、duration、tool activity 等 metadata。

允许读取：

- metric/log name。
- timestamp。
- session id。
- model。
- token count。
- cost metadata。
- duration metadata。
- tool name。
- non-sensitive dimensions。

禁止读取或上传：

- prompt。
- response。
- raw API body。
- raw request。
- raw response。
- tool input payload。
- tool output payload。

### 3. Hook metadata

Hook 只在 OTel 缺少必要 session/tool metadata 时使用。

允许读取：

- `session_id`。
- `transcript_path` 的路径 hash。
- `cwd` 的路径 hash。
- `hook_event_name`。
- `tool_name`。

禁止读取或上传：

- transcript 文件正文。
- prompt。
- response。
- tool input。
- tool output。
- raw JSON payload。

### 4. Fixture

测试可以使用脱敏 fixture。

Fixture 必须满足：

- 不包含真实 prompt。
- 不包含真实 response。
- 不包含真实 transcript。
- 不包含真实 token。
- 不包含真实本地路径。

## Collector 配置

P4 collector 默认一次运行同时读取 Codex 和 Claude Code；客户端无需传单独 Claude 开关。Claude Code 来源配置只用于覆盖默认本地路径或切换测试/OTel/Hook 来源：

```text
--claude-source=<local|otel|hook|fixture>
--claude-projects-dir=<path>
--claude-otel-input=<path-or-endpoint>
--claude-hook-input=<path>
--collector-env-file=<path>
--days=<1|7|30>
```

环境变量等价入口：

```text
TOKEN_METER_CLAUDE_SOURCE
TOKEN_METER_CLAUDE_PROJECTS_DIR
TOKEN_METER_CLAUDE_OTEL_INPUT
TOKEN_METER_CLAUDE_HOOK_INPUT
TOKEN_METER_COLLECTOR_ENV
```

Team collector teammate 配置优先级固定为：

```text
CLI 参数 > collector env file > 系统环境变量
```

默认 env file：

```text
macOS/Linux: ~/.token-meter/collector.env
Windows: %USERPROFILE%\.token-meter\collector.env
```

Windows 兼容旧服务配置 `%USERPROFILE%\.token-meter\collector.env.cmd`。env file 只属于 teammate 客户端，server 端不读取该文件。Admin 页面创建 token 后应生成完整 teammate `.env`，包含 `TOKEN_METER_SERVER_URL`、`TOKEN_METER_USER_ID`、`TOKEN_METER_DEVICE_ID`、`TOKEN_METER_DEVICE_TOKEN` 和默认 `TOKEN_METER_DAYS`。

`--collect-claude-code` 仅作为旧脚本兼容入口保留；正常 team collection 不需要它。

P4 不要求 collector 常驻。周期运行时可以读取最近窗口内的 telemetry metadata，并依赖服务端 `event_key` 幂等去重。

## 失败语义

- 来源不存在：collector 返回机器可读错误，不上传空伪造数据。
- 来源字段缺失：只生成可解释字段，缺失 token 为 0；如果所有 token 字段都为 0，不生成 usage event。
- 来源包含禁止字段：collector 必须忽略禁止字段，不写日志、不上传。
- OTel 和 Hook 同时存在：OTel token 字段优先，Hook 只补 session/tool metadata。

## 隐私规则

collector 不得在 stdout、stderr、日志、payload 或错误响应中输出：

- prompt。
- response。
- raw API body。
- transcript 正文。
- device token 明文。
- device token hash。
- admin token。

本地路径只允许上传 hash 或 basename；默认使用 hash。

## 与 P3 Ingestion 的关系

P4 不新增服务端 ingestion endpoint。collector 必须把 Claude Code metadata 转换为 `tool=claude-code` 的标准 usage event，并继续调用：

```text
POST /api/team/ingest
Authorization: Bearer <device_token>
```

服务端继续使用 P3 device token binding 决定最终 `team_id`、`user_id` 和 `device_id`。
