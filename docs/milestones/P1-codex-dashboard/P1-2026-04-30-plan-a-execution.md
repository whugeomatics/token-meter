# P1 2026-04-30 Plan A Execution

## 目标

把 P1 Codex Dashboard MVP 从后端原型推进到可验收状态。

## 当前阶段

当前仍属于 P1：Codex Dashboard MVP。

本轮只处理：

- 后端原型轻量 review。
- Dashboard 首页。
- 本地构建和 smoke test。
- review 与 acceptance 文档更新。
- 如有长期约定，更新 `AGENTS.md`。

本轮不处理：

- Claude Code。
- Cursor。
- SQLite 持久化。
- 本地模型网关。
- provider adapter。
- 费用估算。

## 成功标准

1. 本地服务可启动。
2. `/` 返回 dashboard 页面。
3. `/api/report` 保持符合 P1 contract。
4. Dashboard 可切换 Today、7D、30D、This Month。
5. Dashboard 展示 summary、daily、models、sessions。
6. 空态和错误态可读。
7. 不读取或展示 prompt/response 正文。
8. 构建通过。
9. smoke test 通过。
10. review 和 acceptance 文档记录结果。

## 执行步骤

### 1. 增加可复跑 smoke test

文件：

- `scripts/P1-2026-04-30-smoke-test.ps1`

验证：

- 启动 jar。
- 检查 `/health`。
- 检查 `/api/report?days=7`。
- 检查 `/` 返回 HTML 且包含 dashboard 根节点。

### 2. 实现 Dashboard 首页

文件：

- `src/main/java/local/token/meter/TokenMeterApp.java`

验证：

- `/` 返回 `text/html; charset=utf-8`。
- 页面通过浏览器端 JavaScript 请求 `/api/report`。
- 页面字段只消费 API contract 字段。
- 不在前端重新定义服务端统计口径。

### 3. 构建和命令行自检

命令：

```text
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package
cmd /c java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --report --days=7
cmd /c java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --report --month=2026-04
```

验证：

- Maven 构建成功。
- CLI 输出包含 `range`、`summary`、`daily`、`models`、`sessions`。

### 4. 运行 smoke test

命令：

```text
powershell -ExecutionPolicy Bypass -File scripts\P1-2026-04-30-smoke-test.ps1
```

验证：

- health、API、dashboard 检查全部通过。

### 5. 更新阶段文档

文件：

- `docs/reviews/P1-2026-04-30-codex-dashboard-review.md`
- `docs/acceptance/P1-2026-04-29-mvp-codex-dashboard.md`
- `docs/milestones/P1-codex-dashboard/P1-2026-04-29-tasks.md`
- `docs/P1-2026-04-29-README.md`
- `AGENTS.md`

验证：

- P1 完成状态清楚。
- 下一阶段进入条件清楚。
- 后续阶段仍不提前实现。
