const axios = require('axios');
const inquirer = require('inquirer');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, loadCryptenvJson, saveCryptenvJson } = require('../utils/config');
const ui = require('../utils/ui');

function coerceId(id) {
  const n = Number(id);
  return Number.isNaN(n) ? id : n;
}

async function requireAuth() {
  const headers = await getAuthHeaders();
  if (!headers) {
    console.error(ui.error('Not authenticated. Run: cryptenv login'));
    process.exit(1);
  }
  return headers;
}

function envName(environment) {
  return environment.displayName || environment.label || environment.name;
}

/** Set the active workspace/environment for this directory (writes .cryptenv.json). */
async function use(opts = {}) {
  const headers = await requireAuth();
  const api = getApiUrl();

  try {
    let workspaceId = opts.workspace;
    if (!workspaceId) {
      const { data } = await axios.get(`${api}/workspaces`, { headers });
      if (!data.length) {
        console.error(ui.error('No workspaces yet. Create one with: cryptenv workspaces create <name> -k <key>'));
        process.exitCode = 1;
        return;
      }
      if (data.length === 1 || opts.yes) {
        workspaceId = data[0].id;
      } else {
        const ans = await inquirer.prompt([
          {
            type: 'list',
            name: 'id',
            message: 'Workspace:',
            choices: data.map((w) => ({ name: `${w.name}  [${w.id}]`, value: w.id })),
          },
        ]);
        workspaceId = ans.id;
      }
    }

    const { data: envs } = await axios.get(`${api}/environments/workspace/${workspaceId}`, { headers });
    if (!envs.length) {
      console.error(ui.error('This workspace has no environments. Create one in the dashboard first.'));
      process.exitCode = 1;
      return;
    }

    let environmentId = opts.env;
    if (environmentId) {
      const match = envs.find((e) => String(e.id) === String(environmentId));
      if (!match) {
        console.error(ui.error(`Environment ${environmentId} does not belong to workspace ${workspaceId}`));
        process.exitCode = 1;
        return;
      }
    } else if (envs.length === 1 || opts.yes) {
      environmentId = envs[0].id;
    } else {
      const ans = await inquirer.prompt([
        {
          type: 'list',
          name: 'id',
          message: 'Environment:',
          choices: envs.map((e) => ({ name: `${envName(e)}  [${e.id}]`, value: e.id })),
        },
      ]);
      environmentId = ans.id;
    }

    const selectedEnv = envs.find((e) => String(e.id) === String(environmentId));
    saveCryptenvJson({ workspaceId: coerceId(workspaceId), environmentId: coerceId(environmentId) });
    console.log('');
    console.log(ui.success('Context saved to .cryptenv.json (no secrets stored)'));
    console.log(
      ui.keyValue([
        ['Workspace', `${workspaceId}`],
        ['Environment', `${envName(selectedEnv)} (${environmentId})`],
      ])
    );
    console.log(ui.nextStep('cryptenv secrets ls'));
  } catch (error) {
    console.error(ui.error(error.response?.data?.message || error.message));
    process.exitCode = 1;
  }
}

/** List environments for the active (or given) workspace. */
async function envs(opts = {}) {
  const headers = await requireAuth();
  const api = getApiUrl();
  const workspaceId = opts.workspace || loadCryptenvJson().workspaceId;
  if (!workspaceId) {
    console.error(ui.error('Specify --workspace <id> or run: cryptenv use'));
    process.exit(1);
  }
  try {
    const { data } = await axios.get(`${api}/environments/workspace/${workspaceId}`, { headers });
    if (opts.json) {
      console.log(JSON.stringify(data, null, 2));
      return;
    }
    if (!data.length) {
      console.log(ui.warn('No environments found in this workspace.'));
      return;
    }
    const rows = data.map((e) => [
      e.id,
      envName(e),
      e.isActive === false ? ui.badge('inactive', 'warn') : ui.badge('active', 'ok'),
      e.isProtected ? ui.badge('protected', 'danger') : '',
    ]);
    console.log('');
    console.log(ui.table(['ID', 'Environment', 'State', 'Access'], rows));
  } catch (e) {
    console.error(ui.error(e.response?.data?.message || e.message));
    process.exitCode = 1;
  }
}

module.exports = { use, envs };