const axios = require('axios');
const keytar = require('keytar');
const chalk = require('chalk');
const ora = require('ora');
const inquirer = require('inquirer');

const SERVICE_NAME = 'cryptenv-cli';
const API_BASE_URL = process.env.CRYPTENV_API_URL || 'http://localhost:8080/api';

async function login() {
  try {
    const answers = await inquirer.prompt([
      {
        type: 'input',
        name: 'email',
        message: 'Email:',
        validate: (input) => input.length > 0 || 'Email is required'
      },
      {
        type: 'password',
        name: 'password',
        message: 'Password:',
        mask: '*',
        validate: (input) => input.length > 0 || 'Password is required'
      }
    ]);

    const spinner = ora('Authenticating...').start();

    try {
      const response = await axios.post(`${API_BASE_URL}/auth/login`, {
        email: answers.email,
        password: answers.password
      });

      const { token, user } = response.data;

      // Store token securely
      await keytar.setPassword(SERVICE_NAME, 'token', token);
      await keytar.setPassword(SERVICE_NAME, 'user', JSON.stringify(user));

      spinner.succeed(chalk.green('Login successful!'));
      console.log(chalk.blue(`Welcome, ${user.email}!`));
    } catch (error) {
      spinner.fail(chalk.red('Login failed'));
      if (error.response) {
        console.error(chalk.red(error.response.data.message || 'Invalid credentials'));
      } else {
        console.error(chalk.red('Connection error. Please check your API URL.'));
      }
    }
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
  }
}

async function logout() {
  try {
    const spinner = ora('Logging out...').start();

    await keytar.deletePassword(SERVICE_NAME, 'token');
    await keytar.deletePassword(SERVICE_NAME, 'user');

    spinner.succeed(chalk.green('Logged out successfully'));
  } catch (error) {
    spinner.fail(chalk.red('Logout failed'));
    console.error(chalk.red('Error:', error.message));
  }
}

async function getAuthToken() {
  try {
    return await keytar.getPassword(SERVICE_NAME, 'token');
  } catch (error) {
    return null;
  }
}

async function getUser() {
  try {
    const userStr = await keytar.getPassword(SERVICE_NAME, 'user');
    return userStr ? JSON.parse(userStr) : null;
  } catch (error) {
    return null;
  }
}

module.exports = {
  login,
  logout,
  getAuthToken,
  getUser
};
