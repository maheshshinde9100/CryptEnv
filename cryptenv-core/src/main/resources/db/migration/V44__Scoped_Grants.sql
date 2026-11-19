-- Project- and environment-scoped grants
CREATE TABLE scoped_grants (
    id BIGSERIAL PRIMARY KEY,
    workspace_member_id BIGINT NOT NULL REFERENCES workspace_members(id) ON DELETE CASCADE,
    project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
    environment_id BIGINT REFERENCES environments(id) ON DELETE CASCADE,
    grant_type VARCHAR(50) NOT NULL,
    permissions TEXT NOT NULL,
    granted_by BIGINT NOT NULL REFERENCES users(id),
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    CONSTRAINT check_scope CHECK (
        (project_id IS NOT NULL AND environment_id IS NULL) OR
        (project_id IS NOT NULL AND environment_id IS NOT NULL) OR
        (project_id IS NULL AND environment_id IS NULL)
    )
);

CREATE INDEX idx_scoped_grants_workspace_member_id ON scoped_grants(workspace_member_id);
CREATE INDEX idx_scoped_grants_project_id ON scoped_grants(project_id);
CREATE INDEX idx_scoped_grants_environment_id ON scoped_grants(environment_id);
