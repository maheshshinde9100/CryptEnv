CREATE TABLE environments (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    workspace_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_environments_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE,
    CONSTRAINT chk_environment_name CHECK (name IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION'))
);

CREATE INDEX idx_environments_workspace_id ON environments(workspace_id);
CREATE INDEX idx_environments_name ON environments(name);
CREATE UNIQUE INDEX idx_environments_workspace_name ON environments(workspace_id, name);
