# Token Meter 配置参考

本文只记录当前代码实际读取的配置，并按使用方分为：

- `token-meter-app`：本地 dashboard、SQLite、Local/Team report、admin/device token、手动本地采集。
- `token-meter-collector`：teammate 端采集并上传 Codex 与 Claude Code usage。
- 共用解析：两者都使用 `AppConfig` 解析 CLI 参数和系统环境变量，但 collector 会先额外合并 `collector.env`。

## 优先级

`token-meter-app` 使用：

```text
非空 CLI 参数 > 非空系统环境变量 > 默认值
```

`token-meter-collector` 使用：

```text
非空 CLI 参数 > 非空 collector.env 配置 > 非空系统环境变量 > 默认值
```

空值不会覆盖后面的配置。例如 `--server-url=` 会被视为没有配置，collector 仍会继续读取 `~/.token-meter/collector.env`
或系统环境变量里的 `TOKEN_METER_SERVER_URL`。

## App 配置

这些配置由 `token-meter-app` 使用。

| 用途                      | CLI 参数                                | 系统环境变量                                      | 默认值                               | 说明                                               |
|-------------------------|---------------------------------------|---------------------------------------------|-----------------------------------|--------------------------------------------------|
| HTTP 端口                 | `--port=18080`                        | `PORT`                                      | `18080`                           | dashboard 和 API 监听端口                             |
| 监听地址                    | `--bind=127.0.0.1`                    | `TOKEN_METER_BIND`                          | `127.0.0.1`                       | 需要局域网访问时设为 `0.0.0.0`                             |
| SQLite 目录               | `--db=<path>`                         | `TOKEN_METER_DB`                            | `<user.home>/.token-meter/sqlite` | Local usage、Team registry、Team event DB 都从这个目录派生 |
| 时区                      | `--timezone=Asia/Shanghai`            | `DASHBOARD_TIMEZONE`                        | Codex 日志时区，否则系统时区                 | 用于按日、周、月聚合，以及 collector `upload_time` 输出     |
| Codex sessions 目录       | `--sessions-dir=<path>`               | `CODEX_SESSIONS_DIR`                        | `<user.home>/.codex/sessions`     | Local Codex 采集读取这里                               |
| Claude Code projects 目录 | `--claude-projects-dir=<path>`        | `TOKEN_METER_CLAUDE_PROJECTS_DIR`           | `<user.home>/.claude/projects`    | Local Claude Code 采集读取这里                         |
| 本地后台采集周期                | `--local-ingest-interval-seconds=300` | `TOKEN_METER_LOCAL_INGEST_INTERVAL_SECONDS` | `300`                             | 单位秒；设为 `0` 表示禁用后台采集                              |
| Admin API token         | `--admin-token=<token>`               | `TOKEN_METER_ADMIN_TOKEN`                   | 未启用                               | 传给 dashboard server，用于 admin 接口鉴权                |

当前 Local 页面/API 只查询 SQLite。页面加载不会触发本地采集；本地数据由 app 内部后台采集循环刷新。

示例：

```powershell
java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --port=18080 --local-ingest-interval-seconds=600
```

## App CLI 模式

这些是 `token-meter-app` 的命令行模式开关。

| 用途                        | CLI 参数                      | 说明                                                   |
|---------------------------|-----------------------------|------------------------------------------------------|
| 启动 dashboard              | 不传模式开关                      | 默认行为                                                 |
| 手动执行一次 Local 采集           | `--ingest`                  | 写入本地 SQLite 后退出                                      |
| 输出 Local report JSON      | `--report`                  | 查询 Local SQLite 后退出                                  |
| 输出 Team report JSON       | `--team-report`             | 查询 Team SQLite 后退出                                   |
| 创建随机 device token         | `--create-device-token`     | 需要同时传 `team-id`、`user-id`、`device-id`                |
| 注册指定 device token         | `--register-device-token`   | 需要同时传 `device-token`、`team-id`、`user-id`、`device-id` |
| 从文件导入 Team ingest payload | `--team-ingest-file=<path>` | app 专用调试/导入入口；需要 `device-token`                      |

`token-meter-app` 不负责 teammate 采集。传 `--collect-team` 会直接报错，因为该模式属于 `token-meter-collector`。

## App Report 参数

这些参数会进入 `reportQuery`，供 `--report`、`--team-report` 和对应 HTTP report 逻辑使用。

| 用途      | CLI 参数                                              | 说明                      |
|---------|-----------------------------------------------------|-------------------------|
| 自然周期    | `--period=day` / `--period=week` / `--period=month` | 当前 dashboard 使用这组参数     |
| 上一周期对比  | `--compare=previous`                                | 与当前自然周期对比               |
| 兼容滚动天数  | `--days=7`                                          | 旧入口，仍保留                 |
| 兼容日历月   | `--month=2026-05`                                   | 旧入口，仍保留                 |
| Team 过滤 | `--team-id=<team>`                                  | Team report 使用          |
| User 过滤 | `--user-id=<user>`                                  | Team report 使用          |
| Tool 过滤 | `--tool=codex` / `--tool=claude-code`               | Local 和 Team report 都支持 |

示例：

```powershell
java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --report --period=day --compare=previous --tool=claude-code
```

## App Device Token 参数

这些参数由 app 的 device token 创建、注册、Team ingest 文件导入使用。

| 用途           | CLI 参数                   | 系统环境变量                     | 说明                                     |
|--------------|--------------------------|----------------------------|----------------------------------------|
| Device token | `--device-token=<token>` | `TOKEN_METER_DEVICE_TOKEN` | 注册已有 token 或导入 Team ingest payload 时使用 |
| Team id      | `--team-id=<team>`       | `TOKEN_METER_TEAM_ID`      | 创建/注册 token 时使用                        |
| User id      | `--user-id=<user>`       | `TOKEN_METER_USER_ID`      | 创建/注册 token 时使用                        |
| Device id    | `--device-id=<device>`   | `TOKEN_METER_DEVICE_ID`    | 创建/注册 token 时使用                        |
| Device name  | `--device-name=<name>`   | `TOKEN_METER_DEVICE_NAME`  | 可选；默认等于 `device-id`                    |

示例：

```powershell
java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --create-device-token --team-id=default --user-id=alice --device-id=alice-laptop
```

## Collector 配置来源

`token-meter-collector` 启动时会先读取 env 文件，再交给 `AppConfig` 解析。

| 用途                 | CLI 参数                        | env 文件或系统环境变量               | 默认值                                      | 说明                             |
|--------------------|-------------------------------|-----------------------------|------------------------------------------|--------------------------------|
| collector env 文件路径 | `--collector-env-file=<path>` | `TOKEN_METER_COLLECTOR_ENV` | `<user.home>/.token-meter/collector.env` | Windows Git Bash 同样使用 Unix 脚本，兼容旧 `collector.env.cmd` |
| collector 本地状态库 | `--collector-state-db=<path>` | `TOKEN_METER_COLLECTOR_STATE_DB` | `<user.home>/.token-meter/sqlite/token-meter-collector-state.sqlite` | collector 端轻量 SQLite，只保存待上报/补传所需 usage event |

Windows 上推荐通过 Git Bash 执行同一个 `run-collector.sh`。如果 `%USERPROFILE%\.token-meter\collector.env` 不存在，
但 `%USERPROFILE%\.token-meter\collector.env.cmd` 存在，会读取 `collector.env.cmd`。

## Collector 采集与上传配置

这些配置由 `token-meter-collector` 使用，适合写入 `~/.token-meter/collector.env`。

| 用途                  | CLI 参数                           | collector.env 或系统环境变量      | 默认值                           | 说明                        |
|---------------------|----------------------------------|----------------------------|-------------------------------|---------------------------|
| dashboard server 地址 | `--server-url=http://host:18080` | `TOKEN_METER_SERVER_URL`   | 无                             | 必填；collector 上传到这里        |
| Device token        | `--device-token=<token>`         | `TOKEN_METER_DEVICE_TOKEN` | 无                             | 必填；由 app admin/token 流程生成 |
| User id             | `--user-id=<user>`               | `TOKEN_METER_USER_ID`      | 无                             | 写入 Team usage event       |
| Device id           | `--device-id=<device>`           | `TOKEN_METER_DEVICE_ID`    | 无                             | 写入 Team usage event       |
| 上传 batch size       | `--batch-size=500`               | `TOKEN_METER_BATCH_SIZE`   | `500`                         | 代码会限制在 `1..500`           |
| 采集回看天数              | `--days=35`                      | `TOKEN_METER_DAYS`         | `35`                          | 上传最近 N 天 usage，默认覆盖当前月常见回填场景 |
| Codex sessions 目录   | `--sessions-dir=<path>`          | `CODEX_SESSIONS_DIR`       | `<user.home>/.codex/sessions` | collector 采集 Codex 时读取这里  |

典型 `collector.env`：

```env
TOKEN_METER_SERVER_URL=http://127.0.0.1:18080
TOKEN_METER_DEVICE_TOKEN=replace-with-admin-generated-token
TOKEN_METER_USER_ID=alice
TOKEN_METER_DEVICE_ID=alice-laptop
TOKEN_METER_DAYS=7
```

`127.0.0.1` 只适合 collector 和 dashboard 在同一台机器上运行。teammate 机器需要填写 dashboard 所在机器的局域网地址，并且
app 需要用 `--bind=0.0.0.0` 或具体局域网 IP 启动。

## Collector Claude Code 配置

collector 默认一次运行同时采集 Codex 和 Claude Code。`--collect-claude-code` 仍存在，但只表示只上传 Claude Code 的旧兼容入口。

| 用途                  | CLI 参数                            | collector.env 或系统环境变量             | 默认值                            | 说明                                   |
|---------------------|-----------------------------------|-----------------------------------|--------------------------------|--------------------------------------|
| Claude source 模式    | `--claude-source=local`           | `TOKEN_METER_CLAUDE_SOURCE`       | `local`                        | 可选值：`local`、`otel`、`hook`、`fixture`  |
| Claude projects 目录  | `--claude-projects-dir=<path>`    | `TOKEN_METER_CLAUDE_PROJECTS_DIR` | `<user.home>/.claude/projects` | `claude-source=local` 时使用            |
| Claude OTEL 输入      | `--claude-otel-input=<path>`      | `TOKEN_METER_CLAUDE_OTEL_INPUT`   | 无                              | `claude-source=otel` 或 `fixture` 时使用 |
| Claude hook 输入      | `--claude-hook-input=<path>`      | `TOKEN_METER_CLAUDE_HOOK_INPUT`   | 无                              | `claude-source=hook` 时使用             |
| Claude usage 文件兼容入口 | `--claude-code-usage-file=<path>` | `CLAUDE_CODE_USAGE_FILE`          | 无                              | 当 OTEL/hook 专用输入未配置时作为 fallback      |

## App 与 Collector 共用但用途不同的配置

| 配置                                                          | app 用途                         | collector 用途            |
|-------------------------------------------------------------|--------------------------------|-------------------------|
| `--sessions-dir` / `CODEX_SESSIONS_DIR`                     | Local Codex 采集                 | Teammate Codex 采集       |
| `--timezone` / `DASHBOARD_TIMEZONE`                         | Local/Team report 聚合和 Local 采集 | Teammate 采集日期窗口和事件日期    |
| `--claude-projects-dir` / `TOKEN_METER_CLAUDE_PROJECTS_DIR` | Local Claude Code 采集           | Teammate Claude Code 采集 |
| `--device-token` / `TOKEN_METER_DEVICE_TOKEN`               | app 注册 token 或导入 Team payload  | collector 上传鉴权          |
| `--user-id` / `TOKEN_METER_USER_ID`                         | app 创建/注册 token                | collector 标记事件归属用户      |
| `--device-id` / `TOKEN_METER_DEVICE_ID`                     | app 创建/注册 token                | collector 标记事件归属设备      |

## 常见场景

| 场景                          | 建议配置                                                                                                                                  |
|-----------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| Local 页面启动快，且可以接受本地数据有几分钟延迟 | 调大 `TOKEN_METER_LOCAL_INGEST_INTERVAL_SECONDS`，例如 `600`                                                                               |
| 完全不想 app 后台采集               | 设置 `TOKEN_METER_LOCAL_INGEST_INTERVAL_SECONDS=0`，需要时手动运行 `--ingest`                                                                   |
| 本地 SQLite 想放到其他磁盘           | 设置 `TOKEN_METER_DB` 或启动 app 时传 `--db=<path>`                                                                                          |
| Codex 日志不在默认目录              | 设置 `CODEX_SESSIONS_DIR` 或传 `--sessions-dir=<path>`                                                                                    |
| Claude Code 日志不在默认目录        | 设置 `TOKEN_METER_CLAUDE_PROJECTS_DIR` 或传 `--claude-projects-dir=<path>`                                                                |
| 给 teammate 固定 collector 配置  | 把 `TOKEN_METER_SERVER_URL`、`TOKEN_METER_DEVICE_TOKEN`、`TOKEN_METER_USER_ID`、`TOKEN_METER_DEVICE_ID` 写入 `~/.token-meter/collector.env` |
| teammate 机器访问 dashboard     | app 启动时传 `--bind=0.0.0.0`，collector 的 `TOKEN_METER_SERVER_URL` 填 dashboard 机器局域网地址                                                    |
