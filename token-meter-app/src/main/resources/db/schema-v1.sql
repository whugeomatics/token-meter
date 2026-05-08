-- name: create_schema_migrations
CREATE TABLE IF NOT EXISTS schema_migrations (
  version INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

-- name: create_source_files
CREATE TABLE IF NOT EXISTS source_files (
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

-- name: create_usage_events
CREATE TABLE IF NOT EXISTS usage_events (
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

-- name: create_idx_usage_events_local_date
CREATE INDEX IF NOT EXISTS idx_usage_events_local_date ON usage_events(local_date);

-- name: create_idx_usage_events_model
CREATE INDEX IF NOT EXISTS idx_usage_events_model ON usage_events(model);

-- name: create_idx_usage_events_session
CREATE INDEX IF NOT EXISTS idx_usage_events_session ON usage_events(session_id);

-- name: create_idx_usage_events_timestamp
CREATE INDEX IF NOT EXISTS idx_usage_events_timestamp ON usage_events(event_timestamp);

-- name: create_device_tokens
CREATE TABLE IF NOT EXISTS device_tokens (
  token_hash TEXT PRIMARY KEY,
  team_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  device_id TEXT NOT NULL,
  display_name TEXT,
  status TEXT NOT NULL,
  created_at TEXT NOT NULL,
  last_seen_at TEXT
);

-- name: alter_device_tokens_add_token_secret
ALTER TABLE device_tokens ADD COLUMN token_secret TEXT;

-- name: create_team_usage_events
CREATE TABLE IF NOT EXISTS team_usage_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  team_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  device_id TEXT NOT NULL,
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
  received_at TEXT NOT NULL,
  UNIQUE(team_id, user_id, device_id, event_key)
);

-- name: create_team_uploads
CREATE TABLE IF NOT EXISTS team_uploads (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  team_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  device_id TEXT NOT NULL,
  upload_date TEXT NOT NULL,
  upload_time TEXT NOT NULL,
  event_count INTEGER NOT NULL,
  accepted INTEGER NOT NULL,
  duplicate INTEGER NOT NULL,
  rejected INTEGER NOT NULL,
  status TEXT NOT NULL,
  message TEXT
);

-- name: create_idx_team_usage_events_local_date
CREATE INDEX IF NOT EXISTS idx_team_usage_events_local_date ON team_usage_events(local_date);

-- name: create_idx_team_usage_events_user
CREATE INDEX IF NOT EXISTS idx_team_usage_events_user ON team_usage_events(team_id, user_id);

-- name: create_idx_team_usage_events_device
CREATE INDEX IF NOT EXISTS idx_team_usage_events_device ON team_usage_events(team_id, device_id);

-- name: create_idx_team_usage_events_model
CREATE INDEX IF NOT EXISTS idx_team_usage_events_model ON team_usage_events(model);

-- name: create_idx_team_uploads_date
CREATE INDEX IF NOT EXISTS idx_team_uploads_date ON team_uploads(upload_date);

-- name: create_idx_team_uploads_team_user
CREATE INDEX IF NOT EXISTS idx_team_uploads_team_user ON team_uploads(team_id, user_id);

-- name: insert_schema_migration
INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (1, ?);

-- name: upsert_device_token
INSERT INTO device_tokens(token_hash, token_secret, team_id, user_id, device_id, display_name, status, created_at, last_seen_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
ON CONFLICT(token_hash) DO UPDATE SET
  token_secret = excluded.token_secret,
  team_id = excluded.team_id,
  user_id = excluded.user_id,
  device_id = excluded.device_id,
  display_name = excluded.display_name,
  status = excluded.status;

-- name: find_device_token
SELECT team_id, user_id, device_id, display_name, status
FROM device_tokens
WHERE token_hash = ?;

-- name: list_device_tokens
SELECT rowid AS token_id, token_secret, team_id, user_id, device_id, display_name, status, created_at, last_seen_at
FROM device_tokens
ORDER BY team_id, user_id, device_id, created_at;

-- name: get_device_token_secret
SELECT token_secret
FROM device_tokens
WHERE rowid = ?;

-- name: delete_device_token
DELETE FROM device_tokens
WHERE rowid = ?;

-- name: find_device_binding
SELECT team_id, user_id, device_id, display_name, status
FROM device_tokens
WHERE team_id = ? AND user_id = ? AND device_id = ?;

-- name: update_device_token_seen
UPDATE device_tokens SET last_seen_at = ? WHERE token_hash = ?;

-- name: insert_team_upload
INSERT INTO team_uploads(team_id, user_id, device_id, upload_date, upload_time, event_count,
  accepted, duplicate, rejected, status, message)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: insert_team_usage_event
INSERT OR IGNORE INTO team_usage_events(team_id, user_id, device_id, event_key, tool, session_id, model,
  event_timestamp, local_date, input_tokens, cached_input_tokens, output_tokens,
  reasoning_output_tokens, total_tokens, received_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: load_team_usage_events
SELECT e.team_id, e.user_id, e.device_id, d.display_name, e.tool, e.session_id, e.model, e.event_timestamp,
  e.input_tokens, e.cached_input_tokens, e.output_tokens, e.reasoning_output_tokens, e.total_tokens
FROM team_usage_events e
LEFT JOIN device_tokens d ON d.team_id = e.team_id AND d.user_id = e.user_id AND d.device_id = e.device_id
WHERE e.local_date >= ? AND e.local_date <= ?
ORDER BY e.event_timestamp, e.id;

-- name: load_team_usage_events_plain
SELECT e.team_id, e.user_id, e.device_id, NULL AS display_name, e.tool, e.session_id, e.model, e.event_timestamp,
  e.input_tokens, e.cached_input_tokens, e.output_tokens, e.reasoning_output_tokens, e.total_tokens
FROM team_usage_events e
WHERE e.local_date >= ? AND e.local_date <= ?
ORDER BY e.event_timestamp, e.id;

-- name: load_team_uploads
SELECT team_id, user_id, device_id, upload_date, upload_time, event_count, accepted, duplicate, rejected, status, message
FROM team_uploads
WHERE upload_date >= ? AND upload_date <= ?
ORDER BY upload_time DESC, id DESC
LIMIT 200;

-- name: find_source_file
SELECT id, size_bytes, modified_at, file_fingerprint
FROM source_files
WHERE tool = ? AND path = ?;

-- name: upsert_source_file
INSERT INTO source_files(tool, path, size_bytes, modified_at, last_line, last_event_timestamp,
  file_fingerprint, status, last_error, scanned_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(tool, path) DO UPDATE SET
  size_bytes = excluded.size_bytes,
  modified_at = excluded.modified_at,
  last_line = excluded.last_line,
  last_event_timestamp = excluded.last_event_timestamp,
  file_fingerprint = excluded.file_fingerprint,
  status = excluded.status,
  last_error = excluded.last_error,
  scanned_at = excluded.scanned_at;

-- name: insert_usage_event
INSERT OR IGNORE INTO usage_events(source_file_id, line_number, event_key, tool, session_id,
  model, event_timestamp, local_date, input_tokens, cached_input_tokens, output_tokens,
  reasoning_output_tokens, total_tokens, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: load_usage_events
SELECT session_id, model, event_timestamp, input_tokens, cached_input_tokens, output_tokens,
  reasoning_output_tokens, total_tokens
FROM usage_events
WHERE local_date >= ? AND local_date <= ?
ORDER BY event_timestamp, id;

-- name: load_export_usage_events
SELECT event_key, session_id, model, event_timestamp, input_tokens, cached_input_tokens, output_tokens,
  reasoning_output_tokens, total_tokens
FROM usage_events
WHERE local_date >= ? AND local_date <= ?
ORDER BY event_timestamp, id;
