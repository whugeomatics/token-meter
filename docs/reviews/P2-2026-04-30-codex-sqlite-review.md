# P2 Codex SQLite Review

## 范围

本 review 覆盖 P2 当前实现：

- SQLite schema 初始化。
- `--ingest` CLI。
- `source_files` checkpoint。
- `usage_events` 去重写入。
- `/api/report` 从 SQLite 聚合。
- P2 smoke test 脚本。

## 结果

状态：通过。

## 检查项

### 阶段边界

结果：通过。

实现仍只支持 Codex，没有加入 Claude Code、Cursor、本地网关或 provider adapter。

### 隐私

结果：通过。

SQLite schema 和 ingestion event 只保存 usage metadata：

- session id。
- model。
- timestamp/local_date。
- token delta。
- source file checkpoint。

未新增 prompt、response、message、content、源码片段字段。

### Token 统计口径

结果：通过。

实现继续使用 `payload.info.total_token_usage` 相邻 cumulative snapshot delta。changed source file 从头重放并通过 `event_key` 去重，避免缺少上一条 snapshot 时错算 delta。

### 去重

结果：通过。

`usage_events.event_key` 使用：

```text
codex|<session_id>|<source_path>|<line_number>|<cumulative_total_tokens>|<cumulative_input_tokens>|<cumulative_output_tokens>
```

P2 smoke test 覆盖二次 ingestion 不重复插入。用户真实 Windows 终端执行结果为 `P2 smoke test passed`。

### API 兼容

结果：通过。

`/api/report` 仍返回 P1 contract 的五个顶层字段：

- `range`
- `summary`
- `daily`
- `models`
- `sessions`

P2 smoke test 已覆盖字段和基本聚合。用户真实 Windows 终端执行结果为 `P2 smoke test passed`。

## 风险

- 新增 `sqlite-jdbc` 和 Maven shade plugin，首次构建会下载依赖。
- 当前 Codex 沙箱仍无法启动 `mvn.cmd`，构建结果以用户真实 Windows 终端为准。
- P2 处理 changed source file 时会从头重放单文件。该策略优先保证 delta 正确，未来如果单个 JSONL 文件过大，可在后续阶段增加上一条 cumulative snapshot checkpoint 字段。

## 结论

P2 review 通过。用户真实终端已执行：

```powershell
mvn -DskipTests clean package
powershell -ExecutionPolicy Bypass -File scripts\P2-2026-04-30-smoke-test.ps1
```

结果：

- Maven：`BUILD SUCCESS`。
- P2 smoke test：`P2 smoke test passed`。

## mac 修复追加 review

状态：通过。

追加检查范围：

- dashboard startup ingestion。
- 默认 SQLite 月分片。
- `--db` 单文件兼容。
- mac smoke test。
- 真实 Codex session ingestion。

检查结果：

- dashboard 启动前会执行一次本地 Codex ingestion，页面仍只消费只读 `/api/report`。
- 默认路径改为 `~/.token-meter/sqlite/token-meter-YYYY-MM.sqlite`，`--db` 指向 `.sqlite` / `.db` 时继续单文件兼容。
- Codex session 文件优先按路径中的 `YYYY/MM/DD` 归属月份分片，避免用文件修改时间误分片。
- 真实 mac Codex sessions 临时 ingestion 结果为 `files_scanned=4`、`events_inserted=116`、`errors=[]`。
- 2026-04-24 的旧 Codex session 文件被扫描并记录 checkpoint，但没有 `token_count`、`total_token_usage`、`input_tokens` 或 `output_tokens`，因此不生成 usage event；该行为符合“不推算虚假 token”的 P2 口径。

mac 复验命令：

```bash
mvn -DskipTests package
sh scripts/P2-2026-04-30-smoke-test.sh
```

mac 复验结果：

- Maven：`BUILD SUCCESS`。
- P2 mac smoke test：`P2 smoke test passed`。
- Codex 沙箱无法绑定本地 HTTP 端口，错误为 `java.net.SocketException: Operation not permitted`，属于沙箱限制；真实终端可继续用同一脚本验证 server 路径。

## 后续架构修正

P2 验收后发现原实现存在维护性问题：前端 HTML、HTTP、ingestion、SQLite、report aggregation 曾集中在单个 Java 类中，且 SQL 曾以内联字符串存在于 Java 代码中。

已补充架构 cleanup 要求：

- 前端静态资源放入 `src/main/resources/static/`。
- SQL 集中放入 `src/main/resources/db/schema-v1.sql`。
- SQLite 访问统一使用 `org.xerial:sqlite-jdbc` 和 JDBC API。
- Java 按入口、配置、HTTP、ingestion、repository、report、domain、utility 拆分。

cleanup 的详细记录见：

- `docs/milestones/P2-codex-sqlite/P2-2026-04-30-architecture-cleanup.md`

cleanup 复验结果：

- `mvn -DskipTests clean package`: `BUILD SUCCESS`。
- `powershell -ExecutionPolicy Bypass -File scripts\P2-2026-04-30-smoke-test.ps1`: `P2 smoke test passed`。
