const qs = (id) => document.getElementById(id);

async function loadTokens() {
  const status = qs('pageStatus');
  status.textContent = 'Loading token bindings...';
  const response = await fetch('/api/admin/device-tokens', { cache: 'no-store' });
  if (response.status === 401) {
    location.href = '/admin-login.html';
    return;
  }
  if (!response.ok) {
    status.textContent = `Failed to load: HTTP ${response.status}`;
    return;
  }
  const data = await response.json();
  renderTokens(data.device_tokens || []);
  status.textContent = `${(data.device_tokens || []).length} token bindings`;
}

qs('createForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  const payload = {
    team_id: qs('teamId').value.trim(),
    user_id: qs('userId').value.trim(),
    device_id: qs('deviceId').value.trim(),
    device_name: qs('deviceName').value.trim()
  };
  const response = await fetch('/api/admin/device-tokens', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (response.status === 401) {
    location.href = '/admin-login.html';
    return;
  }
  const data = await response.json();
  if (!response.ok) {
    qs('pageStatus').textContent = data.error?.message || `HTTP ${response.status}`;
    return;
  }
  const created = qs('createdToken');
  created.classList.remove('hidden');
  created.innerHTML = `Device token created for ${escapeHtml(payload.user_id)} / ${escapeHtml(payload.device_id)}.<code>${escapeHtml(data.device_token)}</code>`;
  qs('createForm').reset();
  loadTokens();
});

function renderTokens(rows) {
  qs('tokensBody').innerHTML = rows.length ? rows.map((row) => `<tr>
    <td>${escapeHtml(row.team_id)}</td>
    <td>${escapeHtml(row.user_id)}</td>
    <td>${escapeHtml(row.device_id)}</td>
    <td><code>${escapeHtml(row.token_preview)}</code></td>
    <td>${escapeHtml(row.display_name)}</td>
    <td>${escapeHtml(row.status)}</td>
    <td>${escapeHtml(row.created_at)}</td>
    <td>${escapeHtml(row.last_seen_at)}</td>
    <td class="actions">
      <button type="button" data-copy-token="${escapeHtml(row.token_id)}" ${row.token_recoverable ? '' : 'disabled'}>Copy</button>
      <button type="button" class="danger" data-delete-token="${escapeHtml(row.token_id)}">Delete</button>
    </td>
  </tr>`).join('') : '<tr><td colspan="9" class="empty">No token bindings yet</td></tr>';
}

document.addEventListener('click', async (event) => {
  const copyId = event.target?.dataset?.copyToken;
  if (copyId) {
    await copyDeviceToken(copyId);
    return;
  }
  const deleteId = event.target?.dataset?.deleteToken;
  if (deleteId) {
    await deleteDeviceToken(deleteId);
  }
});

async function copyDeviceToken(tokenId) {
  const response = await fetch(`/api/admin/device-tokens/${encodeURIComponent(tokenId)}/token`, { cache: 'no-store' });
  if (response.status === 401) {
    location.href = '/admin-login.html';
    return;
  }
  const data = await response.json();
  if (!response.ok) {
    qs('pageStatus').textContent = data.error?.message || `HTTP ${response.status}`;
    return;
  }
  await navigator.clipboard.writeText(data.device_token);
  qs('pageStatus').textContent = 'Device token copied';
}

async function deleteDeviceToken(tokenId) {
  if (!confirm('Delete this device token binding?')) {
    return;
  }
  const response = await fetch(`/api/admin/device-tokens/${encodeURIComponent(tokenId)}`, { method: 'DELETE' });
  if (response.status === 401) {
    location.href = '/admin-login.html';
    return;
  }
  const data = await response.json();
  if (!response.ok) {
    qs('pageStatus').textContent = data.error?.message || `HTTP ${response.status}`;
    return;
  }
  qs('pageStatus').textContent = 'Device token binding deleted';
  loadTokens();
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

loadTokens();
