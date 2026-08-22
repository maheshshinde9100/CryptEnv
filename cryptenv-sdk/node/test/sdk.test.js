/**
 * Offline unit tests for runtime SDK helpers (no backend required).
 * Run: node test/sdk.test.js
 */
const assert = require('assert');
const { CryptEnv } = require('../src/index');
const { encryptWithKey } = require('../src/crypto');

async function run() {
  const sdk = new CryptEnv({
    email: 'test@example.com',
    password: 'secret',
    workspaceId: 1,
    masterKey: 'test-workspace-key-32bytes-long!!',
    apiUrl: 'http://localhost:8080'
  });

  const masterKey = 'test-workspace-key-32bytes-long!!';
  const encDb = encryptWithKey('postgres://localhost/db', masterKey);
  const encJwt = encryptWithKey('jwt-super-secret', masterKey);

  sdk.initialized = true;
  sdk.workspaceId = 1;
  sdk.workspaces = [{ id: 1, name: 'default', hasEncryptionKey: true, environments: [] }];
  sdk.encryptedSecretsMap = new Map([
    ['DATABASE_URL', encDb],
    ['JWT_SECRET', encJwt]
  ]);

  assert.strictEqual(sdk.get('DATABASE_URL'), 'postgres://localhost/db');
  assert.strictEqual(sdk.get('JWT_SECRET'), 'jwt-super-secret');
  assert.deepStrictEqual(sdk.listKeys().sort(), ['DATABASE_URL', 'JWT_SECRET']);

  assert.throws(() => sdk.get('MISSING'), /not found/);
  assert.strictEqual(sdk.get('MISSING', { throwOnMissing: false }), undefined);

  const originalDb = process.env.DATABASE_URL;
  const originalJwt = process.env.JWT_SECRET;
  delete process.env.DATABASE_URL;
  delete process.env.JWT_SECRET;

  const loaded = await sdk.load({ overwrite: true });
  assert.strictEqual(loaded, 2);
  assert.strictEqual(process.env.DATABASE_URL, 'postgres://localhost/db');
  assert.strictEqual(process.env.JWT_SECRET, 'jwt-super-secret');

  process.env.DATABASE_URL = 'keep-me';
  delete process.env.JWT_SECRET;
  const loadedSkip = await sdk.load({ overwrite: false });
  assert.strictEqual(loadedSkip, 1);
  assert.strictEqual(process.env.DATABASE_URL, 'keep-me');
  assert.strictEqual(process.env.JWT_SECRET, 'jwt-super-secret');

  if (originalDb !== undefined) process.env.DATABASE_URL = originalDb;
  else delete process.env.DATABASE_URL;
  if (originalJwt !== undefined) process.env.JWT_SECRET = originalJwt;
  else delete process.env.JWT_SECRET;

  console.log('✓ sdk.test.js passed');
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
