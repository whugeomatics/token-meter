# P4 Claude Code Team Usage Collection Acceptance

## 目标

定义 P4 Claude Code teammate usage collection 的验收标准。

## 测试环境

- OS：Windows 和 macOS/Linux 分别验证 collector 路径。
- 时区：Asia/Shanghai。
- Java 版本：Java 17 target。
- 构建命令：`cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package`。
- Collector 输入：脱敏 Claude Code local JSONL fixture 和 telemetry fixture。
- Server 存储：临时 SQLite 目录或文件。
- 验收日期：2026-05-01。

## 验收项

### 1. 文档和阶段边界

结果：通过（文档检查）

检查：

- P4 README、design、contract、tasks、acceptance 文档存在。
- 文档明确 P4 只做 Claude Code local + teammate usage collection 和 tool 维度统计补齐。
- 文档明确 P4 同时补齐 Local 和 Team 的 Claude Code 统计。
- 文档明确 collector 默认同时采集 Codex 和 Claude Code，不要求 teammate 使用 Claude 专用开关。
- 文档明确 teammate `.env` 只在客户端使用，配置优先级为 `CLI 参数 > collector.env > 系统环境变量`。
- 文档明确不做 Cursor、本地网关、多 provider 自动路由。
- 文档明确不采集 prompt、response、raw API body、raw transcript。

### 2. Claude Code 来源解析

结果：通过（fixture 单元测试）

检查：

- collector 可读取脱敏 OTel usage fixture。
- local dashboard 和 collector 可读取本地 Claude Code JSONL fixture。
- 本地 JSONL 只解析 `sessionId`、`timestamp`、`message.model` 和 `message.usage`。
- collector 可读取受限 hook metadata fixture。
- OTel token 字段优先于 hook metadata。
- Hook 不读取 transcript 正文。
- 缺失 model 时使用 `unknown`。
- 缺失 token 且总量为 0 时不生成 usage event。

### 3. Claude Code Usage Event

结果：通过（fixture 单元测试）

检查：

- event `tool` 为 `claude-code`。
- event 包含稳定 `event_key`。
- event 包含 `source_kind`。
- event 包含 `source_quality`。
- event 不包含完整本地路径。
- event 不包含 prompt、response、raw API body、raw transcript、tool input、tool output。

### 4. Collector CLI

结果：通过（collector 编译与 fixture 单元测试）

检查：

- 默认 team collector 一次运行同时上传 Codex 和 Claude Code，客户端无需传 Claude 专用开关。
- 默认 Claude Code 来源读取 `<user.home>/.claude/projects/**/*.jsonl`。
- `--claude-source=local` 可读取本地 Claude Code JSONL。
- `--claude-projects-dir` 可覆盖默认本地 Claude Code projects 目录。
- `--claude-source=otel` 可读取 fixture。
- `--claude-source=hook` 只读取允许 metadata。
- `--collect-claude-code` 仅作为旧脚本兼容入口保留。
- collector 继续支持 P3 `--server-url`、`--device-token`、`--user-id`、`--device-id` CLI 参数。
- collector 默认读取 teammate 本机 `~/.token-meter/collector.env`；Windows 支持 `%USERPROFILE%\.token-meter\collector.env` 并兼容 `collector.env.cmd`。
- collector 同名配置优先级为 `CLI 参数 > collector.env > 系统环境变量`。
- admin 创建 token 后页面生成完整 teammate `.env`。
- 401 unknown device token 错误提示包含“可能使用旧 token / 从 admin.html 复制当前 teammate .env / 重启 collector / 校验 server database”诊断，不包含 token 明文。
- stdout 为机器可读 JSON。
- stdout/stderr 不包含 device token、prompt、response、raw payload。

### 5. Server Ingestion

结果：通过（ingestion 单元测试）

检查：

- `POST /api/team/ingest` 可接收 `tool=claude-code` batch。
- 服务端归属来自 device token binding。
- 重复 `event_key` 不重复统计。
- Codex `tool=codex` 上报不受影响。
- team usage event 继续写入月度 SQLite 分片。

### 6. Team Report

结果：通过（report 单元测试）

检查：

- `GET /api/team/report` 返回 `tools` 聚合。
- `GET /api/team/report?tool=claude-code` 只返回 Claude Code 聚合。
- `GET /api/team/report?tool=codex` 只返回 Codex 聚合。
- `period=<day|week|month>&compare=previous` 返回 `comparison.tools`。
- 旧 P3 response 字段仍存在。

### 7. Dashboard

结果：通过（静态页面实现，JS 语法检查）

检查：

- Team 页面可选择 All Tools、Codex、Claude Code。
- Local 页面可选择 All Tools、Codex、Claude Code。
- Overview 展示 tool ranking。
- Period Comparison 展示 tool delta。
- Models/Sessions 等表格展示 tool 来源。
- 页面不展示 raw source、完整 transcript path、prompt 或 response。

### 8. 隐私

结果：通过（单元测试覆盖 payload、ingestion、report）

检查：

- 上传 payload 不包含 prompt。
- 上传 payload 不包含 response。
- 上传 payload 不包含 raw API body。
- 上传 payload 不包含 transcript 正文。
- DB 不保存 prompt、response、raw API body、transcript 正文。
- report 不返回 prompt、response、raw API body、transcript 正文。
- 错误日志不包含 token 明文或 raw payload。

### 9. 回归

结果：部分通过

检查：

- P3 Codex team ingestion/report 行为保持兼容。
- Local `/api/report` 保持旧字段兼容，并新增 tool 维度过滤和聚合。
- Local `/api/report` 可通过 `tool=claude-code` 展示 Claude Code 本地 JSONL 用量。
- Day、Week、Month period comparison 行为不变。
- collector jar 仍不包含 dashboard、SQLite、admin 或静态资源。

## 验收命令

```powershell
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd test
node scripts\P3-2026-05-10-dashboard-ui-check.mjs
node --check token-meter-app\src\main\resources\static\app.js
node --check token-meter-app\src\main\resources\static\admin.js
sh scripts/P3-2026-05-01-package-collector.sh all
```

本轮已执行：

```powershell
cmd /c "D:\Softwares\Maven-3.9.9\bin\mvn.cmd" "-Dmaven.repo.local=.m2\repository" test
cmd /c "D:\Softwares\Maven-3.9.9\bin\mvn.cmd" "-Dmaven.repo.local=.m2\repository" -DskipTests package
node --check token-meter-app\src\main\resources\static\app.js
node --check token-meter-app/src/main/resources/static/admin.js
sh scripts/P3-2026-05-01-package-collector.sh all
```

待补充：P3 Codex smoke 或等价端到端回归、P4 真实 collector upload smoke。
