const axios = require('axios');
const chalk = require('chalk');
const inquirer = require('inquirer');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, loadCryptenvJson, saveCryptenvJson } = require('../utils/config');

function coerceId(id) {
  const n = Number(id);
  return Number.isNaN(n) ? id : n;
}

async function requireAuth() {
  const headers = await getAuthHeaders();
  if (!headers) {
    console.error(chalk.red('Not authenticated. Run: cryptenv login'));
    process.exit(1);
  }
  return headers;
}

/** Set the active workspace/environment for this directory (writes .cryptenv.json). */
async function use(opts = {}) {
  const headers = await requireAuth();
  const api = getApiUrl();

  let workspaceId = opts.workspace;
  if (!workspaceId) {
    const { data } = await axios.get(`${api}/workspaces`, { headers });
    if (!data.length) {
      console.error(chalk.red('No workspaces found. Create one with: cryptenv workspaces create <name>'));
      process.exit(1);
    }
    if (data.length === 1 || opts.yes) {
      workspaceId = data[0].id;
    } else {
      const ans = await inquirer.prompt([
        {
          type: 'list',
          name: 'id',
          message: 'Workspace:',
          choices: data.map((w) => ({ name: `${w.name} [${w.id}]`, value: w.id })),
        },
      ]);
      workspaceId = ans.id;
    }
  }

  const { data: envs } = await axios.get(`${api}/environments/workspace/${workspaceId}`, { headers });
  if (!envs.length) {
    console.error(chalk.red('No environments in this workspace'));
    process.exit(1);
  }

  let environmentId = opts.env;
  if (environmentId) {
    const match = envs.find((e) => String(e.id) === String(environmentId));
    if (!match) {
      console.error(chalk.red(`Environment ${environmentId} does not belong to workspace ${workspaceId}`));
      process.exit(1);
    }
  } else if (envs.length === 1 || opts.yes) {
    environmentId = envs[0].id;
  } else {
    const ans = await inquirer.prompt([
      {
        type: 'list',
        name: 'id',
        message: 'Environment:',
        choices: envs.map((e) => ({
          name: `${e.displayName || e.label || e.name} [${e.id}]`,
          value: e.id,
        })),
      },
    ]);
    environmentId = ans.id;
  }

  saveCryptenvJson({ workspaceId: coerceId(workspaceId), environmentId: coerceId(environmentId) });
  console.log(chalk.green(`Context set — workspace ${workspaceId}, environment ${environmentId}`));
  console.log(chalk.gray('Saved to .cryptenv.json (safe to commit, contains no secrets)'));
}

/** List environments for the active (or given) workspace. */
async function envs(opts = {}) {
  const headers = await requireAuth();
  const api = getApiUrl();
  const workspaceId = opts.workspace || loadCryptenvJson().workspaceId;
  if (!workspaceId) {
    console.error(chalk.red('Specify --workspace <id> or run: cryptenv use'));
    process.exit(1);
  }
  try {
    const { data } = await axios.get(`${api}/environments/workspace/${workspaceId}`, { headers });
    if (opts.json) {
      console.log(JSON.stringify(data, null, 2));
      return;
    }
    if (!data.length) {
      console.log(chalk.yellow('No environments found'));
      return;
    }
    data.forEach((e) => {
      const flags = [e.isActive === false ? chalk.yellow('inactive') : null, e.isProtected ? chalk.red('protected') : null]
        .filter(Boolean)
        .join(' ');
      console.log(`  ${chalk.cyan(e.id)}  ${e.displayName || e.label || e.name}  ${flags}`);
    });
  } catch (e) {
    console.error(chalk.red(e.response?.data?.message || e.message));
    process.exit(1);
  }
}

module.exports = { use, envs };
