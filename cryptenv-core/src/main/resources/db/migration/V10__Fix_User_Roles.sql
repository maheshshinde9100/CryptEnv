-- V10: Set default role OWNER for users without a role assigned
UPDATE users SET role = 'OWNER' WHERE role IS NULL;

-- Also make role column default to OWNER for future inserts
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'OWNER';
