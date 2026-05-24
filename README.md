# Token Meter

![what](images/P0-2026-04-29-what.png)

**English** | [中文](README.zh-CN.md)

Token Meter is a local AI CLI usage dashboard and future routing tool. The project started with Codex usage analytics and now collects both Codex and Claude Code usage for Local and Team views, with a later path toward more CLI tools and a local OpenAI-compatible model gateway.

## Current Status

Current phase: **P4 - Claude Code teammate usage collection implementation verification**.

P1, P2, and P3 have passed acceptance. P4 implementation is in verification.

P1, the Codex Dashboard MVP:

- Maven package passed in the user's real Windows terminal.
- P1 smoke test passed with `P1 smoke test passed`.
- The dashboard can read Codex usage metadata and expose `/api/report`.

P2, SQLite persistence and incremental ingestion:

- Maven package passed in the user's real Windows terminal.
- P2 smoke test passed with `P2 smoke test passed`.
- `--ingest` can write Codex usage deltas to local SQLite and `/api/report` aggregates from SQLite.

P3, Codex team usage collection:

- The project uses three Maven modules: `token-meter-core`, `token-meter-app`, and `token-meter-collector`.
- The app provides Local and Team dashboard views.
- The collector is a lightweight teammate-side uploader and does not include dashboard, admin, SQLite, or static UI code.
- Local `/api/report` and Team `/api/team/report` both support Day, Week, and Month period comparison with `period=<day|week|month>&compare=previous`.

P4, Claude Code local and team usage collection:

- Local dashboard startup and `/api/ingest` collect Codex and Claude Code when local data exists.
- Team collector collects Codex and Claude Code in one default run; teammates do not need a separate Claude flag.
- Claude Code local JSONL parsing reads only usage metadata from `<user.home>/.claude/projects/**/*.jsonl`.
- Local and Team reports expose `tool` filtering and tool-level aggregates for `codex` and `claude-code`.
- The admin token creation flow produces a teammate `.env` block for the collector.
- Collector config precedence is `CLI args > ~/.token-meter/collector.env > system environment variables`.
- `--collect-claude-code` is retained only as a legacy compatibility entry point.

## Stage Results

Each completed phase should add links to its screenshots or result artifacts here.

- P1 Codex Dashboard MVP: [2026-04-30](./images/P1-2026-04-30-mvp.png)
- P3 Codex team usage collection: [2026-05-20](./images/P3-2026-05-20-team-usage.png)

## Scope

P1, P2, and P3 focused only on Codex. P4 adds Claude Code usage collection to make the product useful across AI CLI tools.

In scope now:

- Read local Codex session JSONL logs.
- Read Claude Code local JSONL usage metadata without prompt/response content.
- Aggregate token usage by day, model, and session.
- Persist Codex usage metadata to local SQLite in P2.
- Incrementally ingest newly appended Codex logs.
- Collect teammate Codex and Claude Code usage through a lightweight collector.
- Configure teammate collectors through a local `~/.token-meter/collector.env` file generated from admin token creation.
- Compare Local and Team usage by Day, Week, and Month.
- Keep prompt and response bodies out of storage and reports.

Out of scope for the current phase:

- Cursor integration.
- Local model gateway.
- Provider adapters.
- Cloud sync, login, or billing estimation.

## Documentation

Current phase:

- [Current AGENTS.md](AGENTS.md)

P4 implementation baseline:

- [P4 README](docs/P4-2026-05-01-README.md)
- [P4 Claude Code Usage Event Contract](docs/contracts/P4-2026-05-01-claude-code-usage-event.md)
- [P4 Claude Code Ingestion Source Contract](docs/contracts/P4-2026-05-01-claude-code-ingestion-source.md)
- [P4 Tool Usage Report Extension](docs/contracts/P4-2026-05-01-tool-usage-report-extension.md)
- [P4 Design](docs/milestones/P4-claude-code-team-collection/P4-2026-05-01-design.md)
- [P4 Tasks](docs/milestones/P4-claude-code-team-collection/P4-2026-05-01-tasks.md)
- [P4 Acceptance](docs/acceptance/P4-2026-05-01-claude-code-team-collection.md)

Completed P3:

- [P3 README](docs/P3-2026-04-30-README.md)
- [Archived P3 AGENTS](docs/archive/P3-2026-04-30-AGENTS.md)
- [P3 Device Token Contract](docs/contracts/P3-2026-04-30-device-token.md)
- [P3 Team Ingestion Contract](docs/contracts/P3-2026-04-30-team-ingestion-api.md)
- [P3 Team Report Contract](docs/contracts/P3-2026-04-30-team-report-api.md)
- [P3 Team Usage Event Contract](docs/contracts/P3-2026-04-30-team-usage-event.md)
- [P3 Module Architecture](docs/milestones/P3-codex-team-collection/P3-2026-05-08-module-architecture.md)
- [P3 Period Comparison Design](docs/milestones/P3-codex-team-collection/P3-2026-05-21-period-comparison-design.md)
- [P3 Admin Usage Guide](docs/guides/P3-2026-05-01-admin-usage-guide.md)
- [P3 Acceptance](docs/acceptance/P3-2026-04-30-codex-team-collection.md)


Completed P2:

- [P2 README](docs/P2-2026-04-30-README.md)
- [Archived P2 AGENTS](docs/archive/P2-2026-04-30-AGENTS.md)
- [P2 Database Schema Contract](docs/contracts/P2-2026-04-30-database-schema.md)
- [P2 Ingestion Contract](docs/contracts/P2-2026-04-30-ingestion-api.md)
- [P2 Design](docs/milestones/P2-codex-sqlite/P2-2026-04-30-design.md)
- [P2 Tasks](docs/milestones/P2-codex-sqlite/P2-2026-04-30-tasks.md)
- [P2 Implementation Plan](docs/milestones/P2-codex-sqlite/P2-2026-04-30-implementation-plan.md)
- [P2 Architecture Cleanup](docs/milestones/P2-codex-sqlite/P2-2026-04-30-architecture-cleanup.md)
- [P2 Review](docs/reviews/P2-2026-04-30-codex-sqlite-review.md)
- [P2 Acceptance](docs/acceptance/P2-2026-04-30-codex-sqlite.md)

Completed P1:

- [P1 README](docs/P1-2026-04-29-README.md)
- [P1 Requirements](docs/P1-2026-04-29-requirements.md)
- [P1 Codex Log Research](docs/research/P1-2026-04-29-codex-log-research.md)
- [P1 Report API Contract](docs/contracts/P1-2026-04-29-report-api.md)
- [P1 Dashboard IA](docs/P1-2026-04-29-dashboard-ia.md)
- [P1 Backend Prototype](docs/milestones/P1-codex-dashboard/P1-2026-04-29-backend-prototype.md)
- [P1 Review](docs/reviews/P1-2026-04-30-codex-dashboard-review.md)
- [P1 Acceptance](docs/acceptance/P1-2026-04-29-mvp-codex-dashboard.md)

Agent working rules:

- [Current AGENTS.md](AGENTS.md)
- [Archived P1 AGENTS](docs/archive/P1-2026-04-29-AGENTS.md)
- [Archived P2 AGENTS](docs/archive/P2-2026-04-30-AGENTS.md)
- [Archived P3 AGENTS](docs/archive/P3-2026-04-30-AGENTS.md)

## Build

Preferred command in the user's real Windows terminal:

```powershell
mvn -DskipTests clean package
```

Project convention also allows:

```powershell
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd -DskipTests package
```

Codex sandbox note: Windows `.cmd`, `cmd.exe /c`, and local Java service startup may be blocked by sandbox process permissions. When that happens, validation should be performed in the user's real Windows terminal and recorded in the acceptance document.

## Run Dashboard

After packaging:

```powershell
java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --port=18080
```

Open:

- Dashboard: <http://127.0.0.1:18080/>
- Local report API: <http://127.0.0.1:18080/api/report?period=day&compare=previous>
- Team report API: <http://127.0.0.1:18080/api/team/report?period=day&compare=previous>
- Health: <http://127.0.0.1:18080/health>

To allow other machines on the LAN to access the dashboard, start the app with an explicit bind host:

```powershell
java -jar token-meter-app\target\token-meter-app-0.1.0-SNAPSHOT.jar --port=18080 --bind=0.0.0.0
```

Smoke test:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\P1-2026-04-30-smoke-test.ps1
```

## API

P1 and P2 keep the same compatible report endpoint:

```text
GET /api/report?days=7
GET /api/report?days=30
GET /api/report?month=2026-04
```

Current Local and Team dashboard controls use natural period comparisons:

```text
GET /api/report?period=day&compare=previous
GET /api/report?period=week&compare=previous
GET /api/report?period=month&compare=previous
GET /api/team/report?period=day&compare=previous
GET /api/team/report?period=week&compare=previous
GET /api/team/report?period=month&compare=previous
```

Response shape:

```json
{
  "range": {},
  "summary": {},
  "daily": [],
  "models": [],
  "sessions": []
}
```

See [P1 Report API Contract](docs/contracts/P1-2026-04-29-report-api.md) and [P3 Team Report Contract](docs/contracts/P3-2026-04-30-team-report-api.md) for field definitions.

## Privacy

The project must not store or display:

- Prompt text.
- Response text.
- User source snippets.
- API keys.

P1 reads Codex JSONL metadata only. P2 persists usage delta metadata only. P3 uploads normalized usage events only.
P4 also reads Claude Code usage metadata only and must not store prompt, response, raw API body, transcript text, or raw Claude JSONL lines.

## Agent Workflow

Each phase must have complete supporting documents before implementation:

1. AGENTS working guide for the current phase.
2. Design.
3. Contracts.
4. Tasks.
5. Review.
6. Acceptance.

During a phase, root `AGENTS.md` may evolve. When a phase ends, the final version must be archived as:

```text
docs/archive/P<phase>-YYYY-MM-DD-AGENTS.md
```
