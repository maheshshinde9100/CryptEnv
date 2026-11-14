-- Store API keys as SHA-256 hashes; keep a visible prefix for UI display.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE users ADD COLUMN IF NOT EXISTS api_key_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS api_key_prefix VARCHAR(32);
ALTER TABLE users ADD COLUMN IF NOT EXISTS api_key_created_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS api_key_expires_at TIMESTAMP;

-- Migrate existing plaintext keys to hash + prefix, then clear plaintext.
UPDATE users
SET
    api_key_prefix = LEFT(api_key, 16),
    api_key_hash = encode(digest(api_key, 'sha256'), 'hex'),
    api_key_created_at = COALESCE(api_key_created_at, CURRENT_TIMESTAMP)
WHERE api_key IS NOT NULL
  AND length(api_key) > 0
  AND (api_key_hash IS NULL OR api_key_hash = '');

UPDATE users SET api_key = NULL WHERE api_key_hash IS NOT NULL;

DROP INDEX IF EXISTS idx_users_api_key;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_api_key_hash ON users(api_key_hash) WHERE api_key_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_api_key_prefix ON users(api_key_prefix);
