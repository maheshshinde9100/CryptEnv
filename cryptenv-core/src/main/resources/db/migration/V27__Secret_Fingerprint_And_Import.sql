ALTER TABLE secret_versions ADD COLUMN IF NOT EXISTS content_fingerprint VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_secret_versions_fingerprint ON secret_versions(content_fingerprint);

ALTER TABLE secrets ADD COLUMN IF NOT EXISTS content_fingerprint VARCHAR(64);
ALTER TABLE secrets ADD COLUMN IF NOT EXISTS tags VARCHAR(500);
ALTER TABLE secrets ADD COLUMN IF NOT EXISTS secret_type VARCHAR(50) DEFAULT 'TEXT';
