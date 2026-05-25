const state = { localPeriod: 'day', teamPeriod: 'day', view: 'local', localSection: 'overview', teamId: '', teamOptions: [], localTool: '', teamTool: '', toolOptions: [], teamSection: 'overview', teamModelSort: 'date', teamModelDir: 'desc', teamReport: null };
const fmt = new Intl.NumberFormat();
const qs = (id) => document.getElementById(id);
const tokens = (n) => fmt.format(n || 0);
const pct = (n) => `${Math.round((n || 0) * 100)}%`;
const signedPct = (n) => {
  const value = Math.round((n || 0) * 100);
  return `${value > 0 ? '+' : ''}${value}%`;
};

document.querySelectorAll('[data-period]').forEach((button) => {
  button.addEventListener('click', () => {
    setPeriodForView(button.dataset.period);
    renderPeriodButtons();
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
    renderPeriodButtons();
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
qs('toolFilter').addEventListener('change', () => {
  setToolForView(qs('toolFilter').value);
  load();
});
document.querySelectorAll('[data-team-model-sort]').forEach((button) => {
  button.addEventListener('click', () => {
    const field = button.dataset.teamModelSort;
    if (state.teamModelSort === field) {
      state.teamModelDir = state.teamModelDir === 'asc' ? 'desc' : 'asc';
    } else {
      state.teamModelSort = field;
      state.teamModelDir = field === 'user_id' || field === 'tool' || field === 'model' ? 'asc' : 'desc';
    }
    renderTeamModels(state.teamReport?.team_models || []);
  });
});

async function load(options = {}) {
  statusEl().textContent = 'Loading...';
  statusEl().className = 'status';
  try {
    const endpoint = state.view === 'team' ? '/api/team/report' : '/api/report';
    let query = queryForView();
    if (state.view === 'team' && state.teamId) {
      query += `&team_id=${encodeURIComponent(state.teamId)}`;
    }
    const tool = toolForView();
    if (tool) {
      query += `&tool=${encodeURIComponent(tool)}`;
    }
    const response = await fetch(`${endpoint}?${query}`, { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const report = await response.json();
    state.view === 'team' ? renderTeam(report) : renderLocal(report);
    statusEl().textContent = '';
    statusEl().className = 'status';
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

function periodForView() {
  return state.view === 'team' ? state.teamPeriod : state.localPeriod;
}

function queryForView() {
  return `period=${encodeURIComponent(periodForView())}&compare=previous`;
}

function setPeriodForView(period) {
  if (state.view === 'team') {
    state.teamPeriod = period;
  } else {
    state.localPeriod = period;
  }
}

function toolForView() {
  return state.view === 'team' ? state.teamTool : state.localTool;
}

function setToolForView(tool) {
  if (state.view === 'team') {
    state.teamTool = tool;
  } else {
    state.localTool = tool;
  }
}

function renderPeriodButtons() {
  const activePeriod = periodForView();
  document.querySelectorAll('[data-period]').forEach((button) => {
    button.classList.toggle('active', button.dataset.period === activePeriod);
  });
}

function renderLocal(report) {
  qs('rangeMeta').textContent = `Local: ${report.range.start_date} to ${report.range.end_date} (${report.range.timezone})`;
  renderToolFilter(report.tools || []);
  qs('localTotalTokens').textContent = tokens(report.summary.total_tokens);
  qs('localNetTokens').textContent = tokens(report.summary.net_tokens);
  qs('localCachedTokens').textContent = tokens(report.summary.cached_input_tokens);
  qs('localCacheRate').textContent = pct(report.summary.cache_hit_rate);
  qs('localCalls').textContent = tokens(report.summary.usage_event_count);
  qs('localAvgCall').textContent = tokens(report.summary.avg_tokens_per_call);
  qs('localAvgSession').textContent = tokens(report.summary.avg_tokens_per_session);
  qs('localReasoningRatio').textContent = pct(report.summary.reasoning_ratio);
  renderLocalOverview(report);
  renderDaily(report.daily || [], 'localDailyChart', 'localDailyStatus', 'localDailyBody');
  renderModels(report.models || [], 'localModelsBody');
  renderTools(report.tools || [], 'localToolsBody', false);
  renderSessions(report.sessions || []);
  renderLocalSections();
}

function renderTeam(report) {
  state.teamReport = report;
  qs('rangeMeta').textContent = `Team: ${report.range.start_date} to ${report.range.end_date} (${report.range.timezone})`;
  renderTeamFilter(report.teams || []);
  renderToolFilter(report.tools || []);
  qs('teamTotalTokens').textContent = tokens(report.summary.total_tokens);
  qs('teamNetTokens').textContent = tokens(report.summary.net_tokens);
  qs('teamUsers').textContent = tokens(report.summary.users);
  qs('teamDevices').textContent = tokens(report.summary.devices);
  qs('teamCalls').textContent = tokens(report.summary.usage_event_count);
  qs('teamAvgCall').textContent = tokens(report.summary.avg_tokens_per_call);
  qs('teamCacheRate').textContent = pct(report.summary.cache_hit_rate);
  qs('teamReasoningRatio').textContent = pct(report.summary.reasoning_ratio);
  renderTeamOverview(report);
  renderDaily(report.daily || [], 'teamDailyChart', 'teamDailyStatus', 'teamDailyBody');
  renderTeamModels(report.team_models || []);
  renderTools(report.tools || [], 'teamToolsBody', true);
  renderUsers(report.users || []);
  renderDevices(report.devices || []);
  renderUploadHealth(report.upload_health || []);
  renderTeamSections();
}

function renderLocalOverview(report) {
  const daily = report.daily || [];
  const comparison = report.comparison;
  qs('localOverviewStatus').textContent = comparison
    ? `${comparison.current.label}: ${comparison.current.start_date} to ${comparison.current.end_date}`
    : (daily.length ? `${daily.length} days` : 'No data');
  qs('localOverviewChart').innerHTML = comparison
    ? renderPeriodComparisonChart(comparison.daily || [])
    : renderDailyLineChart(daily);
  if (comparison) {
    renderComparisonRows(comparison.models || [], 'localOverviewModelsBody', 'model');
  } else {
    renderOverviewModels(report.models || [], 'localOverviewModelsBody');
  }
}

function renderTeamOverview(report) {
  const label = state.teamId ? `Team ${state.teamId}` : 'All Teams';
  const comparison = report.comparison;
  qs('teamOverviewStatus').textContent = comparison
    ? `${label}: ${comparison.current.start_date} to ${comparison.current.end_date}`
    : label;
  qs('teamOverviewTitle').textContent = comparison
    ? `${comparison.current.label} vs ${comparison.previous.label}`
    : 'Period Comparison';
  qs('teamComparisonChartTitle').textContent = comparison
    ? `${comparison.current.label} vs ${comparison.previous.label}`
    : 'Current vs Previous';
  renderPeriodComparison(comparison);
  renderOverviewHealth(report.upload_health || []);
}

function renderPeriodComparison(comparison) {
  if (!comparison) {
    qs('teamWeekTokens').textContent = '0';
    qs('teamWeekTokensDelta').textContent = 'No comparison data';
    qs('teamWeekChart').innerHTML = '<div class="empty">No weekly trend yet</div>';
    qs('teamOverviewUsersBody').innerHTML = '<tr><td colspan="4" class="empty">No user changes yet</td></tr>';
    qs('teamOverviewModelsBody').innerHTML = '<tr><td colspan="4" class="empty">No model changes yet</td></tr>';
    qs('teamOverviewToolsBody').innerHTML = '<tr><td colspan="4" class="empty">No tool changes yet</td></tr>';
    return;
  }
  qs('teamWeekTokens').textContent = tokens(comparison.current.total_tokens);
  setTrendNote('teamWeekTokensDelta', comparison.delta.total_tokens, comparison.delta.total_tokens_rate, comparison.previous.label);
  qs('teamWeekCalls').textContent = tokens(comparison.current.usage_event_count);
  setTrendNote('teamWeekCallsDelta', comparison.delta.usage_event_count, undefined, comparison.previous.label);
  qs('teamWeekSessions').textContent = tokens(comparison.current.sessions);
  setTrendNote('teamWeekSessionsDelta', comparison.delta.sessions, undefined, comparison.previous.label);
  qs('teamWeekUsers').textContent = tokens(comparison.current.users);
  setTrendNote('teamWeekUsersDelta', comparison.delta.users, undefined, comparison.previous.label);
  qs('teamWeekChart').innerHTML = renderPeriodComparisonChart(comparison.daily || []);
  renderComparisonRows(comparison.users || [], 'teamOverviewUsersBody', 'user');
  renderComparisonRows(comparison.models || [], 'teamOverviewModelsBody', 'model');
  renderComparisonRows(comparison.tools || [], 'teamOverviewToolsBody', 'tool');
}

function setTrendNote(id, delta, rate, previousLabel = 'previous') {
  const element = qs(id);
  const sign = delta > 0 ? '+' : '';
  element.textContent = rate === undefined
    ? `${sign}${tokens(delta)} vs ${previousLabel}`
    : `${sign}${tokens(delta)} (${signedPct(rate)}) vs ${previousLabel}`;
  element.classList.toggle('trend-up', delta > 0);
  element.classList.toggle('trend-down', delta < 0);
}

function renderComparisonRows(rows, bodyId, kind) {
  const visibleRows = rows.slice(0, 6);
  const empty = kind === 'user' ? 'No user changes yet' : kind === 'tool' ? 'No tool changes yet' : 'No model changes yet';
  qs(bodyId).innerHTML = visibleRows.length ? visibleRows.map((row) => `<tr>
    <td>${escapeHtml(comparisonRowLabel(row, kind))}</td>
    <td>${tokens(row.current_total_tokens)}</td>
    <td>${tokens(row.previous_total_tokens)}</td>
    <td class="${row.delta_total_tokens < 0 ? 'trend-down' : row.delta_total_tokens > 0 ? 'trend-up' : ''}">${row.delta_total_tokens > 0 ? '+' : ''}${tokens(row.delta_total_tokens)}</td>
  </tr>`).join('') : '<tr><td colspan="4" class="empty">No users yet</td></tr>';
  if (!visibleRows.length) {
    qs(bodyId).innerHTML = `<tr><td colspan="4" class="empty">${empty}</td></tr>`;
  }
}

function renderOverviewModels(rows, bodyId) {
  const visibleRows = rows.slice(0, 6);
  qs(bodyId).innerHTML = visibleRows.length ? visibleRows.map((row) => `<tr>
    <td>${escapeHtml(row.model)}</td>
    <td>${tokens(row.total_tokens)}</td>
    <td>${tokens(row.usage_event_count)}</td>
    <td>${pct(row.cache_hit_rate)}</td>
  </tr>`).join('') : '<tr><td colspan="4" class="empty">No model usage yet</td></tr>';
}

function renderPeriodComparisonChart(rows) {
  if (!rows.length) {
    return '<div class="empty">No comparison trend yet</div>';
  }
  const width = 720;
  const height = 240;
  const padX = 34;
  const padTop = 20;
  const padBottom = 38;
  const plotWidth = width - padX * 2;
  const plotHeight = height - padTop - padBottom;
  const max = Math.max(1, ...rows.flatMap((row) => [row.current_total_tokens || 0, row.previous_total_tokens || 0]));
  const xFor = (index) => rows.length === 1 ? width / 2 : padX + (index / (rows.length - 1)) * plotWidth;
  const yFor = (value) => padTop + plotHeight - ((value || 0) / max) * plotHeight;
  const lineFor = (field) => rows.map((row, index) => `${Math.round(xFor(index) * 100) / 100},${Math.round(yFor(row[field]) * 100) / 100}`).join(' ');
  const labels = rows.map((row, index) => `<text class="daily-axis-label" x="${xFor(index)}" y="${height - 12}" text-anchor="middle">${escapeHtml(row.label)}</text>`).join('');
  const markers = rows.map((row, index) => {
    const x = xFor(index);
    return `<circle class="daily-point" cx="${x}" cy="${yFor(row.current_total_tokens)}" r="4"><title>${escapeHtml(row.current_date)}: ${tokens(row.current_total_tokens)} tokens</title></circle>
      <circle class="daily-point previous-point" cx="${x}" cy="${yFor(row.previous_total_tokens)}" r="4"><title>${escapeHtml(row.previous_date)}: ${tokens(row.previous_total_tokens)} tokens</title></circle>`;
  }).join('');
  return `<svg class="daily-line-chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="Period comparison total token trend">
    <line class="daily-axis" x1="${padX}" y1="${padTop + plotHeight}" x2="${width - padX}" y2="${padTop + plotHeight}"></line>
    <line class="daily-grid" x1="${padX}" y1="${padTop}" x2="${width - padX}" y2="${padTop}"></line>
    <polyline class="daily-line" points="${lineFor('current_total_tokens')}"></polyline>
    <polyline class="daily-line previous-line" points="${lineFor('previous_total_tokens')}"></polyline>
    ${markers}
    ${labels}
  </svg>`;
}

function renderOverviewHealth(rows) {
  const visibleRows = rows.slice(0, 6);
  qs('teamOverviewHealthBody').innerHTML = visibleRows.length ? visibleRows.map((row) => `<tr>
    <td>${escapeHtml(row.device_id)}</td>
    <td>${escapeHtml(row.user_id)}</td>
    <td><span class="health health-${escapeHtml(row.health_status)}">${escapeHtml(row.health_status)}</span></td>
    <td>${formatDuration(row.upload_gap_seconds)}</td>
  </tr>`).join('') : '<tr><td colspan="4" class="empty">No upload data yet</td></tr>';
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

function comparisonRowLabel(row, kind) {
  if (kind === 'user') return row.display_name || row.user_id;
  if (kind === 'tool') return toolLabel(row.tool);
  return row.model;
}

function renderToolFilter(rows) {
  const select = qs('toolFilter');
  const current = toolForView();
  const known = new Set(state.toolOptions);
  rows.forEach((row) => {
    if (row.tool) {
      known.add(row.tool);
    }
  });
  state.toolOptions = [...known].sort();
  select.innerHTML = '<option value="">All Tools</option>' + state.toolOptions
    .map((tool) => `<option value="${escapeHtml(tool)}">${escapeHtml(toolLabel(tool))}</option>`)
    .join('');
  select.value = state.toolOptions.includes(current) ? current : '';
  setToolForView(select.value);
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
  qs(bodyId).innerHTML = rows.length ? rows.map((row) => `<tr><td>${toolList(row.tools)}</td><td>${escapeHtml(row.model)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.input_tokens)}</td><td>${tokens(row.cached_input_tokens)}</td><td>${tokens(row.output_tokens)}</td><td>${tokens(row.reasoning_output_tokens)}</td><td>${tokens(row.session_count ?? row.sessions)}</td><td>${tokens(row.usage_event_count)}</td><td>${tokens(row.avg_tokens_per_session)}</td><td>${tokens(row.avg_tokens_per_call)}</td><td>${pct(row.cache_hit_rate)}</td><td>${pct(row.reasoning_ratio)}</td><td>${formatDuration(row.active_seconds)}</td></tr>`).join('') : '<tr><td colspan="14" class="empty">No model usage yet</td></tr>';
}

function renderTools(rows, bodyId, teamView) {
  qs(bodyId).innerHTML = rows.length ? rows.map((row) => `<tr>
    <td>${escapeHtml(toolLabel(row.tool))}</td>
    <td>${tokens(row.total_tokens)}</td>
    <td>${tokens(row.input_tokens)}</td>
    <td>${tokens(row.cached_input_tokens)}</td>
    <td>${tokens(row.output_tokens)}</td>
    <td>${tokens(row.reasoning_output_tokens)}</td>
    <td>${tokens(row.sessions)}</td>
    ${teamView ? `<td>${tokens(row.users)}</td><td>${tokens(row.devices)}</td>` : ''}
    <td>${tokens(row.usage_event_count)}</td>
    <td>${tokens(row.avg_tokens_per_call)}</td>
    <td>${pct(row.cache_hit_rate)}</td>
    <td>${pct(row.reasoning_ratio)}</td>
    <td>${formatDuration(row.active_seconds)}</td>
  </tr>`).join('') : `<tr><td colspan="${teamView ? 14 : 12}" class="empty">No tool usage yet</td></tr>`;
}

function renderTeamModels(rows) {
  const sorted = [...rows].sort(teamModelComparator);
  const visibleRows = sorted.slice(0, 50);
  qs('teamModelsStatus').textContent = sorted.length > 50 ? `Top 50 of ${tokens(sorted.length)}` : `${tokens(sorted.length)} rows`;
  qs('teamModelsBody').innerHTML = visibleRows.length ? visibleRows.map((row) => `<tr>
    <td>${escapeHtml(row.date)}</td>
    <td>${escapeHtml(row.team_id)}</td>
    <td>${escapeHtml(row.display_name || row.user_id)}</td>
    <td>${toolPill(row.tool)}</td>
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
  </tr>`).join('') : '<tr><td colspan="18" class="empty">No model usage yet</td></tr>';
}

function teamModelComparator(left, right) {
  const field = state.teamModelSort;
  const dir = state.teamModelDir === 'asc' ? 1 : -1;
  const a = comparable(left[field]);
  const b = comparable(right[field]);
  if (a < b) return -1 * dir;
  if (a > b) return 1 * dir;
  if ((left.date || '') !== (right.date || '')) return String(right.date || '').localeCompare(String(left.date || ''));
  if ((left.tool || '') !== (right.tool || '')) return String(left.tool || '').localeCompare(String(right.tool || ''));
  return String(left.model || '').localeCompare(String(right.model || ''));
}

function comparable(value) {
  return typeof value === 'number' ? value : String(value || '').toLowerCase();
}

function renderSessions(rows) {
  const visibleRows = rows.slice(0, 50);
  qs('localSessionsStatus').textContent = rows.length > 50 ? `Latest 50 of ${tokens(rows.length)}` : `${tokens(rows.length)} rows`;
  qs('localSessionsBody').innerHTML = visibleRows.length ? visibleRows.map((row) => `<tr><td>${toolList(row.tools)}</td><td>${escapeHtml(row.session_id)}</td><td>${tokens(row.total_tokens)}</td><td>${tokens(row.net_tokens)}</td><td>${tokens(row.usage_event_count)}</td><td>${tokens(row.avg_tokens_per_call)}</td><td>${formatDuration(row.active_seconds)}</td><td>${escapeHtml(formatDateTime(row.started_at))}</td><td>${escapeHtml(formatDateTime(row.ended_at))}</td><td>${escapeHtml((row.models || []).join(', '))}</td></tr>`).join('') : '<tr><td colspan="10" class="empty">No sessions yet</td></tr>';
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
  return qs('loadStatus');
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

function toolLabel(tool) {
  if (tool === 'claude-code') return 'Claude Code';
  if (tool === 'codex') return 'Codex';
  return tool || 'Unknown';
}

function toolPill(tool) {
  const name = toolLabel(tool);
  return `<span class="tool-pill tool-${escapeHtml(String(tool || 'unknown').replace(/[^a-z0-9-]/gi, '-'))}">${escapeHtml(name)}</span>`;
}

function toolList(tools) {
  const values = Array.isArray(tools) && tools.length ? tools : [''];
  return values.map((tool) => toolPill(tool)).join(' ');
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
