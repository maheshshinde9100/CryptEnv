-- Add constraint for secret_type to ensure valid values
ALTER TABLE secrets DROP CONSTRAINT IF EXISTS chk_secret_type;
ALTER TABLE secrets ADD CONSTRAINT chk_secret_type
    CHECK (secret_type IN ('TEXT', 'MULTILINE', 'JSON', 'CERTIFICATE', 'SSH_KEY', 'BINARY'));
