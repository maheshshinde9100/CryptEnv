-- Add columns for Argon2 password hashing migration
ALTER TABLE users ADD COLUMN password_hash_algorithm VARCHAR(20) DEFAULT 'bcrypt';
ALTER TABLE users ADD COLUMN password_hash_iterations INTEGER DEFAULT 10;
ALTER TABLE users ADD COLUMN password_hash_memory INTEGER;
ALTER TABLE users ADD COLUMN password_hash_parallelism INTEGER;
ALTER TABLE users ADD COLUMN password_hash_salt TEXT;
