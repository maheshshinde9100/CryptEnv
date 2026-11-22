const axios = require('axios');
const inquirer = require('inquirer');
const ora = require('ora');
const config = require('../utils/config');
const keystore = require('../utils/keystore');
const ui = require('../utils/ui');

/** Store keys: token (access), refresh (refresh token), user (cached profile). */

const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/;

function normalizeEmail(value) {
  return String(value || '').trim().toLowerCase();
}

function decodeExpiry(token) {
  try {
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
    return payload.exp ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

async function safeEmailCheck(email) {
  try {
    const { data } = await axios.get(`${config.getApiUrl()}/auth/email-check`, {
      params: { email },
      timeout: 10000,
    });
    return data;
  } catch {
    return null;
  }
}

async function promptEmail(message = 'Email:') {
  for (;;) {
    const { email } = await inquirer.prompt([
      {
        type: 'input',
        name: 'email',
        message,
        validate: (input) => EMAIL_PATTERN.test(normalizeEmail(input)) || 'Enter a valid email address',
      },
    ]);
    let value = normalizeEmail(email);
    const result = await safeEmailCheck(value);

    if (result && result.valid === false) {
      console.log(ui.error(result.reason || 'That email address is not deliverable.'));
      if (result.suggestion) {
        const { use } = await inquirer.prompt([
          {
            type: 'confirm',
            name: 'use',
            message: `Did you mean ${result.suggestion}?`,
            default: true,
          },
        ]);
        if (use) value = result.suggestion;
        else continue;
      } else {
        continue;
      }
    } else if (result && result.suggestion) {
      const { use } = await inquirer.prompt([
        {
          type: 'confirm',
          name: 'use',
          message: `Did you mean ${result.suggestion}?`,
          default: true,
        },
      ]);
      if (use) value = result.suggestion;
    }
    return value;
  }
}

async function login(options = {}) {
  try {
    const email = options.email ? normalizeEmail(options.email) : await promptEmail();
    const { password } = await inquirer.prompt([
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
      let response = await axios.post(`${apiUrl}/auth/login`, { email, password });
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
        spinner.start('Verifying MFA...');
        response = await axios.post(`${apiUrl}/auth/mfa/verify`, {
          mfaToken: data.mfaToken,
          code: mfa.code.trim(),
        });
        data = response.data;
      }

      if (!data.token) {
        throw new Error('The server did not return an access token');
      }

      const user = {
        id: data.userId,
        username: data.username,
        email: data.email || email,
      };

      await keystore.setSecret('token', data.token);
      if (data.refreshToken) await keystore.setSecret('refresh', data.refreshToken);
      await keystore.setSecret('user', JSON.stringify(user));

      spinner.succeed(ui.white(`Signed in as ${ui.white(user.email)}`));
      if (!keystore.usingKeyring()) {
        console.log(ui.hint('OS keyring unavailable — credentials kept in an encrypted file at ~/.cryptenv.'));
      }
      if (!config.loadCryptenvJson().environmentId) {
        console.log(ui.hint('Pick a workspace and environment with: cryptenv use'));
      }
    } catch (error) {
      spinner.fail(ui.danger('Login failed'));
      const status = error.response?.status;
      const message = error.response?.data?.message || error.message;
      if (status === 403 || /verif/i.test(message || '')) {
        console.error(ui.warn('Your email is not verified yet.'));
        console.log(ui.nextStep(`cryptenv verify-email ${email}`));
      } else if (error.response) {
        console.error(ui.error(message || 'Invalid credentials'));
      } else {
        console.error(ui.error('Connection error. Check CRYPTENV_API_URL and try again.'));
      }
      process.exitCode = 1;
    }
  } catch (error) {
    console.error(ui.error(error.message));
    process.exitCode = 1;
  }
}

async function register() {
  try {
    const email = await promptEmail();
    const answers = await inquirer.prompt([
      {
        type: 'input',
        name: 'username',
        message: 'Username:',
        validate: (input) => /^[A-Za-z0-9_.-]{3,40}$/.test(input.trim()) || 'Use 3-40 letters, numbers, dot, dash or underscore',
      },
      {
        type: 'password',
        name: 'password',
        message: 'Password:',
        mask: '*',
        validate: (input) => input.length >= 8 || 'Password must be at least 8 characters',
      },
      { type: 'input', name: 'firstName', message: 'First name (optional):' },
      { type: 'input', name: 'lastName', message: 'Last name (optional):' },
    ]);

    const spinner = ora('Creating your account...').start();
    const apiUrl = config.getApiUrl();

    try {
      const { data } = await axios.post(`${apiUrl}/auth/register`, {
        email,
        username: answers.username.trim(),
        password: answers.password,
        firstName: answers.firstName.trim() || undefined,
        lastName: answers.lastName.trim() || undefined,
      });
      spinner.succeed(ui.white(`Account created for ${ui.white(data.email || email)}`));
    } catch (error) {
      spinner.fail(ui.danger('Registration failed'));
      console.error(ui.error(error.response?.data?.message || error.message));
      process.exitCode = 1;
      return;
    }

    console.log(ui.info('A 6-digit verification code is on its way to your inbox.'));
    await verifyEmailLoop(email);
  } catch (error) {
    console.error(ui.error(error.message));
    process.exitCode = 1;
  }
}

async function verifyEmailLoop(email) {
  let mailConfigured = true;
  try {
    const { data } = await axios.get(`${config.getApiUrl()}/auth/email-verification/status`);
    mailConfigured = data.mailConfigured !== false;
  } catch {
    // older backend without the status endpoint
  }
  if (!mailConfigured) {
    console.log(ui.warn('This deployment has no mail transport configured, so no code can be delivered.'));
    console.log(ui.hint('Configure SMTP, BREVO_API_KEY or RESEND_API_KEY on the backend.'));
    return;
  }

  let verified = false;
  for (let attempt = 0; attempt < 3 && !verified; attempt += 1) {
    const { code } = await inquirer.prompt([
      {
        type: 'input',
        name: 'code',
        message: 'Verification code (leave blank to skip):',
        validate: (input) => !input.trim() || /^\d{6}$/.test(input.trim()) || 'The code is 6 digits',
      },
    ]);
    if (!code.trim()) break;
    try {
      await axios.post(`${config.getApiUrl()}/auth/email-verification/verify`, {
        email,
        code: code.trim(),
      });
      verified = true;
      console.log(ui.success('Email verified.'));
    } catch (error) {
      console.log(ui.error(error.response?.data?.message || 'Invalid or expired verification code.'));
      if (attempt === 0) {
        try {
          await axios.post(`${config.getApiUrl()}/auth/email-verification/resend`, { email });
          console.log(ui.info('A fresh code has been sent.'));
        } catch {
          // ignore resend failures
        }
      }
    }
  }
  if (verified) {
    console.log(ui.nextStep(`cryptenv login --email ${email}`));
  } else {
    console.log(ui.warn('Email not verified. Sign-in stays locked until it is.'));
    console.log(ui.nextStep(`cryptenv verify-email ${email}`));
  }
}

async function verifyEmail(emailArg, options = {}) {
  try {
    const email = emailArg ? normalizeEmail(emailArg) : await promptEmail();
    if (options.resend) {
      await axios.post(`${config.getApiUrl()}/auth/email-verification/resend`, { email });
      console.log(ui.success(`If ${email} has a pending account, a new code has been sent.`));
      return;
    }
    const { code } = await inquirer.prompt([
      {
        type: 'input',
        name: 'code',
        message: 'Verification code:',
        validate: (input) => /^\d{6}$/.test(input.trim()) || 'The code is 6 digits',
      },
    ]);
    await axios.post(`${config.getApiUrl()}/auth/email-verification/verify`, {
      email,
      code: code.trim(),
    });
    console.log(ui.success('Email verified. You can sign in now.'));
    console.log(ui.nextStep(`cryptenv login --email ${email}`));
  } catch (error) {
    console.error(ui.error(error.response?.data?.message || error.message));
    process.exitCode = 1;
  }
}

async function forgotPassword(emailArg) {
  try {
    const email = emailArg ? normalizeEmail(emailArg) : await promptEmail();
    const apiUrl = config.getApiUrl();

    const spinner = ora('Requesting a reset code...').start();
    try {
      const { data } = await axios.post(`${apiUrl}/auth/forgot-password`, { email });
      spinner.succeed(ui.white(data.message || 'Reset code requested.'));
    } catch (error) {
      spinner.fail(ui.danger('Could not request a reset code'));
      console.error(ui.error(error.response?.data?.message || error.message));
      process.exitCode = 1;
      return;
    }

    const { code } = await inquirer.prompt([
      {
        type: 'input',
        name: 'code',
        message: '6-digit reset code from your inbox:',
        validate: (input) => /^\d{6}$/.test(input.trim()) || 'The code is 6 digits',
      },
    ]);

    let resetToken;
    try {
      const { data } = await axios.post(`${apiUrl}/auth/verify-reset-code`, { email, code: code.trim() });
      resetToken = data.resetToken;
      if (!resetToken) throw new Error('The server did not return a reset token');
      console.log(ui.success('Code verified. Choose a new password.'));
    } catch (error) {
      console.error(ui.error(error.response?.data?.message || error.message));
      process.exitCode = 1;
      return;
    }

    const { password, confirm } = await inquirer.prompt([
      {
        type: 'password',
        name: 'password',
        message: 'New password:',
        mask: '*',
        validate: (input) => input.length >= 8 || 'Password must be at least 8 characters',
      },
      {
        type: 'password',
        name: 'confirm',
        message: 'Confirm new password:',
        mask: '*',
        validate: (input, answers) => input === answers.password || 'Passwords do not match',
      },
    ]);

    const spinner2 = ora('Updating password...').start();
    try {
      const { data } = await axios.post(`${apiUrl}/auth/reset-password`, {
        token: resetToken,
        newPassword: password,
      });
      spinner2.succeed(ui.white(data.message || 'Password updated.'));
      console.log(ui.nextStep(`cryptenv login --email ${email}`));
    } catch (error) {
      spinner2.fail(ui.danger('Password update failed'));
      console.error(ui.error(error.response?.data?.message || error.message));
      process.exitCode = 1;
    }
  } catch (error) {
    console.error(ui.error(error.message));
    process.exitCode = 1;
  }
}

async function logout(options = {}) {
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
            params: options.everywhere ? { everywhere: true } : undefined,
            timeout: 10000,
          }
        );
        console.log(ui.success(options.everywhere ? 'Signed out on all devices' : 'Signed out'));
      } catch {
        console.log(ui.warn('Server logout failed — clearing local credentials only.'));
      }
    }

    await keystore.deleteSecret('token');
    await keystore.deleteSecret('refresh');
    await keystore.deleteSecret('user');
    console.log(ui.hint('Local credentials removed.'));
  } catch (error) {
    console.error(ui.error(error.message));
    process.exitCode = 1;
  }
}

let refreshInFlight = null;

async function refreshSession() {
  if (process.env.CRYPTENV_TOKEN) return process.env.CRYPTENV_TOKEN;
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const refreshToken = await keystore.getSecret('refresh');
    if (!refreshToken) return null;
    const { data } = await axios.post(
      `${config.getApiUrl()}/auth/refresh`,
      { refreshToken },
      { timeout: 15000 }
    );
    if (!data?.token) return null;
    await keystore.setSecret('token', data.token);
    if (data.refreshToken) await keystore.setSecret('refresh', data.refreshToken);
    return data.token;
  })()
    .catch(() => null)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

async function getAuthToken() {
  if (process.env.CRYPTENV_TOKEN) return process.env.CRYPTENV_TOKEN;
  let token = null;
  try {
    token = await keystore.getSecret('token');
  } catch {
    token = null;
  }
  if (!token) return refreshSession();
  const expiresAt = decodeExpiry(token);
  if (expiresAt && expiresAt - Date.now() < 60000) {
    const fresh = await refreshSession();
    if (fresh) return fresh;
  }
  return token;
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

/** Refresh expired access tokens once and retry the failed request. */
function installAuthInterceptor() {
  axios.interceptors.response.use(undefined, async (error) => {
    const request = error.config;
    const status = error.response?.status;
    if (!request || status !== 401 || request.__cryptenvRetried) throw error;
    const url = request.url || '';
    if (/\/auth\/(login|register|refresh|logout|forgot-password|reset-password|verify-reset-code|email-check|email-verification)/.test(url)) {
      throw error;
    }
    const authorization = request.headers?.Authorization || request.headers?.authorization;
    if (!authorization || !String(authorization).startsWith('Bearer ')) throw error;
    const token = await refreshSession();
    if (!token) throw error;
    request.__cryptenvRetried = true;
    request.headers = { ...request.headers, Authorization: `Bearer ${token}` };
    return axios(request);
  });
}

module.exports = {
  login,
  register,
  logout,
  verifyEmail,
  forgotPassword,
  getAuthToken,
  getRefreshToken,
  getUser,
  getAuthHeaders,
  refreshSession,
  installAuthInterceptor,
  promptEmail,
  normalizeEmail,
};