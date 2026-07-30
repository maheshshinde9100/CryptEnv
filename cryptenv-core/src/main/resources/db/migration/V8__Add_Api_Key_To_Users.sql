-- Add API key column to users table (snake_case to match Hibernate naming convention)
ALTER TABLE users ADD COLUMN IF NOT EXISTS api_key VARCHAR(255) UNIQUE;
CREATE INDEX IF NOT EXISTS idx_users_api_key ON users(api_key);
