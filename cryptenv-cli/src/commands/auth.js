const axios = require('axios');
const chalk = require('chalk');
const ora = require('ora');
const inquirer = require('inquirer');
const config = require('../utils/config');
const keystore = require('../utils/keystore');

/** Store keys: token (access), refresh (refresh token), user (cached profile). */

async function login() {
  try {
    const answers = await inquirer.prompt([
      {
        type: 'input',
        name: 'email',
        message: 'Email:',
        validate: (input) => input.length > 0 || 'Email is required',
      },
      {
        type: 'password',
        name: 'password',
        message: 'Password:',
        mask: '*',
        validate: (input) => input.length > 0 || 'Password is required',
      },
    ]);

    const spinner = ora('Authenticating...').start();
    const apiUrl = config.getApiUrl();

    try {
      const response = await axios.post(`${apiUrl}/auth/login`, {
        email: answers.email,
        password: answers.password,
      });

      let data = response.data;

      if (data.mfaRequired) {
        spinner.stop();
        const mfa = await inquirer.prompt([
          {
            type: 'input',
            name: 'code',
            message: 'MFA code (TOTP or recovery code):',
            validate: (input) => input.length > 0 || 'Code is required',
          },
        ]);
        const verify = await axios.post(`${apiUrl}/auth/mfa/verify`, {
          mfaToken: data.mfaToken,
          code: mfa.code,
        });
        data = verify.data;
        spinner.start('Finalizing...');
      }

      const user = {
        id: data.userId,
        username: data.username,
        email: data.email,
      };

      await keystore.setSecret('token', data.token);
      if (data.refreshToken) await keystore.setSecret('refresh', data.refreshToken);
      await keystore.setSecret('user', JSON.stringify(user));

      spinner.succeed(chalk.green('Login successful!'));
      console.log(chalk.blue(`Welcome, ${user.email}!`));
      if (!keystore.usingKeyring()) {
        console.log(chalk.yellow('OS keyring unavailable — token stored in encrypted file (~/.cryptenv).'));
      }
    } catch (error) {
      spinner.fail(chalk.red('Login failed'));
      if (error.response) {
        console.error(chalk.red(error.response.data.message || 'Invalid credentials'));
      } else {
        console.error(chalk.red('Connection error. Please check your API URL.'));
      }
      process.exit(1);
    }
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
    process.exit(1);
  }
}

async function register() {
  try {
    const answers = await inquirer.prompt([
      {
        type: 'input',
        name: 'email',
        message: 'Email:',
        validate: (input) => input.includes('@') || 'Please enter a valid email',
      },
      {
        type: 'input',
        name: 'username',
        message: 'Username:',
        validate: (input) => input.length >= 3 || 'Username must be at least 3 characters',
      },
      {
        type: 'password',
        name: 'password',
        message: 'Password:',
        mask: '*',
        validate: (input) => input.length >= 8 || 'Password must be at least 8 characters',
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
      },
    ]);

    const spinner = ora('Registering...').start();
    const apiUrl = config.getApiUrl();

    try {
      const response = await axios.post(`${apiUrl}/auth/register`, {
        email: answers.email,
        username: answers.username,
        password: answers.password,
        firstName: answers.firstName || undefined,
        lastName: answers.lastName || undefined,
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
      process.exit(1);
    }
  } catch (error) {
    console.error(chalk.red('Error:', error.message));
    process.exit(1);
  }
}

async function logout(opts = {}) {
  try {
    const apiUrl = config.getApiUrl();
    const token = await getAuthToken();

    if (token) {
      try {
        await axios.post(
          `${apiUrl}/auth/logout`,
          {},
          {
            headers: { Authorization: `Bearer ${token}` },
            params: opts.everywhere ? { everywhere: true } : undefined,
          }
        );
        console.log(chalk.green(opts.everywhere ? 'Signed out everywhere' : 'Signed out'));
      } catch {
        console.log(chalk.yellow('Server logout failed — clearing local credentials only.'));
      }
    }

    await keystore.deleteSecret('token');
    await keystore.deleteSecret('refresh');
    await keystore.deleteSecret('user');
    console.log(chalk.green('Logged out successfully'));
  } catch (error) {
    console.error(chalk.red('Logout failed'), chalk.red(error.message));
    process.exit(1);
  }
}

async function getAuthToken() {
  if (process.env.CRYPTENV_TOKEN) return process.env.CRYPTENV_TOKEN;
  try {
    return await keystore.getSecret('token');
  } catch {
    return null;
  }
}

async function getRefreshToken() {
  try {
    return await keystore.getSecret('refresh');
  } catch {
    return null;
  }
}

async function getUser() {
  try {
    const userStr = await keystore.getSecret('user');
    return userStr ? JSON.parse(userStr) : null;
  } catch {
    return null;
  }
}

/** Returns headers for either JWT or API-key auth, or null when unauthenticated. */
async function getAuthHeaders() {
  const token = await getAuthToken();
  if (token) return { Authorization: `Bearer ${token}` };
  const apiKey = process.env.CRYPTENV_API_KEY;
  if (apiKey) return { 'X-API-Key': apiKey };
  return null;
}

module.exports = {
  login,
  register,
  logout,
  getAuthToken,
  getRefreshToken,
  getUser,
  getAuthHeaders,
};
