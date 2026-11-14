-- secrets.value and secrets.encrypted_value both held the same ciphertext.
-- Keep value as the canonical column; copy any encrypted_value-only rows first.
UPDATE secrets
SET value = encrypted_value
WHERE (value IS NULL OR value = '')
  AND encrypted_value IS NOT NULL
  AND encrypted_value <> '';

ALTER TABLE secrets DROP COLUMN IF EXISTS encrypted_value;
