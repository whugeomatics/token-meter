# P3 Admin Usage Guide


## 目标

本文档说明管理员如何给 teammate 分配 Codex collector 上报 token，以及为什么 Team 页面可能仍然是 0。

P3 的关键边界：

- 管理员分配 token 只建立 `token -> team/user/device` 绑定。
- Team 页面只统计已经上报到服务端的 usage event。
- 成员电脑必须使用分配到的 token 运行 collector，上报成功后 Team 页面才会出现数据。
- Device Token Bindings 列表只展示 mask 后的 token，管理员可以点击 token 单元格右上角的复制图标复制完整 token。
- 为支持后续复制，服务端 registry 会保存可恢复 token；不要把 registry SQLite 发给别人。

## 角色

管理员机器：

- 运行 token-meter 服务端。
- 打开 admin 页面分配 teammate token。
- 打开 Team Tab 查看团队用量。

成员机器：

- 运行 Codex。
- 本地存在 `~/.codex/sessions`。
- 使用管理员分配的 device token 运行 collector。

## 1. 管理员启动服务端

在管理员机器上构建：

```sh
mvn -DskipTests package
```

启动 dashboard，并开启 admin 页面：

```sh
java -jar token-meter-app/target/token-meter-app-0.1.0-SNAPSHOT.jar \
  --db="$HOME/.token-meter/sqlite" \
  --admin-token="replace-with-admin-token"
```

说明：

- `--admin-token` 是管理员登录凭证。
- 不配置 `--admin-token` 或 `TOKEN_METER_ADMIN_TOKEN` 时，admin 页面关闭。
- admin token 不会写入数据库、日志、HTML 或 JS。
- SQLite 默认在 `~/.token-meter/sqlite` 下按月分片保存。

也可以用环境变量启动：

```sh
export TOKEN_METER_ADMIN_TOKEN="replace-with-admin-token"
java -jar token-meter-app/target/token-meter-app-0.1.0-SNAPSHOT.jar \
  --db="$HOME/.token-meter/sqlite"
```

启动后，本机访问：

```text
http://127.0.0.1:18080/
http://127.0.0.1:18080/admin-login.html
```

## 2. 管理员登录 admin 页面

打开：

```text
http://127.0.0.1:18080/admin-login.html
```

输入启动服务时配置的 admin token。

登录成功后进入：

```text
http://127.0.0.1:18080/admin.html
```

未登录时直接访问 `/admin.html` 会返回未授权。

## 3. 给 teammate 分配 device token

在 `/admin.html` 的 `Create Device Token` 表单填写：

```text
Team ID:      团队标识，例如 open-space
User ID:      成员标识，例如 zhangsan
Device ID:    成员设备标识，例如 zhangsan-macbook
Device Name:  展示名称，例如 Zhangsan MacBook Pro
```

点击 `Create Token` 后页面会展示一个 device token。

重要规则：

- 管理员需要把这个 token 发给对应 teammate。
- `Device Token Bindings` 列表会显示 mask 后的 token，例如 `abc123****wxyz`，完整 token 可通过 token 单元格右上角的复制图标复制。
- 点击 `Copy` 可以复制完整 token，但页面不会直接展开完整 token。
- token 不允许编辑；绑定错了应删除后重新创建。
- 点击 `Delete` 会删除这个 token binding，删除后该 token 不能继续上报。
- token 列表不展示 token hash。
- registry SQLite 保存可恢复 token，管理员机器应按敏感数据保护。

## 4. 成员机器运行 collector 上报

成员拿到 token 后，在自己的 Mac 上运行 collector。

前提：

- 成员机器已经运行过 Codex 或 Claude Code。
- Codex 数据默认来自 `~/.codex/sessions`；Claude Code 数据默认来自 `~/.claude/projects/**/*.jsonl`。
- `server-url` 必须是成员机器能访问到的 dashboard 服务地址。

关键说明：

- collector 是独立采集模式，不启动 HTTP server。
- collector 不占用 `10080`、`18080` 或任何 dashboard server 端口。
- collector 不需要 `--port`。
- collector 只主动 POST 到 server，不接受入站请求；server 地址可以来自 `--server-url`、`~/.token-meter/collector.env` 或系统环境变量。
- collector 默认同时读取 `~/.codex/sessions` 和 `~/.claude/projects`，不需要显式传 `--sessions-dir` 或 Claude 专用开关。
- collector 不依赖本地数据库，也不会创建 checkpoint SQLite。

推荐示例：

```sh
java -jar token-meter-collector/target/token-meter-collector-0.1.0-SNAPSHOT.jar \
  --collect-team \
  --server-url="http://127.0.0.1:18080" \
  --device-token="teammate-device-token" \
  --user-id="zhangsan" \
  --device-id="zhangsan-macbook" \
  --days=30
```

推荐把 admin 页面生成的 teammate `.env` 保存到客户端本机：

```text
~/.token-meter/collector.env
```

collector 读取配置的优先级固定为：

```text
CLI 参数 > ~/.token-meter/collector.env > 系统环境变量
```

因此显式传入的 `--server-url`、`--device-token`、`--user-id`、`--device-id`、`--days` 会覆盖 env 文件；没有传 CLI 参数时优先使用客户端 env 文件；env 文件也缺失时才使用系统环境变量。server 端不需要这个 env 文件。

字段说明：

- `--collect-team`: 运行 collector，上报团队 usage event。
- `--server-url`: dashboard 服务端地址。
- `--device-token`: 管理员分配给该成员设备的 token。
- `--user-id`: 仅做一致性校验，必须和 token 绑定的 `user_id` 一致。
- `--device-id`: 仅做一致性校验，必须和 token 绑定的 `device_id` 一致。
- `--days`: 本次扫描和上报最近多少天。

可选参数：

- `--sessions-dir`: 覆盖 Codex sessions 目录；默认 `~/.codex/sessions`。
- `--claude-projects-dir`: 覆盖 Claude Code projects 目录；默认 `~/.claude/projects`。
- `--collect-claude-code`: 旧脚本兼容入口。正常 team collection 不需要该开关。

如果在同一台电脑上同时充当管理员和 teammate：

- server 可以继续运行在 `18080` 或你选择的 `10080`。
- collector 不会监听端口，所以不会和 server 端口冲突。
- collector 的 server URL 指向这台 server，例如 `http://127.0.0.1:10080`，可写在 CLI 或 `~/.token-meter/collector.env`。
- collector 不写本地数据库；重复上报由服务端按 `team_id + user_id + device_id + event_key` 幂等去重。

示例：

```sh
java -jar token-meter-collector/target/token-meter-collector-0.1.0-SNAPSHOT.jar \
  --collect-team \
  --server-url="http://127.0.0.1:10080" \
  --device-token="teammate-device-token" \
  --user-id="zhangsan" \
  --device-id="zhangsan-macbook" \
  --days=30
```

也可以使用封装脚本。推荐先保存 admin 页面生成的 teammate `.env`：

```sh
mkdir -p ~/.token-meter
# 将 teammate .env 保存为 ~/.token-meter/collector.env
sh scripts/P3-2026-05-01-run-collector.sh
```

脚本如果能从 env 文件或环境变量解析到 `TOKEN_METER_SERVER_URL`，会先检查 `${TOKEN_METER_SERVER_URL}/health`。如果 dashboard 实际启动在 `18080`，但配置仍然是 `10080`，collector 会直接提示服务不可达。

collector 成功时会输出类似：

```json
{"status":"ok","events":12,"batches":1,"accepted":12,"duplicate":0,"rejected":0,"upload_time":"2026-05-01T12:40:00+08:00","client_user_id":"zhangsan","client_device_id":"zhangsan-macbook","server_url":"http://127.0.0.1:18080","start_date":"2026-04-02","end_date":"2026-05-01"}
```

如果重复运行，可能看到：

```json
{"status":"ok","events":12,"batches":1,"accepted":0,"duplicate":12,"rejected":0,"upload_time":"2026-05-01T12:40:00+08:00","client_user_id":"zhangsan","client_device_id":"zhangsan-macbook","server_url":"http://127.0.0.1:18080","start_date":"2026-04-02","end_date":"2026-05-01"}
```

这是正常的，表示服务端通过 `event_key` 去重，没有重复统计。

collector 输出会包含 `upload_time`、`client_user_id`、`client_device_id`、`server_url`、`start_date`、`end_date` 便于排查；`upload_time` 按配置时区输出；不会输出 device token。

## 5. 把 collector 安装成 mac 服务

P3 推荐用 mac `launchd` 做周期上报，而不是让 Java collector 常驻。

原因：

- collector 是一次性任务，运行完就退出。
- `launchd` 周期性拉起 collector，默认每 300 秒执行一次。
- Codex 关闭时也没关系，下次运行会扫描本地 sessions 并补报。
- 重复上报由服务端 `event_key` 去重。
- collector 不监听端口，不会和 dashboard server 端口冲突。

### 5.1 管理员打包 collector

teammate 机器不需要完整项目源码。管理员机器先构建 jar：

```sh
mvn -DskipTests package
```

然后生成统一的 Unix/Git Bash collector 分发目录：

```sh
sh scripts/P3-2026-05-01-package-collector.sh all
```

发送该目录给 teammate。Windows teammate 通过 Git Bash 执行同一套脚本：

```text
dist/token-meter-collector-unix/
  README.md
  token-meter-collector.jar
  run-collector.sh
  run-collector-service.sh
  install-collector-service.sh
  uninstall-collector-service.sh
```

### 5.2 teammate 安装服务

teammate 解压或复制目录后，在这个目录里执行：

```sh
mkdir -p ~/.token-meter
# 将 admin 页面生成的 teammate .env 保存为 ~/.token-meter/collector.env
sh install-collector-service.sh
```

`TOKEN_METER_SERVER_URL` 必须是 teammate 电脑能访问到的 dashboard server 地址。跨机器时不要写 `127.0.0.1`，除非 server 也在 teammate 自己电脑上。

可选配置：

```sh
export TOKEN_METER_COLLECTOR_INTERVAL_SECONDS=300
export TOKEN_METER_DAYS=30
export TOKEN_METER_JAVA="/absolute/path/to/java"
```

分发包里的安装脚本默认会从脚本同目录寻找 `token-meter-collector.jar`。如果 teammate 的 Java 不在标准 PATH 中，脚本会优先使用 `JAVA_HOME/bin/java`，也可以显式传 `TOKEN_METER_JAVA`。

如果安装时出现：

```text
Could not kickstart service "local.token.meter.collector": 1: Operation not permitted
```

这通常表示 macOS 拒绝了立即触发，不等于 plist 和配置没有写入。脚本会保留安装结果，服务会按 `StartInterval` 周期运行；也可以在 teammate 终端手动执行：

```sh
launchctl kickstart -k "gui/$(id -u)/local.token.meter.collector"
```

安装后文件位置：

```text
~/Library/LaunchAgents/local.token.meter.collector.plist
~/.token-meter/collector.env
~/.token-meter/logs/collector.out.log
~/.token-meter/logs/collector.err.log
```

安全说明：

- `~/.token-meter/collector.env` 会保存 teammate 的 device token。
- 安装脚本会把该文件权限设为 `600`。
- 不要把 `collector.env` 放进仓库或发给别人。

查看服务：

```sh
launchctl print "gui/$(id -u)/local.token.meter.collector"
```

立即触发一次：

```sh
launchctl kickstart -k "gui/$(id -u)/local.token.meter.collector"
```

卸载服务：

```sh
sh uninstall-collector-service.sh
```

卸载脚本不会删除 `~/.token-meter/collector.env`。如果要清除本机 token 配置，需要手动删除：

```sh
rm ~/.token-meter/collector.env
```

## 6. 跨机器上报注意事项

当前服务端默认只绑定 `127.0.0.1`，这是安全默认值。需要跨机器访问时，启动 dashboard server 时显式指定 `--bind=0.0.0.0`，或通过环境变量 `TOKEN_METER_BIND=0.0.0.0` 配置。

因此：

- 如果 collector 和 dashboard 在同一台机器，`--server-url="http://127.0.0.1:18080"` 可用。
- 如果 teammate 在另一台机器，`127.0.0.1` 指的是 teammate 自己的电脑，不是管理员机器。
- 跨机器上报时，需要让 teammate 能访问管理员机器上的服务地址。
- dashboard 需要监听非 loopback 地址，例如 `--bind=0.0.0.0`；collector 的 `--server-url` 需要填写管理员机器的局域网 IP 或可解析主机名。

可选方式：

- 使用内网反向代理。
- 使用 SSH tunnel。
- 使用 Tailscale、ZeroTier 或类似内网组网工具。

在服务未暴露给成员机器前，Team 页面会一直是 0，因为成员数据没有进入中央服务端。

## 7. 为什么 Team 页面还是 0

按下面顺序排查。

### 7.1 只创建 token，没有上报数据

admin 页面创建 token 只建立绑定关系，不会生成 usage event。

需要在成员机器执行：

```sh
java -jar token-meter-collector/target/token-meter-collector-0.1.0-SNAPSHOT.jar \
  --collect-team \
  --server-url="http://<dashboard-server>:18080" \
  --device-token="<device-token>" \
  --user-id="<bound-user-id>" \
  --device-id="<bound-device-id>" \
  --days=30
```

### 7.2 成员机器没有 Codex token usage 记录

P3 只统计 Codex JSONL 中的 `token_count` / `total_token_usage`。

如果 session 没有 token usage 字段，collector 不会伪造 token，也不会用正文估算。

### 7.3 `user_id` 或 `device_id` 和 token 绑定不一致

collector 会把 `--user-id` 和 `--device-id` 作为一致性校验字段。

如果和 admin 页面创建 token 时的绑定不一致，服务端返回 `identity_conflict`，不会写入数据。

### 7.4 server-url 指错

跨机器时不要给 teammate 配：

```text
http://127.0.0.1:18080
```

除非 dashboard 服务也运行在 teammate 自己的机器上。

### 7.4.1 401 unknown device token

如果 collector 输出：

```text
HTTP 401 ... unknown device token
```

优先按下面顺序检查：

1. 回到 `admin.html`，复制当前设备绑定生成的 teammate `.env`。
2. 用复制结果完整覆盖 teammate 本机 `~/.token-meter/collector.env`。
3. 确认 collector 读到的是这个文件；macOS/Linux 默认路径是 `~/.token-meter/collector.env`，也可以用 `TOKEN_METER_COLLECTOR_ENV` 或 `--collector-env-file` 覆盖。
4. 重新启动 collector 或服务任务。已运行的 launchd/Task Scheduler 进程不会自动继承你后来在 shell 里 export 的新 token。
5. 确认 dashboard server 使用的是创建 token 的同一个 `--db` 目录；server 换了 DB 时，registry 里没有旧 token，也会返回 `unknown device token`。

不要把完整 token 写进日志、issue 或聊天记录。排查时只比较 token 长度、前后缀或 hash 摘要即可。

建议权限：

```sh
chmod 600 ~/.token-meter/collector.env
```

collector 配置优先级固定为：

```text
CLI 参数 > ~/.token-meter/collector.env > 系统环境变量
```

### 7.5 日期范围不包含数据

Team Tab 默认看 Day，即今天和昨天的对比。

如果上报的是更早的数据，切换到 `Week` 或 `Month`。旧接口仍支持 `days=30` 和 `month=current`，但当前页面主控件不再展示 `30D`。

### 7.6 服务端负责 DB，collector 不使用 DB

服务端 `--db` 是中央库，collector 只读取本机 Codex sessions 和 Claude Code projects 并调用服务端 API。

Team 页面只读取服务端 `--db` 里的 team shard：

```text
token-meter-team-registry.sqlite
token-meter-team-YYYY-MM.sqlite
```

## 8. CLI 直接创建 token

如果暂时不使用 admin 页面，也可以在服务端机器通过 CLI 创建 token：

```sh
java -jar token-meter-app/target/token-meter-app-0.1.0-SNAPSHOT.jar \
  --create-device-token \
  --db="$HOME/.token-meter/sqlite" \
  --team-id="open-space" \
  --user-id="zhangsan" \
  --device-id="zhangsan-macbook" \
  --device-name="Zhangsan MacBook Pro"
```

输出里的 `device_token` 可直接发给成员。后续如果通过 admin 页面管理新建 token，也可以在 Device Token Bindings 列表点击 token 单元格右上角的复制图标复制。成员机器仍然需要运行 `--collect-team` 上报。

## 9. 最小验证流程

1. 管理员启动服务端并配置 admin token。
2. 管理员登录 `/admin-login.html`。
3. 管理员在 `/admin.html` 创建 teammate token。
4. 成员机器使用该 token 运行 `--collect-team`，或安装 collector launchd 服务。
5. 管理员打开 dashboard 的 Team Tab。
6. 切换 `Day`、`Week` 或 `Month` 查看数据。

如果第 4 步没有成功，Team Tab 会是 0。

## 10. 如何查看 Daily

Team 页的 Daily 不是排查单个用户或设备的主入口，它用于快速判断团队用量的按天趋势：

- 是否有某一天 token 用量突然升高。
- 是否有某一天用量异常接近 0，可能表示 collector 没有上报。
- 当前 Day、Week 或 Month 窗口内是否持续增长。
- token 增长是否伴随 calls、avg/call、cache 或 reasoning 指标变化。

Daily 上方折线图只展示 total tokens 的走势。图内不展示 Y 轴数字，避免大数字挤占图表空间；需要精确数值时，把鼠标悬停在折线点上，或查看下方 daily 表格。

Daily 表格展示每天的 `total/input/output/calls/users/avg per call/cache/reasoning`。如果要定位是谁或哪个模型导致某天用量变化，应切到 Users 或 Models；如果怀疑数据没上报，应切到 Upload Health。
