require('dotenv').config();
const axios = require('axios');
const { encryptWithKey, decryptWithKey } = require('./crypto');

class CryptEnv {
  constructor(options = {}) {
    this.email = options.email || process.env.CRYPTENV_EMAIL;
    this.password = options.password || process.env.CRYPTENV_PASSWORD;
    this.workspaceId = options.workspaceId || process.env.CRYPTENV_WORKSPACE_ID;
    this.workspaceEncryptionKey = options.workspaceEncryptionKey || process.env.CRYPTENV_WORKSPACE_ENCRYPTION_KEY;
    this.apiUrl = (options.apiUrl || process.env.CRYPTENV_API_URL || 'https://cryptenv-backend.onrender.com').replace(/\/$/, '');
    this.token = options.token || null;
    this.userId = null;
    this.username = null;
    this.workspaces = [];
    this.initialized = false;
    this.encryptedSecretsMap = new Map();
    this.plaintextCache = new Map();
    this.axiosInstance = null;
  }

  _createAxios() {
    this.axiosInstance = axios.create({
      baseURL: this.apiUrl,
      timeout: 30000,
      headers: {
        'Content-Type': 'application/json',
        ...(this.token ? { Authorization: `Bearer ${this.token}` } : {})
      }
    });
  }

  _getAxios() {
    if (!this.axiosInstance) {
      this._createAxios();
    }
    if (this.token) {
      this.axiosInstance.defaults.headers.common['Authorization'] = `Bearer ${this.token}`;
    }
    return this.axiosInstance;
  }

  async init(options = {}) {
    if (options.email != null) {
      if (options.email) this.email = options.email;
      if (options.password) this.password = options.password;
      if (options.workspaceId) this.workspaceId = options.workspaceId;
      if (options.workspaceEncryptionKey) this.workspaceEncryptionKey = options.workspaceEncryptionKey;
      if (options.apiUrl) {
        this.apiUrl = options.apiUrl.replace(/\/$/, '');
        this.axiosInstance = null;
      }
    }

    if (!this.email || !this.password) {
      throw new Error(
        'CryptEnv SDK: Missing credentials. Set CRYPTENV_EMAIL and CRYPTENV_PASSWORD in .env ' +
        'or pass { email, password } to init().'
      );
    }

    try {
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
      this.workspaces = data.workspaces || [];

      if (this.workspaceId == null && this.workspaces.length > 0) {
        const firstWithKey = this.workspaces.find(w => w.hasEncryptionKey);
        this.workspaceId = (firstWithKey || this.workspaces[0]).id;
      }

      this.initialized = true;

      if (this.workspaceId) {
        await this.refreshEncryptedSecrets();
      }

      return this._summary();
    } catch (err) {
      let msg = err.message;
      if (err && err.response && err.response.data) {
        msg = err.response.data.message || err.response.data.error || err.response.statusText || msg;
      }
      throw new Error('CryptEnv SDK init failed: ' + msg);
    }
  }

  async refreshEncryptedSecrets() {
    if (!this.initialized || !this.token) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    if (!this.workspaceId) {
      throw new Error('No workspace selected. Set CRYPTENV_WORKSPACE_ID or create a workspace first.');
    }
    const api = this._getAxios();
    const res = await api.get(`/api/sdk/workspaces/${this.workspaceId}/encrypted-secrets-map`);
    this.encryptedSecretsMap = new Map(Object.entries(res.data || {}));
    this.plaintextCache.clear();
    return this.encryptedSecretsMap.size;
  }

  async listKeys() {
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
    if (!workspaceId) throw new Error('workspaceId is required');
    const ws = this.workspaces.find(w => w.id === workspaceId);
    if (!ws) {
      throw new Error(`Workspace ${workspaceId} not found in your workspaces.`);
    }
    if (!ws.hasEncryptionKey) {
      console.warn(`[cryptenv/sdk] Workspace '${ws.name}' does not have an encryption key set. Set it via the dashboard or API before using it.`);
    }
    this.workspaceId = workspaceId;
    this.encryptedSecretsMap.clear();
    this.plaintextCache.clear();
    return ws;
  }

  get(key, options = {}) {
    if (!this.initialized) {
      throw new Error('CryptEnv SDK not initialized. Call init() first.');
    }
    if (!key) return null;

    if (options.refresh === true) {
      this.plaintextCache.delete(key);
    }

    if (this.plaintextCache.has(key) && options.refresh !== true) {
      return this.plaintextCache.get(key);
    }

    const enc = this.encryptedSecretsMap.get(key);
    if (enc == null) {
      if (options.throwOnMissing === false) return undefined;
      throw new Error(`Secret '${key}' not found in workspace ${this.workspaceId}. Available keys: ${this.listKeys().join(', ')}`);
    }
    if (!this.workspaceEncryptionKey) {
      throw new Error(
        'CRYPTENV_WORKSPACE_ENCRYPTION_KEY not set. Set it in .env to decrypt secrets. ' +
        'This is the same key you set when creating the workspace.'
      );
    }
    try {
      const plain = decryptWithKey(enc, this.workspaceEncryptionKey);
      this.plaintextCache.set(key, plain);
      return plain;
    } catch (e) {
      throw new Error(`Failed to decrypt secret '${key}'. Wrong CRYPTENV_WORKSPACE_ENCRYPTION_KEY may be incorrect. Details: ${e.message}`);
    }
  }

  async getOrFetch(key) {
    if (!this.initialized) throw new Error('Call init() first.');
    const cached = this.encryptedSecretsMap.get(key);
    if (cached != null) {
      return this.get(key);
    }
    const api = this._getAxios();
    try {
      const res = await api.get(`/api/sdk/secrets/encrypted/${encodeURIComponent(key)}`);
      const encVal = res.data.encryptedValue;
      this.encryptedSecretsMap.set(key, encVal);
      return this.get(key);
    } catch (e) {
      if (e.response && e.response.status === 404) {
        throw new Error(`Secret '${key}' not found`);
      }
      throw e;
    }
  }

  async getAll() {
    if (!this.initialized) throw new Error('Call init() first.');
    if (this.encryptedSecretsMap.size === 0) {
      await this.refreshEncryptedSecrets();
    }
    const result = {};
    for (const key of this.encryptedSecretsMap.keys()) {
      result[key] = this.get(key, { throwOnMissing: false });
    }
    return result;
  }

  async setSecret(key, value, options = {}) {
    if (!this.initialized) throw new Error('Call init() first.');
    if (!key || value == null) {
      throw new Error('key and value are required to create/update a secret');
    }
    // Server encrypts with the workspace key — send plaintext to avoid double-encryption.
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
        const alreadyExists = e.response && (
          e.response.status === 409 ||
          (e.response.data && /already exists/i.test(e.response.data.message || ''))
        );
        if (alreadyExists) {
          await api.put(
            `/api/secrets/environment/${options.environmentId}/${encodeURIComponent(key)}`,
            { value: plain }
          );
        } else {
          throw new Error('Failed to save secret: ' + (e.response?.data?.message || e.message));
        }
      }
      // Refresh ciphertext cache from server so local decrypt uses server-produced ciphertext
      await this.refreshEncryptedSecrets();
      this.plaintextCache.set(key, plain);
      return true;
    }
    // Local-only cache (no environmentId): encrypt client-side for in-memory use
    if (!this.workspaceEncryptionKey) {
      throw new Error('CRYPTENV_WORKSPACE_ENCRYPTION_KEY must be set to encrypt secrets locally');
    }
    const encVal = encryptWithKey(plain, this.workspaceEncryptionKey);
    this.encryptedSecretsMap.set(key, encVal);
    this.plaintextCache.set(key, plain);
    return true;
  }

  async deleteSecret(key, options = {}) {
    if (!this.initialized) throw new Error('Call init() first.');
    const api = this._getAxios();
    try {
      if (options.environmentId) {
        await api.delete(`/api/secrets/environment/${options.environmentId}/${encodeURIComponent(key)}`);
      } else {
        await api.delete(`/api/secrets/${encodeURIComponent(key)}`);
      }
      this.encryptedSecretsMap.delete(key);
      this.plaintextCache.delete(key);
      return true;
    } catch (e) {
      throw new Error('Failed to delete secret: ' + (e.response?.data?.message || e.message));
    }
  }

  isInitialized() {
    return this.initialized;
  }

  getActiveWorkspace() {
    if (!this.workspaceId) return null;
    return this.workspaces.find(w => w.id === this.workspaceId) || null;
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
}

const defaultInstance = new CryptEnv();
defaultInstance.CryptEnv = CryptEnv;

module.exports = defaultInstance;
module.exports.CryptEnv = CryptEnv;
module.exports.default = defaultInstance;
