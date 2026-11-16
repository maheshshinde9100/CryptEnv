-- Shadow rollout with grace window
ALTER TABLE secrets ADD COLUMN rotation_grace_window_seconds INTEGER;
ALTER TABLE secrets ADD COLUMN rotation_shadow_value TEXT;
ALTER TABLE secrets ADD COLUMN rotation_shadow_created_at TIMESTAMP;
ALTER TABLE secrets ADD COLUMN rotation_grace_ends_at TIMESTAMP;
