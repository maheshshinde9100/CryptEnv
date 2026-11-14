const axios = require('axios');
const chalk = require('chalk');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, loadCryptenvJson } = require('../utils/config');

async function authHeaders() {
  const headers = await getAuthHeaders();
  if (!headers) {
    console.error(chalk.red('Not authenticated. Run: cryptenv login'));
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
    console.error(chalk.red('workspace and environment required (--workspace/--env or .cryptenv.json)'));
    process.exit(1);
  }
  const hours = Number(opts.hours || 2);
  try {
    const { data } = await axios.post(
      `${getApiUrl()}/jit/request`,
      { workspaceId, environmentId, justification, durationHours: hours },
      { headers }
    );
    console.log(`JIT request ${chalk.cyan(data.id ?? '')} submitted (expires ${data.expiresAt || 'n/a'})`);
  } catch (e) {
    console.error(chalk.red(e.response?.data?.message || e.message));
    process.exit(1);
  }
}

async function approve(id) {
  const headers = await authHeaders();
  try {
    await axios.post(`${getApiUrl()}/jit/${id}/approve`, {}, { headers });
    console.log(chalk.green(`Approved JIT request ${id}`));
  } catch (e) {
    console.error(chalk.red(e.response?.data?.message || e.message));
    process.exit(1);
  }
}

async function deny(id) {
  const headers = await authHeaders();
  try {
    await axios.post(`${getApiUrl()}/jit/${id}/deny`, {}, { headers });
    console.log(chalk.green(`Denied JIT request ${id}`));
  } catch (e) {
    console.error(chalk.red(e.response?.data?.message || e.message));
    process.exit(1);
  }
}

async function list(opts = {}) {
  const headers = await authHeaders();
  const workspaceId = opts.workspace || loadCryptenvJson().workspaceId;
  if (!workspaceId) {
    console.error(chalk.red('Specify --workspace <id> or run: cryptenv use'));
    process.exit(1);
  }
  try {
    const { data } = await axios.get(`${getApiUrl()}/jit/workspace/${workspaceId}`, { headers });
    if (opts.json) {
      console.log(JSON.stringify(data, null, 2));
      return;
    }
    if (!data.length) {
      console.log(chalk.yellow('No JIT requests found'));
      return;
    }
    data.forEach((r) => {
      console.log(`  ${chalk.cyan(r.id)}  ${r.status || ''}  env=${r.environmentId ?? ''}  ${r.justification || ''}`);
    });
  } catch (e) {
    console.error(chalk.red(e.response?.data?.message || e.message));
    process.exit(1);
  }
}

module.exports = { request, approve, deny, list };
