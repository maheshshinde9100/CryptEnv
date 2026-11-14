const { spawn } = require('child_process');
const axios = require('axios');
const chalk = require('chalk');
const ora = require('ora');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, resolveContext } = require('../utils/config');

async function run(commandArgs, opts = {}) {
  try {
    const headers = await getAuthHeaders();
    if (!headers) {
      console.error(chalk.red('Not authenticated. Run: cryptenv login OR set CRYPTENV_API_KEY'));
      process.exit(1);
    }

    const ctx = resolveContext(opts);
    const spinner = ora('Fetching secrets...').start();
    const apiUrl = getApiUrl();

    try {
      const url = ctx.environmentId
        ? `${apiUrl}/secrets/environment/${ctx.environmentId}`
        : `${apiUrl}/secrets`;
      const response = await axios.get(url, { headers });

      spinner.succeed(chalk.green(`Fetched ${response.data.length} secrets`));

      const envVars = {};
      response.data.forEach((secret) => {
        envVars[secret.key] = secret.value;
      });

      const mergedEnv = { ...process.env, ...envVars };
      const [cmd, ...args] = commandArgs;

      if (!cmd) {
        console.error(chalk.red('No command specified'));
        process.exit(1);
      }

      console.log(chalk.blue(`Running: ${cmd} ${args.join(' ')}`));

      const child = spawn(cmd, args, {
        env: mergedEnv,
        stdio: 'inherit',
        shell: true,
      });

      child.on('error', (error) => {
        console.error(chalk.red(`Failed to start command: ${error.message}`));
        process.exit(1);
      });

      child.on('exit', (code) => process.exit(code ?? 1));
    } catch (error) {
      spinner.fail(chalk.red('Failed to fetch secrets'));
      if (error.response) {
        console.error(chalk.red(error.response.data.message || 'API error'));
      } else {
        console.error(chalk.red(error.message || 'Connection error'));
      }
      process.exit(1);
    }
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
    process.exit(1);
  }
}

module.exports = run;
