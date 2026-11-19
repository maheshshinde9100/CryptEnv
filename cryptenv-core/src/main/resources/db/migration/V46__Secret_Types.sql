-- Secret types: multiline, JSON, certificate, SSH key, tags
ALTER TABLE secrets ADD COLUMN secret_type VARCHAR(50) DEFAULT 'text';
ALTER TABLE secrets ADD COLUMN tags TEXT;
ALTER TABLE secrets ADD COLUMN is_multiline BOOLEAN NOT NULL DEFAULT false;
