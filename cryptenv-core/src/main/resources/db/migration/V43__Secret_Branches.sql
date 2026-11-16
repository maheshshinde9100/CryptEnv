-- Secret branches for version control
CREATE TABLE secret_branches (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
    environment_id BIGINT REFERENCES environments(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT false,
    parent_branch_id BIGINT REFERENCES secret_branches(id) ON DELETE SET NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(workspace_id, name)
);

CREATE TABLE secret_branch_versions (
    id BIGSERIAL PRIMARY KEY,
    secret_branch_id BIGINT NOT NULL REFERENCES secret_branches(id) ON DELETE CASCADE,
    secret_id BIGINT NOT NULL REFERENCES secrets(id) ON DELETE CASCADE,
    secret_version_id BIGINT NOT NULL REFERENCES secret_versions(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(secret_branch_id, secret_id)
);

CREATE INDEX idx_secret_branches_workspace_id ON secret_branches(workspace_id);
CREATE INDEX idx_secret_branches_project_id ON secret_branches(project_id);
CREATE INDEX idx_secret_branch_versions_branch_id ON secret_branch_versions(secret_branch_id);
