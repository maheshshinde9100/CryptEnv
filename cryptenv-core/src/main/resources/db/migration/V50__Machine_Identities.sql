-- Machine identities separate from user accounts
CREATE TABLE machine_identities (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    scopes TEXT,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- OIDC federation for CI/CD
CREATE TABLE oidc_federated_identities (
    id BIGSERIAL PRIMARY KEY,
    machine_identity_id BIGINT NOT NULL REFERENCES machine_identities(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_subject TEXT NOT NULL,
    issuer_url TEXT NOT NULL,
    audience TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_subject)
);

-- Workload key envelope for ZK CI
CREATE TABLE workload_keys (
    id BIGSERIAL PRIMARY KEY,
    machine_identity_id BIGINT NOT NULL REFERENCES machine_identities(id) ON DELETE CASCADE,
    project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
    public_key TEXT NOT NULL,
    wrapped_private_key TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE INDEX idx_machine_identities_workspace_id ON machine_identities(workspace_id);
CREATE INDEX idx_oidc_federated_identities_machine_identity_id ON oidc_federated_identities(machine_identity_id);
CREATE INDEX idx_workload_keys_machine_identity_id ON workload_keys(machine_identity_id);
