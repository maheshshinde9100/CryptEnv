-- Secret types: multiline, JSON, certificate, SSH key, tags
ALTER TABLE secrets ADD COLUMN IF NOT EXISTS secret_type VARCHAR(50) DEFAULT 'text';
ALTER TABLE secrets ADD COLUMN IF NOT EXISTS tags TEXT;
ALTER TABLE secrets ADD COLUMN IF NOT EXISTS is_multiline BOOLEAN NOT NULL DEFAULT false;
