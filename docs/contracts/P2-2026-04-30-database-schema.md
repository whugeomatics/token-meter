# P2 Database Schema Contract

## 目标

定义 P2 SQLite 持久化所需的最小 schema。P2 只持久化 Codex usage metadata，不存 prompt 或 response 正文。

## 数据库位置

默认位置从 2026-04-30 mac 修复起调整为月分片目录：

```text
%USERPROFILE%\.token-meter\sqlite\
```

默认分片文件名：

```text
token-meter-YYYY-MM.sqlite
```

允许通过环境变量覆盖：

```text
TOKEN_METER_DB
```

兼容规则：

- `TOKEN_METER_DB` 或 `--db` 指向 `.sqlite` / `.db` 文件时，继续使用单 SQLite 文件。
- `TOKEN_METER_DB` 或 `--db` 指向目录时，按月写入该目录下的 `token-meter-YYYY-MM.sqlite`。
- `/api/report` 查询跨月范围时，读取查询范围覆盖的所有月分片。
- Codex session 文件优先按路径中的 `YYYY/MM/DD` 归属月份分片；无法识别时才回退到文件修改时间。

P2 不写入 `%USERPROFILE%\.codex`。

## SQL 管理

P2 起，SQLite DDL 和 DML 必须集中在 SQL 文件中：

```text
src/main/resources/db/schema-v1.sql
```

Java 代码通过 JDBC 执行该文件中的命名 SQL 语句，不得在 repository 之外内联 SQL。后续 schema 变更应新增版本化 SQL 文件，例如：

```text
src/main/resources/db/schema-v2.sql
```

SQLite 连接统一使用 `org.xerial:sqlite-jdbc`：

```text
jdbc:sqlite:<db-path>
```

## 表：schema_migrations

用途：记录 schema 版本。

```sql
CREATE TABLE schema_migrations (
  version INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);
```

P2 初始版本：`1`。

## 表：source_files

用途：记录已扫描的 Codex JSONL 文件和 checkpoint。

```sql
CREATE TABLE source_files (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tool TEXT NOT NULL,
  path TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  modified_at TEXT NOT NULL,
  last_line INTEGER NOT NULL,
  last_event_timestamp TEXT,
  file_fingerprint TEXT NOT NULL,
  status TEXT NOT NULL,
  last_error TEXT,
  scanned_at TEXT NOT NULL,
  UNIQUE(tool, path)
);
```

字段说明：

- `tool`: P2 固定为 `codex`。
- `path`: JSONL 文件绝对路径。
- `size_bytes`: 最近一次扫描时的文件大小。
- `modified_at`: 最近一次扫描时的文件修改时间。
- `last_line`: 已成功处理到的行号，从 0 开始表示尚未处理。
- `last_event_timestamp`: 最近一次成功入库的 usage event timestamp。
- `file_fingerprint`: 用于检测文件替换，P2 可由 `path + size + modified_at` 生成。
- `status`: `active`、`missing`、`error`。
- `last_error`: 最近一次解析错误摘要，不包含原始 prompt/response。
- `scanned_at`: 最近一次扫描时间。

## 表：usage_events

用途：持久化从 Codex 累计快照转换后的 delta event。

```sql
CREATE TABLE usage_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  source_file_id INTEGER NOT NULL,
  line_number INTEGER NOT NULL,
  event_key TEXT NOT NULL,
  tool TEXT NOT NULL,
  session_id TEXT NOT NULL,
  model TEXT NOT NULL,
  event_timestamp TEXT NOT NULL,
  local_date TEXT NOT NULL,
  input_tokens INTEGER NOT NULL,
  cached_input_tokens INTEGER NOT NULL,
  output_tokens INTEGER NOT NULL,
  reasoning_output_tokens INTEGER NOT NULL,
  total_tokens INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY(source_file_id) REFERENCES source_files(id),
  UNIQUE(event_key)
);
```

字段说明：

- `event_key`: 去重键，建议由 `tool + session_id + source path + line_number + cumulative total snapshot` 生成。
- `tool`: P2 固定为 `codex`。
- `session_id`: Codex `session_meta.payload.id`，缺失时用文件名 fallback。
- `model`: token 事件自身 model、最近 `turn_context.payload.model` 或 `unknown`。
- `event_timestamp`: 原始 UTC ISO timestamp。
- `local_date`: 按 dashboard timezone 转换后的 `YYYY-MM-DD`。
- token 字段全部为 delta，不存 cumulative snapshot。

## 索引

```sql
CREATE INDEX idx_usage_events_local_date ON usage_events(local_date);
CREATE INDEX idx_usage_events_model ON usage_events(model);
CREATE INDEX idx_usage_events_session ON usage_events(session_id);
CREATE INDEX idx_usage_events_timestamp ON usage_events(event_timestamp);
```

## 隐私约束

P2 禁止新增以下字段：

- prompt。
- response。
- message。
- content。
- user input。
- assistant output。
- 源码片段。

如果后续阶段确实需要调试原始事件，只能增加短期本地 debug 开关，并且默认关闭；P2 不做。

## 兼容要求

P2 的 `/api/report` 统计结果必须与 P1 contract 保持兼容：

- `range`
- `summary`
- `daily`
- `models`
- `sessions`

P2 可以从 SQLite 查询这些结果，但不能改变字段含义。
