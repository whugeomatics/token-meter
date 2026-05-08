# P2 Architecture Cleanup

## 背景

P2 已跑通 SQLite 持久化与增量采集，但实现过程中为了快速闭环，入口、HTTP、前端 HTML、Codex ingestion、SQLite repository、report aggregation 和 JSON 工具曾集中在 `TokenMeterApp.java` 一个类中。

这会增加 P3 本地网关阶段的维护成本，因此在进入 P3 设计开发前先完成结构性整理。

## 决策

### 前后端拆分

前端静态页面不再写在 Java text block 中，统一放在：

```text
src/main/resources/static/index.html
```

Java 后端只负责读取该静态资源并返回 HTTP response。

### 后端职责拆分

后端按职责拆成以下文件：

- `TokenMeterApp.java`: 应用入口和模式分发。
- `AppConfig.java`: CLI/env 配置解析。
- `DashboardServer.java`: HTTP server、路由、response 写出。
- `DashboardPage.java`: 静态前端资源读取。
- `CodexIngestionService.java`: Codex JSONL ingestion。
- `SqliteUsageStore.java`: SQLite repository，通过 JDBC 连接数据库。
- `SqlScripts.java`: SQL 文件加载和命名语句解析。
- `ReportService.java`: report aggregation。
- `ReportQuery.java`: report 查询窗口。
- `Snapshot.java`, `UsageEvent.java`, `TokenTotals.java`: usage domain model。
- `IngestionResult.java`, `IngestionError.java`, `IngestedUsageEvent.java`, `SourceFileState.java`, `SourceFileRecord.java`: ingestion/repository DTO。
- `Json.java`, `JsonMapper.java`, `BadRequestException.java`: shared utilities。

### SQL 集中管理

所有 SQLite DDL 和 DML 必须集中在 SQL 文件中：

```text
src/main/resources/db/schema-v1.sql
```

Java 代码不得再内联 `CREATE TABLE`、`CREATE INDEX`、`INSERT ...`、`SELECT ...` 等 SQL 字符串。需要新增 SQL 时，应先添加命名 SQL block，再由 repository 层通过 `SqlScripts` 读取执行。

### JDBC 访问约束

SQLite 访问统一使用：

```xml
org.xerial:sqlite-jdbc
```

连接方式统一封装在 repository 层：

```java
DriverManager.getConnection("jdbc:sqlite:" + dbPath)
```

业务层不得直接创建 JDBC connection。

## 非目标

本次 cleanup 不改变：

- CLI 参数。
- `/api/report` contract。
- P2 ingestion contract。
- SQLite schema 字段含义。
- dashboard 展示数据来源。

本次 cleanup 不提前实现：

- P3 本地模型网关。
- Claude Code / Cursor 接入。
- provider adapter。

## 验收

验收使用 P2 既有命令：

```powershell
mvn -DskipTests clean package
powershell -ExecutionPolicy Bypass -File scripts\P2-2026-04-30-smoke-test.ps1
```

通过标准：

- Maven build 成功。
- P2 smoke test 输出 `P2 smoke test passed`。
- dashboard 首页仍包含 `id="app"` 和 `Codex Usage Dashboard`。
- `--ingest` 仍通过 JDBC 写入 SQLite。

## 验收结果

状态：通过。

用户真实 Windows 终端结果：

- `mvn -DskipTests clean package`: `BUILD SUCCESS`。
- `powershell -ExecutionPolicy Bypass -File scripts\P2-2026-04-30-smoke-test.ps1`: `P2 smoke test passed`。

说明：

- Maven shade 的 overlapping `META-INF/MANIFEST.MF` warning 不阻断验收。
- SLF4J StaticLoggerBinder warning 来自 sqlite-jdbc/slf4j，当前使用 no-op logger，不影响功能。
