CREATE TABLE jit_requests (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    environment_id BIGINT NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    requester_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    justification VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    duration_hours INTEGER NOT NULL DEFAULT 2,
    starts_at TIMESTAMP,
    ends_at TIMESTAMP,
    approved_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_jit_status CHECK (status IN ('PENDING', 'APPROVED', 'DENIED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT chk_jit_duration CHECK (duration_hours >= 1 AND duration_hours <= 24)
);

CREATE INDEX idx_jit_workspace ON jit_requests(workspace_id);
CREATE INDEX idx_jit_requester ON jit_requests(requester_id);
CREATE INDEX idx_jit_status ON jit_requests(status);
CREATE INDEX idx_jit_ends ON jit_requests(ends_at);
