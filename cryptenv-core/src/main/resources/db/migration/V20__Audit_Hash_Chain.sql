ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entry_hash VARCHAR(64);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS workspace_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_audit_workspace ON audit_logs(workspace_id);
CREATE INDEX IF NOT EXISTS idx_audit_entry_hash ON audit_logs(entry_hash);
