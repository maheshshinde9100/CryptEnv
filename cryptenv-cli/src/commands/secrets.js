const axios = require('axios');
const ora = require('ora');
const fs = require('fs');
const { getAuthHeaders } = require('./auth');
const { getApiUrl, resolveContext } = require('../utils/config');
const ui = require('../utils/ui');

function fail(message, err) {
  console.error(ui.error(message));
  if (err?.response?.data?.message) console.error(ui.hint(err.response.data.message));
  else if (err?.message) console.error(ui.hint(err.message));
  process.exitCode = 1;
}

async function authed() {
  const headers = await getAuthHeaders();
  if (!headers) {
    fail('Not authenticated. Run: cryptenv login or set CRYPTENV_API_KEY');
    process.exit(1);
  }
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
    spinner.succeed(ui.white(`${data.length} secret${data.length === 1 ? '' : 's'} in ${ctx.environmentId ? `environment ${ctx.environmentId}` : 'all environments'}`));
    if (ctx.json) {
      console.log(
        JSON.stringify(
          data.map((s) => ({
            key: s.key,
            environmentId: s.environmentId,
            secretType: s.secretType,
            version: s.version,
            isActive: s.isActive,
            expiresAt: s.expiresAt,
            updatedAt: s.updatedAt,
          })),
          null,
          2
        )
      );
      return;
    }
    if (!data.length) {
      console.log(ui.hint('No secrets yet. Add one with: cryptenv secrets set KEY value'));
      return;
    }
    const rows = data.map((s) => [
      s.key,
      s.environmentId ?? '',
      s.secretType || 'TEXT',
      `v${s.version ?? 1}`,
      s.isActive === false ? ui.badge('inactive', 'warn') : s.expiresAt ? ui.badge('expiring', 'warn') : ui.badge('active', 'ok'),
      ui.timeAgo(s.updatedAt),
    ]);
    console.log('');
    console.log(ui.table(['Key', 'Env', 'Type', 'Ver', 'State', 'Updated'], rows));
  } catch (e) {
    spinner.fail(ui.danger('Failed to fetch secrets'));
    fail('Could not list secrets', e);
  }
}

async function get(key, opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  const spinner = ora(`Fetching ${key}...`).start();
  try {
    const { data } = await axios.get(secretUrl(ctx, key), { headers });
    spinner.succeed(ui.white(`${key} (v${data.version ?? 1})`));
    if (ctx.json) {
      const { value, ...meta } = data;
      console.log(JSON.stringify({ ...meta, value }, null, 2));
    } else if (data.value === null || data.value === undefined) {
      console.error(ui.error('The server returned no value. Check the workspace encryption key.'));
      process.exitCode = 1;
    } else {
      process.stdout.write(`${data.value}\n`);
    }
  } catch (e) {
    spinner.fail(ui.danger(`Could not fetch ${key}`));
    fail('get failed', e);
  }
}

async function set(key, value, opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  if (!ctx.environmentId) {
    fail('An environment is required: pass --env <id> or run cryptenv use');
    process.exit(1);
  }
  const spinner = ora(`Saving ${key}...`).start();
  const payload = {
    key,
    value,
    environmentId: Number(ctx.environmentId),
    description: opts.description || undefined,
    tags: opts.tags || undefined,
    secretType: opts.type ? String(opts.type).toUpperCase() : undefined,
  };
  try {
    const { data } = await axios.post(`${getApiUrl()}/secrets`, payload, { headers });
    spinner.succeed(ui.white(`Created ${key} (v${data.version ?? 1})`));
  } catch (e) {
    if (e.response?.status !== 409) {
      spinner.fail(ui.danger(`Could not create ${key}`));
      fail('set failed', e);
      return;
    }
    try {
      const { data } = await axios.put(secretUrl(ctx, key), { value }, { headers });
      spinner.succeed(ui.white(`Updated ${key} (v${data.version ?? 1})`));
    } catch (e2) {
      spinner.fail(ui.danger(`Could not update ${key}`));
      fail('set failed', e2);
    }
  }
}

async function rotate(key, opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  let environmentId = ctx.environmentId;
  const spinner = ora(`Rotating ${key}...`).start();
  try {
    if (!environmentId) {
      const { data } = await axios.get(`${getApiUrl()}/secrets/${encodeURIComponent(key)}`, { headers });
      environmentId = data.environmentId;
    }
    if (!environmentId) throw new Error('Could not resolve the environment for this secret');
    const { data } = await axios.post(
      `${getApiUrl()}/secrets/environment/${environmentId}/${encodeURIComponent(key)}/rotate`,
      {},
      { headers }
    );
    spinner.succeed(ui.white(`Rotated ${key} to v${data.version ?? '?'}`));
  } catch (e) {
    spinner.fail(ui.danger(`Could not rotate ${key}`));
    fail('rotate failed', e);
  }
}

async function deleteSecret(key, opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  const spinner = ora(`Deleting ${key}...`).start();
  try {
    await axios.delete(secretUrl(ctx, key), { headers });
    spinner.succeed(ui.white(`Deleted ${key}${ctx.environmentId ? ` from environment ${ctx.environmentId}` : ''}`));
  } catch (e) {
    spinner.fail(ui.danger(`Could not delete ${key}`));
    fail('delete failed', e);
  }
}

async function importSecrets(file, opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  if (!ctx.environmentId) {
    fail('--env is required');
    process.exit(1);
  }
  let content;
  try {
    content = fs.readFileSync(file, 'utf8');
  } catch (e) {
    fail(`Cannot read file: ${file}`, e);
    return;
  }
  const format = (opts.format || 'ENV').toUpperCase();
  const spinner = ora(`Importing ${file}...`).start();
  try {
    const { data } = await axios.post(
      `${getApiUrl()}/secrets/import`,
      { environmentId: String(ctx.environmentId), format, content },
      { headers }
    );
    spinner.succeed(ui.white(`Import complete — created ${data.created ?? 0}, updated ${data.updated ?? 0}`));
    if (ctx.json) console.log(JSON.stringify(data, null, 2));
  } catch (e) {
    spinner.fail(ui.danger('Import failed'));
    fail('import failed', e);
  }
}

async function exportSecrets(opts = {}) {
  const headers = await authed();
  const ctx = resolveContext(opts);
  if (!ctx.environmentId) {
    fail('--env is required');
    process.exit(1);
  }
  const format = (opts.format || 'ENV').toUpperCase();
  try {
    const { data } = await axios.get(`${getApiUrl()}/secrets/export`, {
      headers,
      params: { environmentId: ctx.environmentId, format },
      responseType: 'text',
      transformResponse: [(v) => v],
    });
    if (opts.out) {
      fs.writeFileSync(opts.out, data, { mode: 0o600 });
      console.log(ui.success(`Wrote ${opts.out}`));
      console.log(ui.hint('Treat this file as a secret and keep it out of version control.'));
    } else {
      console.log(data);
    }
  } catch (e) {
    fail('export failed', e);
  }
}

async function versions(key, opts = {}) {
  const headers = await authed();
  const spinner = ora(`Fetching versions of ${key}...`).start();
  try {
    const { data } = await axios.get(`${getApiUrl()}/secrets/${encodeURIComponent(key)}/versions`, { headers });
    spinner.succeed(ui.white(`${data.length} version${data.length === 1 ? '' : 's'}`));
    if (opts.json) {
      console.log(JSON.stringify(data, null, 2));
      return;
    }
    if (!data.length) {
      console.log(ui.hint('No versions recorded yet.'));
      return;
    }
    const rows = data.map((v) => [
      `v${v.version}`,
      v.createdAt ? new Date(v.createdAt).toLocaleString() : '',
      v.active ? ui.badge('active', 'ok') : ui.badge('superseded', 'neutral'),
    ]);
    console.log('');
    console.log(ui.table(['Version', 'Created', 'State'], rows));
  } catch (e) {
    spinner.fail(ui.danger('Could not fetch versions'));
    fail('versions failed', e);
  }
}

async function rollback(key, version, opts = {}) {
  const headers = await authed();
  const spinner = ora(`Rolling back ${key} to v${version}...`).start();
  try {
    await axios.post(
      `${getApiUrl()}/secrets/${encodeURIComponent(key)}/versions/rollback/${version}`,
      {},
      { headers }
    );
    spinner.succeed(ui.white(`${key} rolled back to v${version}`));
  } catch (e) {
    spinner.fail(ui.danger('Rollback failed'));
    fail('rollback failed', e);
  }
}

async function lifecycle(key, action, opts = {}) {
  const headers = await authed();
  const base = `${getApiUrl()}/secrets/${encodeURIComponent(key)}/lifecycle`;
  if (action === 'expire' && Number.isNaN(new Date(opts.at).getTime())) {
    fail('--at must be an ISO-8601 datetime, e.g. 2026-12-31T00:00:00');
    return;
  }
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
        if (!opts.at) throw new Error('--at <ISO datetime> is required');
        await axios.post(`${base}/expiration`, {}, { headers, params: { expiresAt: opts.at } });
        break;
      case 'no-expire':
        await axios.delete(`${base}/expiration`, { headers });
        break;
      case 'rotation':
        if (!opts.days) throw new Error('--days <n> is required');
        await axios.post(`${base}/rotation-interval`, {}, { headers, params: { intervalDays: Number(opts.days) } });
        break;
      case 'auto-rotate-on':
        await axios.post(`${base}/auto-rotate/enable`, {}, { headers });
        break;
      case 'auto-rotate-off':
        await axios.post(`${base}/auto-rotate/disable`, {}, { headers });
        break;
      default:
        throw new Error(`Unknown lifecycle action: ${action}`);
    }
    spinner.succeed(ui.white(`${key}: ${action}`));
  } catch (e) {
    spinner.fail(ui.danger(`${action} failed`));
    fail(`${action} failed`, e);
  }
}

module.exports = {
  list,
  get,
  set,
  rotate,
  delete: deleteSecret,
  importSecrets,
  exportSecrets,
  versions,
  rollback,
  lifecycle,
};