const axios = require('axios');

class CryptEnvAPI {
    constructor(context) {
        this.context = context;
        this.DEFAULT_BASE_URL = 'https://cryptenv-backend.onrender.com/api';
        this._jwtCache = null;
        this._apiKeyCache = null;
    }

    async getBaseUrl() {
        return this.context.globalState.get('cryptenv.baseUrl', this.DEFAULT_BASE_URL);
    }

    async setBaseUrl(url) {
        const trimmed = (url || '').replace(/\/+$/, '');
        await this.context.globalState.update('cryptenv.baseUrl', trimmed);
    }

    async getJwt() {
        if (this._jwtCache) {
            return this._jwtCache;
        }
        const t = await this.context.secrets.get('cryptenv.jwt');
        if (t) {
            this._jwtCache = t;
        }
        return t || null;
    }

    async setJwt(token) {
        this._jwtCache = token || null;
        if (token) {
            await this.context.secrets.store('cryptenv.jwt', token);
            await this.context.secrets.delete('cryptenv.apiKey');
            this._apiKeyCache = null;
        } else {
            await this.context.secrets.delete('cryptenv.jwt');
        }
    }

    async getApiKey() {
        if (this._apiKeyCache) {
            return this._apiKeyCache;
        }
        const k = await this.context.secrets.get('cryptenv.apiKey');
        if (k) {
            this._apiKeyCache = k;
        }
        return k || null;
    }

    async setApiKey(key) {
        this._apiKeyCache = key || null;
        if (key) {
            await this.context.secrets.store('cryptenv.apiKey', key);
            await this.context.secrets.delete('cryptenv.jwt');
            this._jwtCache = null;
        } else {
            await this.context.secrets.delete('cryptenv.apiKey');
        }
    }

    async clearAuth() {
        this._jwtCache = null;
        this._apiKeyCache = null;
        await this.context.secrets.delete('cryptenv.jwt');
        await this.context.secrets.delete('cryptenv.apiKey');
    }

    async isAuthenticated() {
        const jwt = await this.getJwt();
        const apiKey = await this.getApiKey();
        return Boolean(jwt || apiKey);
    }

    _extractMessage(error, fallback) {
        if (error && error.response && error.response.data) {
            const d = error.response.data;
            if (typeof d === 'string' && d.length) {
                return d;
            }
            return d.message || d.error || d.detail || fallback || error.message;
        }
        return (error && error.message) ? error.message : fallback;
    }

    async _request(config) {
        const baseUrl = await this.getBaseUrl();
        const jwt = await this.getJwt();
        const apiKey = await this.getApiKey();

        const headers = { 'Content-Type': 'application/json' };
        if (jwt) {
            headers['Authorization'] = 'Bearer ' + jwt;
        }
        if (apiKey) {
            headers['X-API-Key'] = apiKey;
        }

        try {
            const response = await axios({
                baseURL: baseUrl,
                ...config,
                headers: { ...headers, ...(config.headers || {}) },
                timeout: 15000
            });
            return response.data;
        } catch (error) {
            if (error.response) {
                const status = error.response.status;
                const msg = this._extractMessage(error, 'Request failed');
                if (status === 401 || status === 403) {
                    throw new Error('Authentication failed: ' + (msg || 'invalid or expired credentials'));
                }
                if (status === 404) {
                    throw new Error('Resource not found: ' + (msg || '404'));
                }
                if (status === 400) {
                    throw new Error(msg || 'Invalid request');
                }
                if (status === 409) {
                    throw new Error(msg || 'Conflict');
                }
                if (status >= 400 && status < 500) {
                    throw new Error(msg || 'Request failed');
                }
                throw new Error('Server error (' + status + '): ' + (msg || 'unexpected response'));
            }
            if (error.code === 'ECONNABORTED') {
                throw new Error('Request timed out. Check your network or the backend URL.');
            }
            if (error.code === 'ERR_NETWORK' || !error.status) {
                throw new Error('Could not reach the CryptEnv backend. Verify your API URL and internet connection.');
            }
            throw new Error(this._extractMessage(error, 'Request failed'));
        }
    }

    async login(email, password) {
        return this._request({
            method: 'post',
            url: '/auth/login',
            data: { email, password }
        });
    }

    async register(email, username, password, firstName, lastName) {
        return this._request({
            method: 'post',
            url: '/auth/register',
            data: { email, username, password, firstName, lastName }
        });
    }

    async getProfile() {
        return this._request({
            method: 'get',
            url: '/auth/me'
        });
    }

    async regenerateApiKey() {
        return this._request({
            method: 'post',
            url: '/auth/api-key/regenerate'
        });
    }

    async listWorkspaces() {
        return this._request({
            method: 'get',
            url: '/workspaces'
        });
    }

    async createWorkspace(name, description) {
        return this._request({
            method: 'post',
            url: '/workspaces',
            data: { name, description }
        });
    }

    async deleteWorkspace(id) {
        return this._request({
            method: 'delete',
            url: '/workspaces/' + encodeURIComponent(id)
        });
    }

    async listEnvironments(workspaceId) {
        return this._request({
            method: 'get',
            url: '/environments/workspace/' + encodeURIComponent(workspaceId)
        });
    }

    async createEnvironment(workspaceId, name) {
        return this._request({
            method: 'post',
            url: '/environments',
            data: { workspaceId, name, isActive: true }
        });
    }

    async listSecrets() {
        return this._request({
            method: 'get',
            url: '/secrets'
        });
    }

    async listSecretsByEnvironment(environmentId) {
        return this._request({
            method: 'get',
            url: '/secrets/environment/' + encodeURIComponent(environmentId)
        });
    }

    async getSecret(key) {
        return this._request({
            method: 'get',
            url: '/secrets/' + encodeURIComponent(key)
        });
    }

    async createSecret(key, value, environmentId, description, encrypted) {
        const payload = { key, value };
        if (environmentId) {
            payload.environmentId = environmentId;
        }
        if (description !== undefined && description !== null && description !== '') {
            payload.description = description;
        }
        if (encrypted !== undefined) {
            payload.encrypted = Boolean(encrypted);
        }
        return this._request({
            method: 'post',
            url: '/secrets',
            data: payload
        });
    }

    async updateSecret(environmentId, key, value, description) {
        const payload = {};
        if (value !== undefined) {
            payload.value = value;
        }
        if (description !== undefined) {
            payload.description = description;
        }
        return this._request({
            method: 'put',
            url: '/secrets/environment/' + encodeURIComponent(environmentId) + '/' + encodeURIComponent(key),
            data: payload
        });
    }

    async deleteSecret(key) {
        return this._request({
            method: 'delete',
            url: '/secrets/' + encodeURIComponent(key)
        });
    }
}

module.exports = CryptEnvAPI;
