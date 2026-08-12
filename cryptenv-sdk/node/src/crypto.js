const crypto = require('crypto');

const ALGORITHM = 'aes-256-gcm';
const GCM_IV_LENGTH = 12;
const GCM_TAG_LENGTH = 16;

function deriveKey(rawKey) {
  if (!rawKey || typeof rawKey !== 'string') {
    throw new Error('Workspace encryption key is required for decryption');
  }
  const keyBytes = Buffer.from(rawKey, 'utf8');
  const finalKey = Buffer.alloc(32);
  const copyLen = Math.min(keyBytes.length, 32);
  keyBytes.copy(finalKey, 0, 0, copyLen);
  return finalKey;
}

function encryptWithKey(plaintext, encryptionKey) {
  if (plaintext == null) {
    return null;
  }
  const key = deriveKey(encryptionKey);
  const iv = crypto.randomBytes(GCM_IV_LENGTH);
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
  const ciphertext = Buffer.concat([
    cipher.update(Buffer.from(String(plaintext), 'utf8')),
    cipher.final()
  ]);
  const authTag = cipher.getAuthTag();
  const combined = Buffer.concat([iv, ciphertext, authTag]);
  return combined.toString('base64');
}

function decryptWithKey(ciphertextB64, decryptionKey) {
  if (!ciphertextB64 || typeof ciphertextB64 !== 'string') {
    return null;
  }
  const key = deriveKey(decryptionKey);
  const combined = Buffer.from(ciphertextB64, 'base64');
  if (combined.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
    throw new Error('Invalid encrypted data: too short');
  }
  const iv = combined.subarray(0, GCM_IV_LENGTH);
  const authTag = combined.subarray(combined.length - GCM_TAG_LENGTH);
  const ciphertext = combined.subarray(GCM_IV_LENGTH, combined.length - GCM_TAG_LENGTH);
  const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
  decipher.setAuthTag(authTag);
  const plaintext = Buffer.concat([
    decipher.update(ciphertext),
    decipher.final()
  ]);
  return plaintext.toString('utf8');
}

module.exports = {
  encryptWithKey,
  decryptWithKey,
  deriveKey,
  GCM_IV_LENGTH,
  GCM_TAG_LENGTH
};
