-- WorkspaceMember.joinedAt is required by the entity but was never created
-- by the original table migration; older databases may already have it.
ALTER TABLE workspace_members
    ADD COLUMN IF NOT EXISTS joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
