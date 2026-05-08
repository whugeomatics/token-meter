const state = { range: 'days=7', view: 'local', teamModelSort: 'total_tokens', teamModelDir: 'desc', teamReport: null };
const fmt = new Intl.NumberFormat();
const qs = (id) => document.getElementById(id);
const tokens = (n) => fmt.format(n || 0);
const pct = (n) => `${Math.round((n || 0) * 100)}%`;

document.querySelectorAll('[data-range]').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelectorAll('[data-range]').forEach((item) => item.classList.remove('active'));
    button.classList.add('active');
    state.range = button.dataset.range;
    load();
  });
});
document.querySelectorAll('[data-view]').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelectorAll('[data-view]').forEach((item) => {
      item.classList.remove('active');
      item.setAttribute('aria-selected', 'false');
    });
    button.classList.add('active');
    button.setAttribute('aria-selected', 'true');
    state.view = button.dataset.view;
    qs('localPanel').classList.toggle('hidden', state.view !== 'local');
    qs('teamPanel').classList.toggle('hidden', state.view !== 'team');
    load();
  });
});
qs('refresh').addEventListener('click', load);
document.querySelectorAll('[data-team-model-sort]').forEach((button) => {
  button.addEventListener('click', () => {
    const field = button.dataset.teamModelSort;
    if (state.teamModelSort === field) {
      state.teamModelDir = state.teamModelDir === 'asc' ? 'desc' : 'asc';
    } else {
      state.teamModelSort = field;
      state.teamModelDir = field === 'user' || field === 'model' ? 'asc' : 'desc';
    }
    renderTeamModels(state.teamReport?.team_models || []);
  });
});

async function load() {
  statusEl().textContent = 'Loading...';
  statusEl().className = 'status';
  try {
    const endpoint = state.view === 'team' ? '/api/team/report' : '/api/report';
    const response = await fetch(`${endpoint}?${state.range}`, { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const report = await response.json();
    state.view === 'team' ? renderTeam(report) : renderLocal(report);
  } catch (error) {
    statusEl().textContent = error.message;
    statusEl().className = 'status error';
  }
}

function renderLocal(report) {
  qs('rangeMeta').textContent = `Local: ${report.range.start_date} to ${report.range.end_date} (${report.range.timezone})`;
  qs('localTotalTokens').textContent = tokens(report.summary.total_tokens);
  qs('localNetTokens').textContent = tokens(report.summary.net_tokens);
  qs('localCachedTokens').textContent = tokens(report.summary.cached_input_tokens);
  qs('localCacheRate').textContent = pct(report.summary.cache_hit_rate);
  renderDaily(report.daily || [], 'localDailyChart', 'localDailyStatus');
  renderModels(report.models || [], 'localModelsBody');
  renderSessions(report.sessions || []);
}

function renderTeam(report) {
  state.teamReport = report;
  qs('rangeMeta').textContent = `Team: ${report.range.start_date} to ${report.range.end_date} (${report.range.timezone})`;
  qs('teamTotalTokens').textContent = tokens(report.summary.total_tokens);
  qs('teamNetTokens').textContent = tokens(report.summary.net_tokens);
  qs('teamUsers').textContent = tokens(report.summary.users);
  qs('teamDevices').textContent = tokens(report.summary.devices);
  renderDaily(report.daily || [], 'teamDailyChart', 'teamDailyStatus');
  renderTeamModels(report.team_models || []);
  renderUsers(report.users || []);
  renderDevices(report.devices || []);
}

function renderDaily(rows, chartId, statusId) {
  const max = Math.max(1, ...rows.map((row) => row.total_tokens || 0));
  qs(statusId).textContent = rows.length ? `${rows.length} days` : 'No data';
  qs(statusId).className = 'status';
  qs(chartId).innerHTML = rows.map((row) => {
    const height = Math.max(2, Math.round(((row.total_tokens || 0) / max) * 150));
    return `<div class="bar-wrap" title="${escapeHtml(row.date)}: ${tokens(row.total_tokens)}"><div class="bar" style="height:${height}px"></div><div class="bar-label">${escapeHtml(String(row.date).slice(5))}</div></div>`;
  }).join('');
}

function renderModels(rows, bodyId) {
  qs(bodyId).innerHTML = rows.length ? rows.map((row) => `<tr><td>${escapeHtml(row.model)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.input_tokens)}</td><td>${tokens(row.cached_input_tokens)}</td><td>${tokens(row.output_tokens)}</td><td>${tokens(row.reasoning_output_tokens)}</td><td>${tokens(row.session_count ?? row.sessions)}</td></tr>`).join('') : '<tr><td colspan="7" class="empty">No model usage yet</td></tr>';
}

function renderTeamModels(rows) {
  const sorted = [...rows].sort(teamModelComparator);
  qs('teamModelsBody').innerHTML = sorted.length ? sorted.map((row) => `<tr>
    <td>${escapeHtml(row.date)}</td>
    <td>${escapeHtml(row.display_name || row.user_id)}</td>
    <td>${escapeHtml(row.model)}</td>
    <td>${tokens(row.total_tokens)}</td>
    <td>${tokens(row.input_tokens)}</td>
    <td>${tokens(row.cached_input_tokens)}</td>
    <td>${tokens(row.output_tokens)}</td>
    <td>${tokens(row.reasoning_output_tokens)}</td>
    <td>${tokens(row.sessions)}</td>
    <td>${escapeHtml(formatDateTime(row.started_at))}</td>
    <td>${escapeHtml(formatDateTime(row.ended_at))}</td>
  </tr>`).join('') : '<tr><td colspan="11" class="empty">No model usage yet</td></tr>';
}

function teamModelComparator(left, right) {
  const field = state.teamModelSort;
  const dir = state.teamModelDir === 'asc' ? 1 : -1;
  const a = comparable(left[field]);
  const b = comparable(right[field]);
  if (a < b) return -1 * dir;
  if (a > b) return 1 * dir;
  if ((left.date || '') !== (right.date || '')) return String(right.date || '').localeCompare(String(left.date || ''));
  return String(left.model || '').localeCompare(String(right.model || ''));
}

function comparable(value) {
  return typeof value === 'number' ? value : String(value || '').toLowerCase();
}

function renderSessions(rows) {
  qs('localSessionsBody').innerHTML = rows.length ? rows.map((row) => `<tr><td>${escapeHtml(row.session_id)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.net_tokens)}</td><td>${escapeHtml(formatDateTime(row.started_at))}</td><td>${escapeHtml(formatDateTime(row.ended_at))}</td><td>${escapeHtml((row.models || []).join(', '))}</td></tr>`).join('') : '<tr><td colspan="6" class="empty">No sessions yet</td></tr>';
}

function renderUsers(rows) {
  qs('teamUsersBody').innerHTML = rows.length ? rows.map((row) => `<tr><td>${escapeHtml(row.display_name || row.user_id)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.input_tokens)}</td><td>${tokens(row.output_tokens)}</td><td>${tokens(row.sessions)}</td><td>${tokens(row.devices)}</td><td>${escapeHtml(formatDateTime(row.last_seen_at))}</td></tr>`).join('') : '<tr><td colspan="7" class="empty">No users yet</td></tr>';
}

function renderDevices(rows) {
  qs('teamDevicesBody').innerHTML = rows.length ? rows.map((row) => `<tr><td>${escapeHtml(row.display_name || row.device_id)}</td><td>${escapeHtml(row.user_id)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.sessions)}</td><td>${escapeHtml(formatDateTime(row.last_seen_at))}</td></tr>`).join('') : '<tr><td colspan="5" class="empty">No devices yet</td></tr>';
}

function statusEl() {
  return state.view === 'team' ? qs('teamDailyStatus') : qs('localDailyStatus');
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

function formatDateTime(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
}

load();
