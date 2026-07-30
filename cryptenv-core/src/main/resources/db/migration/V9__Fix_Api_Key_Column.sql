-- V9: Ensure api_key column exists with correct snake_case naming
-- This repairs any prior V8 migration that may have used camelCase (apiKey)

-- If old camelCase column exists, rename it
DO $$
BEGIN
    -- Check if "apiKey" (camelCase) column exists and rename it
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'users' AND column_name = 'apiKey'
    ) THEN
        ALTER TABLE users RENAME COLUMN "apiKey" TO api_key;
    END IF;

    -- Add api_key column if it doesn't exist at all
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'users' AND column_name = 'api_key'
    ) THEN
        ALTER TABLE users ADD COLUMN api_key VARCHAR(255) UNIQUE;
    END IF;

    -- Add unique constraint if not present
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage cu ON tc.constraint_name = cu.constraint_name
        WHERE tc.table_name = 'users' AND cu.column_name = 'api_key' AND tc.constraint_type = 'UNIQUE'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT users_api_key_unique UNIQUE (api_key);
    END IF;
END $$;

-- Create index if not exists
CREATE INDEX IF NOT EXISTS idx_users_api_key ON users(api_key);
