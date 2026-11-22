const axios = require('axios');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, loadCryptenvJson } = require('../utils/config');
const ui = require('../utils/ui');

async function authHeaders() {
  const headers = await getAuthHeaders();
  if (!headers) {
    console.error(ui.error('Not authenticated. Run: cryptenv login'));
    process.exit(1);
  }
  return headers;
}

async function request(justification, opts = {}) {
  const headers = await authHeaders();
  const config = loadCryptenvJson();
  const workspaceId = opts.workspace || config.workspaceId;
  const environmentId = opts.env || config.environmentId;
  if (!workspaceId || !environmentId) {
    console.error(ui.error('Workspace and environment required (--workspace/--env or .cryptenv.json)'));
    process.exit(1);
  }
  const hours = Number(opts.hours || 2);
  if (hours < 1 || hours > 24) {
    console.error(ui.error('--hours must be between 1 and 24'));
    process.exitCode = 1;
    return;
  }
  try {
    const { data } = await axios.post(
      `${getApiUrl()}/jit/request`,
      { workspaceId, environmentId, justification, durationHours: hours },
      { headers }
    );
    console.log(ui.success(`JIT request ${data.id ?? ''} submitted`));
    console.log(ui.keyValue([
      ['Status', data.status || 'PENDING'],
      ['Expires', data.endsAt || 'after approval'],
    ]));
  } catch (e) {
    console.error(ui.error(e.response?.data?.message || e.message));
    process.exitCode = 1;
  }
}

async function approve(id) {
  const headers = await authHeaders();
  try {
    await axios.post(`${getApiUrl()}/jit/${id}/approve`, {}, { headers });
    console.log(ui.success(`Approved JIT request ${id}`));
  } catch (e) {
    console.error(ui.error(e.response?.data?.message || e.message));
    process.exitCode = 1;
  }
}

async function deny(id) {
  const headers = await authHeaders();
  try {
    await axios.post(`${getApiUrl()}/jit/${id}/deny`, {}, { headers });
    console.log(ui.success(`Denied JIT request ${id}`));
  } catch (e) {
    console.error(ui.error(e.response?.data?.message || e.message));
    process.exitCode = 1;
  }
}

async function list(opts = {}) {
  const headers = await authHeaders();
  const workspaceId = opts.workspace || loadCryptenvJson().workspaceId;
  if (!workspaceId) {
    console.error(ui.error('Specify --workspace <id> or run: cryptenv use'));
    process.exit(1);
  }
  try {
    const { data } = await axios.get(`${getApiUrl()}/jit/workspace/${workspaceId}`, { headers });
    if (opts.json) {
      console.log(JSON.stringify(data, null, 2));
      return;
    }
    if (!data.length) {
      console.log(ui.hint('No JIT requests in this workspace.'));
      return;
    }
    const tone = (status) => {
      if (status === 'APPROVED') return 'ok';
      if (status === 'DENIED' || status === 'EXPIRED') return 'danger';
      return 'warn';
    };
    const rows = data.map((r) => [
      r.id,
      ui.badge(r.status || 'PENDING', tone(r.status)),
      `env ${r.environmentId ?? ''}`,
      r.endsAt ? new Date(r.endsAt).toLocaleString() : '',
      r.justification || '',
    ]);
    console.log('');
    console.log(ui.table(['ID', 'Status', 'Scope', 'Ends', 'Justification'], rows));
  } catch (e) {
    console.error(ui.error(e.response?.data?.message || e.message));
    process.exitCode = 1;
  }
}

module.exports = { request, approve, deny, list };