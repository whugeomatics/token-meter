# P5 Unified CLI Usage Metrics Acceptance

> 2026-06-15 review update: P5 review follow-up 已处理并验证。详见 `docs/reviews/P5-2026-06-15-unified-cli-usage-metrics-review.md`。

## 目标

定义 P5 unified CLI usage metrics 的验收标准。P5 通过后，Codex 和 Claude Code 的 canonical event 字段、report 派生指标和 dashboard 展示语义一致，并且 schema 可以支撑未来 CLI source mapping。

## 测试环境

- OS：Windows 和 macOS/Linux 分别保留 collector 路径约束。
- 时区：Asia/Shanghai。
- Java 版本：Java 17 target。
- 构建命令：`cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package`。
- Collector 输入：Codex fixture、Claude Code local JSONL fixture、Claude Code telemetry fixture。
- Server 存储：临时 SQLite 目录或文件。
- 验收日期：2026-05-24。

## 验收项

### 1. 文档和阶段边界

结果：通过（文档检查）

检查：

- P5 README、contract、integration mapping、design、tasks、acceptance 文档存在。
- 文档明确 P5 统一 Codex 和 Claude Code 的统计模型。
- 文档明确 P5 schema 支撑未来 CLI source mapping，但不接入新 CLI。
- 文档明确 P5 不实现 Cursor、Gemini CLI、本地网关、provider 自动路由、费用估算、预算或告警。
- 文档明确事实字段和派生指标边界。

### 2. Canonical Usage Event

结果：通过（单元测试与代码检查）

检查：

- Codex event 能映射到 P5 canonical facts。
- Claude Code event 能映射到 P5 canonical facts。
- `tool`、`source_kind`、`source_quality`、`model`、`session_id`、`timestamp` 和 token 字段含义一致。
- `provider` 如落地，缺失时为 `unknown`，且不作为 P5 dashboard 主维度；本轮未落地 provider 字段。
- 无效零 token usage 不生成 event。

### 3. Report 派生指标

结果：通过（单元测试）

检查：

- `net_input_tokens = max(input_tokens - cached_input_tokens, 0)`。
- `net_total_tokens = net_input_tokens + output_tokens + reasoning_output_tokens`。
- `cache_rate = cached_input_tokens / input_tokens`，输入为 0 时为 0。
- Local `/api/report` 和 Team `/api/team/report` 使用同一公式。
- summary、daily、models、sessions、tools 和 comparison 使用同一公式。

### 4. Codex 与 Claude Code 映射

结果：通过（integration mapping 文档与 fixture 单元测试）

检查：

- Codex mapping 文档覆盖 source、event key、fallback、隐私边界。
- Claude Code mapping 文档覆盖 source、event key、fallback、隐私边界。
- Claude Code `input_tokens` 使用 `input_tokens + cache_creation_input_tokens + cache_read_input_tokens`。
- Claude Code `cached_input_tokens` 使用 `cache_creation_input_tokens + cache_read_input_tokens`。
- Codex cache token 缺失时不伪造，使用 0。
- 两种工具缺失 model/session 时 fallback 一致。

### 5. 隐私

结果：通过（隐私 fixture 回归）

检查：

- payload 不包含 prompt、response、raw JSONL、raw API body、raw transcript、tool input 或 tool output。
- DB 不保存 prompt、response、raw JSONL、raw API body、raw transcript、tool input 或 tool output。
- report 不返回 prompt、response、raw JSONL、raw API body、raw transcript、tool input 或 tool output。
- stdout/stderr 和日志不包含 admin token、device token 明文或 token hash。
- 完整本地路径不进入 canonical event、team payload、report、export、stdout 或日志。Local SQLite 的 `source_files.path` 允许保存完整本地路径，仅作为本机内部增量采集索引和问题定位依据。

### 6. 回归

结果：通过（回归测试）

检查：

- P1/P2 Local `/api/report` 兼容字段不被移除。
- P3 Team `/api/team/report` 兼容字段不被移除。
- P4 `tool=codex|claude-code` filter 继续可用。
- P4 `tools` 聚合和 comparison tools 继续可用。
- collector 仍默认同时采集 Codex 和 Claude Code。
- collector jar 仍不包含 dashboard、SQLite、admin 或静态资源。

## 验收命令

```powershell
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd test
node --check token-meter-app\src\main\resources\static\app.js
node --check token-meter-app\src\main\resources\static\admin.js
sh scripts/P3-2026-05-01-package-collector.sh all
sh scripts/P4-2026-05-24-claude-code-team-smoke-test.sh
```

如 Codex 沙箱无法绑定本地 HTTP 端口，应记录为“沙箱执行受限”，不判断为代码失败。

## 本轮已执行

```text
mvn -pl token-meter-collector -am -Dtest=ClaudeCodeUsageSourceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl token-meter-app -am -Dtest=TokenTotalsTest,ClaudeCodeLocalIngestionServiceTest,LocalIngestionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl token-meter-app -am -Dtest=ClaudeCodeLocalIngestionServiceTest,TeamIngestionServiceToolTest,ReportServiceToolTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn test
mvn -DskipTests package
node --check token-meter-app/src/main/resources/static/app.js
node --check token-meter-app/src/main/resources/static/admin.js
sh scripts/P3-2026-05-01-package-collector.sh all
```

结果：

- Java 全量测试通过：app 13 tests，collector 13 tests，0 failures。
- Maven package 通过。
- JS 语法检查通过。
- Collector macOS/Linux 与 Windows 分发包生成通过。

## 2026-06-15 review follow-up 验证

本轮修复：

- Claude Code flat usage 缺失 `total_tokens` 时不再额外相加 `cached_input_tokens`。
- Team ingest 缺失 `total_tokens` 时按 `input_tokens + output_tokens + reasoning_output_tokens` 补齐。
- Team ingest 缺失 `source_kind` / `source_quality` 时写入 `unknown`，兼容旧 payload。
- P5 privacy boundary 明确 Local SQLite `source_files.path` 可保存完整本地路径，仅用于本机内部增量采集和问题定位。
- Codex session 中 `token_count` 早于 `turn_context.model` 时，scanner 使用同文件后续第一条已知 model 回填，减少新增 `model=unknown`。
- Claude Code `toolUseResult.usage` 行缺失 model 时，collector 使用同 session 文件前序或后续的已知 model 回填，避免 subagent/tool-use usage 生成新增 `model=unknown`。

新增测试：

- `ClaudeCodeUsageSourceTest.flatUsageFallbackTotalDoesNotDoubleCountCachedInput`
- `ClaudeCodeUsageSourceTest.backfillsToolUseResultModelFromSameClaudeProjectSession`
- `TeamIngestionServiceToolTest.appliesCanonicalFallbacksForMissingTotalAndSourceMetadata`
- `LocalIngestionServiceTest.scannerBackfillsModelWhenTokenCountAppearsBeforeTurnContext`

已执行：

```text
mvn -pl token-meter-collector -am -Dtest=ClaudeCodeUsageSourceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl token-meter-app -am -Dtest=TeamIngestionServiceToolTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl token-meter-app -am -Dtest=LocalIngestionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn test
```

结果：

- Targeted Claude Code source tests 通过：4 tests，0 failures。
- Targeted Team ingest tests 通过：5 tests，0 failures。
- Java 全量测试通过：app 26 tests，collector 18 tests，0 failures。
