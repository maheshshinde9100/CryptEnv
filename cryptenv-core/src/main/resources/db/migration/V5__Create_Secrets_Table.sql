CREATE TABLE secrets (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(255) NOT NULL,
    value TEXT NOT NULL,
    environment_id BIGINT NOT NULL,
    description VARCHAR(500),
    encrypted BOOLEAN NOT NULL DEFAULT true,
    versioned BOOLEAN NOT NULL DEFAULT true,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_secrets_environment FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT uk_secrets_environment_key UNIQUE (environment_id, key)
);

CREATE INDEX idx_secrets_environment_id ON secrets(environment_id);
CREATE INDEX idx_secrets_key ON secrets(key);
