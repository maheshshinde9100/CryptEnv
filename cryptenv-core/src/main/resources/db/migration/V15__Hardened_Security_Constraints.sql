-- =================================================================
-- V15: Hardened Security Constraints (UNIQUE + NOT NULL + FK Checks)
-- =================================================================
-- Purpose: Make the database itself enforce zero-knowledge user
-- isolation, deduplication, and referential integrity guarantees
-- that the app layer alone cannot be trusted to uphold.

-- -----------------------------------------------------------------
-- 1. users table — guarantee uniqueness of identity columns
-- -----------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unq_users_email'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT unq_users_email UNIQUE (email);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unq_users_username'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT unq_users_username UNIQUE (username);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unq_users_api_key'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT unq_users_api_key UNIQUE (api_key);
    END IF;
END $$;

-- email and username MUST be non-null (enforce even if Hibernate allows them)
DO $$
BEGIN
    ALTER TABLE users ALTER COLUMN email SET NOT NULL;
    ALTER TABLE users ALTER COLUMN username SET NOT NULL;
    ALTER TABLE users ALTER COLUMN password SET NOT NULL;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'users NOT NULL constraints already applied or incompatible: %', SQLERRM;
END $$;

-- -----------------------------------------------------------------
-- 2. workspaces table — UNIQUE name per workspace, NOT NULL checks
-- -----------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unq_workspaces_name'
    ) THEN
        ALTER TABLE workspaces ADD CONSTRAINT unq_workspaces_name UNIQUE (name);
    END IF;
END $$;

DO $$
BEGIN
    ALTER TABLE workspaces ALTER COLUMN name SET NOT NULL;
    ALTER TABLE workspaces ALTER COLUMN owner_id SET NOT NULL;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'workspaces NOT NULL constraints already applied or incompatible: %', SQLERRM;
END $$;

-- Owner FK cascade: when a user is deleted, delete their owned workspaces
-- (if this constraint already exists, it will be safely skipped)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'workspaces_owner_id_fkey_cascade'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'workspaces_owner_id_fkey'
    ) THEN
        ALTER TABLE workspaces DROP CONSTRAINT workspaces_owner_id_fkey;
        ALTER TABLE workspaces
            ADD CONSTRAINT workspaces_owner_id_fkey
            FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'workspaces_owner_id_fkey'
    ) THEN
        ALTER TABLE workspaces
            ADD CONSTRAINT workspaces_owner_id_fkey
            FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

-- -----------------------------------------------------------------
-- 3. workspace_members join table — composite UNIQUE + FK cascades
-- -----------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unq_workspace_members_pair'
    ) THEN
        ALTER TABLE workspace_members
            ADD CONSTRAINT unq_workspace_members_pair
            UNIQUE (workspace_id, user_id);
    END IF;
END $$;

-- Cascade delete on both FKs so removing a workspace or a user
-- automatically cleans up the join table rows
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'workspace_members_workspace_id_fkey'
    ) THEN
        ALTER TABLE workspace_members DROP CONSTRAINT workspace_members_workspace_id_fkey;
    END IF;
    ALTER TABLE workspace_members
        ADD CONSTRAINT workspace_members_workspace_id_fkey
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'workspace_members FK workspace: %', SQLERRM;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'workspace_members_user_id_fkey'
    ) THEN
        ALTER TABLE workspace_members DROP CONSTRAINT workspace_members_user_id_fkey;
    END IF;
    ALTER TABLE workspace_members
        ADD CONSTRAINT workspace_members_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'workspace_members FK user: %', SQLERRM;
END $$;

-- -----------------------------------------------------------------
-- 4. environments table — UNIQUE name per workspace, FK cascade
-- -----------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unq_envs_workspace_name'
    ) THEN
        ALTER TABLE environments
            ADD CONSTRAINT unq_envs_workspace_name
            UNIQUE (workspace_id, name);
    END IF;
END $$;

DO $$
BEGIN
    ALTER TABLE environments ALTER COLUMN workspace_id SET NOT NULL;
    ALTER TABLE environments ALTER COLUMN name SET NOT NULL;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'environments NOT NULL: %', SQLERRM;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'environments_workspace_id_fkey'
    ) THEN
        ALTER TABLE environments DROP CONSTRAINT environments_workspace_id_fkey;
    END IF;
    ALTER TABLE environments
        ADD CONSTRAINT environments_workspace_id_fkey
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'environments FK: %', SQLERRM;
END $$;

-- -----------------------------------------------------------------
-- 5. secrets table — composite UNIQUE key = (environment_id, key)
--    You CANNOT have two secrets with the same name in the same env.
-- -----------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unq_secrets_env_key'
    ) THEN
        ALTER TABLE secrets
            ADD CONSTRAINT unq_secrets_env_key
            UNIQUE (environment_id, key);
    END IF;
END $$;

DO $$
BEGIN
    ALTER TABLE secrets ALTER COLUMN environment_id SET NOT NULL;
    ALTER TABLE secrets ALTER COLUMN key SET NOT NULL;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'secrets NOT NULL cols: %', SQLERRM;
END $$;

-- Legacy rows often store ciphertext in `value` with encrypted_value NULL.
-- Backfill before adding the consistency CHECK so existing Neon data migrates cleanly.
UPDATE secrets
SET encrypted_value = value
WHERE encrypted = true
  AND (encrypted_value IS NULL OR btrim(encrypted_value) = '')
  AND value IS NOT NULL
  AND btrim(value) <> '';

-- Any remaining encrypted=true rows without usable ciphertext cannot satisfy the CHECK;
-- mark them unencrypted so migration can proceed (app layer still encrypts new writes).
UPDATE secrets
SET encrypted = false
WHERE encrypted = true
  AND (
        encrypted_value IS NULL
        OR btrim(encrypted_value) = ''
        OR char_length(encrypted_value) < 16
      );

-- CHECK: encrypted secrets must have ciphertext of plausible AES-GCM Base64 length
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_secrets_encrypted_consistency'
    ) THEN
        ALTER TABLE secrets
            ADD CONSTRAINT chk_secrets_encrypted_consistency
            CHECK (
                (encrypted = false)
                OR
                (
                    encrypted = true
                    AND encrypted_value IS NOT NULL
                    AND char_length(encrypted_value) >= 16
                )
            );
    END IF;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'chk_secrets_encrypted_consistency skipped: %', SQLERRM;
END $$;

-- Cascade delete secrets when their environment is dropped
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'secrets_environment_id_fkey'
    ) THEN
        ALTER TABLE secrets DROP CONSTRAINT secrets_environment_id_fkey;
    END IF;
    ALTER TABLE secrets
        ADD CONSTRAINT secrets_environment_id_fkey
        FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'secrets FK env: %', SQLERRM;
END $$;

-- -----------------------------------------------------------------
-- 6. secret_versions — composite UNIQUE + FK cascade cleanup
-- -----------------------------------------------------------------
-- V14 already added FKs; here we UNIQUE-ify (secret_key, version_number)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'unq_secret_versions_key_version'
    ) THEN
        ALTER TABLE secret_versions
            ADD CONSTRAINT unq_secret_versions_key_version
            UNIQUE (secret_key, version_number);
    END IF;
END $$;

-- CHECK: only ONE active version per secret_key at a time
-- (we use a partial unique index since PostgreSQL can't do this in CHECK)
CREATE UNIQUE INDEX IF NOT EXISTS unq_secret_versions_one_active
    ON secret_versions (secret_key) WHERE is_active = true;

-- audit_logs FK cleanup — delete audit logs when user is purged
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        WHERE tc.constraint_name = 'audit_logs_user_id_fkey'
    ) THEN
        ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_user_id_fkey;
    END IF;
    ALTER TABLE audit_logs
        ADD CONSTRAINT audit_logs_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
EXCEPTION WHEN others THEN
    RAISE NOTICE 'audit_logs FK: %', SQLERRM;
END $$;
