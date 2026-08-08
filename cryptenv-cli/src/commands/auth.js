const axios = require('axios');
const keytar = require('keytar');
const chalk = require('chalk');
const ora = require('ora');
const inquirer = require('inquirer');
const config = require('../utils/config');

const SERVICE_NAME = 'cryptenv-cli';

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
    const apiUrl = config.getApiUrl();

    try {
      const response = await axios.post(`${apiUrl}/auth/login`, {
        email: answers.email,
        password: answers.password
      });

      // Server returns: { token, type, userId, username, email }
      const data = response.data;
      const token = data.token;
      const user = {
        id: data.userId,
        username: data.username,
        email: data.email
      };

      // Store token and user info securely in OS keychain
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

async function register() {
  try {
    const answers = await inquirer.prompt([
      {
        type: 'input',
        name: 'email',
        message: 'Email:',
        validate: (input) => input.includes('@') || 'Please enter a valid email'
      },
      {
        type: 'input',
        name: 'username',
        message: 'Username:',
        validate: (input) => input.length >= 3 || 'Username must be at least 3 characters'
      },
      {
        type: 'password',
        name: 'password',
        message: 'Password:',
        mask: '*',
        validate: (input) => input.length >= 8 || 'Password must be at least 8 characters'
      },
      {
        type: 'input',
        name: 'firstName',
        message: 'First Name (optional):',
      },
      {
        type: 'input',
        name: 'lastName',
        message: 'Last Name (optional):',
      }
    ]);

    const spinner = ora('Registering...').start();
    const apiUrl = config.getApiUrl();

    try {
      const response = await axios.post(`${apiUrl}/auth/register`, {
        email: answers.email,
        username: answers.username,
        password: answers.password,
        firstName: answers.firstName || undefined,
        lastName: answers.lastName || undefined
      });

      spinner.succeed(chalk.green('Registration successful!'));
      console.log(chalk.blue(`Account created for ${response.data.email}`));
      console.log(chalk.yellow('Run: cryptenv login to authenticate'));
    } catch (error) {
      spinner.fail(chalk.red('Registration failed'));
      if (error.response) {
        console.error(chalk.red(error.response.data.message || 'Registration error'));
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
    console.error(chalk.red('Logout failed'));
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
  register,
  logout,
  getAuthToken,
  getUser
};
