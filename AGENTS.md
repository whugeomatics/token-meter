# AGENTS.md

本文件只保留后续 agent 进入项目时必须先知道的核心约束和文档入口。阶段细节以 docs 为准，避免把已完成阶段的执行细节继续堆在根目录。

## 1. 工作原则

- 先文档、再设计、再开发、再验收。没有阶段设计、contract、tasks 和 acceptance 时，不直接写功能代码。
- 每个阶段只解决当前阶段问题；后续阶段能力只记录到文档，不提前实现。
- 关键约定必须写入 docs，不依赖聊天上下文传递。
- 修改认证、admin token、device token、隐私相关代码前，先明确安全影响。
- 不记录 prompt 正文、response 正文、raw JSONL、用户输入、模型输出、admin token、device token 明文或 token hash。
- git commit 信息使用英文，格式：`type(scope): description`。

## 2. 当前阶段

当前阶段：P3 设计准备。

P1、P2、P2.5 已通过验收。P3 只聚焦 OpenAI-compatible 本地模型网关的设计、contract、任务拆分和验收标准；在 P3 文档补齐前，不实现网关代码。

P3 文档待办：

- `docs/P3-2026-05-01-README.md`
- `docs/contracts/P3-2026-05-01-openai-compatible-gateway.md`
- `docs/contracts/P3-2026-05-01-provider-adapter.md`
- `docs/contracts/P3-2026-05-01-usage-event.md`
- `docs/milestones/P3-local-gateway/P3-2026-05-01-design.md`
- `docs/milestones/P3-local-gateway/P3-2026-05-01-tasks.md`
- `docs/acceptance/P3-2026-05-01-local-gateway.md`

P3 禁止：

- 不接入 Claude Code、Cursor。
- 不实现多 provider 自动路由。
- 不引入登录、云同步、计费系统。
- 不存 prompt/response 正文。
- 不破坏 P1/P2 `/api/report` contract。
- 不破坏 P2.5 `/api/team/report` 和 `/api/team/ingest` contract。

## 3. 项目架构硬约束

项目是三 module 架构：

- `agent-dashboard-core`：只放 app 和 collector 都需要的配置、Codex session 解析、team payload DTO、通用 DTO 和通用工具。
- `agent-dashboard-app`：负责 dashboard/server 入口、HTTP/API、admin、report、SQLite CRUD/store、SQL resources 和静态资源。只有 app module 可以依赖 SQLite。
- `agent-dashboard-collector`：负责 teammate 端轻量上报入口和周期性上报逻辑；collector 不依赖数据库，不启动 HTTP server，不包含 dashboard 静态资源或 CRUD/API/report/admin 实现。

如果后续发现 app 和 collector 都需要的类或方法，先确认当前阶段确实双端共用，再补到 core。

代码结构：

- 前端静态页面和脚本放在 app module 的 `src/main/resources/static/`。
- HTTP/API 层只处理路由、参数、状态码和 response。
- ingestion 层只处理日志读取和 usage event 生成。
- repository/store 层只处理 JDBC、SQL 执行和数据库映射。
- report 层只处理统计聚合和 contract 输出。
- SQL DDL/DML 集中在 app module 的 `src/main/resources/db/*.sql`，Java 代码不内联建表或查询 SQL。
- Java 入口和业务代码不得用 `System.out.print` / `System.err.print` 输出运行日志；日志使用日志 API。CLI 机器可读输出可使用专用 stdout writer。

P2.5 module 细节见：

- `docs/milestones/P2.5-codex-team-collection/P2.5-2026-05-08-module-architecture.md`

## 4. 当前阶段必读

进入 P3 设计前至少先读：

- `docs/archive/P1-2026-04-29-AGENTS.md`
- `docs/archive/P2-2026-04-30-AGENTS.md`
- `docs/archive/P2.5-2026-04-30-AGENTS.md`
- `docs/P2-2026-04-30-README.md`
- `docs/P2.5-2026-04-30-README.md`
- `docs/contracts/P1-2026-04-29-report-api.md`
- `docs/contracts/P2-2026-04-30-database-schema.md`
- `docs/contracts/P2-2026-04-30-ingestion-api.md`
- `docs/contracts/P2.5-2026-04-30-team-ingestion-api.md`
- `docs/contracts/P2.5-2026-04-30-team-usage-event.md`
- `docs/contracts/P2.5-2026-04-30-device-token.md`
- `docs/contracts/P2.5-2026-04-30-team-report-api.md`
- `docs/milestones/P2.5-codex-team-collection/P2.5-2026-05-08-module-architecture.md`
- `docs/acceptance/P2-2026-04-30-codex-sqlite.md`
- `docs/acceptance/P2.5-2026-04-30-codex-team-collection.md`

## 5. 阶段索引

P1：Codex Dashboard MVP，通过。

- `docs/archive/P1-2026-04-29-AGENTS.md`
- `docs/P1-2026-04-29-README.md`
- `docs/contracts/P1-2026-04-29-report-api.md`
- `docs/reviews/P1-2026-04-30-codex-dashboard-review.md`
- `docs/acceptance/P1-2026-04-29-mvp-codex-dashboard.md`

P2：SQLite 持久化与增量采集，通过。

- `docs/archive/P2-2026-04-30-AGENTS.md`
- `docs/P2-2026-04-30-README.md`
- `docs/contracts/P2-2026-04-30-database-schema.md`
- `docs/contracts/P2-2026-04-30-ingestion-api.md`
- `docs/milestones/P2-codex-sqlite/P2-2026-04-30-design.md`
- `docs/milestones/P2-codex-sqlite/P2-2026-04-30-tasks.md`
- `docs/reviews/P2-2026-04-30-codex-sqlite-review.md`
- `docs/acceptance/P2-2026-04-30-codex-sqlite.md`

P2.5：Codex 团队用量采集，通过。

- `docs/archive/P2.5-2026-04-30-AGENTS.md`
- `docs/P2.5-2026-04-30-README.md`
- `docs/contracts/P2.5-2026-04-30-device-token.md`
- `docs/contracts/P2.5-2026-04-30-team-ingestion-api.md`
- `docs/contracts/P2.5-2026-04-30-team-report-api.md`
- `docs/contracts/P2.5-2026-04-30-team-usage-event.md`
- `docs/milestones/P2.5-codex-team-collection/P2.5-2026-04-30-design.md`
- `docs/milestones/P2.5-codex-team-collection/P2.5-2026-05-08-module-architecture.md`
- `docs/milestones/P2.5-codex-team-collection/P2.5-2026-04-30-tasks.md`
- `docs/reviews/P2.5-2026-04-30-codex-team-collection-design-review.md`
- `docs/acceptance/P2.5-2026-04-30-codex-team-collection.md`
- `docs/guides/P2.5-2026-05-01-admin-usage-guide.md`

## 6. 验证命令

- 构建：`mvn -DskipTests package`
- 完整清理构建：`mvn -DskipTests clean package`
- P2 smoke：`sh scripts/P2-2026-04-30-smoke-test.sh`
- P2.5 smoke：`sh scripts/P2.5-2026-04-30-smoke-test.sh`

如果 Codex 沙箱无法绑定本地 HTTP 端口，应在验收文档中记录为“沙箱执行受限”，不要判断为代码失败。
