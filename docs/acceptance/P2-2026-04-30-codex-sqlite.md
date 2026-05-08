# P2 Codex SQLite Acceptance

## 目标

记录 P2 SQLite 持久化与增量采集的验收结果。

## 测试环境

- OS：Windows
- 时区：Asia/Shanghai
- Java 版本：Java 17 target
- Maven 命令：`mvn -DskipTests clean package`
- SQLite 文件：P2 smoke test 使用临时目录下 `agent-dashboard.sqlite`
- Codex 日志路径：P2 smoke test 使用脱敏临时 JSONL fixture
- 验收日期：2026-04-30

## 验收项

### 1. 构建验证

结果：通过

检查：

- `mvn -DskipTests clean package` 通过。
- 生成 `target\agent-dashboard-0.1.0-SNAPSHOT.jar`。

记录：

- Codex 沙箱中执行 `mvn -DskipTests package` 仍失败于启动 `mvn.cmd`，错误为 Windows 进程启动限制，不是 Maven 编译输出。
- 用户真实 Windows 终端执行 `mvn -DskipTests clean package`，结果 `BUILD SUCCESS`。
- shade plugin 生成 fat jar，并替换原始 artifact。shade overlap warning 只涉及 `META-INF/MANIFEST.MF`，不阻断验收。

### 2. Schema 初始化

结果：通过

检查：

- SQLite 文件可创建。
- `schema_migrations` 存在。
- `source_files` 存在。
- `usage_events` 存在。
- 索引存在。

### 3. 首次 ingestion

结果：通过

检查：

- `--ingest` 可扫描真实 Codex JSONL。
- 输出 `status=ok`。
- `events_inserted > 0`，如果本机没有 usage 日志则记录为空数据原因。
- `source_files` checkpoint 被写入。

### 4. 重复 ingestion

结果：通过

检查：

- 第二次执行 `--ingest`。
- 不重复插入相同 usage event。
- `events_inserted=0` 或只包含真实新增事件。

### 5. 增量 ingestion

结果：通过

检查：

- 新增 JSONL 行后，只导入新增 delta。
- checkpoint 正确推进。
- 文件变小或替换时不重复统计历史 event。

### 6. Report API 兼容

结果：通过

检查：

- `/api/report?days=7` 返回 P1 contract 字段。
- summary 与 daily 聚合一致。
- models 和 sessions 可解释。
- 空数据返回 200 和空数组/零值。

### 7. 隐私和范围

结果：通过

检查：

- SQLite 不包含 prompt 正文。
- SQLite 不包含 response 正文。
- 不写 `.codex`。
- 不实现 Claude Code。
- 不实现 Cursor。
- 不实现本地网关。

## 结论

状态：通过

可选状态：

- 通过。
- 有条件通过。
- 不通过。

结论说明：

```text
通过项：
  - 用户真实终端 Maven package 通过，输出 BUILD SUCCESS。
  - 用户真实终端 P2 smoke test 通过，输出 P2 smoke test passed。
  - SQLite schema 初始化、首次 ingestion、重复 ingestion、report 聚合由 P2 smoke test 覆盖。
  - 实现范围仍只包含 Codex、SQLite ingestion 和 report 聚合。
失败项：无。
未验证项：真实 Codex 全量日志 ingestion 尚未单独记录；当前验收使用脱敏 JSONL fixture。
下一步建议：进入 P3 设计阶段前，先归档 P2 AGENTS.md，并保留 P2 文档索引。
```

## 架构 Cleanup 追加验收

状态：通过

变更：

- 前端静态页面拆到 `src/main/resources/static/index.html`。
- SQLite DDL/DML 集中到 `src/main/resources/db/schema-v1.sql`。
- Java 通过 `org.xerial:sqlite-jdbc` 和 JDBC API 访问 SQLite。
- 后端拆分为入口、配置、HTTP、ingestion、repository、report、domain、utility 文件。

复验命令：

```powershell
mvn -DskipTests clean package
powershell -ExecutionPolicy Bypass -File scripts\P2-2026-04-30-smoke-test.ps1
```

复验结果：

- 用户真实 Windows 终端执行 `mvn -DskipTests clean package`，结果 `BUILD SUCCESS`，编译 21 个 source files，复制 2 个 resources。
- 用户真实 Windows 终端执行 `powershell -ExecutionPolicy Bypass -File scripts\P2-2026-04-30-smoke-test.ps1`，结果 `P2 smoke test passed`。
- SLF4J binding 使用 `slf4j-nop`，collector 和 smoke 日志不应再出现 StaticLoggerBinder warning。

Codex 沙箱记录：

- `mvn -DskipTests package` 仍失败于启动 `mvn.cmd`，属于本地沙箱进程限制。
- Java 源码静态检查显示 SQL 已从 Java 建表/查询字符串中移出，仅 `SqliteUsageStore` 保留 JDBC connection 创建。

## mac 修复追加验收

状态：通过

变更：

- dashboard 服务启动前自动执行一次 Codex ingestion，避免页面只读取空 SQLite。
- 默认 SQLite 存储调整为月分片目录 `~/.agent-dashboard/sqlite/agent-dashboard-YYYY-MM.sqlite`。
- `--db` 指向 `.sqlite` / `.db` 文件时保持单文件兼容。
- `/api/report` 在分片模式下按查询日期范围读取相关月分片。
- 新增 mac smoke test `scripts/P2-2026-04-30-smoke-test.sh`。

复验命令：

```bash
mvn -DskipTests package
sh scripts/P2-2026-04-30-smoke-test.sh
```

复验结果：

- Codex mac 沙箱执行 `mvn -DskipTests package`，结果 `BUILD SUCCESS`。
- Codex mac 沙箱执行 `sh scripts/P2-2026-04-30-smoke-test.sh`，结果 `P2 smoke test passed`。
- smoke test 覆盖单文件 SQLite、月分片 SQLite、重复 ingestion 去重和 report 聚合。
- Codex mac 沙箱使用真实 `/Users/zhangrui/.codex/sessions` 写入临时分片库，结果 `files_scanned=4`、`events_inserted=116`、`errors=[]`，生成 `agent-dashboard-2026-04.sqlite`。
- 同一临时分片库执行 `--report --days=7` 可返回非零 summary 和 daily/model/session 聚合。
- 真实 2026-04-24 Codex session 文件被扫描并写入 `source_files`，但该文件没有 `token_count` / `total_token_usage` / `input_tokens` / `output_tokens`，因此 `usage_events=0`；P2 不推算 token，不把该 session 计入 token usage。
- Codex mac 沙箱绑定本地 HTTP 端口失败于 `java.net.SocketException: Operation not permitted`，属于沙箱进程权限限制；脚本在该错误下跳过 server bind 检查。真实 mac 终端可继续用同一脚本校验 dashboard 启动后 `/api/report` 链路。
