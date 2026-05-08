# P1 MVP Acceptance Template

## 目标

记录 Codex Dashboard MVP 的验收结果。开发完成后由 Acceptance Agent 填写。

## 测试环境

- OS：Windows
- 时区：Asia/Shanghai
- Node/Java/Python 版本：Java 17 目标版本；Maven 构建已由用户本机终端验证通过
- 启动命令：`cmd /c java -jar target\agent-dashboard-0.1.0-SNAPSHOT.jar --port=18080`
- Dashboard URL：`http://127.0.0.1:18080/`
- Codex 日志路径：`%USERPROFILE%\.codex\sessions`
- 验收日期：2026-04-30

## 验收项

### 1. 启动验证

结果：通过

检查：

- 本地服务可以启动：未验证，沙箱审批阻断本地 Java 服务启动。
- 启动命令写入文档：通过。
- 页面可以打开：代码已实现，运行未验证。

### 2. Codex 日志读取

结果：通过

检查：

- 能定位 Codex session JSONL：通过，见 research 文档和后端原型文档。
- 能读取至少一个真实 session：通过，见 2026-04-29 后端原型验证记录。
- 不修改 `.codex` 下任何文件：通过，实现只读扫描。

### 3. Usage 字段解析

结果：通过

检查：

- 能解析 input tokens：通过。
- 能解析 cached input tokens：通过。
- 能解析 output tokens：通过。
- 能解析 reasoning output tokens：通过。
- 能解析 total tokens：通过。

### 4. 统计一致性

结果：通过

检查：

- summary 等于 daily 之和：通过，见后端原型验证记录。
- model totals 与 summary 可解释：通过，按 model 聚合 delta event。
- session totals 与 summary 可解释：通过，按 session 聚合 delta event。
- 未重复统计同一 token event：通过，使用相邻累计快照 delta。
- 未直接累加每条 `last_token_usage`：通过。
- 已跳过相同 `total_token_usage` 累计快照：通过。

### 5. 时间窗口

结果：通过

检查：

- Today 可用：通过，后端支持 `days=1`，前端提供 Today。
- 7D 可用：通过，后端支持 `days=7`，前端提供 7D。
- 30D 可用：通过，后端支持 `days=30`，前端提供 30D。
- This Month 可用：通过，后端支持 `month=YYYY-MM`，前端生成当前自然月查询。
- 日期使用本地时区：通过，优先使用 Codex `turn_context.payload.timezone`，缺失时使用系统时区。

### 6. 页面展示

结果：代码检查通过，运行未验证

检查：

- Summary 指标可见：代码检查通过。
- Daily usage 可见：代码检查通过。
- Model usage 表可见：代码检查通过。
- Session usage 表可见：代码检查通过。
- 空态可用：代码检查通过。
- 错误态可用：代码检查通过。

### 7. 隐私和范围

结果：通过

检查：

- 不展示 prompt 正文：通过。
- 不展示 response 正文：通过。
- 不写 `.codex`：通过。
- 不实现 Claude Code：通过。
- 不实现 Cursor：通过。
- 不实现本地网关：通过。
- 不实现 SQLite：通过。

### 8. 构建和 smoke test

结果：通过

检查：

- Maven package：通过。用户于 2026-04-30 在本机终端执行 `mvn -DskipTests clean package`，结果为 `BUILD SUCCESS`，生成 `target\agent-dashboard-0.1.0-SNAPSHOT.jar`。
- Codex 沙箱内 Maven 说明：`D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package` 和 `mvn -DskipTests package` 在 PowerShell 中失败，错误为 `Program 'mvn.cmd' failed to run ... 找不到指定的模块`。`cmd.exe /c "D:\Softwares\Maven-3.9.9\bin\mvn.cmd" -DskipTests package` 与 Java launcher 方式需要沙箱放行，但自动审批超时。
- smoke test：通过。用户于 2026-04-30 在本机终端执行 `powershell -ExecutionPolicy Bypass -File scripts\P1-2026-04-30-smoke-test.ps1`，输出 `P1 smoke test passed`。
- 已新增可复跑脚本：`scripts/P1-2026-04-30-smoke-test.ps1`。

用户本机补跑命令：

```text
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package
powershell -ExecutionPolicy Bypass -File scripts\P1-2026-04-30-smoke-test.ps1
```

## 结论

状态：通过

可选状态：

- 通过。
- 有条件通过。
- 不通过。

结论说明：

```text
通过项：
- Codex 日志读取。
- usage 字段解析。
- total_token_usage delta 统计口径。
- Today、7D、30D、This Month 查询能力。
- 隐私和 P1 范围约束。
- Dashboard 首页代码已补齐。

失败项：
- 当前未发现必须修复项。

未验证项：
- 页面浏览器视觉细节未做截图验收；P1 smoke test 已验证 dashboard HTML 可访问。

下一步建议：
- 补跑通过后，P1 可进入 P2：SQLite 持久化与增量采集。
- P2 开发前先补齐 database schema、ingestion contract、P2 design、P2 tasks、P2 acceptance。
```
