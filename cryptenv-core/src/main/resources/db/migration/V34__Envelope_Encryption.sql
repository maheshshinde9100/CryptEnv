-- Add envelope encryption columns to secret_versions table
-- This enables per-version DEK (Data Encryption Key) wrapped by workspace KEK

ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS wrapped_dek TEXT;
ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS dek_algorithm VARCHAR(50);
ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS dek_iv VARCHAR(64);
ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS dek_tag VARCHAR(64);

-- Add index for envelope encryption queries (when we need to rotate DEKs)
CREATE INDEX IF NOT EXISTS idx_secret_versions_wrapped_dek ON secret_versions(wrapped_dek) WHERE wrapped_dek IS NOT NULL;
