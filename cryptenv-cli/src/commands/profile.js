const axios = require('axios');
const chalk = require('chalk');
const ora = require('ora');
const { getAuthToken, getUser } = require('./auth');
const config = require('../utils/config');

async function profile() {
  try {
    const token = await getAuthToken();
    if (!token) {
      console.error(chalk.red('Not authenticated. Please run: cryptenv login'));
      process.exit(1);
    }

    const spinner = ora('Fetching profile...').start();
    const apiUrl = config.getApiUrl();

    try {
      // Fetch live profile from the server
      const response = await axios.get(`${apiUrl}/auth/me`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      const user = response.data;
      spinner.succeed(chalk.green('Profile fetched successfully'));

      console.log('\n' + chalk.bold('User Profile:'));
      console.log(`  ${chalk.cyan('Email')}: ${user.email}`);
      console.log(`  ${chalk.cyan('Username')}: ${user.username}`);
      console.log(`  ${chalk.cyan('First Name')}: ${user.firstName || 'N/A'}`);
      console.log(`  ${chalk.cyan('Last Name')}: ${user.lastName || 'N/A'}`);
      console.log(`  ${chalk.cyan('API Key')}: ${user.apiKey ? user.apiKey.substring(0, 12) + '...' : 'N/A'}`);
      console.log(`  ${chalk.cyan('Created At')}: ${user.createdAt ? new Date(user.createdAt).toLocaleString() : 'N/A'}`);
    } catch (error) {
      spinner.fail(chalk.red('Failed to fetch profile from server'));
      // Fallback to cached user from keytar
      const cachedUser = await getUser();
      if (cachedUser) {
        console.log('\n' + chalk.bold('Cached User Profile:'));
        console.log(`  ${chalk.cyan('Email')}: ${cachedUser.email}`);
        console.log(`  ${chalk.cyan('Username')}: ${cachedUser.username || 'N/A'}`);
      }
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
