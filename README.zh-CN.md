# Token Meter

![what](images/P0-2026-04-29-what.png)

[English](README.md) | **中文**

Token Meter 是一个本地 AI CLI 使用统计 dashboard，并会逐步演进成本地模型路由工具。项目先从 Codex 使用统计开始，再进入多 CLI 团队采集，后续扩展到本地 OpenAI-compatible 模型网关。

## 当前状态

当前阶段：**P4 - Claude Code teammate 用量采集设计准备**。

P1、P2 和 P3 均已通过验收。

P1 Codex Dashboard MVP：

- Maven package 已在用户真实 Windows 终端通过。
- P1 smoke test 已通过，输出 `P1 smoke test passed`。
- Dashboard 可以读取 Codex usage metadata，并提供 `/api/report`。

P2 SQLite 持久化与增量采集：

- Maven package 已在用户真实 Windows 终端通过。
- P2 smoke test 已通过，输出 `P2 smoke test passed`。
- `--ingest` 可以把 Codex usage delta 写入本地 SQLite，`/api/report` 从 SQLite 聚合。

P3 Codex 团队用量采集：

- 项目采用 `token-meter-core`、`token-meter-app`、`token-meter-collector` 三个 Maven module。
- app 提供 Local 和 Team 两个 dashboard 视角。
- collector 是 teammate 端轻量上报程序，不包含 dashboard、admin、SQLite 或静态 UI 代码。
- Local `/api/report` 和 Team `/api/team/report` 都支持 `period=<day|week|month>&compare=previous` 的日、周、月对比。

下一步进入 P4 设计文档：Claude Code usage collection contract、collector 接入设计、任务拆分、隐私规则和验收标准。

## 阶段成果

每个阶段完成后，都应在这里添加该阶段截图或成果物链接。

- P1 Codex Dashboard MVP：[2026-04-30](images/P1-2026-04-30-mvp.png)

## 当前范围

P1、P2 和 P3 只聚焦 Codex。P4 开始补齐 Claude Code 使用上报，使产品从单一 Codex 统计扩展到多 AI CLI 工具统计。

当前阶段允许：

- 读取本机 Codex session JSONL 日志。
- 按日期、模型、会话聚合 token usage。
- 在 P2 中把 Codex usage metadata 持久化到本地 SQLite。
- 增量采集新追加的 Codex 日志。
- 在 P3 中通过轻量 collector 采集团队成员 Codex usage。
- 设计 P4 Claude Code teammate usage collection。
- 按 Day、Week、Month 横向对比 Local 和 Team 用量。
- 不存储、不展示 prompt 和 response 正文。

当前阶段不做：

- Cursor 接入。
- 本地模型网关。
- provider adapter。
- 云同步、登录或费用估算。

## 文档

当前阶段：

- [当前 AGENTS.md](AGENTS.md)

已完成的 P3：

- [P3 README](docs/P3-2026-04-30-README.md)
- [P3 AGENTS 归档](docs/archive/P3-2026-04-30-AGENTS.md)
- [P3 Device Token Contract](docs/contracts/P3-2026-04-30-device-token.md)
- [P3 Team Ingestion Contract](docs/contracts/P3-2026-04-30-team-ingestion-api.md)
- [P3 Team Report Contract](docs/contracts/P3-2026-04-30-team-report-api.md)
- [P3 Team Usage Event Contract](docs/contracts/P3-2026-04-30-team-usage-event.md)
- [P3 Module Architecture](docs/milestones/P3-codex-team-collection/P3-2026-05-08-module-architecture.md)
- [P3 Period Comparison Design](docs/milestones/P3-codex-team-collection/P3-2026-05-21-period-comparison-design.md)
- [P3 Admin Usage Guide](docs/guides/P3-2026-05-01-admin-usage-guide.md)
- [P3 Acceptance](docs/acceptance/P3-2026-04-30-codex-team-collection.md)


已完成的 P2：

- [P2 README](docs/P2-2026-04-30-README.md)
- [P2 AGENTS 归档](docs/archive/P2-2026-04-30-AGENTS.md)
- [P2 Database Schema Contract](docs/contracts/P2-2026-04-30-database-schema.md)
- [P2 Ingestion Contract](docs/contracts/P2-2026-04-30-ingestion-api.md)
- [P2 Design](docs/milestones/P2-codex-sqlite/P2-2026-04-30-design.md)
- [P2 Tasks](docs/milestones/P2-codex-sqlite/P2-2026-04-30-tasks.md)
- [P2 Implementation Plan](docs/milestones/P2-codex-sqlite/P2-2026-04-30-implementation-plan.md)
- [P2 Architecture Cleanup](docs/milestones/P2-codex-sqlite/P2-2026-04-30-architecture-cleanup.md)
- [P2 Review](docs/reviews/P2-2026-04-30-codex-sqlite-review.md)
- [P2 Acceptance](docs/acceptance/P2-2026-04-30-codex-sqlite.md)

已完成的 P1：

- [P1 README](docs/P1-2026-04-29-README.md)
- [P1 Requirements](docs/P1-2026-04-29-requirements.md)
- [P1 Codex Log Research](docs/research/P1-2026-04-29-codex-log-research.md)
- [P1 Report API Contract](docs/contracts/P1-2026-04-29-report-api.md)
- [P1 Dashboard IA](docs/P1-2026-04-29-dashboard-ia.md)
- [P1 Backend Prototype](docs/milestones/P1-codex-dashboard/P1-2026-04-29-backend-prototype.md)
- [P1 Review](docs/reviews/P1-2026-04-30-codex-dashboard-review.md)
- [P1 Acceptance](docs/acceptance/P1-2026-04-29-mvp-codex-dashboard.md)

Agent 工作规则：

- [当前 AGENTS.md](AGENTS.md)
- [P1 AGENTS 归档](docs/archive/P1-2026-04-29-AGENTS.md)
- [P2 AGENTS 归档](docs/archive/P2-2026-04-30-AGENTS.md)
- [P3 AGENTS 归档](docs/archive/P3-2026-04-30-AGENTS.md)

## 构建

推荐在用户真实 Windows 终端执行：

```powershell
mvn -DskipTests clean package
```

项目约定也允许：

```powershell
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package
```

Codex 沙箱说明：Windows `.cmd`、`cmd.exe /c` 和本地 Java 服务启动可能被沙箱进程权限拦截。遇到这种情况时，应在用户真实 Windows 终端执行验证，并把结果写入验收文档。

## 运行 Dashboard

打包后执行：

```powershell
java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --port=18080
```

打开：

- Dashboard: <http://127.0.0.1:18080/>
- Local report API: <http://127.0.0.1:18080/api/report?period=day&compare=previous>
- Team report API: <http://127.0.0.1:18080/api/team/report?period=day&compare=previous>
- Health: <http://127.0.0.1:18080/health>

如果需要让局域网内其他机器访问 dashboard，启动时显式指定 bind host：

```powershell
java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --port=18080 --bind=0.0.0.0
```

Smoke test：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\P1-2026-04-30-smoke-test.ps1
```

## API

P1 和 P2 保持同一个兼容 report endpoint：

```text
GET /api/report?days=7
GET /api/report?days=30
GET /api/report?month=2026-04
```

当前 Local 和 Team dashboard 控件使用自然周期对比：

```text
GET /api/report?period=day&compare=previous
GET /api/report?period=week&compare=previous
GET /api/report?period=month&compare=previous
GET /api/team/report?period=day&compare=previous
GET /api/team/report?period=week&compare=previous
GET /api/team/report?period=month&compare=previous
```

返回结构：

```json
{
  "range": {},
  "summary": {},
  "daily": [],
  "models": [],
  "sessions": []
}
```

字段定义见 [P1 Report API Contract](docs/contracts/P1-2026-04-29-report-api.md) 和 [P3 Team Report Contract](docs/contracts/P3-2026-04-30-team-report-api.md)。

## 隐私

项目禁止存储或展示：

- prompt 正文。
- response 正文。
- 用户源码片段。
- API key。

P1 只读取 Codex JSONL metadata。P2 只持久化 usage delta metadata。P3 只上传标准化 usage event。

## Agent 工作流

每个阶段进入实现前必须有完整支撑文档：

1. 当前阶段 AGENTS 工作手册。
2. 设计文档。
3. Contract。
4. 任务拆分。
5. Review。
6. Acceptance。

一个阶段内，根目录 `AGENTS.md` 可以动态调整。阶段结束时，最终版必须归档为：

```text
docs/archive/P<阶段>-YYYY-MM-DD-AGENTS.md
```
