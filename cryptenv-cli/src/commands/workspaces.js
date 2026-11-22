const axios = require('axios');
const ora = require('ora');
const { getAuthHeaders } = require('./auth');
const config = require('../utils/config');
const ui = require('../utils/ui');

async function headers() {
  const auth = await getAuthHeaders();
  if (!auth) {
    console.error(ui.error('Not authenticated. Run: cryptenv login'));
    process.exit(1);
  }
  return auth;
}

async function list(opts = {}) {
  const auth = await headers();
  const spinner = ora('Fetching workspaces...').start();
  try {
    const { data } = await axios.get(`${config.getApiUrl()}/workspaces`, { headers: auth });
    spinner.succeed(ui.white(`${data.length} workspace${data.length === 1 ? '' : 's'}`));
    if (opts.json) {
      console.log(JSON.stringify(data, null, 2));
      return;
    }
    if (!data.length) {
      console.log(ui.hint('Create one with: cryptenv workspaces create <name> -k <encryption-key>'));
      return;
    }
    const rows = data.map((w) => [
      w.id,
      w.name,
      w.currentUserRole || (w.owner?.email === 'you' ? 'OWNER' : ''),
      w.hasEncryptionKey ? ui.badge('ready', 'ok') : ui.badge('no key', 'warn'),
      ui.timeAgo(w.updatedAt || w.createdAt),
    ]);
    console.log('');
    console.log(ui.table(['ID', 'Workspace', 'Role', 'Encryption', 'Updated'], rows));
  } catch (error) {
    spinner.fail(ui.danger('Failed to list workspaces'));
    console.error(ui.hint(error.response?.data?.message || error.message));
    process.exitCode = 1;
  }
}

async function create(name, options = {}) {
  const auth = await headers();
  const payload = { name };
  if (options.description) payload.description = options.description;
  if (options.key) {
    if (String(options.key).length < 16) {
      console.error(ui.error('Workspace encryption key must be at least 16 characters'));
      process.exitCode = 1;
      return;
    }
    payload.workspaceEncryptionKey = options.key;
  }
  const spinner = ora(`Creating workspace ${name}...`).start();
  try {
    const { data } = await axios.post(`${config.getApiUrl()}/workspaces`, payload, { headers: auth });
    spinner.succeed(ui.white(`Created workspace ${data.name} [${data.id}]`));
    try {
      const { data: envs } = await axios.get(`${config.getApiUrl()}/environments/workspace/${data.id}`, { headers: auth });
      if (!envs.length) {
        await axios.post(`${config.getApiUrl()}/environments`, { name: 'DEVELOPMENT', workspaceId: data.id }, { headers: auth });
        console.log(ui.hint('Added a default DEVELOPMENT environment.'));
      }
    } catch {
      console.log(ui.hint('Create an environment in the dashboard before adding secrets.'));
    }
    if (!options.key) {
      console.log(ui.warn('No encryption key supplied — set one in the dashboard before storing secrets.'));
    }
    console.log(ui.nextStep(`cryptenv use --workspace ${data.id}`));
  } catch (error) {
    spinner.fail(ui.danger('Failed to create workspace'));
    console.error(ui.hint(error.response?.data?.message || error.message));
    process.exitCode = 1;
  }
}

module.exports = { list, create };