# P1 2026-04-30 Codex Dashboard Review

## Review 范围

本次 review 覆盖 P1 Codex Dashboard MVP 当前实现：

- `src/main/java/local/token/meter/TokenMeterApp.java`
- `/api/report`
- `/`
- `scripts/P1-2026-04-30-smoke-test.ps1`
- P1 milestone、contract、acceptance 文档

不覆盖：

- SQLite。
- Claude Code。
- Cursor。
- 本地模型网关。
- provider adapter。

## 结论

状态：通过。

代码层面已补齐 P1 MVP 所需的最小 dashboard 页面，并保留原有后端统计口径。Maven package 已由用户在本机终端验证通过，命令为 `mvn -DskipTests clean package`，结果为 `BUILD SUCCESS`。smoke test 已由用户在本机终端验证通过，命令为 `powershell -ExecutionPolicy Bypass -File scripts\P1-2026-04-30-smoke-test.ps1`，输出 `P1 smoke test passed`。Codex 沙箱内未能复现这些命令是进程启动权限限制，不影响 P1 结论。

## 已检查项

### 1. 阶段范围

结果：通过。

- 当前实现仍只支持 Codex。
- 没有实现 Claude Code。
- 没有实现 Cursor。
- 没有实现 SQLite。
- 没有实现本地模型网关。
- 没有引入登录、云同步、费用估算。

### 2. 隐私边界

结果：通过。

- 后端只读取 Codex JSONL metadata。
- 页面只展示 API 聚合字段。
- 没有展示 prompt 正文。
- 没有展示 response 正文。
- 没有写入 `.codex`。

### 3. 统计口径

结果：通过。

已确认实现继续使用：

- `payload.info.total_token_usage` 累计快照。
- 相邻累计快照 delta。
- 相同快照或非正 delta 跳过。
- `last_token_usage` 不作为主聚合来源。

风险：

- 当前 JSON 解析器是轻量字符串解析，不是通用 JSON parser。P1 日志字段路径简单，暂可接受；P2 如果引入更多来源或 SQLite ingestion，建议替换为结构化 JSON parser。

### 4. API contract

结果：通过。

`/api/report` 返回结构仍包含：

- `range`
- `summary`
- `daily`
- `models`
- `sessions`

查询参数仍符合 contract：

- `days=1`
- `days=7`
- `days=30`
- `month=YYYY-MM`

### 5. Dashboard 页面

结果：通过。

新增 `/` 和 `/index.html`：

- 返回 dashboard HTML。
- 浏览器端请求 `/api/report`。
- 支持 Today、7D、30D、This Month。
- 展示 summary、daily、models、sessions。
- 有空态和错误态。

### 6. 可复跑验证

结果：通过。

新增：

- `scripts/P1-2026-04-30-smoke-test.ps1`

脚本检查：

- `/health`
- `/api/report?days=7`
- `/`
- dashboard 根节点与标题

## 已完成验证

```text
mvn -DskipTests clean package
powershell -ExecutionPolicy Bypass -File scripts\P1-2026-04-30-smoke-test.ps1
```

结果：

- Maven：`BUILD SUCCESS`。
- smoke test：`P1 smoke test passed`。

通过后，把 `docs/acceptance/P1-2026-04-29-mvp-codex-dashboard.md` 中未验证项更新为通过。

## 下一阶段建议

P1 可进入 P2：SQLite 持久化与增量采集。

进入 P2 前应先补齐：

- `docs/contracts/P2-YYYY-MM-DD-database-schema.md`
- `docs/contracts/P2-YYYY-MM-DD-ingestion-api.md`
- `docs/milestones/P2-codex-sqlite/P2-YYYY-MM-DD-design.md`
- `docs/milestones/P2-codex-sqlite/P2-YYYY-MM-DD-tasks.md`
- `docs/acceptance/P2-YYYY-MM-DD-codex-sqlite.md`
