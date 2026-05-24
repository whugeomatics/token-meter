# P3 Team Ingestion API Contract

## 目标

定义 collector 向中央服务上报标准化 tool usage event 的 HTTP API。P3 初始只上传 Codex；P4 起同一 endpoint 也接收 `tool=claude-code`。

P3 只新增团队采集 ingestion API，不改变 P1/P2 `/api/report` contract。


## Endpoint

```text
POST /api/team/ingest
Authorization: Bearer <device_token>
Content-Type: application/json
```

请求体：

```json
{
  "collector_version": "0.1.0",
  "client_user_id": "optional-user-id",
  "client_device_id": "optional-device-id",
  "events": []
}
```

字段规则：

- `collector_version`: 必填，用于排查兼容问题。
- `client_user_id`: 可选，只做 token 绑定一致性校验。
- `client_device_id`: 可选，只做 token 绑定一致性校验。
- `events`: 必填数组，元素遵守 `P3-2026-04-30-team-usage-event.md`。

## 成功响应

```json
{
  "status": "ok",
  "accepted": 10,
  "duplicate": 2,
  "rejected": 0,
  "event_count": 12,
  "team_id": "team-1",
  "user_id": "user-1",
  "device_id": "device-1",
  "upload_date": "2026-04-30",
  "server_time": "2026-04-30T12:35:00Z"
}
```

语义：

- `accepted`: 新写入事件数量。
- `duplicate`: 因 `event_key` 已存在而跳过的事件数量。
- `rejected`: 因字段非法被拒绝的事件数量。
- `event_count`: 本 batch event 数量。
- `team_id`、`user_id`、`device_id`: 服务端从 device token binding 解析出的归属，用于非敏感日志诊断。
- `upload_date`: 服务端按配置时区计算的上报日期。
- `server_time`: 服务端时间，collector 可用于日志诊断。

响应不得包含 device token。

## 错误响应

未知 token：

```text
401 Unauthorized
```

禁用或吊销 token：

```text
403 Forbidden
```

token 绑定与客户端自报身份冲突：

```text
409 Conflict
```

非法 payload：

```text
400 Bad Request
```

服务端错误：

```text
500 Internal Server Error
```

错误响应体：

```json
{
  "status": "error",
  "error_code": "invalid_payload",
  "message": "payload validation failed"
}
```

错误响应不得包含 prompt、response、raw JSONL、token 明文或原始日志行。

## 幂等规则

服务端以以下组合做幂等：

```text
team_id + user_id + device_id + event_key
```

`team_id`、`user_id`、`device_id` 来自设备 token 绑定，不来自客户端 payload。

## Batch 规则

MVP 规则：

- 单次最多 500 条 event。
- 单个 payload 最大 1 MB。
- 超限返回 `413 Payload Too Large` 或 `400 Bad Request`。
- collector 必须按 batch 分批上报，不能把长期历史数据一次性塞进一个请求。
- batch 间不要求事务一致性；每个 batch 独立返回 accepted/duplicate/rejected。

## Retry 语义

collector 可重试：

- 网络失败。
- 5xx。
- 429。

collector 不应自动重试：

- 400。
- 401。
- 403。
- 409。

由于服务端使用 `event_key` 幂等，collector 重试同一 batch 不应造成重复统计。

## 增量与周期上报

collector 不依赖本地 SQLite checkpoint：

- Codex 或 Claude Code 打开并持续产生本地 usage metadata 时，可周期性运行 collector。
- Codex 关闭期间不需要后台常驻；下次 collector 启动时重新扫描最近窗口内的 sessions 并批量补报。
- 网络失败或服务端 5xx 时，下一次 collector 运行可以重新发送最近窗口内 event。
- 服务端通过 `team_id + user_id + device_id + event_key` 去重，重复补报不重复统计。
