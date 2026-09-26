const assert = require('assert');
const CryptEnvAPI = require('../api');

const values = new Map();
const context = {
    globalState: {
        get: function (key, fallback) { return values.has(key) ? values.get(key) : fallback; },
        update: async function (key, value) { values.set(key, value); }
    },
    secrets: {
        get: async function () { return null; },
        store: async function () {},
        delete: async function () {}
    }
};

(async function () {
    const api = new CryptEnvAPI(context);

    await api.setBaseUrl('https://cryptenv-backend.onrender.com/');
    assert.strictEqual(await api.getBaseUrl(), 'https://cryptenv-backend.onrender.com/api');

    await api.setBaseUrl('https://example.test/api/');
    assert.strictEqual(await api.getBaseUrl(), 'https://example.test/api');

    await assert.rejects(function () { return api.setBaseUrl('example.test'); }, /http/);

    const requests = [];
    api._request = async function (config) { requests.push(config); return config; };
    await api.getSecretByEnvironment(25, 'DATABASE_URL');
    await api.deleteSecret(25, 'DATABASE_URL');
    assert.strictEqual(requests[0].url, '/secrets/environment/25/DATABASE_URL');
    assert.strictEqual(requests[1].url, '/secrets/environment/25/DATABASE_URL');
    assert.strictEqual(requests[1].method, 'delete');

    console.log('Extension API test passed.');
})().catch(function (error) {
    console.error(error);
    process.exitCode = 1;
});
