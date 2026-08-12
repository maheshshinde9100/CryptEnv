ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS workspace_encryption_key TEXT;

ALTER TABLE secrets
    ALTER COLUMN encrypted SET DEFAULT true;

ALTER TABLE secrets
    ALTER COLUMN encrypted SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_secrets_environment_workspace ON secrets(environment_id);
CREATE INDEX IF NOT EXISTS idx_environments_workspace_id ON environments(workspace_id);
