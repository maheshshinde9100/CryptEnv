const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');

const SERVICE_NAME = 'cryptenv-cli';
const FALLBACK_DIR = path.join(os.homedir(), '.cryptenv');
const FALLBACK_FILE = path.join(FALLBACK_DIR, 'credentials.json');

let keytar = null;
try {
  // keytar is a native dependency and may be unavailable in headless environments
  keytar = require('keytar');
} catch {
  keytar = null;
}

/**
 * Machine-bound key for the on-disk fallback store. Derived from host + user so
 * the encrypted file is not portable across machines, and never stored in plaintext.
 */
function machineKey() {
  const seed = `${os.hostname()}:${os.userInfo().username}:cryptenv-cli-v1`;
  return crypto.createHash('sha256').update(seed).digest();
}

function encryptValue(plain) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', machineKey(), iv);
  const body = Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, body]).toString('base64');
}

function decryptValue(payload) {
  const buf = Buffer.from(payload, 'base64');
  const iv = buf.subarray(0, 12);
  const tag = buf.subarray(12, 28);
  const body = buf.subarray(28);
  const decipher = crypto.createDecipheriv('aes-256-gcm', machineKey(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(body), decipher.final()]).toString('utf8');
}

function readStore() {
  if (!fs.existsSync(FALLBACK_FILE)) return {};
  try {
    const stored = JSON.parse(fs.readFileSync(FALLBACK_FILE, 'utf8'));
    const out = {};
    for (const [key, value] of Object.entries(stored)) {
      try {
        out[key] = decryptValue(value);
      } catch {
        // ignore entries that cannot be decrypted on this machine
      }
    }
    return out;
  } catch {
    return {};
  }
}

function writeStore(store) {
  fs.mkdirSync(FALLBACK_DIR, { recursive: true });
  const enc = {};
  for (const [key, value] of Object.entries(store)) {
    enc[key] = encryptValue(value);
  }
  fs.writeFileSync(FALLBACK_FILE, JSON.stringify(enc), { mode: 0o600 });
}

async function setSecret(key, value) {
  if (keytar) {
    try {
      await keytar.setPassword(SERVICE_NAME, key, value);
      return;
    } catch {
      // fall through to file store
    }
  }
  const store = readStore();
  store[key] = value;
  writeStore(store);
}

async function getSecret(key) {
  if (keytar) {
    try {
      const value = await keytar.getPassword(SERVICE_NAME, key);
      if (value !== null && value !== undefined) return value;
    } catch {
      // fall through to file store
    }
  }
  const store = readStore();
  return store[key] ?? null;
}

async function deleteSecret(key) {
  if (keytar) {
    try {
      await keytar.deletePassword(SERVICE_NAME, key);
    } catch {
      // ignore
    }
  }
  const store = readStore();
  if (key in store) {
    delete store[key];
    writeStore(store);
  }
}

function usingKeyring() {
  return Boolean(keytar);
}

module.exports = { setSecret, getSecret, deleteSecret, usingKeyring };
