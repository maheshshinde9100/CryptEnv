const axios = require('axios');
const chalk = require('chalk');
const ora = require('ora');
const { getAuthToken } = require('./auth');

const API_BASE_URL = process.env.CRYPTENV_API_URL || 'http://localhost:8080/api';

async function list() {
  try {
    const token = await getAuthToken();
    if (!token) {
      console.error(chalk.red('Not authenticated. Please run: cryptenv login'));
      process.exit(1);
    }

    const spinner = ora('Fetching secrets...').start();

    try {
      const response = await axios.get(`${API_BASE_URL}/secrets`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      spinner.succeed(chalk.green('Secrets fetched successfully'));

      if (response.data.length === 0) {
        console.log(chalk.yellow('No secrets found'));
        return;
      }

      console.log('\n' + chalk.bold('Secrets:'));
      response.data.forEach(secret => {
        console.log(`  ${chalk.cyan(secret.key)}: ${'•'.repeat(20)}`);
      });
    } catch (error) {
      spinner.fail(chalk.red('Failed to fetch secrets'));
      if (error.response) {
        console.error(chalk.red(error.response.data.message || 'API error'));
      } else {
        console.error(chalk.red('Connection error'));
      }
    }
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
  }
}

async function get(key) {
  try {
    const token = await getAuthToken();
    if (!token) {
      console.error(chalk.red('Not authenticated. Please run: cryptenv login'));
      process.exit(1);
    }

    const spinner = ora(`Fetching secret: ${key}...`).start();

    try {
      const response = await axios.get(`${API_BASE_URL}/secrets/${key}`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      spinner.succeed(chalk.green('Secret fetched successfully'));
      console.log(`${chalk.cyan(key)}: ${response.data.value}`);
    } catch (error) {
      spinner.fail(chalk.red('Failed to fetch secret'));
      if (error.response) {
        console.error(chalk.red(error.response.data.message || 'Secret not found'));
      } else {
        console.error(chalk.red('Connection error'));
      }
    }
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
  }
}

async function set(key, value) {
  try {
    const token = await getAuthToken();
    if (!token) {
      console.error(chalk.red('Not authenticated. Please run: cryptenv login'));
      process.exit(1);
    }

    const spinner = ora(`Setting secret: ${key}...`).start();

    try {
      await axios.post(`${API_BASE_URL}/secrets`, {
        key,
        value
      }, {
        headers: { Authorization: `Bearer ${token}` }
      });

      spinner.succeed(chalk.green('Secret set successfully'));
      console.log(`${chalk.cyan(key)}: ${'•'.repeat(20)}`);
    } catch (error) {
      spinner.fail(chalk.red('Failed to set secret'));
      if (error.response) {
        console.error(chalk.red(error.response.data.message || 'API error'));
      } else {
        console.error(chalk.red('Connection error'));
      }
    }
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
  }
}

async function deleteSecret(key) {
  try {
    const token = await getAuthToken();
    if (!token) {
      console.error(chalk.red('Not authenticated. Please run: cryptenv login'));
      process.exit(1);
    }

    const spinner = ora(`Deleting secret: ${key}...`).start();

    try {
      await axios.delete(`${API_BASE_URL}/secrets/${key}`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      spinner.succeed(chalk.green('Secret deleted successfully'));
    } catch (error) {
      spinner.fail(chalk.red('Failed to delete secret'));
      if (error.response) {
        console.error(chalk.red(error.response.data.message || 'Secret not found'));
      } else {
        console.error(chalk.red('Connection error'));
      }
    }
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
  }
}

module.exports = {
  list,
  get,
  set,
  delete: deleteSecret
};
