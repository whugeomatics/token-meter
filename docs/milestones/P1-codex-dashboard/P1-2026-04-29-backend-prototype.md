# P1 Backend Prototype

## 已实现范围

当前后端原型是 Codex Dashboard MVP 的最小可运行链路：

- 只读扫描 `%USERPROFILE%\.codex\sessions`。
- 解析 `session_meta`、`turn_context`、`event_msg.payload.type=token_count`。
- 使用 `payload.info.total_token_usage` 相邻累计快照 delta 聚合 token。
- 跳过相同累计快照，避免重复计数。
- 按 date、model、session 聚合。
- 提供 `/api/report`。
- 提供 `--report` 命令行自检模式。

未实现：

- 前端 dashboard。
- SQLite。
- Claude Code。
- Cursor。
- 本地模型网关。

## 构建

按项目约定使用：

```text
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package
```

## 命令行自检

```text
cmd /c java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --report --days=7
cmd /c java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --report --days=1
cmd /c java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --report --month=2026-04
```

## 启动 API

```text
cmd /c java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --port=18080
```

启动后访问：

```text
http://127.0.0.1:18080/api/report?days=7
http://127.0.0.1:18080/api/report?days=30
http://127.0.0.1:18080/api/report?month=2026-04
http://127.0.0.1:18080/
```

## 环境变量

- `CODEX_SESSIONS_DIR`: 覆盖 Codex session JSONL 目录。
- `DASHBOARD_TIMEZONE`: 覆盖统计时区，例如 `Asia/Shanghai`。
- `PORT`: 覆盖默认端口，默认 `18080`。

## 本机验证结果

验证日期：2026-04-29。

已通过：

- Maven package 成功。
- `--report --days=7` 返回 contract JSON。
- `summary.total_tokens` 等于 `daily[].total_tokens` 之和。
- `range.timezone` 可从 Codex 日志检测为 `Asia/Shanghai`。
- `--report --days=1` 可用。
- `--report --month=2026-04` 可用。

当前环境限制：

- 后台启动 HTTP 服务时，桌面沙箱权限审查超时。
- 命令行自检已验证核心统计逻辑，HTTP 启动命令保留给交互环境执行。
