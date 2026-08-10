-- V12: Create secret_versions table for SecretVersion JPA entity

CREATE TABLE IF NOT EXISTS secret_versions (
    id BIGSERIAL PRIMARY KEY,
    secret_key VARCHAR(255) NOT NULL,
    version_number INTEGER NOT NULL,
    encrypted_value TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT false,
    rotation_reason VARCHAR(255),
    rotated_by_email VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    metadata TEXT
);

CREATE INDEX IF NOT EXISTS idx_secret_version_key ON secret_versions(secret_key);
CREATE INDEX IF NOT EXISTS idx_secret_version_number ON secret_versions(secret_key, version_number);
CREATE INDEX IF NOT EXISTS idx_secret_version_active ON secret_versions(is_active);
