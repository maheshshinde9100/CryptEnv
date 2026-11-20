-- Quarterly access-certification reviews
CREATE TABLE access_reviews (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    review_period_start TIMESTAMP NOT NULL,
    review_period_end TIMESTAMP NOT NULL,
    due_date TIMESTAMP NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE access_review_items (
    id BIGSERIAL PRIMARY KEY,
    access_review_id BIGINT NOT NULL REFERENCES access_reviews(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    workspace_member_id BIGINT,
    role VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    reviewed_by BIGINT REFERENCES users(id),
    reviewed_at TIMESTAMP,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_access_reviews_workspace_id ON access_reviews(workspace_id);
CREATE INDEX idx_access_review_items_access_review_id ON access_review_items(access_review_id);
CREATE INDEX idx_access_review_items_user_id ON access_review_items(user_id);
