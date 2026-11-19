-- Zero-knowledge mode per workspace
ALTER TABLE workspaces ADD COLUMN zero_knowledge_enabled BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE workspaces ADD COLUMN zk_passphrase_hash TEXT;
ALTER TABLE workspaces ADD COLUMN zk_recovery_key TEXT;
ALTER TABLE workspaces ADD COLUMN zk_recovery_key_hash TEXT;
ALTER TABLE workspaces ADD COLUMN zk_salt TEXT;

-- Master key rotation tracking
CREATE TABLE master_key_rotations (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    old_key_hash TEXT NOT NULL,
    new_key_hash TEXT NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress',
    total_secrets INTEGER NOT NULL DEFAULT 0,
    processed_secrets INTEGER NOT NULL DEFAULT 0,
    failed_secrets INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_by BIGINT NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_master_key_rotations_workspace_id ON master_key_rotations(workspace_id);
