require('dotenv').config();
const axios = require('axios');
const { encryptWithKey, decryptWithKey } = require('./crypto');
const { resolveConfig, resolveMasterKey } = require('./config');

function sanitizeErrorMessage(err) {
  let msg = (err && err.message) || 'Unknown error';
  if (err && err.response && err.response.data) {
    const data = err.response.data;
    msg = data.message || data.error || err.response.statusText || msg;
  }
  return String(msg)
    .replace(/Bearer\s+\S+/gi, 'Bearer [redacted]')
    .replace(/ce_live_[a-f0-9]+/gi, 'ce_live_[redacted]');
}

function mapWorkspaceFromLogin(dto) {
  return {
    id: dto.id,
    name: dto.name,
    description: dto.description || null,
    hasEncryptionKey: Boolean(dto.hasEncryptionKey),
    environments: dto.environments || []
  };
}

function mapWorkspaceFromApi(dto) {
  return {
    id: dto.id,
    name: dto.name,
    description: dto.description || null,
    hasEncryptionKey: Boolean(dto.hasEncryptionKey),
    environments: []
  };
}

class CryptEnv {
  constructor(options = {}) {
    this._applyConfig(resolveConfig(options));
    this.userId = null;
    this.username = null;
    this.workspaces = [];
    this.initialized = false;
    this.encryptedSecretsMap = new Map();
    this.plaintextCache = new Map();
    this.axiosInstance = null;
  }

  _applyConfig(config) {
    this.email = config.email;
    this.password = config.password;
    this.apiKey = config.apiKey;
    this.token = config.token;
    this.workspaceId = config.workspaceId;
    this.masterKey = config.masterKey;
    this.apiUrl = config.apiUrl;
    this.workspaceName = config.workspaceName;
  }

  _createAxios() {
    const headers = { 'Content-Type': 'application/json' };
    if (this.token) {
      headers.Authorization = `Bearer ${this.token}`;
    } else if (this.apiKey) {
      headers['X-API-Key'] = this.apiKey;
    }

    this.axiosInstance = axios.create({
      baseURL: this.apiUrl,
      timeout: 30000,
      headers
    });
  }

  _getAxios() {
    if (!this.axiosInstance) {
      this._createAxios();
    }
    if (this.token) {
      this.axiosInstance.defaults.headers.common.Authorization = `Bearer ${this.token}`;
      delete this.axiosInstance.defaults.headers.common['X-API-Key'];
    } else if (this.apiKey) {
      this.axiosInstance.defaults.headers.common['X-API-Key'] = this.apiKey;
      delete this.axiosInstance.defaults.headers.common.Authorization;
    }
    return this.axiosInstance;
  }

  _requireMasterKey() {
    if (!this.masterKey) {
      throw new Error(
        'CryptEnv SDK: master key not configured. Set CRYPTENV_MASTER_KEY in .env ' +
        '(or CRYPTENV_WORKSPACE_ENCRYPTION_KEY for backward compatibility). ' +
        'This is the workspace encryption key you set when creating the workspace.'
      );
    }
  }

  _selectWorkspace() {
    if (this.workspaceId != null) {
      return;
    }
    if (this.workspaceName && this.workspaces.length > 0) {
      const byName = this.workspaces.find(
        (w) => w.name && w.name.toLowerCase() === String(this.workspaceName).toLowerCase()
      );
      if (byName) {
        this.workspaceId = byName.id;
        return;
      }
    }
    if (this.workspaces.length > 0) {
      const firstWithKey = this.workspaces.find((w) => w.hasEncryptionKey);
      this.workspaceId = (firstWithKey || this.workspaces[0]).id;
    }
  }

  async _authenticateWithPassword() {
    const api = this._getAxios();
    const res = await api.post('/api/sdk/login', {
      email: this.email,
      password: this.password
    });
    const data = res.data;
    this.token = data.token;
    this.userId = data.userId;
    this.email = data.email;
    this.username = data.username;
    this.workspaces = (data.workspaces || []).map(mapWorkspaceFromLogin);
    this._createAxios();
  }

  async _authenticateWithApiKey() {
    const api = this._getAxios();
    const res = await api.get('/api/workspaces');
    this.workspaces = (res.data || []).map(mapWorkspaceFromApi);
    this.initialized = true;
  }

  async _authenticateWithToken() {
    const api = this._getAxios();
    const meRes = await api.get('/api/auth/me');
    this.userId = meRes.data.id;
    this.email = meRes.data.email;
    this.username = meRes.data.username;
    const wsRes = await api.get('/api/workspaces');
    this.workspaces = (wsRes.data || []).map(mapWorkspaceFromApi);
  }

  async init(options = {}) {
    if (options && Object.keys(options).length > 0) {
      this._applyConfig(resolveConfig(options));
      this.axiosInstance = null;
    }

    const hasPasswordAuth = Boolean(this.email && this.password);
    const hasApiKeyAuth = Boolean(this.apiKey);
    const hasTokenAuth = Boolean(this.token);

    if (!hasPasswordAuth && !hasApiKeyAuth && !hasTokenAuth) {
      throw new Error(
        'CryptEnv SDK: missing credentials. Set CRYPTENV_EMAIL and CRYPTENV_PASSWORD, ' +
        'or CRYPTENV_API_KEY, or CRYPTENV_TOKEN in .env, or pass them to init().'
      );
    }

    try {
      if (hasPasswordAuth) {
        await this._authenticateWithPassword();
      } else if (hasTokenAuth) {
        await this._authenticateWithToken();
      } else {
        await this._authenticateWithApiKey();
      }

      this.initialized = true;
      this._selectWorkspace();

      if (!this.workspaceId) {
        throw new Error(
          'CryptEnv SDK: no workspace available. Set CRYPTENV_WORKSPACE_ID or create a workspace first.'
        );
      }

      await this.refresh();
      return this._summary();
    } catch (err) {
      throw new Error('CryptEnv SDK init failed: ' + sanitizeErrorMessage(err));
    }
  }

  /** Reload encrypted secrets from the CryptEnv backend. */
  async refresh() {
    if (!this.initialized) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    if (!this.workspaceId) {
      throw new Error('No workspace selected. Set CRYPTENV_WORKSPACE_ID.');
    }

    try {
      const api = this._getAxios();
      const res = await api.get(`/api/sdk/workspaces/${this.workspaceId}/encrypted-secrets-map`);
      this.encryptedSecretsMap = new Map(Object.entries(res.data || {}));
      this.plaintextCache.clear();
      return this.encryptedSecretsMap.size;
    } catch (err) {
      throw new Error('CryptEnv SDK refresh failed: ' + sanitizeErrorMessage(err));
    }
  }

  /** @deprecated Use refresh() — kept for backward compatibility. */
  async refreshEncryptedSecrets() {
    return this.refresh();
  }

  listKeys() {
    if (!this.initialized) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    return Array.from(this.encryptedSecretsMap.keys());
  }

  listWorkspaces() {
    if (!this.initialized) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    return this.workspaces;
  }

  setActiveWorkspace(workspaceId) {
    if (!workspaceId) {
      throw new Error('workspaceId is required');
    }
    const ws = this.workspaces.find((w) => String(w.id) === String(workspaceId));
    if (!ws) {
      throw new Error(`Workspace ${workspaceId} not found in your workspaces.`);
    }
    if (!ws.hasEncryptionKey) {
      console.warn(
        `[cryptenv/sdk] Workspace '${ws.name}' has no encryption key configured on the server.`
      );
    }
    this.workspaceId = ws.id;
    this.encryptedSecretsMap.clear();
    this.plaintextCache.clear();
    return ws;
  }

  get(key, options = {}) {
    if (!this.initialized) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    if (!key) {
      return null;
    }

    if (options.refresh === true) {
      this.plaintextCache.delete(key);
    }

    if (this.plaintextCache.has(key) && options.refresh !== true) {
      return this.plaintextCache.get(key);
    }

    const enc = this.encryptedSecretsMap.get(key);
    if (enc == null) {
      if (options.throwOnMissing === false) {
        return undefined;
      }
      throw new Error(
        `Secret '${key}' not found in workspace ${this.workspaceId}. ` +
        `Available keys: ${this.listKeys().join(', ') || '(none)'}`
      );
    }

    this._requireMasterKey();
    try {
      const plain = decryptWithKey(enc, this.masterKey);
      this.plaintextCache.set(key, plain);
      return plain;
    } catch {
      throw new Error(
        `Failed to decrypt secret '${key}'. Verify CRYPTENV_MASTER_KEY matches the workspace encryption key.`
      );
    }
  }

  async getOrFetch(key) {
    if (!this.initialized) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    if (this.encryptedSecretsMap.has(key)) {
      return this.get(key);
    }

    try {
      const api = this._getAxios();
      const res = await api.get(`/api/sdk/secrets/encrypted/${encodeURIComponent(key)}`);
      const encVal = res.data.encryptedValue;
      this.encryptedSecretsMap.set(key, encVal);
      return this.get(key);
    } catch (err) {
      if (err.response && err.response.status === 404) {
        throw new Error(`Secret '${key}' not found`);
      }
      throw new Error('CryptEnv SDK fetch failed: ' + sanitizeErrorMessage(err));
    }
  }

  async getAll() {
    if (!this.initialized) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    if (this.encryptedSecretsMap.size === 0) {
      await this.refresh();
    }
    const result = {};
    for (const key of this.encryptedSecretsMap.keys()) {
      result[key] = this.get(key, { throwOnMissing: false });
    }
    return result;
  }

  /**
   * Initialize (if needed), decrypt all secrets, and inject them into process.env.
   * Does not write to disk.
   */
  async load(options = {}) {
    if (!this.initialized) {
      await this.init();
    }

    const all = await this.getAll();
    const prefix = options.prefix || '';
    const overwrite = options.overwrite !== false;
    let loaded = 0;

    for (const [key, value] of Object.entries(all)) {
      if (value == null) {
        continue;
      }
      const envKey = prefix + key;
      if (overwrite || process.env[envKey] === undefined) {
        process.env[envKey] = value;
        loaded += 1;
      }
    }

    return loaded;
  }

  isInitialized() {
    return this.initialized;
  }

  getActiveWorkspace() {
    if (!this.workspaceId) {
      return null;
    }
    return this.workspaces.find((w) => String(w.id) === String(this.workspaceId)) || null;
  }

  _summary() {
    return {
      success: true,
      user: {
        email: this.email,
        userId: this.userId,
        username: this.username
      },
      activeWorkspaceId: this.workspaceId,
      workspaceCount: this.workspaces.length,
      loadedSecretCount: this.encryptedSecretsMap.size
    };
  }

  // --- Advanced: secret management (prefer cryptenv-cli for operator workflows) ---

  async setSecret(key, value, options = {}) {
    if (!this.initialized) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    if (!key || value == null) {
      throw new Error('key and value are required');
    }

    const plain = String(value);
    if (options.environmentId) {
      const api = this._getAxios();
      const payload = {
        key,
        value: plain,
        environmentId: options.environmentId,
        description: options.description || '',
        encrypted: true
      };
      try {
        await api.post('/api/secrets', payload);
      } catch (e) {
        const alreadyExists =
          e.response &&
          (e.response.status === 409 ||
            (e.response.data && /already exists/i.test(e.response.data.message || '')));
        if (alreadyExists) {
          await api.put(
            `/api/secrets/environment/${options.environmentId}/${encodeURIComponent(key)}`,
            { value: plain }
          );
        } else {
          throw new Error('Failed to save secret: ' + sanitizeErrorMessage(e));
        }
      }
      await this.refresh();
      this.plaintextCache.set(key, plain);
      return true;
    }

    this._requireMasterKey();
    const encVal = encryptWithKey(plain, this.masterKey);
    this.encryptedSecretsMap.set(key, encVal);
    this.plaintextCache.set(key, plain);
    return true;
  }

  async deleteSecret(key, options = {}) {
    if (!this.initialized) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    const api = this._getAxios();
    try {
      if (options.environmentId) {
        await api.delete(
          `/api/secrets/environment/${options.environmentId}/${encodeURIComponent(key)}`
        );
      } else {
        await api.delete(`/api/secrets/${encodeURIComponent(key)}`);
      }
      this.encryptedSecretsMap.delete(key);
      this.plaintextCache.delete(key);
      return true;
    } catch (e) {
      throw new Error('Failed to delete secret: ' + sanitizeErrorMessage(e));
    }
  }
}

const defaultInstance = new CryptEnv();
defaultInstance.CryptEnv = CryptEnv;

module.exports = defaultInstance;
module.exports.CryptEnv = CryptEnv;
module.exports.default = defaultInstance;
module.exports.resolveMasterKey = resolveMasterKey;
