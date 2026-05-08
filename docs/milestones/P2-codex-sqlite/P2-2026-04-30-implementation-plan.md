# P2 Codex SQLite Implementation Plan

## 目标

实现 P2：SQLite 持久化与增量采集。当前阶段只支持 Codex，新增 `--ingest` CLI，把 Codex `total_token_usage` delta 写入本地 SQLite，并让 `/api/report` 继续返回 P1 contract 结构。

## 设计确认

P2 采用保守方案：

1. 引入 `org.xerial:sqlite-jdbc` 作为唯一新增运行时依赖。
2. 使用 Maven shade 生成可直接运行的 jar，避免用户运行时手动拼 classpath。
3. 不拆出复杂模块，优先在现有 Java 应用内增加小型 SQLite repository 和 ingestion service。
4. 只处理 changed source files；对于发生变化的文件，从头重放该文件以保持 cumulative snapshot delta 正确，并依赖 `usage_events.event_key` 去重。
5. `/api/report` 从 SQLite 聚合；没有 ingestion 数据时返回空统计结构。

选择理由：

- P2 目标是跑通本地持久化闭环，不提前做 provider adapter。
- 重新读取单个 changed file 可以避免 checkpoint 缺少上一条 cumulative snapshot 导致的错误 delta。
- event_key 是 P2 contract 已定义的幂等边界。

## 改动范围

### `pom.xml`

- 增加 `org.xerial:sqlite-jdbc`。
- 增加 `maven-shade-plugin`，保留 main class。

### `src/main/java/local/token/meter/TokenMeterApp.java`

- 增加 CLI 参数：
  - `--ingest`
  - `--sessions-dir=<path>`
  - `--db=<path>`
  - `--timezone=<iana-zone>`
- 增加 SQLite schema 初始化。
- 增加 `source_files` checkpoint upsert。
- 增加 `usage_events` insert with duplicate handling。
- 增加 ingestion JSON 输出。
- 修改 `ReportService` 从 SQLite 查询 usage events。

### `scripts/P2-2026-04-30-smoke-test.ps1`

- 使用临时 fixture 目录。
- 构造脱敏 JSONL。
- 执行首次 ingestion、重复 ingestion、report 查询。
- 不使用递归删除。

### 文档

- 更新 ingestion contract：变化文件从头重放以保证 delta 正确。
- 更新 design：说明 checkpoint 与 event_key 的关系。
- P2 完成后更新 review 和 acceptance。

## 验收命令

推荐在用户真实 Windows 终端执行：

```powershell
mvn -DskipTests clean package
powershell -ExecutionPolicy Bypass -File scripts\P2-2026-04-30-smoke-test.ps1
```

Codex 沙箱如果无法执行 Maven 或 Java 进程，应记录为沙箱限制，以用户真实终端结果为准。

## 成功标准

- `--ingest` 创建 SQLite 文件和三张 contract 表。
- 首次 ingestion 插入 usage delta。
- 第二次 ingestion 不重复插入。
- `/api/report?days=30` 返回 P1 contract 字段。
- SQLite 和 report 中不包含 prompt/response 正文。
- 不写 `.codex`。
