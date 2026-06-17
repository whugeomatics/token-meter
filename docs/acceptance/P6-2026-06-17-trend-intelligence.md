# P6 Team Trend Intelligence Acceptance

## Scope

P6 is accepted only when Token Meter can explain Team token usage changes from existing usage and upload health data.

## Acceptance Criteria

- Team dashboard shows current vs previous period deltas for total tokens, net usage, calls, cache hit rate, and reasoning ratio.
- Team dashboard identifies top positive and negative usage drivers by user, device, model, and tool.
- `What changed?` explains the main source of token growth or decline for week and month views.
- Trend signals cover token spike/drop, cache hit drop, reasoning ratio change, duplicate upload increase, rejected upload increase, and stale device.
- Markdown summary can be copied and reflects the current filters and current period.
- Empty states provide useful guidance based on whether other filters contain data.
- Existing Local report behavior remains unchanged.
- Existing Team report compatibility remains intact.
- P5 token formulas remain intact.
- No prompt, response, raw JSONL, raw API body, transcript text, local full source path, admin token, device token plaintext, or token hash appears in trend responses, Markdown summary, logs, or exports.

## Verification Commands

```powershell
node --check token-meter-app/src/main/resources/static/app.js
cmd /c D:\Softwares\Maven-3.9.9\bin\mvn.cmd test
```

If the Codex sandbox cannot bind local HTTP ports during manual dashboard verification, record the sandbox limitation here instead of treating it as a product failure.
