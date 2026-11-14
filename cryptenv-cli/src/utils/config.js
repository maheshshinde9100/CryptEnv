const fs = require('fs');
const path = require('path');

const DEFAULT_API_URL = 'https://cryptenv-backend.onrender.com/api';

function configPath() {
  return path.join(process.cwd(), '.cryptenv.json');
}

function loadCryptenvJson() {
  const file = configPath();
  if (!fs.existsSync(file)) return {};
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return {};
  }
}

function saveCryptenvJson(patch) {
  const current = loadCryptenvJson();
  const next = { ...current, ...patch };
  fs.writeFileSync(configPath(), JSON.stringify(next, null, 2) + '\n');
  return next;
}

function getApiUrl() {
  if (process.env.CRYPTENV_API_URL) {
    return process.env.CRYPTENV_API_URL;
  }
  const config = loadCryptenvJson();
  if (config.apiUrl) return config.apiUrl;
  return DEFAULT_API_URL;
}

function resolveContext(opts = {}) {
  const config = loadCryptenvJson();
  return {
    workspaceId: opts.workspace || process.env.CRYPTENV_WORKSPACE || config.workspaceId || null,
    environmentId: opts.env || opts.environment || process.env.CRYPTENV_ENV || config.environmentId || null,
    json: Boolean(opts.json),
  };
}

module.exports = {
  DEFAULT_API_URL,
  configPath,
  getApiUrl,
  loadCryptenvJson,
  saveCryptenvJson,
  resolveContext,
};
