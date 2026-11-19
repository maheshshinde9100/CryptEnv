CREATE TABLE leak_findings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id BIGINT,
    environment_id BIGINT,
    source VARCHAR(500) NOT NULL,
    line_number INTEGER,
    rule_name VARCHAR(100) NOT NULL,
    value_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_leak_status CHECK (status IN ('OPEN','IGNORED','ROTATION_SUGGESTED','RESOLVED'))
);
CREATE INDEX idx_leak_findings_user_created ON leak_findings(user_id, created_at DESC);
