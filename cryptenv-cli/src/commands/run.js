const { spawn } = require('child_process');
const axios = require('axios');
const ora = require('ora');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, resolveContext } = require('../utils/config');
const ui = require('../utils/ui');

async function run(commandArgs, opts = {}) {
  try {
    const headers = await getAuthHeaders();
    if (!headers) {
      console.error(ui.error('Not authenticated. Run: cryptenv login or set CRYPTENV_API_KEY'));
      process.exitCode = 1;
      return;
    }

    const ctx = resolveContext(opts);
    const spinner = ora('Fetching secrets...').start();
    const apiUrl = getApiUrl();

    let secrets;
    try {
      const url = ctx.environmentId
        ? `${apiUrl}/secrets/environment/${ctx.environmentId}`
        : `${apiUrl}/secrets`;
      const response = await axios.get(url, { headers });
      secrets = response.data;
    } catch (error) {
      spinner.fail(ui.danger('Failed to fetch secrets'));
      console.error(ui.hint(error.response?.data?.message || error.message || 'Connection error'));
      process.exitCode = 1;
      return;
    }

    const envVars = {};
    for (const secret of secrets) {
      if (secret.value !== null && secret.value !== undefined) envVars[secret.key] = secret.value;
    }
    const count = Object.keys(envVars).length;

    spinner.succeed(
      ui.success(
        `Injecting ${count} secret${count === 1 ? '' : 's'}${
          ctx.environmentId ? ` from environment ${ctx.environmentId}` : ' across all environments'
        }`
      )
    );
    if (!ctx.environmentId) {
      console.log(ui.warn('No environment selected — secrets from every environment were merged.'));
      console.log(ui.hint('Pin one with: cryptenv use'));
    }

    if (opts.dryRun || opts['dry-run']) {
      console.log('');
      console.log(ui.table(['Variable'], Object.keys(envVars).sort().map((key) => [key])));
      return;
    }

    const [cmd, ...args] = commandArgs;
    if (!cmd) {
      console.error(ui.error('No command specified. Usage: cryptenv run -- <command> [args...]'));
      process.exitCode = 1;
      return;
    }

    console.log(ui.info(`Running: ${ui.white([cmd, ...args].join(' '))}`));
    const child = spawn(cmd, args, {
      env: { ...process.env, ...envVars },
      stdio: 'inherit',
      shell: process.platform === 'win32',
    });

    child.on('error', (error) => {
      console.error(ui.error(`Failed to start command: ${error.message}`));
      process.exitCode = 1;
    });
    child.on('exit', (code, signal) => {
      if (signal) process.kill(process.pid, signal);
      else process.exit(code ?? 0);
    });
  } catch (error) {
    console.error(ui.error(error.message));
    process.exitCode = 1;
  }
}

module.exports = run;