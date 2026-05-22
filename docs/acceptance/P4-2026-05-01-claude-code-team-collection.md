# P4 Claude Code Team Usage Collection Acceptance

## 目标

定义 P4 Claude Code teammate usage collection 的验收标准。

## 测试环境

- OS：Windows 和 macOS/Linux 分别验证 collector 路径。
- 时区：Asia/Shanghai。
- Java 版本：Java 17 target。
- 构建命令：`cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package`。
- Collector 输入：脱敏 Claude Code telemetry fixture。
- Server 存储：临时 SQLite 目录或文件。
- 验收日期：2026-05-01。

## 验收项

### 1. 文档和阶段边界

结果：通过（文档检查）

检查：

- P4 README、design、contract、tasks、acceptance 文档存在。
- 文档明确 P4 只做 Claude Code teammate usage collection。
- 文档明确不做 Cursor、本地网关、多 provider 自动路由。
- 文档明确不采集 prompt、response、raw API body、raw transcript。

### 2. Claude Code 来源解析

结果：通过（fixture 单元测试）

检查：

- collector 可读取脱敏 OTel usage fixture。
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

- `--collect-claude-code` 可触发 Claude Code 采集模式。
- `--claude-source=otel` 可读取 fixture。
- `--claude-source=hook` 只读取允许 metadata。
- collector 继续复用 P3 `--server-url`、`--device-token`、`--user-id`、`--device-id`。
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
- Overview 展示 tool ranking。
- Period Comparison 展示 tool delta。
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

- P3 Codex team ingestion/report 行为不变。
- Local `/api/report` 行为不变。
- Day、Week、Month period comparison 行为不变。
- collector jar 仍不包含 dashboard、SQLite、admin 或静态资源。

## 验收命令

```powershell
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd test
node scripts\P3-2026-05-10-dashboard-ui-check.mjs
```

本轮已执行：

```powershell
cmd /c "D:\Softwares\Maven-3.9.9\bin\mvn.cmd" "-Dmaven.repo.local=.m2\repository" test
cmd /c "D:\Softwares\Maven-3.9.9\bin\mvn.cmd" "-Dmaven.repo.local=.m2\repository" -DskipTests package
node --check token-meter-app\src\main\resources\static\app.js
```

待补充：P3 Codex smoke 或等价端到端回归、P4 真实 collector upload smoke。
