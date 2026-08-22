const fs = require('fs');
const path = require('path');

const DEFAULT_API_HOST = 'https://cryptenv-backend.onrender.com';

/**
 * Normalize CRYPTENV_API_URL to a host base without trailing /api.
 * Accepts both "https://host" and "https://host/api" (CLI style).
 */
function normalizeApiUrl(url) {
  if (!url || typeof url !== 'string') {
    return DEFAULT_API_HOST;
  }
  let normalized = url.trim().replace(/\/+$/, '');
  if (normalized.endsWith('/api')) {
    normalized = normalized.slice(0, -4);
  }
  return normalized;
}

function readCryptenvJson() {
  const configPath = path.join(process.cwd(), '.cryptenv.json');
  if (!fs.existsSync(configPath)) {
    return null;
  }
  try {
    return JSON.parse(fs.readFileSync(configPath, 'utf8'));
  } catch {
    return null;
  }
}

/**
 * Workspace encryption key — CRYPTENV_MASTER_KEY is the preferred runtime name;
 * CRYPTENV_WORKSPACE_ENCRYPTION_KEY is kept for backward compatibility.
 */
function resolveMasterKey(options = {}) {
  return (
    options.masterKey ||
    options.workspaceEncryptionKey ||
    process.env.CRYPTENV_MASTER_KEY ||
    process.env.CRYPTENV_WORKSPACE_ENCRYPTION_KEY ||
    null
  );
}

function resolveConfig(options = {}) {
  const cryptenvJson = readCryptenvJson();

  const apiUrl = normalizeApiUrl(
    options.apiUrl ||
      process.env.CRYPTENV_API_URL ||
      (cryptenvJson && cryptenvJson.apiUrl) ||
      DEFAULT_API_HOST
  );

  return {
    apiUrl,
    email: options.email || process.env.CRYPTENV_EMAIL || null,
    password: options.password || process.env.CRYPTENV_PASSWORD || null,
    apiKey: options.apiKey || process.env.CRYPTENV_API_KEY || null,
    token: options.token || process.env.CRYPTENV_TOKEN || null,
    workspaceId:
      options.workspaceId ||
      process.env.CRYPTENV_WORKSPACE_ID ||
      (cryptenvJson && cryptenvJson.workspaceId) ||
      null,
    masterKey: resolveMasterKey(options),
    workspaceName: (cryptenvJson && cryptenvJson.workspace) || null
  };
}

module.exports = {
  DEFAULT_API_HOST,
  normalizeApiUrl,
  readCryptenvJson,
  resolveMasterKey,
  resolveConfig
};
