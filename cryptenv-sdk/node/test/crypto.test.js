/**
 * Offline unit tests for AES-256-GCM crypto (no backend required).
 * Run: node test/crypto.test.js
 */
const assert = require('assert');
const { encryptWithKey, decryptWithKey } = require('../src/crypto');

function run() {
  const key = 'test-workspace-key-32bytes-long!!';
  const plain = 'db-password-super-secret';

  const c1 = encryptWithKey(plain, key);
  const c2 = encryptWithKey(plain, key);
  assert.ok(c1 && c2);
  assert.notStrictEqual(c1, c2, 'IV must randomize ciphertext');
  assert.strictEqual(decryptWithKey(c1, key), plain);
  assert.strictEqual(decryptWithKey(c2, key), plain);

  assert.throws(() => decryptWithKey(c1, 'wrong-key-xxxxxxxxxxxxxxxxx'), /wrong|Invalid|unable|auth|decrypt/i);

  const unicode = '你好 confidential';
  assert.strictEqual(decryptWithKey(encryptWithKey(unicode, key), key), unicode);

  assert.strictEqual(decryptWithKey(encryptWithKey('', key), key), '');

  // Tamper
  const buf = Buffer.from(c1, 'base64');
  buf[buf.length >> 1] ^= 0x01;
  assert.throws(() => decryptWithKey(buf.toString('base64'), key));

  console.log('✓ crypto.test.js passed');
}

run();
