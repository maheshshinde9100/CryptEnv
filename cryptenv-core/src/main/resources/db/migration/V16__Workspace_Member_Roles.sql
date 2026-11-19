-- Per-workspace member roles (OWNER remains workspace.owner_id; members get scoped roles).
ALTER TABLE workspace_members
    ADD COLUMN IF NOT EXISTS role VARCHAR(50) NOT NULL DEFAULT 'DEVELOPER';

UPDATE workspace_members SET role = 'DEVELOPER' WHERE role IS NULL OR role = '';

CREATE INDEX IF NOT EXISTS idx_workspace_members_role ON workspace_members(role);
