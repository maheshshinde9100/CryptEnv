ALTER TABLE secret_versions
    ADD COLUMN IF NOT EXISTS secret_id BIGINT;

ALTER TABLE secret_versions
    ADD COLUMN IF NOT EXISTS environment_id BIGINT;

ALTER TABLE secret_versions
    ADD COLUMN IF NOT EXISTS workspace_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'fk_secret_versions_secret'
    ) THEN
        ALTER TABLE secret_versions
            ADD CONSTRAINT fk_secret_versions_secret
            FOREIGN KEY (secret_id) REFERENCES secrets(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'fk_secret_versions_environment'
    ) THEN
        ALTER TABLE secret_versions
            ADD CONSTRAINT fk_secret_versions_environment
            FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'fk_secret_versions_workspace'
    ) THEN
        ALTER TABLE secret_versions
            ADD CONSTRAINT fk_secret_versions_workspace
            FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_secret_versions_secret_id ON secret_versions(secret_id);
CREATE INDEX IF NOT EXISTS idx_secret_versions_environment_id ON secret_versions(environment_id);
CREATE INDEX IF NOT EXISTS idx_secret_versions_workspace_id ON secret_versions(workspace_id);
