-- Incident management enhancements.
-- V37 already created timeline/post_mortem, so only add what is missing.
ALTER TABLE security_incidents ADD COLUMN IF NOT EXISTS incident_number VARCHAR(24) UNIQUE;
ALTER TABLE security_incidents ADD COLUMN IF NOT EXISTS severity VARCHAR(20);
ALTER TABLE security_incidents ADD COLUMN IF NOT EXISTS affected_secrets TEXT;
ALTER TABLE security_incidents ADD COLUMN IF NOT EXISTS affected_versions TEXT;
ALTER TABLE security_incidents ADD COLUMN IF NOT EXISTS quarantine_actions TEXT;

-- V37 declared incident_number NOT NULL; the service generates it after insert.
ALTER TABLE security_incidents ALTER COLUMN incident_number DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_security_incidents_workspace
    ON security_incidents(workspace_id, created_at DESC);
