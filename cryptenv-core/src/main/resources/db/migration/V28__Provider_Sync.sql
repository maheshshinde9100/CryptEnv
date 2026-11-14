-- Provider Sync (§8): connect external platforms and push workspace secrets to them.
-- Provider credentials are stored as master-key encrypted ciphertext (never plaintext).
-- Sync work is queued in a DB-backed queue with exponential backoff + dead-letter replay.

CREATE TABLE provider_integrations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    name VARCHAR(150),
    -- AES-GCM ciphertext (wrapped by the platform master key) of the provider credential JSON
    encrypted_credentials TEXT NOT NULL,
    -- Non-secret provider options (accountId, namespace, apiUrl, ...) as JSON
    config TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'CONNECTED',
    last_checked_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_provider_integration_user_provider UNIQUE (user_id, provider),
    CONSTRAINT chk_provider_integration_status CHECK (status IN ('CONNECTED', 'ERROR', 'DISCONNECTED'))
);

CREATE INDEX idx_provider_integration_user ON provider_integrations(user_id);
CREATE INDEX idx_provider_integration_provider ON provider_integrations(provider);

CREATE TABLE provider_sync_jobs (
    id BIGSERIAL PRIMARY KEY,
    integration_id BIGINT NOT NULL REFERENCES provider_integrations(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    environment_id BIGINT REFERENCES environments(id) ON DELETE SET NULL,
    provider VARCHAR(40) NOT NULL,
    target VARCHAR(255) NOT NULL,
    operation VARCHAR(20) NOT NULL DEFAULT 'PUSH',
    -- Master-key encrypted JSON map of {secretKey: plaintextValue} for PUSH operations
    payload_encrypted TEXT,
    -- Secret key name for DELETE operations
    secret_key VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP,
    last_error VARCHAR(1000),
    -- Per-secret outcome JSON: [{key, success, error}]
    results TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_sync_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'DEAD')),
    CONSTRAINT chk_sync_job_operation CHECK (operation IN ('PUSH', 'DELETE'))
);

CREATE INDEX idx_sync_job_status ON provider_sync_jobs(status);
CREATE INDEX idx_sync_job_integration ON provider_sync_jobs(integration_id);
CREATE INDEX idx_sync_job_next_attempt ON provider_sync_jobs(next_attempt_at);
