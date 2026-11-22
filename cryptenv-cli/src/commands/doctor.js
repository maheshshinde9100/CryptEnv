const axios = require('axios');
const { getApiUrl, loadCryptenvJson, configPath } = require('../utils/config');
const { getAuthHeaders, getRefreshToken } = require('./auth');
const keystore = require('../utils/keystore');
const ui = require('../utils/ui');

async function doctor() {
  const apiUrl = getApiUrl();
  const local = loadCryptenvJson();
  const base = apiUrl.replace(/\/api\/?$/, '');

  console.log('');
  console.log(ui.keyValue([
    ['CLI version', require('../../package.json').version],
    ['API URL', apiUrl],
    ['Config file', configPath()],
    ['Workspace', local.workspaceId || '(not set)'],
    ['Environment', local.environmentId || '(not set)'],
    ['Credentials', process.env.CRYPTENV_API_KEY ? 'API key (env)' : keystore.usingKeyring() ? 'OS keyring' : 'encrypted file (~/.cryptenv)'],
  ]));

  try {
    const health = await axios.get(`${base}/health`, { timeout: 8000 });
    console.log(ui.success(`Health check OK (HTTP ${health.status})`));
  } catch (e) {
    console.error(ui.error(`Health check failed: ${e.message}`));
    process.exitCode = 1;
  }

  try {
    const { data } = await axios.get(`${apiUrl}/auth/email-verification/status`, { timeout: 8000 });
    console.log(data.mailConfigured ? ui.success('Mail transport configured') : ui.warn('Mail transport NOT configured — verification and reset codes cannot be delivered'));
  } catch {
    console.log(ui.hint('Mail status endpoint unavailable'));
  }

  const headers = await getAuthHeaders();
  if (!headers) {
    console.log(ui.warn('Not signed in. Run: cryptenv login or set CRYPTENV_API_KEY'));
    process.exitCode = process.exitCode || 1;
    return;
  }

  try {
    const me = await axios.get(`${apiUrl}/auth/me`, { headers, timeout: 8000 });
    console.log(ui.success(`Authenticated as ${me.data.email || me.data.username}`));
  } catch (e) {
    console.error(ui.error(`Authentication failed: ${e.response?.data?.message || e.message}`));
    process.exitCode = 1;
  }

  if (!process.env.CRYPTENV_API_KEY) {
    const refresh = await getRefreshToken();
    console.log(refresh ? ui.hint('Refresh token present — sessions renew automatically') : ui.warn('No refresh token; run cryptenv login again'));
  }
}

async function whoami(opts = {}) {
  const headers = await getAuthHeaders();
  if (!headers) {
    console.error(ui.error('Not authenticated. Run: cryptenv login'));
    process.exit(1);
  }
  try {
    const { data } = await axios.get(`${getApiUrl()}/auth/me`, { headers });
    if (opts.json) {
      console.log(JSON.stringify(data, null, 2));
      return;
    }
    console.log(ui.keyValue([
      ['Username', data.username || ''],
      ['Email', data.email || ''],
      ['Name', [data.firstName, data.lastName].filter(Boolean).join(' ') || ''],
      ['User id', data.id],
      ['API key', data.apiKeyPrefix ? `${data.apiKeyPrefix}…` : '(not set)'],
      ['Created', data.createdAt ? new Date(data.createdAt).toLocaleString() : ''],
    ]));
  } catch (e) {
    console.error(ui.error(e.response?.data?.message || e.message));
    process.exitCode = 1;
  }
}

async function status(opts = {}) {
  const headers = await getAuthHeaders();
  if (!headers) {
    console.error(ui.error('Not authenticated. Run: cryptenv login'));
    process.exit(1);
  }
  try {
    const [me, workspaces] = await Promise.all([
      axios.get(`${getApiUrl()}/auth/me`, { headers }),
      axios.get(`${getApiUrl()}/workspaces`, { headers }),
    ]);
    const payload = {
      user: { id: me.data.id, email: me.data.email, username: me.data.username },
      workspaces: (workspaces.data || []).map((w) => ({
        id: w.id,
        name: w.name,
        role: w.currentUserRole || w.role,
        hasEncryptionKey: w.hasEncryptionKey,
      })),
    };
    if (opts.json) {
      console.log(JSON.stringify(payload, null, 2));
      return;
    }
    console.log(ui.keyValue([
      ['Signed in as', payload.user.email],
      ['Workspaces', String(payload.workspaces.length)],
    ]));
    if (payload.workspaces.length) {
      console.log('');
      console.log(ui.table(
        ['ID', 'Workspace', 'Role', 'Encryption'],
        payload.workspaces.map((w) => [
          w.id,
          w.name,
          w.role || 'member',
          w.hasEncryptionKey ? ui.badge('ready', 'ok') : ui.badge('no key', 'warn'),
        ])
      ));
    }
  } catch (e) {
    console.error(ui.error(e.response?.data?.message || e.message));
    process.exitCode = 1;
  }
}

module.exports = { doctor, whoami, status };