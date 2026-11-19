-- Drift Detection (§9): fingerprint-only comparison of CryptEnv secrets against the values
-- last pushed to external providers. No plaintext is ever stored — only SHA-256 fingerprints.

-- Records the fingerprint of the value last successfully pushed to a provider target.
-- A drift scan compares this against the secret's current content_fingerprint.
CREATE TABLE provider_sync_state (
    id BIGSERIAL PRIMARY KEY,
    integration_id BIGINT NOT NULL REFERENCES provider_integrations(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    environment_id BIGINT REFERENCES environments(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    target VARCHAR(255) NOT NULL,
    secret_key VARCHAR(255) NOT NULL,
    -- SHA-256 hex of the plaintext value pushed (never the value itself)
    last_fingerprint VARCHAR(64),
    last_synced_at TIMESTAMP,
    last_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_sync_state_integration_target_key UNIQUE (integration_id, target, secret_key)
);

CREATE INDEX idx_sync_state_environment ON provider_sync_state(environment_id);
CREATE INDEX idx_sync_state_user ON provider_sync_state(user_id);
CREATE INDEX idx_sync_state_integration ON provider_sync_state(integration_id);

CREATE TABLE drift_scans (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    environment_id BIGINT REFERENCES environments(id) ON DELETE CASCADE,
    trigger VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    total INTEGER NOT NULL DEFAULT 0,
    in_sync INTEGER NOT NULL DEFAULT 0,
    stale INTEGER NOT NULL DEFAULT 0,
    modified INTEGER NOT NULL DEFAULT 0,
    missing INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    detail VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_drift_scan_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_drift_scan_trigger CHECK (trigger IN ('MANUAL', 'SCHEDULED'))
);

CREATE INDEX idx_drift_scan_user ON drift_scans(user_id);
CREATE INDEX idx_drift_scan_environment ON drift_scans(environment_id);
CREATE INDEX idx_drift_scan_created ON drift_scans(created_at);

CREATE TABLE drift_findings (
    id BIGSERIAL PRIMARY KEY,
    scan_id BIGINT NOT NULL REFERENCES drift_scans(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    environment_id BIGINT REFERENCES environments(id) ON DELETE CASCADE,
    secret_key VARCHAR(255) NOT NULL,
    provider VARCHAR(40),
    target VARCHAR(255),
    classification VARCHAR(20) NOT NULL,
    expected_fingerprint VARCHAR(64),
    actual_fingerprint VARCHAR(64),
    detail VARCHAR(1000),
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_drift_finding_class CHECK (classification IN ('IN_SYNC', 'STALE', 'MODIFIED', 'MISSING'))
);

CREATE INDEX idx_drift_finding_scan ON drift_findings(scan_id);
CREATE INDEX idx_drift_finding_env ON drift_findings(environment_id);
CREATE INDEX idx_drift_finding_class ON drift_findings(classification);
