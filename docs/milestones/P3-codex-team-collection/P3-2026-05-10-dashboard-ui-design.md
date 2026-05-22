# P3 Dashboard UI Refresh Design


## Goal

Adjust the existing dashboard UI so each tab has one clear purpose, while keeping the current P1/P2 `/api/report` and P3 `/api/team/report` query behavior compatible.

## Assumptions

- The top-level dashboard keeps two views: Local and Team.
- Team view defaults to all-team aggregation by omitting `team_id`.
- The team selector is a drill-down control. It must not block the default all-team dashboard.
- The UI refresh itself is frontend-focused. The later period comparison update extends existing report endpoints with `period=<day|week|month>&compare=previous`; it does not add a new endpoint or storage field.

## Layout

- Use a light, clean dashboard shell inspired by the prototype: a compact left navigation, a clear title area, summary metrics, then one focused content area.
- Keep the visual style restrained: white panels, light borders, teal accent, and enough spacing to separate tasks.
- Avoid presenting all team tables at once.
- The primary period controls are Day, Week, and Month for both Local and Team views.

## Tabs

Local sections:

- Overview: local summary, daily trend, and top models.
- Sessions: local session table.
- Models: local model table.
- Daily Trend: local daily chart and daily table.

Team sections:

- Overview: all-team summary by default, team list, top users, top models, and upload health summary.
- Users: user ranking and per-user metrics.
- Devices: device ranking and user ownership.
- Models: date + team + user + model detail, with sorting.
- Daily Trend: team daily chart and daily table.
- Upload Health: upload health only; no token metrics.

## Acceptance

- Loading the dashboard defaults to Local Overview.
- Switching to Team defaults to Team Overview and calls `/api/team/report` without `team_id` unless the user selects a specific team.
- Team selector starts at `All Teams`.
- Local and Team period controls call `period=<day|week|month>&compare=previous`.
- Each Team tab presents one analysis object and does not vertically render all team sections at once.
- No prompt, response, raw JSONL, device token, token hash, or full source path appears in the UI.
