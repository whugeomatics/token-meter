const state = { localRange: 'days=1', teamRange: 'days=7', view: 'local', localSection: 'sessions', teamId: '', teamOptions: [], teamSection: 'users', teamModelSort: 'date', teamModelDir: 'desc', teamReport: null };
const fmt = new Intl.NumberFormat();
const qs = (id) => document.getElementById(id);
const tokens = (n) => fmt.format(n || 0);
const pct = (n) => `${Math.round((n || 0) * 100)}%`;

document.querySelectorAll('[data-range]').forEach((button) => {
  button.addEventListener('click', () => {
    setRangeForView(button.dataset.range);
    renderRangeButtons();
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
    qs('teamFilter').classList.toggle('hidden', state.view !== 'team');
    renderRangeButtons();
    load();
  });
});
document.querySelectorAll('[data-local-section-tab]').forEach((button) => {
  button.addEventListener('click', () => {
    state.localSection = button.dataset.localSectionTab;
    renderLocalSections();
  });
});
document.querySelectorAll('[data-team-section-tab]').forEach((button) => {
  button.addEventListener('click', () => {
    state.teamSection = button.dataset.teamSectionTab;
    renderTeamSections();
  });
});
qs('refresh').addEventListener('click', () => load({ showToast: true }));
qs('teamFilter').addEventListener('change', () => {
  state.teamId = qs('teamFilter').value;
  load();
});
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

async function load(options = {}) {
  statusEl().textContent = 'Loading...';
  statusEl().className = 'status';
  try {
    if (state.view === 'local') {
      const ingestResponse = await fetch('/api/ingest', { method: 'POST', cache: 'no-store' });
      if (!ingestResponse.ok) throw new Error(`Ingest HTTP ${ingestResponse.status}`);
      const ingestResult = await ingestResponse.json();
      if (ingestResult.status !== 'ok') throw new Error('Ingest failed');
    }
    const endpoint = state.view === 'team' ? '/api/team/report' : '/api/report';
    const query = state.view === 'team' && state.teamId
      ? `${rangeForView()}&team_id=${encodeURIComponent(state.teamId)}`
      : rangeForView();
    const response = await fetch(`${endpoint}?${query}`, { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const report = await response.json();
    state.view === 'team' ? renderTeam(report) : renderLocal(report);
    if (options.showToast) {
      showToast(`${viewLabel()} refreshed`);
    }
  } catch (error) {
    statusEl().textContent = error.message;
    statusEl().className = 'status error';
    if (options.showToast) {
      showToast(`${viewLabel()} refresh failed: ${error.message}`, 'error');
    }
  }
}

function rangeForView() {
  return state.view === 'team' ? state.teamRange : state.localRange;
}

function setRangeForView(range) {
  if (state.view === 'team') {
    state.teamRange = range;
  } else {
    state.localRange = range;
  }
}

function renderRangeButtons() {
  const activeRange = rangeForView();
  document.querySelectorAll('[data-range]').forEach((button) => {
    button.classList.toggle('active', button.dataset.range === activeRange);
  });
}

function renderLocal(report) {
  qs('rangeMeta').textContent = `Local: ${report.range.start_date} to ${report.range.end_date} (${report.range.timezone})`;
  qs('localTotalTokens').textContent = tokens(report.summary.total_tokens);
  qs('localNetTokens').textContent = tokens(report.summary.net_tokens);
  qs('localCachedTokens').textContent = tokens(report.summary.cached_input_tokens);
  qs('localCacheRate').textContent = pct(report.summary.cache_hit_rate);
  qs('localCalls').textContent = tokens(report.summary.usage_event_count);
  qs('localAvgCall').textContent = tokens(report.summary.avg_tokens_per_call);
  qs('localAvgSession').textContent = tokens(report.summary.avg_tokens_per_session);
  qs('localReasoningRatio').textContent = pct(report.summary.reasoning_ratio);
  renderDaily(report.daily || [], 'localDailyChart', 'localDailyStatus', 'localDailyBody');
  renderModels(report.models || [], 'localModelsBody');
  renderSessions(report.sessions || []);
  renderLocalSections();
}

function renderTeam(report) {
  state.teamReport = report;
  qs('rangeMeta').textContent = `Team: ${report.range.start_date} to ${report.range.end_date} (${report.range.timezone})`;
  renderTeamFilter(report.teams || []);
  qs('teamTotalTokens').textContent = tokens(report.summary.total_tokens);
  qs('teamNetTokens').textContent = tokens(report.summary.net_tokens);
  qs('teamUsers').textContent = tokens(report.summary.users);
  qs('teamDevices').textContent = tokens(report.summary.devices);
  qs('teamCalls').textContent = tokens(report.summary.usage_event_count);
  qs('teamAvgCall').textContent = tokens(report.summary.avg_tokens_per_call);
  qs('teamCacheRate').textContent = pct(report.summary.cache_hit_rate);
  qs('teamReasoningRatio').textContent = pct(report.summary.reasoning_ratio);
  renderDaily(report.daily || [], 'teamDailyChart', 'teamDailyStatus', 'teamDailyBody');
  renderTeamModels(report.team_models || []);
  renderUsers(report.users || []);
  renderDevices(report.devices || []);
  renderUploadHealth(report.upload_health || []);
  renderTeamSections();
}

function renderTeamFilter(rows) {
  const select = qs('teamFilter');
  const current = state.teamId;
  const known = new Set(state.teamOptions);
  rows.forEach((row) => {
    if (row.team_id) {
      known.add(row.team_id);
    }
  });
  state.teamOptions = [...known].sort();
  const options = ['<option value="">All Teams</option>'].concat(
    state.teamOptions.map((teamId) => `<option value="${escapeHtml(teamId)}">${escapeHtml(teamId)}</option>`)
  );
  select.innerHTML = options.join('');
  select.value = state.teamOptions.includes(current) ? current : '';
  state.teamId = select.value;
}

function renderDaily(rows, chartId, statusId, bodyId) {
  qs(statusId).textContent = rows.length ? `${rows.length} days` : 'No data';
  qs(statusId).className = 'status';
  qs(chartId).innerHTML = renderDailyLineChart(rows);
  renderDailyTable(rows, bodyId);
}

function renderDailyLineChart(rows) {
  if (!rows.length) {
    return '<div class="empty">No daily usage yet</div>';
  }
  const width = 640;
  const height = 220;
  const padX = 18;
  const padTop = 18;
  const padBottom = 34;
  const plotWidth = width - padX * 2;
  const plotHeight = height - padTop - padBottom;
  const max = Math.max(1, ...rows.map((row) => row.total_tokens || 0));
  const points = rows.map((row, index) => {
    const x = rows.length === 1 ? width / 2 : padX + (index / (rows.length - 1)) * plotWidth;
    const y = padTop + plotHeight - ((row.total_tokens || 0) / max) * plotHeight;
    return { row, x: Math.round(x * 100) / 100, y: Math.round(y * 100) / 100 };
  });
  const polyline = points.map((point) => `${point.x},${point.y}`).join(' ');
  const labels = points.map((point, index) => {
    if (rows.length > 12 && index % Math.ceil(rows.length / 6) !== 0 && index !== rows.length - 1) return '';
    return `<text class="daily-axis-label" x="${point.x}" y="${height - 10}" text-anchor="middle">${escapeHtml(String(point.row.date).slice(5))}</text>`;
  }).join('');
  const markers = points.map((point) => `<circle class="daily-point" cx="${point.x}" cy="${point.y}" r="4"><title>${escapeHtml(point.row.date)}: ${tokens(point.row.total_tokens)} tokens, ${tokens(point.row.usage_event_count)} calls</title></circle>`).join('');
  return `<svg class="daily-line-chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="Daily total token trend">
    <line class="daily-axis" x1="${padX}" y1="${padTop + plotHeight}" x2="${width - padX}" y2="${padTop + plotHeight}"></line>
    <line class="daily-grid" x1="${padX}" y1="${padTop}" x2="${width - padX}" y2="${padTop}"></line>
    <polyline class="daily-line" points="${polyline}"></polyline>
    ${markers}
    ${labels}
  </svg>`;
}

function renderDailyTable(rows, bodyId) {
  qs(bodyId).innerHTML = rows.length ? rows.map((row) => `<tr>
    <td>${escapeHtml(row.date)}</td>
    <td>${tokens(row.total_tokens)}</td>
    <td>${tokens(row.input_tokens)}</td>
    <td>${tokens(row.output_tokens)}</td>
    <td>${tokens(row.usage_event_count)}</td>
    <td>${tokens(row.users ?? row.sessions)}</td>
    <td>${tokens(row.avg_tokens_per_call)}</td>
    <td>${pct(row.cache_hit_rate)}</td>
    <td>${pct(row.reasoning_ratio)}</td>
  </tr>`).join('') : '<tr><td colspan="9" class="empty">No daily usage yet</td></tr>';
}

function renderModels(rows, bodyId) {
  qs(bodyId).innerHTML = rows.length ? rows.map((row) => `<tr><td>${escapeHtml(row.model)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.input_tokens)}</td><td>${tokens(row.cached_input_tokens)}</td><td>${tokens(row.output_tokens)}</td><td>${tokens(row.reasoning_output_tokens)}</td><td>${tokens(row.session_count ?? row.sessions)}</td><td>${tokens(row.usage_event_count)}</td><td>${tokens(row.avg_tokens_per_session)}</td><td>${tokens(row.avg_tokens_per_call)}</td><td>${pct(row.cache_hit_rate)}</td><td>${pct(row.reasoning_ratio)}</td><td>${formatDuration(row.active_seconds)}</td></tr>`).join('') : '<tr><td colspan="13" class="empty">No model usage yet</td></tr>';
}

function renderTeamModels(rows) {
  const sorted = [...rows].sort(teamModelComparator);
  const visibleRows = sorted.slice(0, 50);
  qs('teamModelsStatus').textContent = sorted.length > 50 ? `Top 50 of ${tokens(sorted.length)}` : `${tokens(sorted.length)} rows`;
  qs('teamModelsBody').innerHTML = visibleRows.length ? visibleRows.map((row) => `<tr>
    <td>${escapeHtml(row.date)}</td>
    <td>${escapeHtml(row.team_id)}</td>
    <td>${escapeHtml(row.display_name || row.user_id)}</td>
    <td>${escapeHtml(row.model)}</td>
    <td>${tokens(row.total_tokens)}</td>
    <td>${tokens(row.input_tokens)}</td>
    <td>${tokens(row.cached_input_tokens)}</td>
    <td>${tokens(row.output_tokens)}</td>
    <td>${tokens(row.reasoning_output_tokens)}</td>
    <td>${tokens(row.sessions)}</td>
    <td>${tokens(row.usage_event_count)}</td>
    <td>${tokens(row.avg_tokens_per_call)}</td>
    <td>${pct(row.cache_hit_rate)}</td>
    <td>${pct(row.reasoning_ratio)}</td>
    <td>${formatDuration(row.active_seconds)}</td>
    <td>${escapeHtml(formatDateTime(row.started_at))}</td>
    <td>${escapeHtml(formatDateTime(row.ended_at))}</td>
  </tr>`).join('') : '<tr><td colspan="17" class="empty">No model usage yet</td></tr>';
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
  const visibleRows = rows.slice(0, 50);
  qs('localSessionsStatus').textContent = rows.length > 50 ? `Latest 50 of ${tokens(rows.length)}` : `${tokens(rows.length)} rows`;
  qs('localSessionsBody').innerHTML = visibleRows.length ? visibleRows.map((row) => `<tr><td>${escapeHtml(row.session_id)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.net_tokens)}</td><td>${tokens(row.usage_event_count)}</td><td>${tokens(row.avg_tokens_per_call)}</td><td>${formatDuration(row.active_seconds)}</td><td>${escapeHtml(formatDateTime(row.started_at))}</td><td>${escapeHtml(formatDateTime(row.ended_at))}</td><td>${escapeHtml((row.models || []).join(', '))}</td></tr>`).join('') : '<tr><td colspan="9" class="empty">No sessions yet</td></tr>';
}

function renderLocalSections() {
  document.querySelectorAll('[data-local-section-tab]').forEach((button) => {
    const active = button.dataset.localSectionTab === state.localSection;
    button.classList.toggle('active', active);
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  });
  document.querySelectorAll('[data-local-section]').forEach((section) => {
    section.classList.toggle('hidden', section.dataset.localSection !== state.localSection);
  });
}

function renderUsers(rows) {
  qs('teamUsersBody').innerHTML = rows.length ? rows.map((row) => `<tr><td>${escapeHtml(row.team_id)}</td><td>${escapeHtml(row.display_name || row.user_id)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.input_tokens)}</td><td>${tokens(row.output_tokens)}</td><td>${tokens(row.sessions)}</td><td>${tokens(row.usage_event_count)}</td><td>${tokens(row.avg_tokens_per_session)}</td><td>${tokens(row.avg_tokens_per_call)}</td><td>${pct(row.cache_hit_rate)}</td><td>${pct(row.reasoning_ratio)}</td><td>${formatDuration(row.active_seconds)}</td><td>${tokens(row.devices)}</td><td>${escapeHtml(formatDateTime(row.last_seen_at))}</td></tr>`).join('') : '<tr><td colspan="14" class="empty">No users yet</td></tr>';
}

function renderDevices(rows) {
  qs('teamDevicesBody').innerHTML = rows.length ? rows.map((row) => `<tr><td>${escapeHtml(row.team_id)}</td><td>${escapeHtml(row.display_name || row.device_id)}</td><td>${escapeHtml(row.user_id)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.sessions)}</td><td>${tokens(row.usage_event_count)}</td><td>${tokens(row.avg_tokens_per_call)}</td><td>${formatDuration(row.active_seconds)}</td><td>${escapeHtml(formatDateTime(row.last_seen_at))}</td></tr>`).join('') : '<tr><td colspan="9" class="empty">No devices yet</td></tr>';
}

function renderUploadHealth(rows) {
  qs('teamUploadsStatus').textContent = rows.length ? `${tokens(rows.length)} devices` : 'No upload data';
  qs('teamUploadHealthBody').innerHTML = rows.length ? rows.map((row) => `<tr>
    <td>${escapeHtml(row.team_id)}</td>
    <td>${escapeHtml(row.user_id)}</td>
    <td>${escapeHtml(row.device_id)}</td>
    <td>${escapeHtml(formatDateTime(row.last_upload_at))}</td>
    <td>${formatDuration(row.upload_gap_seconds)}</td>
    <td><span class="health health-${escapeHtml(row.health_status)}">${escapeHtml(row.health_status)}</span></td>
    <td>${escapeHtml(row.latest_status)}</td>
    <td>${tokens(row.latest_accepted)}</td>
    <td>${tokens(row.latest_duplicate)}</td>
    <td>${tokens(row.latest_rejected)}</td>
    <td>${renderRecentUploads(row.recent_uploads || [])}</td>
  </tr>`).join('') : '<tr><td colspan="11" class="empty">No upload data yet</td></tr>';
}

function renderRecentUploads(rows) {
  return rows.slice(0, 3).map((row) => `${escapeHtml(formatDateTime(row.upload_time))} ${escapeHtml(row.status)} A:${tokens(row.accepted)} D:${tokens(row.duplicate)} R:${tokens(row.rejected)}`).join('<br>');
}

function renderTeamSections() {
  document.querySelectorAll('[data-team-section-tab]').forEach((button) => {
    const active = button.dataset.teamSectionTab === state.teamSection;
    button.classList.toggle('active', active);
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  });
  document.querySelectorAll('[data-team-section]').forEach((section) => {
    section.classList.toggle('hidden', section.dataset.teamSection !== state.teamSection);
  });
}

function statusEl() {
  return state.view === 'team' ? qs('teamDailyStatus') : qs('localDailyStatus');
}

let toastTimer;
function showToast(message, type = 'success') {
  const toast = qs('toast');
  toast.textContent = message;
  toast.classList.toggle('error', type === 'error');
  toast.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.add('hidden'), 2200);
}

function viewLabel() {
  return state.view === 'team' ? 'Team' : 'Local';
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

function formatDuration(value) {
  const seconds = Math.max(0, Number(value || 0));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

load();
