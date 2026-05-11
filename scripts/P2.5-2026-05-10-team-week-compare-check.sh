#!/usr/bin/env sh
set -eu

JAR="token-meter-app/target/token-meter-app-0.1.0-SNAPSHOT.jar"
ROOT="$(mktemp -d /tmp/token-meter-week-compare.XXXXXX)"
DB="$ROOT/db"
PAYLOAD="$ROOT/events.json"
ZONE="Asia/Shanghai"
TOKEN="week-compare-token"

node - "$PAYLOAD" <<'NODE'
const fs = require('fs');
const payloadPath = process.argv[2];

const now = new Date();
const local = new Date(now.toLocaleString('en-US', { timeZone: 'Asia/Shanghai' }));
const day = local.getDay();
const mondayOffset = day === 0 ? -6 : 1 - day;
const currentMonday = new Date(local);
currentMonday.setDate(local.getDate() + mondayOffset);
currentMonday.setHours(12, 0, 0, 0);
const previousMonday = new Date(currentMonday);
previousMonday.setDate(currentMonday.getDate() - 7);

function iso(date) {
  return date.toISOString().replace('.000Z', 'Z');
}

function event(key, baseDate, dayIndex, totalTokens, model) {
  const date = new Date(baseDate);
  date.setDate(baseDate.getDate() + dayIndex);
  return {
    event_key: key,
    tool: 'codex',
    session_id: key.replace('event', 'session'),
    model,
    timestamp: iso(date),
    input_tokens: Math.floor(totalTokens * 0.7),
    cached_input_tokens: 0,
    output_tokens: Math.ceil(totalTokens * 0.3),
    reasoning_output_tokens: 0,
    total_tokens: totalTokens
  };
}

const body = {
  collector_version: '0.1.0',
  client_user_id: 'alice',
  client_device_id: 'alice-mac',
  events: [
    event('event-current-1', currentMonday, 0, 1000, 'model-a'),
    event('event-current-2', currentMonday, 1, 2000, 'model-b'),
    event('event-previous-1', previousMonday, 0, 400, 'model-a'),
    event('event-previous-2', previousMonday, 1, 600, 'model-b')
  ]
};

fs.writeFileSync(payloadPath, JSON.stringify(body));
NODE

java -jar "$JAR" --register-device-token --db="$DB" --timezone="$ZONE" \
  --device-token="$TOKEN" --team-id=team-a --user-id=alice --device-id=alice-mac >/dev/null

java -jar "$JAR" --team-ingest-file="$PAYLOAD" --db="$DB" --timezone="$ZONE" \
  --device-token="$TOKEN" >/dev/null

REPORT="$(java -jar "$JAR" --team-report --db="$DB" --timezone="$ZONE" --period=week --compare=previous)"

node - "$REPORT" <<'NODE'
const assert = require('assert/strict');
const report = JSON.parse(process.argv[2]);

assert.equal(report.comparison.period, 'natural_week');
assert.equal(report.comparison.current.total_tokens, 3000);
assert.equal(report.comparison.previous.total_tokens, 1000);
assert.equal(report.comparison.delta.total_tokens, 2000);
assert.equal(report.comparison.delta.total_tokens_rate, 2);
assert.equal(report.comparison.daily.length >= 2, true);
assert.equal(report.comparison.daily[0].current_total_tokens, 1000);
assert.equal(report.comparison.daily[0].previous_total_tokens, 400);
assert.equal(report.comparison.users[0].user_id, 'alice');
assert.equal(report.comparison.users[0].delta_total_tokens, 2000);
assert.equal(report.comparison.models[0].delta_total_tokens > 0, true);
NODE
