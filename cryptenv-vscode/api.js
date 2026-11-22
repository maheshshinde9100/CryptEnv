const manifest = require('./package.json');

class CryptEnvAPI {
    constructor(context) {
        this.context = context;
        // Single source of truth for the published default: package.json -> cryptenv.backendUrl
        this.DEFAULT_BASE_URL = manifest.contributes.configuration.properties['cryptenv.backendUrl'].default;
        this._jwtCache = null;
        this._apiKeyCache = null;
        this._refreshCache = null;
    }

    _normalizeBaseUrl(url) {
        const trimmed = (url || '').trim().replace(/\/+$/, '');
        if (!/^https?:\/\//i.test(trimmed)) {
            throw new Error('Backend URL must start with http:// or https://');
        }
        return /\/api$/i.test(trimmed) ? trimmed : trimmed + '/api';
    }

    async getBaseUrl() {
        try {
            // Resolved lazily so the module stays testable outside VS Code.
            const vscode = require('vscode');
            if (vscode.workspace && vscode.workspace.getConfiguration) {
                const configured = vscode.workspace.getConfiguration('cryptenv').get('backendUrl');
                if (configured) return this._normalizeBaseUrl(configured);
            }
        } catch (_) {
        }
        return this.context.globalState.get('cryptenv.baseUrl', this.DEFAULT_BASE_URL);
    }

    async setBaseUrl(url) {
        const apiUrl = this._normalizeBaseUrl(url);
        await this.context.globalState.update('cryptenv.baseUrl', apiUrl);
        try {
            const vscode = require('vscode');
            if (vscode.workspace && vscode.workspace.getConfiguration) {
                await vscode.workspace.getConfiguration('cryptenv')
                    .update('backendUrl', apiUrl, vscode.ConfigurationTarget.Global);
            }
        } catch (_) {
        }
        return apiUrl;
    }

    async getJwt() {
        if (this._jwtCache) return this._jwtCache;
        const t = await this.context.secrets.get('cryptenv.jwt');
        if (t) this._jwtCache = t;
        return t || null;
    }

    async setJwt(token, refreshToken) {
        this._jwtCache = token || null;
        if (token) {
            await this.context.secrets.store('cryptenv.jwt', token);
            await this.context.secrets.delete('cryptenv.apiKey');
            this._apiKeyCache = null;
        } else {
            await this.context.secrets.delete('cryptenv.jwt');
        }
        if (refreshToken !== undefined) {
            this._refreshCache = refreshToken || null;
            if (refreshToken) await this.context.secrets.store('cryptenv.refreshToken', refreshToken);
            else await this.context.secrets.delete('cryptenv.refreshToken');
        }
    }

    async getRefreshToken() {
        if (this._refreshCache) return this._refreshCache;
        const t = await this.context.secrets.get('cryptenv.refreshToken');
        if (t) this._refreshCache = t;
        return t || null;
    }

    async getApiKey() {
        if (this._apiKeyCache) return this._apiKeyCache;
        const k = await this.context.secrets.get('cryptenv.apiKey');
        if (k) this._apiKeyCache = k;
        return k || null;
    }

    async setApiKey(key) {
        this._apiKeyCache = key || null;
        if (key) {
            await this.context.secrets.store('cryptenv.apiKey', key);
            await this.context.secrets.delete('cryptenv.jwt');
            await this.context.secrets.delete('cryptenv.refreshToken');
            this._jwtCache = null;
            this._refreshCache = null;
        } else {
            await this.context.secrets.delete('cryptenv.apiKey');
        }
    }

    async clearAuth() {
        this._jwtCache = null;
        this._apiKeyCache = null;
        this._refreshCache = null;
        await this.context.secrets.delete('cryptenv.jwt');
        await this.context.secrets.delete('cryptenv.apiKey');
        await this.context.secrets.delete('cryptenv.refreshToken');
    }

    async isAuthenticated() {
        const jwt = await this.getJwt();
        const apiKey = await this.getApiKey();
        return Boolean(jwt || apiKey);
    }

    _extractMessage(error, fallback) {
        if (error && error.response && error.response.data) {
            const d = error.response.data;
            if (typeof d === 'string' && d.length) return d;
            return d.message || d.error || d.detail || fallback || error.message;
        }
        return (error && error.message) ? error.message : fallback;
    }

    _decodeExpiry(token) {
        try {
            const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
            return payload.exp ? payload.exp * 1000 : null;
        } catch (_) {
            return null;
        }
    }

    _isAuthPath(url) {
        return /\/auth\/(login|register|refresh|logout|mfa|email-verification|forgot-password|verify-reset-code|reset-password|email-check)/.test(url || '');
    }

    async _refreshAccessToken() {
        const refreshToken = await this.getRefreshToken();
        if (!refreshToken) return false;
        try {
            const baseUrl = await this.getBaseUrl();
            const controller = new AbortController();
            const timer = setTimeout(function() { controller.abort(); }, 15000);
            let response;
            try {
                response = await fetch(baseUrl + '/auth/refresh', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ refreshToken }),
                    signal: controller.signal
                });
            } finally {
                clearTimeout(timer);
            }
            if (!response.ok) throw new Error('Refresh rejected');
            const data = await response.json();
            if (!data || !data.token) throw new Error('Refresh returned no token');
            await this.setJwt(data.token, data.refreshToken || refreshToken);
            return true;
        } catch (_) {
            await this.clearAuth();
            return false;
        }
    }

    async _request(config) {
        const baseUrl = await this.getBaseUrl();
        let jwt = await this.getJwt();
        const apiKey = await this.getApiKey();

        if (jwt && !config._retried && !config._noRefresh && !this._isAuthPath(config.url)) {
            const expiresAt = this._decodeExpiry(jwt);
            if (expiresAt && expiresAt - Date.now() < 60000) {
                if (await this._refreshAccessToken()) jwt = await this.getJwt();
            }
        }

        const headers = { 'Content-Type': 'application/json' };
        if (jwt) headers['Authorization'] = 'Bearer ' + jwt;
        if (apiKey) headers['X-API-Key'] = apiKey;

        const abortController = new AbortController();
        const timeout = setTimeout(function() { abortController.abort(); }, 30000);
        let response;
        try {
            response = await fetch(baseUrl + config.url, {
                method: config.method || 'get',
                headers: Object.assign({}, headers, config.headers || {}),
                body: config.data === undefined ? undefined : JSON.stringify(config.data),
                signal: abortController.signal
            });
        } catch (error) {
            if (error && error.name === 'AbortError') {
                throw new Error('Request timed out. Check your network or the backend URL.');
            }
            throw new Error('Could not reach the CryptEnv backend. Verify your API URL and internet connection.');
        } finally {
            clearTimeout(timeout);
        }

        const text = response.status === 204 ? '' : await response.text();
        let data = text;
        if (text) {
            try { data = JSON.parse(text); } catch (_) { }
        }

        const errorMessage = this._extractMessage({ response: { data: data } }, 'Request failed');
        const stepUpChallenge = response.status === 401 && /step-?up|mfa/i.test(String(errorMessage));

        if (response.status === 401 && jwt && !config._retried && !this._isAuthPath(config.url) && !stepUpChallenge) {
            if (await this._refreshAccessToken()) {
                return this._request(Object.assign({}, config, { _retried: true }));
            }
        }

        if (!response.ok) {
            const msg = errorMessage;
            if (stepUpChallenge) {
                throw new Error('Step-up MFA required: ' + msg);
            }
            if (response.status === 401) {
                await this.setJwt(null);
                throw new Error('Session expired. Please sign in again.' + (msg ? ' (' + msg + ')' : ''));
            }
            if (response.status === 403) {
                throw new Error('Permission denied: ' + (msg || 'your account cannot perform this action.'));
            }
            if (response.status === 404) {
                throw new Error('Resource not found: ' + (msg || '404'));
            }
            if (response.status === 409) {
                throw new Error(msg || 'Conflict');
            }
            if (response.status >= 400 && response.status < 500) {
                throw new Error(msg || 'Request failed');
            }
            throw new Error('Server error (' + response.status + '): ' + (msg || 'unexpected response'));
        }
        return data;
    }

    async login(email, password) {
        return this._request({ method: 'post', url: '/auth/login', data: { email, password } });
    }

    async verifyMfa(mfaToken, code) {
        return this._request({ method: 'post', url: '/auth/mfa/verify', data: { mfaToken, code }, _noRefresh: true });
    }

    async register(email, username, password, firstName, lastName) {
        return this._request({
            method: 'post',
            url: '/auth/register',
            data: { email, username, password, firstName, lastName }
        });
    }

    async checkEmail(email) {
        return this._request({ method: 'get', url: '/auth/email-check?email=' + encodeURIComponent(email) });
    }

    async mailStatus() {
        return this._request({ method: 'get', url: '/auth/email-verification/status' });
    }

    async verifyEmail(email, code) {
        return this._request({
            method: 'post',
            url: '/auth/email-verification/verify',
            data: { email, code }
        });
    }

    async resendVerification(email) {
        return this._request({
            method: 'post',
            url: '/auth/email-verification/resend',
            data: { email }
        });
    }

    async forgotPassword(email) {
        return this._request({ method: 'post', url: '/auth/forgot-password', data: { email } });
    }

    async verifyResetCode(email, code) {
        return this._request({ method: 'post', url: '/auth/verify-reset-code', data: { email, code } });
    }

    async resetPassword(token, newPassword) {
        return this._request({
            method: 'post',
            url: '/auth/reset-password',
            data: { token, newPassword }
        });
    }

    async logout() {
        const jwt = await this.getJwt();
        if (!jwt) return;
        try {
            await this._request({ method: 'post', url: '/auth/logout', _noRefresh: true });
        } catch (_) {
            // Local credentials are cleared regardless.
        }
    }

    async getProfile() {
        return this._request({ method: 'get', url: '/auth/me' });
    }

    async regenerateApiKey() {
        return this._request({ method: 'post', url: '/auth/api-key/regenerate' });
    }

    async startStepUp() {
        return this._request({ method: 'post', url: '/auth/mfa/step-up' });
    }

    async listWorkspaces() {
        const result = await this._request({ method: 'get', url: '/workspaces' });
        return Array.isArray(result) ? result : [];
    }

    async createWorkspace(name, description, workspaceEncryptionKey) {
        const data = { name };
        if (description) data.description = description;
        if (workspaceEncryptionKey) data.workspaceEncryptionKey = workspaceEncryptionKey;
        return this._request({ method: 'post', url: '/workspaces', data });
    }

    async setWorkspaceEncryptionKey(id, workspaceEncryptionKey) {
        return this._request({
            method: 'put',
            url: '/workspaces/' + encodeURIComponent(id) + '/encryption-key',
            data: { workspaceEncryptionKey }
        });
    }

    async deleteWorkspace(id) {
        return this._request({ method: 'delete', url: '/workspaces/' + encodeURIComponent(id) });
    }

    async listEnvironments(workspaceId) {
        const result = await this._request({
            method: 'get',
            url: '/environments/workspace/' + encodeURIComponent(workspaceId)
        });
        return Array.isArray(result) ? result : [];
    }

    async createEnvironment(workspaceId, name) {
        return this._request({
            method: 'post',
            url: '/environments',
            data: { workspaceId, name }
        });
    }

    async deleteEnvironment(environmentId) {
        return this._request({ method: 'delete', url: '/environments/' + encodeURIComponent(environmentId) });
    }

    async listSecrets() {
        const result = await this._request({ method: 'get', url: '/secrets' });
        return Array.isArray(result) ? result : [];
    }

    async listSecretsByEnvironment(environmentId) {
        const result = await this._request({
            method: 'get',
            url: '/secrets/environment/' + encodeURIComponent(environmentId)
        });
        return Array.isArray(result) ? result : [];
    }

    async getSecret(key) {
        return this._request({ method: 'get', url: '/secrets/' + encodeURIComponent(key) });
    }

    async getSecretByEnvironment(environmentId, key) {
        return this._request({
            method: 'get',
            url: '/secrets/environment/' + encodeURIComponent(environmentId) + '/' + encodeURIComponent(key)
        });
    }

    async revealSecret(environmentId, key, stepUp) {
        const body = {};
        if (stepUp && stepUp.stepUpToken) body.stepUpToken = stepUp.stepUpToken;
        if (stepUp && stepUp.totpCode) body.totpCode = stepUp.totpCode;
        return this._request({
            method: 'post',
            url: '/secrets/environment/' + encodeURIComponent(environmentId) + '/' + encodeURIComponent(key) + '/reveal',
            data: body
        });
    }

    async listSecretVersions(key) {
        const result = await this._request({
            method: 'get',
            url: '/secrets/' + encodeURIComponent(key) + '/versions'
        });
        return Array.isArray(result) ? result : [];
    }

    async rollbackSecret(key, version) {
        return this._request({
            method: 'post',
            url: '/secrets/' + encodeURIComponent(key) + '/versions/rollback/' + encodeURIComponent(version)
        });
    }

    async createSecret(key, value, environmentId, description, encrypted) {
        const payload = { key, value };
        if (environmentId) payload.environmentId = environmentId;
        if (description !== undefined && description !== null && description !== '') {
            payload.description = description;
        }
        if (encrypted !== undefined) payload.encrypted = Boolean(encrypted);
        return this._request({ method: 'post', url: '/secrets', data: payload });
    }

    async updateSecret(environmentId, key, value, description) {
        const payload = {};
        if (value !== undefined && value !== null && value !== '') {
            payload.value = value;
        } else {
            const existing = await this.getSecretByEnvironment(environmentId, key);
            if (existing && existing.value !== undefined) payload.value = existing.value;
            else throw new Error('Value is required when updating a secret.');
        }
        if (description !== undefined) payload.description = description || '';
        return this._request({
            method: 'put',
            url: '/secrets/environment/' + encodeURIComponent(environmentId) + '/' + encodeURIComponent(key),
            data: payload
        });
    }

    async deleteSecret(environmentId, key) {
        return this._request({
            method: 'delete',
            url: '/secrets/environment/' + encodeURIComponent(environmentId) + '/' + encodeURIComponent(key)
        });
    }
}

module.exports = CryptEnvAPI;