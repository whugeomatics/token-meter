# P4 Claude Code Team Usage Collection Review

## 目标

Review P4 实现是否与当前 contract、tasks、acceptance 和隐私边界一致。

## Review 范围

- `AGENTS.md`
- `README.md`
- `README.zh-CN.md`
- `docs/P1-2026-04-29-roadmap.md`
- `docs/P4-2026-05-01-README.md`
- `docs/contracts/P1-2026-04-29-report-api.md`
- `docs/contracts/P3-2026-04-30-device-token.md`
- `docs/contracts/P3-2026-04-30-team-ingestion-api.md`
- `docs/contracts/P3-2026-04-30-team-usage-event.md`
- `docs/contracts/P3-2026-04-30-team-report-api.md`
- `docs/contracts/P4-2026-05-01-claude-code-usage-event.md`
- `docs/contracts/P4-2026-05-01-claude-code-ingestion-source.md`
- `docs/contracts/P4-2026-05-01-tool-usage-report-extension.md`
- `docs/milestones/P4-claude-code-team-collection/P4-2026-05-01-design.md`
- `docs/milestones/P4-claude-code-team-collection/P4-2026-05-01-tasks.md`
- `docs/acceptance/P4-2026-05-01-claude-code-team-collection.md`
- `docs/guides/P3-2026-05-01-admin-usage-guide.md`
- `scripts/P4-2026-05-24-claude-code-team-smoke-test.sh`

## 结论

状态：通过，P4 可以视为实现验证完成；Windows collector 安装路径仍建议在真实 Windows 终端做发布前复核。

## 检查结果

阶段边界：通过

- P4 只补齐 Claude Code local/team usage collection 和 tool 维度统计。
- P4 没有接入 Cursor、本地模型网关、多 provider 自动路由、登录、云同步或计费系统。
- P4 没有新增第二套 server ingestion API，继续复用 `/api/team/ingest`。

Local 与 Team 行为：通过

- Local ingestion 默认同时采集 Codex 和 Claude Code。
- Team collector 默认一次运行同时采集 Codex 和 Claude Code。
- `--collect-claude-code` 仅保留为旧脚本兼容入口，不是正常 teammate 流程要求。
- Local `/api/report` 与 Team `/api/team/report` 都支持 tool filter 和 tool 聚合。
- Models/Sessions 等视图展示 tool 来源。

Claude Code 数据来源：通过

- 默认读取 `<user.home>/.claude/projects/**/*.jsonl`。
- 本地 JSONL 只解析 `sessionId`、`timestamp`、`message.id`、`message.model` 和 `message.usage`。
- 同一 Claude assistant message 通过 `message.id` 去重，避免一次对话被重复统计。
- 缺失 model 使用 `unknown`；无正向 token usage 时不生成 event。

统计口径：通过

- Claude Code `cached_input_tokens` 使用 `cache_creation_input_tokens + cache_read_input_tokens`。
- cache rate 按工具口径归一化，避免 Claude cache rate 超过 100%，也避免拉低 Codex 原有 cache rate。
- `TokenTotals` 对混合工具聚合按事件累加归一化分母。

Teammate 配置：通过

- admin 创建 device token 后展示完整 teammate `.env`。
- collector 配置优先级为 `CLI 参数 > collector env file > 系统环境变量`。
- macOS/Linux 默认读取 `~/.token-meter/collector.env`。
- Windows 默认读取 `%USERPROFILE%\.token-meter\collector.env`，并兼容旧 `collector.env.cmd`。
- server 端不读取 teammate `.env`。
- 401 `unknown device token` 错误提示指向旧 token、admin.html、重启 collector 和 server DB 校验，不包含 token 明文。

隐私与安全：通过

- 不采集 prompt、response、raw API body、raw transcript、tool input 或 tool output。
- 不存储 raw Claude JSONL 行。
- 不上传完整本地路径。
- report、错误响应、stdout/stderr 不输出 admin token、device token 明文或 token hash。
- 用户真实 token 只应存在于 teammate 本机 env 文件、HTTP Authorization header 和服务端 registry 的敏感字段中。

分发包：通过

- collector jar 不包含 dashboard、HTTP/API、admin、app store 或静态资源；collector 仅包含轻量 SQLite 状态库所需 driver。
- Unix/Git Bash dist README 已说明 Codex + Claude Code、teammate `.env` 和配置优先级。
- `dist/` 保持 `token-meter-collector-unix/` 一个目录；Windows 通过 Git Bash 执行同一套脚本。

## 验证记录

已执行并通过：

```text
mvn test
mvn -DskipTests package
node --check token-meter-app/src/main/resources/static/admin.js
sh scripts/P3-2026-05-01-package-collector.sh all
sh -n scripts/P3-2026-05-01-package-collector-unix.sh
sh -n dist/token-meter-collector-unix/run-collector.sh
```

新增：

```text
sh scripts/P4-2026-05-24-claude-code-team-smoke-test.sh
```

用户已在本机真实环境执行通过，输出：

```text
P4 Claude Code team smoke test passed
```

该 smoke 覆盖：

- admin API 创建 device token。
- teammate `.env` 写入和读取。
- collector 默认一次运行上传 Codex + Claude Code。
- Claude Code 重复 JSONL 行按 `message.id` 去重。
- Team report 同时返回 `tool=codex` 和 `tool=claude-code`。
- `tool=codex` 与 `tool=claude-code` 筛选互不串数据。
- period comparison 返回 `comparison.tools`。
- report 不泄露 Claude message content。

用户已在本机真实 Claude Code 数据上验证：

- 一次 Claude 对话不再重复统计。
- Claude cache rate 不超过 100%。
- Codex cache rate 不被 Claude 口径错误拉低。
- Local/Team tool 过滤一致。

## 残余风险

- Windows Task Scheduler 安装脚本和 `%USERPROFILE%\.token-meter\collector.env` 真实执行路径仍建议在 Windows 终端复核。
- Claude Code OpenTelemetry/Hook 来源当前主要通过 fixture contract 保留，默认实际路径仍是本地 JSONL metadata。
- P4 不解决 Cursor、provider adapter、本地网关或费用估算。
