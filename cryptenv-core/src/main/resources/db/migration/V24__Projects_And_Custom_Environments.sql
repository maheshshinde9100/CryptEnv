CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uq_projects_workspace_name UNIQUE (workspace_id, name)
);

CREATE INDEX idx_projects_workspace ON projects(workspace_id);

-- Default project per existing workspace
INSERT INTO projects (workspace_id, name, description)
SELECT id, 'Default', 'Auto-created default project'
FROM workspaces
WHERE NOT EXISTS (
    SELECT 1 FROM projects p WHERE p.workspace_id = workspaces.id AND p.name = 'Default'
);

ALTER TABLE environments ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL;
ALTER TABLE environments ADD COLUMN IF NOT EXISTS label VARCHAR(100);
ALTER TABLE environments ADD COLUMN IF NOT EXISTS is_protected BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE environments e
SET project_id = p.id
FROM projects p
WHERE e.workspace_id = p.workspace_id
  AND p.name = 'Default'
  AND e.project_id IS NULL;

-- Allow CUSTOM environment type (drop old check if present)
ALTER TABLE environments DROP CONSTRAINT IF EXISTS chk_environment_name;
ALTER TABLE environments ADD CONSTRAINT chk_environment_name
    CHECK (name IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION', 'CUSTOM'));
