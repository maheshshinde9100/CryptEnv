const axios = require('axios');
const chalk = require('chalk');
const ora = require('ora');
const fs = require('fs');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, resolveContext } = require('../utils/config');

function fail(msg, err) {
  console.error(chalk.red(msg));
  if (err?.response?.data?.message) console.error(chalk.red(err.response.data.message));
  else if (err?.message) console.error(chalk.red(err.message));
  process.exit(1);
}

async function authed() {
  const headers = await getAuthHeaders();
  if (!headers) fail('Not authenticated. Please run: cryptenv login');
  return headers;
}

function secretsUrl(ctx) {
  const api = getApiUrl();
  if (ctx.environmentId) return `${api}/secrets/environment/${ctx.environmentId}`;
  return `${api}/secrets`;
}

function secretUrl(ctx, key) {
  return `${secretsUrl(ctx)}/${encodeURIComponent(key)}`;
}

async function list(opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  const spinner = ora('Fetching secrets...').start();
  try {
    const { data } = await axios.get(secretsUrl(ctx), { headers });
    spinner.succeed('Secrets fetched');
    if (ctx.json) {
      console.log(JSON.stringify(data.map((s) => ({ key: s.key, environmentId: s.environmentId })), null, 2));
      return;
    }
    if (!data.length) {
      console.log(chalk.yellow('No secrets found'));
      return;
    }
    data.forEach((s) => console.log(`  ${chalk.cyan(s.key)}: ${'•'.repeat(16)}`));
  } catch (e) {
    spinner.fail('Failed to fetch secrets');
    fail('list failed', e);
  }
}

async function get(key, opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  const spinner = ora(`Fetching ${key}...`).start();
  try {
    const { data } = await axios.get(secretUrl(ctx, key), { headers });
    spinner.succeed('OK');
    if (ctx.json) console.log(JSON.stringify(data, null, 2));
    else console.log(`${chalk.cyan(key)}=${data.value}`);
  } catch (e) {
    spinner.fail('Failed');
    fail('get failed', e);
  }
}

async function set(key, value, opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  if (!ctx.environmentId) fail('environment required: --env <id> or .cryptenv.json environmentId');
  const spinner = ora(`Setting ${key}...`).start();
  try {
    await axios.post(
      `${getApiUrl()}/secrets`,
      { key, value, environmentId: Number(ctx.environmentId), encrypted: true },
      { headers }
    );
    spinner.succeed('Secret set');
  } catch (e) {
    // If create conflicts, fall back to updating the existing secret
    try {
      await axios.put(secretUrl(ctx, key), { value }, { headers });
      spinner.succeed('Secret updated');
    } catch (e2) {
      spinner.fail('Failed');
      fail('set failed', e2);
    }
  }
}

async function deleteSecret(key, opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  const spinner = ora(`Deleting ${key}...`).start();
  try {
    await axios.delete(secretUrl(ctx, key), { headers });
    spinner.succeed('Deleted');
  } catch (e) {
    spinner.fail('Failed');
    fail('delete failed', e);
  }
}

async function importSecrets(file, opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  if (!ctx.environmentId) fail('--env required');
  let content;
  try {
    content = fs.readFileSync(file, 'utf8');
  } catch (e) {
    fail(`Cannot read file: ${file}`, e);
  }
  const format = (opts.format || 'ENV').toUpperCase();
  try {
    const { data } = await axios.post(
      `${getApiUrl()}/secrets/import`,
      { environmentId: String(ctx.environmentId), format, content },
      { headers }
    );
    console.log(
      ctx.json
        ? JSON.stringify(data, null, 2)
        : `imported created=${data.created ?? 0} updated=${data.updated ?? 0}`
    );
  } catch (e) {
    fail('import failed', e);
  }
}

async function exportSecrets(opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  if (!ctx.environmentId) fail('--env required');
  const format = (opts.format || 'ENV').toUpperCase();
  try {
    const { data } = await axios.get(`${getApiUrl()}/secrets/export`, {
      headers,
      params: { environmentId: ctx.environmentId, format },
      responseType: 'text',
      transformResponse: [(v) => v],
    });
    if (opts.out) {
      fs.writeFileSync(opts.out, data);
      console.log(`Wrote ${opts.out}`);
    } else {
      console.log(data);
    }
  } catch (e) {
    fail('export failed', e);
  }
}

async function versions(key, opts = {}) {
  const headers = await authed();
  try {
    const { data } = await axios.get(`${getApiUrl()}/secrets/${encodeURIComponent(key)}/versions`, { headers });
    if (opts.json) {
      console.log(JSON.stringify(data, null, 2));
      return;
    }
    if (!data.length) {
      console.log(chalk.yellow('No versions found'));
      return;
    }
    data.forEach((v) => {
      const active = v.active ? chalk.green(' (active)') : '';
      console.log(`  v${v.version}  ${v.createdAt || ''}${active}`);
    });
  } catch (e) {
    fail('versions failed', e);
  }
}

async function rollback(key, version, opts = {}) {
  const headers = await authed();
  try {
    await axios.post(
      `${getApiUrl()}/secrets/${encodeURIComponent(key)}/versions/rollback/${version}`,
      {},
      { headers }
    );
    console.log(`Rolled back ${key} to version ${version}`);
  } catch (e) {
    fail('rollback failed', e);
  }
}

async function lifecycle(key, action, opts = {}) {
  const headers = await authed();
  const base = `${getApiUrl()}/secrets/${encodeURIComponent(key)}/lifecycle`;
  const spinner = ora(`${action} ${key}...`).start();
  try {
    switch (action) {
      case 'activate':
      case 'deactivate':
      case 'soft-delete':
      case 'restore':
        await axios.post(`${base}/${action}`, {}, { headers });
        break;
      case 'expire':
        if (!opts.at) fail('--at <ISO datetime> required');
        await axios.post(`${base}/expiration`, {}, { headers, params: { expiresAt: opts.at } });
        break;
      case 'no-expire':
        await axios.delete(`${base}/expiration`, { headers });
        break;
      case 'rotation':
        if (!opts.days) fail('--days <n> required');
        await axios.post(`${base}/rotation-interval`, {}, { headers, params: { intervalDays: Number(opts.days) } });
        break;
      case 'auto-rotate-on':
        await axios.post(`${base}/auto-rotate/enable`, {}, { headers });
        break;
      case 'auto-rotate-off':
        await axios.post(`${base}/auto-rotate/disable`, {}, { headers });
        break;
      default:
        fail(`unknown lifecycle action: ${action}`);
    }
    spinner.succeed(`${action} done`);
  } catch (e) {
    spinner.fail(`${action} failed`);
    fail(`${action} failed`, e);
  }
}

module.exports = {
  list,
  get,
  set,
  delete: deleteSecret,
  importSecrets,
  exportSecrets,
  versions,
  rollback,
  lifecycle,
};
