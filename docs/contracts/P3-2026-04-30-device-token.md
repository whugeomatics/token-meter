# P3 Device Token Contract

## 目标

定义 P3 中设备 token、团队、使用人和设备之间的绑定关系。


设备 token 用于 collector 向中央服务上报 Codex usage event。token 不是匿名凭证，服务端必须能从 token 查到它对应的使用人。

## 绑定模型

服务端维护设备 token 绑定表。MVP 推荐字段：

```text
device_tokens
  token_hash text primary key
  token_secret text null
  team_id text not null
  user_id text not null
  device_id text not null
  display_name text null
  status text not null
  created_at text not null
  last_seen_at text null
```

字段说明：

- `token_hash`: token 的 SHA-256 哈希值，用于 ingestion 鉴权查找。
- `token_secret`: 可恢复的 device token，仅供管理员后续复制给 teammate；历史 hash-only 记录可以为空。
- `team_id`: 该设备所属团队。
- `user_id`: 该设备对应的使用人。
- `device_id`: 该成员电脑的稳定设备标识。
- `display_name`: 便于 dashboard 展示的人类可读名称，例如 `Alice MacBook Pro`。
- `status`: `active`、`disabled` 或 `revoked`。
- `created_at`: token 创建时间。
- `last_seen_at`: 最近成功上报时间。

## 存储位置

设备 token 绑定不是高增长 usage event，不按月拆分。

服务端使用一个小型 registry SQLite 保存 token 绑定：

```text
token-meter-team-registry.sqlite
```

团队 usage event 按月拆分到独立 SQLite 文件。registry 保存 token hash、可恢复 token 和归属关系，不保存事件明细，因此长期运行不会成为主要增长点。

## Token 识别规则

collector 使用 HTTP header 上报 token：

```text
Authorization: Bearer <device_token>
```

服务端处理请求时：

1. 读取 bearer token。
2. 对 token 做哈希。
3. 查找 `device_tokens`。
4. 未找到时返回 `401 Unauthorized`。
5. `status != active` 时返回 `403 Forbidden`。
6. 找到 active 绑定后，服务端用绑定中的 `team_id`、`user_id`、`device_id` 覆盖事件归属。

## 客户端身份字段

上传 payload 可以带 `client_user_id`、`client_device_id` 用于诊断，但它们不参与最终归属。

规则：

- 服务端最终写入的 `team_id`、`user_id`、`device_id` 必须来自 token 绑定。
- 如果 `client_user_id` 存在且与 token 绑定的 `user_id` 不一致，服务端应拒绝该 batch，返回 `409 Conflict`。
- 如果 `client_device_id` 存在且与 token 绑定的 `device_id` 不一致，服务端应拒绝该 batch，返回 `409 Conflict`。
- 服务端不得因为客户端自报字段而把事件归到其他用户或设备。

## Token 存储

collector 本地需要保存上报 token。

MVP 可接受：

- mac 使用用户 home 下的 collector 配置文件，文件权限限制为当前用户可读写。
- 配置文件不得进入项目仓库。
- 日志不得输出 token 明文。

后续产品化再考虑系统 keychain。

## Token 创建

服务端必须提供一次性生成并绑定 token 的入口，避免要求管理员手工编造 token。

MVP CLI：

```text
--create-device-token --team-id=<team> --user-id=<user> --device-id=<device> [--device-name=<name>]
```

规则：

- 服务端生成高熵随机 token。
- 明文 token 在创建命令 stdout 中返回。
- 服务端 registry 保存 token hash 和可恢复 token，便于管理员后续复制给 teammate。
- 日志、错误响应、普通 dashboard 页面不得输出 token 明文。
- 后续 collector 使用该 token 上报，dashboard 根据 token 绑定展示到对应 user/device。

## 管理页面

P3 必须提供管理员分配 teammate token 的页面。

MVP 管理入口：

```text
GET /admin-login.html
GET /admin.html
GET /api/admin/device-tokens
GET /api/admin/device-tokens/<token_id>/token
POST /api/admin/device-tokens
DELETE /api/admin/device-tokens/<token_id>
POST /api/admin/login
```

权限规则：

- 管理页面和管理 API 只允许管理员访问。
- 管理员凭证由 `--admin-token=<token>` 或 `TOKEN_METER_ADMIN_TOKEN` 提供。
- 未配置 admin token 时，管理页面和管理 API 关闭。
- `POST /api/admin/login` 校验 admin token 后设置 HttpOnly cookie。
- `/admin.html` 和 `/api/admin/*` 必须校验 cookie 或等价管理员凭证。
- 管理列表只展示 `token_preview`，例如 `abc123****wxyz`，不得直接展示完整 device token。
- 管理页面提供 `Copy` 按钮，按钮通过受 admin 保护的 API 获取完整 token 并写入剪贴板。
- 管理页面不得允许编辑 token。
- 管理页面提供删除 token binding 的能力；删除后该 token 不能继续上报。
- 管理页面可以展示 `team_id`、`user_id`、`device_id`、`display_name`、`status`、`created_at`、`last_seen_at`。

创建 teammate token 的请求：

```json
{
  "team_id": "team-a",
  "user_id": "alice",
  "device_id": "alice-macbook",
  "device_name": "Alice MacBook"
}
```

成功响应：

```json
{
  "status": "ok",
  "device_token": "one-time-token",
  "binding": {
    "team_id": "team-a",
    "user_id": "alice",
    "device_id": "alice-macbook",
    "display_name": "Alice MacBook",
    "status": "active"
  }
}
```

管理员可以在创建成功响应复制 token，也可以后续在 binding 列表中通过 `Copy` 按钮复制。列表本身只显示 mask 后的 token。

## 安全边界

P3 不实现完整账号体系，但必须满足：

- token 明文存在于成员设备本地配置、HTTP header，以及服务端 registry 的 `token_secret` 字段。
- `token_secret` 是为了满足管理员后续复制 token 的 P3 产品需求；因此 registry SQLite 必须按管理员机器的敏感数据处理。
- admin 页面列表不返回完整 token 或 token hash，只返回 mask 后的 `token_preview`。
- 完整 token 只通过受 admin 鉴权保护的复制 API 返回。
- token 可禁用或吊销。
- 失败日志不包含 token 明文。
- usage event 归属只由 token 绑定决定。
- admin token 不写入数据库、日志、HTML 或 JS。
