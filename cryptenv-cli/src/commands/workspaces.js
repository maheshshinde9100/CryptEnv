const axios = require('axios');
const chalk = require('chalk');
const ora = require('ora');
const { getAuthToken } = require('./auth');
const config = require('../utils/config');

async function list() {
  const token = await getAuthToken();
  if (!token) {
    console.error(chalk.red('Not authenticated. Run: cryptenv login'));
    process.exit(1);
  }
  const spinner = ora('Fetching workspaces...').start();
  try {
    const apiUrl = config.getApiUrl();
    const response = await axios.get(`${apiUrl}/workspaces`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    spinner.succeed(chalk.green('Workspaces'));
    if (!response.data.length) {
      console.log(chalk.yellow('No workspaces found'));
      return;
    }
    response.data.forEach((w) => {
      const keyFlag = w.hasEncryptionKey ? chalk.green('key✓') : chalk.yellow('no-key');
      console.log(`  ${chalk.cyan(w.id)}  ${w.name}  [${keyFlag}]`);
    });
  } catch (error) {
    spinner.fail(chalk.red('Failed to list workspaces'));
    console.error(chalk.red(error.response?.data?.message || error.message));
  }
}

async function create(name, options) {
  const token = await getAuthToken();
  if (!token) {
    console.error(chalk.red('Not authenticated. Run: cryptenv login'));
    process.exit(1);
  }
  const payload = { name };
  if (options.description) payload.description = options.description;
  if (options.key) {
    if (String(options.key).length < 16) {
      console.error(chalk.red('Workspace encryption key must be at least 16 characters'));
      process.exit(1);
    }
    payload.workspaceEncryptionKey = options.key;
  }
  const spinner = ora(`Creating workspace ${name}...`).start();
  try {
    const apiUrl = config.getApiUrl();
    const response = await axios.post(`${apiUrl}/workspaces`, payload, {
      headers: { Authorization: `Bearer ${token}` },
    });
    spinner.succeed(chalk.green(`Created workspace id=${response.data.id}`));
    if (!options.key) {
      console.log(chalk.yellow('Tip: set an encryption key with the dashboard or API before storing secrets.'));
    }
  } catch (error) {
    spinner.fail(chalk.red('Failed to create workspace'));
    console.error(chalk.red(error.response?.data?.message || error.message));
  }
}

module.exports = { list, create };
