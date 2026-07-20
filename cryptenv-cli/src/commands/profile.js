const axios = require('axios');
const chalk = require('chalk');
const ora = require('ora');
const { getAuthToken, getUser } = require('./auth');

const API_BASE_URL = process.env.CRYPTENV_API_URL || 'http://localhost:8080/api';

async function profile() {
  try {
    const token = await getAuthToken();
    if (!token) {
      console.error(chalk.red('Not authenticated. Please run: cryptenv login'));
      process.exit(1);
    }

    const spinner = ora('Fetching profile...').start();

    try {
      const user = await getUser();
      
      spinner.succeed(chalk.green('Profile fetched successfully'));

      console.log('\n' + chalk.bold('User Profile:'));
      console.log(`  ${chalk.cyan('Email')}: ${user.email}`);
      console.log(`  ${chalk.cyan('Name')}: ${user.name || 'N/A'}`);
      console.log(`  ${chalk.cyan('Created At')}: ${user.createdAt ? new Date(user.createdAt).toLocaleString() : 'N/A'}`);
    } catch (error) {
      spinner.fail(chalk.red('Failed to fetch profile'));
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

module.exports = profile;
