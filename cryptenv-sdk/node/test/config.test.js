/**
 * Unit tests for config resolution (no backend required).
 * Run: node test/config.test.js
 */
const assert = require('assert');
const { normalizeApiUrl, resolveMasterKey } = require('../src/config');

function run() {
  assert.strictEqual(
    normalizeApiUrl('https://cryptenv-backend.onrender.com/api'),
    'https://cryptenv-backend.onrender.com'
  );
  assert.strictEqual(
    normalizeApiUrl('https://cryptenv-backend.onrender.com/api/'),
    'https://cryptenv-backend.onrender.com'
  );
  assert.strictEqual(
    normalizeApiUrl('https://cryptenv-backend.onrender.com'),
    'https://cryptenv-backend.onrender.com'
  );
  assert.strictEqual(
    normalizeApiUrl('http://localhost:8080/api'),
    'http://localhost:8080'
  );

  const prevMaster = process.env.CRYPTENV_MASTER_KEY;
  const prevWsKey = process.env.CRYPTENV_WORKSPACE_ENCRYPTION_KEY;
  delete process.env.CRYPTENV_MASTER_KEY;
  delete process.env.CRYPTENV_WORKSPACE_ENCRYPTION_KEY;

  assert.strictEqual(resolveMasterKey({ masterKey: 'from-options' }), 'from-options');
  assert.strictEqual(
    resolveMasterKey({ workspaceEncryptionKey: 'legacy-key' }),
    'legacy-key'
  );
  assert.strictEqual(resolveMasterKey({ masterKey: 'preferred' }), 'preferred');

  process.env.CRYPTENV_MASTER_KEY = 'env-master';
  assert.strictEqual(resolveMasterKey({}), 'env-master');

  delete process.env.CRYPTENV_MASTER_KEY;
  process.env.CRYPTENV_WORKSPACE_ENCRYPTION_KEY = 'env-legacy';
  assert.strictEqual(resolveMasterKey({}), 'env-legacy');

  if (prevMaster !== undefined) process.env.CRYPTENV_MASTER_KEY = prevMaster;
  else delete process.env.CRYPTENV_MASTER_KEY;
  if (prevWsKey !== undefined) process.env.CRYPTENV_WORKSPACE_ENCRYPTION_KEY = prevWsKey;
  else delete process.env.CRYPTENV_WORKSPACE_ENCRYPTION_KEY;

  console.log('✓ config.test.js passed');
}

run();
