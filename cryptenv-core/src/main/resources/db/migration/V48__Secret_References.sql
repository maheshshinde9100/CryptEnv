-- Secret references (${OTHER_KEY})
CREATE TABLE secret_references (
    id BIGSERIAL PRIMARY KEY,
    secret_id BIGINT NOT NULL REFERENCES secrets(id) ON DELETE CASCADE,
    reference_key TEXT NOT NULL,
    target_secret_id BIGINT REFERENCES secrets(id) ON DELETE SET NULL,
    target_environment_id BIGINT REFERENCES environments(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_secret_references_secret_id ON secret_references(secret_id);
CREATE INDEX idx_secret_references_target_secret_id ON secret_references(target_secret_id);
