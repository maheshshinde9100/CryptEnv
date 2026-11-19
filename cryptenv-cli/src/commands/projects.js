const axios = require('axios');
const chalk = require('chalk');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, loadCryptenvJson } = require('../utils/config');

async function resolveWorkspace(opts) {
  const workspaceId = opts.workspace || loadCryptenvJson().workspaceId;
  if (!workspaceId) {
    console.error(chalk.red('Specify --workspace <id> or run: cryptenv use'));
    process.exit(1);
  }
  const headers = await getAuthHeaders();
  if (!headers) {
    console.error(chalk.red('Not authenticated. Run: cryptenv login'));
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
      console.log(chalk.yellow('No projects found'));
      return;
    }
    data.forEach((p) => console.log(`  ${chalk.cyan(p.id)}  ${p.name}${p.description ? chalk.gray(' — ' + p.description) : ''}`));
  } catch (e) {
    console.error(chalk.red(e.response?.data?.message || e.message));
    process.exit(1);
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
    console.log(`Created project ${chalk.cyan(data.id)} ${data.name}`);
  } catch (e) {
    console.error(chalk.red(e.response?.data?.message || e.message));
    process.exit(1);
  }
}

module.exports = { list, create };
