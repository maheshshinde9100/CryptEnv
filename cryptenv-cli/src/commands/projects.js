const axios = require('axios');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, loadCryptenvJson } = require('../utils/config');
const ui = require('../utils/ui');

async function resolveWorkspace(opts) {
  const workspaceId = opts.workspace || loadCryptenvJson().workspaceId;
  if (!workspaceId) {
    console.error(ui.error('Specify --workspace <id> or run: cryptenv use'));
    process.exit(1);
  }
  const headers = await getAuthHeaders();
  if (!headers) {
    console.error(ui.error('Not authenticated. Run: cryptenv login'));
    process.exit(1);
  }
  return { workspaceId, headers };
}

async function list(opts = {}) {
  const { workspaceId, headers } = await resolveWorkspace(opts);
  try {
    const { data } = await axios.get(`${getApiUrl()}/workspaces/${workspaceId}/projects`, { headers });
    if (opts.json) {
      console.log(JSON.stringify(data, null, 2));
      return;
    }
    if (!data.length) {
      console.log(ui.hint('No projects yet. Create one with: cryptenv project create <name>'));
      return;
    }
    const rows = data.map((p) => [p.id, p.name, p.description || '']);
    console.log('');
    console.log(ui.table(['ID', 'Project', 'Description'], rows));
  } catch (e) {
    console.error(ui.error(e.response?.data?.message || e.message));
    process.exitCode = 1;
  }
}

async function create(name, opts = {}) {
  const { workspaceId, headers } = await resolveWorkspace(opts);
  try {
    const { data } = await axios.post(
      `${getApiUrl()}/workspaces/${workspaceId}/projects`,
      { name, description: opts.description || '' },
      { headers }
    );
    console.log(ui.success(`Created project ${data.name} [${data.id}]`));
  } catch (e) {
    console.error(ui.error(e.response?.data?.message || e.message));
    process.exitCode = 1;
  }
}

module.exports = { list, create };