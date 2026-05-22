# P3 Module Architecture

## 目标

P3 在团队上报需求中补齐三 module 架构边界，使 teammate 端分发的是轻量 collector jar，而不是包含 dashboard、SQLite、静态资源和管理接口的完整 app jar。

本文件记录 P3 结束后必须长期遵守的 module 约束。后续阶段如果需要调整边界，应先更新设计、contract、tasks 和验收文档，再改代码。


## Module 边界

```text
token-meter-core
  -> app and collector shared session parsing, payload DTOs, config, utilities

token-meter-app
  -> dashboard HTTP server, CRUD/API, admin, report, SQLite stores, static assets

token-meter-collector
  -> teammate CLI collector, local Codex session scan, periodic upload client
```

### `token-meter-core`

core 只保存 app 和 collector 都需要的代码：

- Codex session JSONL 扫描和 usage delta 生成。
- team ingestion payload DTO 和序列化辅助。
- app/collector 共用配置读取。
- app/collector 共用的简单工具。

core 不应包含：

- dashboard HTTP server 或 handler。
- `/api/report`、`/api/team/report`、`/api/team/ingest` 的服务端实现。
- SQLite store、SQL resources、schema migration。
- admin 页面、admin API、token registry CRUD。
- dashboard 静态资源。
- collector 专属调度、安装或上报入口。

### `token-meter-app`

app 负责所有 dashboard 侧能力：

- app/server CLI 入口。
- HTTP/API routing。
- Local dashboard 和 Team dashboard 静态资源。
- 本地 usage ingestion。
- team ingestion 服务端 API。
- admin 登录、device token 分配、复制和删除。
- report 聚合。
- SQLite store、SQL scripts、DB schema。

只有 app module 可以依赖 SQLite。collector 和 core 不得引入 SQLite 依赖。

### `token-meter-collector`

collector 只负责 teammate 端上报：

- 扫描本机 Codex sessions。
- 使用 core 的 session parser 生成标准化 usage event。
- 生成不泄漏本机完整路径的 `event_key`。
- 分批调用 app 端 `POST /api/team/ingest`。
- 输出非敏感运行结果，方便脚本和 launchd 记录。

collector 不应包含：

- SQLite 依赖。
- 本地 checkpoint DB 或 retry queue DB。
- HTTP server。
- dashboard 静态资源。
- app CRUD/API/report/admin 实现。

重复上报由 app 端按 `team_id + user_id + device_id + event_key` 幂等去重。collector 周期性运行时可以重新扫描最近窗口；网络失败或 5xx 后，下次运行重新上报同一窗口，不应造成重复统计。

## 分发约束

teammate 分发包只应包含：

- collector jar。
- collector runner shell script。
- 可选的 mac launchd install/uninstall scripts。

分发包不应包含完整源码仓库、app jar、dashboard 静态资源、SQLite driver 或服务端管理代码。

P3 smoke/package 脚本必须检查 collector jar 中没有以下内容：

- `local/token/meter/http/`
- `local/token/meter/report/`
- `local/token/meter/store/`
- dashboard app entrypoint。
- `static/`
- `org/sqlite/`

## 日志和 stdout

Java 入口和业务代码不得直接使用 `System.out.print` 或 `System.err.print` 输出运行日志。运行日志使用日志 API。

CLI 机器可读 JSON 结果允许通过专用 stdout writer 输出，但不得包含：

- admin token。
- device token 明文。
- token hash。
- prompt、response、raw JSONL 或本机完整路径。

## 文档和开发顺序

P3 的 team 上报是正式阶段能力；后续补丁必须继续保持文档先行：

1. 先更新阶段 `AGENTS.md` 或对应 archive，明确阶段边界。
2. 更新 README，写清文档入口和 MVP 边界。
3. 更新 design，描述数据流、module 边界、隐私和验收策略。
4. 更新 contract，确保 API 与实现一致。
5. 更新 tasks，记录实际开发顺序和已完成项。
6. 更新 smoke/package 脚本。
7. 跑验收并更新 acceptance。

如果后续发现 app 和 collector 同时需要某个类或方法，只有在当前任务确实出现双端共用需求时才放入 core；不要因为未来可能复用而提前搬入 core。
