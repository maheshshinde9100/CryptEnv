const axios = require('axios');
const { getApiUrl, loadCryptenvJson } = require('../utils/config');
const { getAuthToken } = require('./auth');
const keystore = require('../utils/keystore');

async function doctor() {
  const apiUrl = getApiUrl();
  const config = loadCryptenvJson();
  console.log('CryptEnv doctor');
  console.log('  API URL:', apiUrl);
  console.log('  Credential store:', keystore.usingKeyring() ? 'OS keyring' : 'encrypted file (~/.cryptenv)');
  console.log('  .cryptenv.json workspaceId:', config.workspaceId || '(not set)');
  console.log('  .cryptenv.json environmentId:', config.environmentId || '(not set)');

  try {
    const base = apiUrl.replace(/\/api\/?$/, '');
    const health = await axios.get(`${base}/health`, { timeout: 8000 });
    console.log('  Health:', health.status === 200 ? 'OK' : health.status);
  } catch (e) {
    console.error('  Health: FAIL —', e.message);
    process.exitCode = 1;
  }

  let token;
  try {
    token = await getAuthToken();
  } catch {
    token = null;
  }
  if (!token) {
    console.log('  Auth: not logged in');
    process.exitCode = process.exitCode || 1;
    return;
  }
  try {
    const me = await axios.get(`${apiUrl}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    console.log('  Auth: OK as', me.data.email || me.data.username);
  } catch (e) {
    console.error('  Auth: FAIL —', e.response?.data?.message || e.message);
    process.exitCode = 1;
  }
}

async function whoami(opts = {}) {
  const apiUrl = getApiUrl();
  const token = await getAuthToken();
  if (!token) {
    console.error('Not authenticated');
    process.exit(1);
  }
  const { data } = await axios.get(`${apiUrl}/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (opts.json) {
    console.log(JSON.stringify(data, null, 2));
  } else {
    console.log(`${data.username} <${data.email}> id=${data.id}`);
    if (data.apiKeyPrefix) console.log(`API key prefix: ${data.apiKeyPrefix}`);
  }
}

async function status(opts = {}) {
  const apiUrl = getApiUrl();
  const token = await getAuthToken();
  if (!token) {
    console.error('Not authenticated');
    process.exit(1);
  }
  const headers = { Authorization: `Bearer ${token}` };
  const [me, workspaces] = await Promise.all([
    axios.get(`${apiUrl}/auth/me`, { headers }),
    axios.get(`${apiUrl}/workspaces`, { headers }),
  ]);
  const payload = {
    user: { id: me.data.id, email: me.data.email, username: me.data.username },
    workspaces: (workspaces.data || []).map((w) => ({
      id: w.id,
      name: w.name,
      role: w.currentUserRole,
    })),
  };
  if (opts.json) {
    console.log(JSON.stringify(payload, null, 2));
  } else {
    console.log(`Logged in as ${payload.user.email}`);
    console.log(`Workspaces: ${payload.workspaces.length}`);
    payload.workspaces.forEach((w) => console.log(`  - [${w.id}] ${w.name} (${w.role || 'member'})`));
  }
}

module.exports = { doctor, whoami, status };
