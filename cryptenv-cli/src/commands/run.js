const { spawn } = require('child_process');
const axios = require('axios');
const chalk = require('chalk');
const ora = require('ora');
const { getAuthToken } = require('./auth');

const API_BASE_URL = process.env.CRYPTENV_API_URL || 'http://localhost:8080/api';

async function run(commandArgs) {
  try {
    const token = await getAuthToken();
    if (!token) {
      console.error(chalk.red('Not authenticated. Please run: cryptenv login'));
      process.exit(1);
    }

    const spinner = ora('Fetching and decrypting secrets...').start();

    try {
      // Fetch secrets from API
      const response = await axios.get(`${API_BASE_URL}/secrets`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      spinner.succeed(chalk.green('Secrets fetched successfully'));

      // Convert secrets to environment variables
      const envVars = {};
      response.data.forEach(secret => {
        envVars[secret.key] = secret.value;
      });

      // Merge with existing environment variables
      const mergedEnv = { ...process.env, ...envVars };

      // Execute the command with injected environment variables
      const [cmd, ...args] = commandArgs;

      if (!cmd) {
        console.error(chalk.red('No command specified'));
        process.exit(1);
      }

      console.log(chalk.blue(`Running: ${cmd} ${args.join(' ')}`));

      const child = spawn(cmd, args, {
        env: mergedEnv,
        stdio: 'inherit',
        shell: true
      });

      child.on('error', (error) => {
        console.error(chalk.red(`Failed to start command: ${error.message}`));
        process.exit(1);
      });

      child.on('exit', (code) => {
        process.exit(code);
      });

    } catch (error) {
      spinner.fail(chalk.red('Failed to fetch secrets'));
      if (error.response) {
        console.error(chalk.red(error.response.data.message || 'API error'));
      } else {
        console.error(chalk.red('Connection error'));
      }
      process.exit(1);
    }
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
    process.exit(1);
  }
}

module.exports = run;
