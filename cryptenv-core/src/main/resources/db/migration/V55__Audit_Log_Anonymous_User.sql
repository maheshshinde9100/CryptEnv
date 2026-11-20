-- Failed logins for unknown emails have no user row to reference; keep auditing them.
ALTER TABLE audit_logs ALTER COLUMN user_id DROP NOT NULL;
