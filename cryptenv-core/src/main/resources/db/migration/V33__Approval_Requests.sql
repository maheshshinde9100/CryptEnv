-- Four-eyes approval (§10) for destructive actions.
--
-- A user raises an approval request; a *different* eligible user (workspace owner/admin, or a
-- global admin) approves or rejects it. On approval the deferred action is executed by the
-- registered executor for that action. The requester can never approve their own request.
--
-- payload is a JSON object carrying the action parameters (e.g. {"environmentId":5,"key":"X"}).

CREATE TABLE approval_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    action VARCHAR(60) NOT NULL,
    resource_type VARCHAR(40),
    resource_id VARCHAR(255),
    payload TEXT,
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approver_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    decision_note VARCHAR(500),
    result_detail VARCHAR(1000),
    decided_at TIMESTAMP,
    executed_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_approval_status CHECK (status IN
        ('PENDING', 'APPROVED', 'REJECTED', 'EXECUTED', 'FAILED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_approval_requester ON approval_requests(requester_id, created_at);
CREATE INDEX idx_approval_workspace_status ON approval_requests(workspace_id, status);
CREATE INDEX idx_approval_status_expiry ON approval_requests(status, expires_at);
