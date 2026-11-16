-- Add workspace_id to audit logs for workspace-scoped queries
ALTER TABLE audit_logs ADD COLUMN workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE;
CREATE INDEX idx_audit_logs_workspace_id ON audit_logs(workspace_id);

-- Make audit logs append-only at DB level
CREATE OR REPLACE FUNCTION prevent_audit_log_update()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are append-only and cannot be updated';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_update_trigger
BEFORE UPDATE ON audit_logs
FOR EACH ROW
EXECUTE FUNCTION prevent_audit_log_update();

CREATE OR REPLACE FUNCTION prevent_audit_log_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are append-only and cannot be deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_delete_trigger
BEFORE DELETE ON audit_logs
FOR EACH ROW
EXECUTE FUNCTION prevent_audit_log_delete();
