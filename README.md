# Agent Dashboard

![what](images/P1-2026-04-29-what.png)

**English** | [中文](README.zh-CN.md)

Agent Dashboard is a local model usage dashboard and future routing tool. The project starts with Codex usage analytics, then evolves toward SQLite-backed incremental collection and, later, a local OpenAI-compatible model gateway.

## Current Status

Current phase: **P3 - OpenAI-compatible local gateway design preparation**.

P1 and P2 have passed acceptance.

P1, the Codex Dashboard MVP:

- Maven package passed in the user's real Windows terminal.
- P1 smoke test passed with `P1 smoke test passed`.
- The dashboard can read Codex usage metadata and expose `/api/report`.

P2, SQLite persistence and incremental ingestion:

- Maven package passed in the user's real Windows terminal.
- P2 smoke test passed with `P2 smoke test passed`.
- `--ingest` can write Codex usage deltas to local SQLite and `/api/report` aggregates from SQLite.

The next work is P3 design documentation: OpenAI-compatible gateway contracts, provider adapter contracts, usage event contracts, task breakdown, and acceptance criteria.

## Stage Results

Each completed phase should add links to its screenshots or result artifacts here.

- P1 Codex Dashboard MVP: [2026-04-30](images/P1-2026-04-30-mvp.png)

## Scope

P1 and P2 focus only on Codex.

In scope now:

- Read local Codex session JSONL logs.
- Aggregate token usage by day, model, and session.
- Persist Codex usage metadata to local SQLite in P2.
- Incrementally ingest newly appended Codex logs.
- Keep prompt and response bodies out of storage and reports.

Out of scope for the current phase:

- Claude Code integration.
- Cursor integration.
- Local model gateway.
- Provider adapters.
- Cloud sync, login, or billing estimation.

## Documentation

Current phase:

- [Current AGENTS.md](AGENTS.md)

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

## Run P1 Dashboard

After packaging:

```powershell
java -jar target\agent-dashboard-0.1.0-SNAPSHOT.jar --port=18080
```

Open:

- Dashboard: <http://127.0.0.1:18080/>
- Report API: <http://127.0.0.1:18080/api/report?days=7>
- Health: <http://127.0.0.1:18080/health>

Smoke test:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\P1-2026-04-30-smoke-test.ps1
```

## API

P1 and P2 keep the same report endpoint:

```text
GET /api/report?days=7
GET /api/report?days=30
GET /api/report?month=2026-04
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

See [P1 Report API Contract](docs/contracts/P1-2026-04-29-report-api.md) for field definitions.

## Privacy

The project must not store or display:

- Prompt text.
- Response text.
- User source snippets.
- API keys.

P1 reads Codex JSONL metadata only. P2 persists usage delta metadata only.

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
