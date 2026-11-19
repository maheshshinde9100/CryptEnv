CREATE TABLE security_incidents (
    id BIGSERIAL PRIMARY KEY,
    incident_number VARCHAR(24) NOT NULL UNIQUE,
    reporter_id BIGINT NOT NULL REFERENCES users(id),
    workspace_id BIGINT,
    environment_id BIGINT,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    timeline TEXT,
    post_mortem TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN','INVESTIGATING','CONTAINED','RESOLVED','POST_MORTEM'))
);
CREATE INDEX idx_security_incidents_reporter ON security_incidents(reporter_id, created_at DESC);
