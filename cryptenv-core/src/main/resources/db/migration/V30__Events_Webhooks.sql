-- Events, Automation & Webhooks (§10): a transactional outbox of domain events, an SSE feed,
-- and outbound webhook delivery with HMAC-SHA256 signing and retries.
--
-- Domain events are written in the same transaction as the change that produced them (outbox
-- pattern). A background dispatcher then fans them out to webhook endpoints and automation rules.

CREATE TABLE domain_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    type VARCHAR(60) NOT NULL,
    resource_type VARCHAR(40),
    resource_id VARCHAR(255),
    -- JSON payload; NEVER contains plaintext secret values (redacted upstream)
    payload TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMP,
    last_error VARCHAR(1000),
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_domain_event_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX idx_domain_event_user ON domain_events(user_id);
CREATE INDEX idx_domain_event_workspace ON domain_events(workspace_id);
CREATE INDEX idx_domain_event_status ON domain_events(status, next_attempt_at);
CREATE INDEX idx_domain_event_created ON domain_events(created_at);

CREATE TABLE webhook_endpoints (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    url VARCHAR(1000) NOT NULL,
    description VARCHAR(255),
    -- HMAC signing secret (master-key encrypted). Never returned by the API.
    secret_encrypted TEXT NOT NULL,
    -- Comma-separated event types the endpoint subscribes to; '*' means all
    event_types VARCHAR(1000) NOT NULL DEFAULT '*',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_delivery_at TIMESTAMP,
    last_status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_webhook_endpoint_user ON webhook_endpoints(user_id);
CREATE INDEX idx_webhook_endpoint_workspace ON webhook_endpoints(workspace_id);

CREATE TABLE webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    endpoint_id BIGINT NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_id BIGINT REFERENCES domain_events(id) ON DELETE SET NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(60) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    response_code INTEGER,
    response_body VARCHAR(1000),
    error VARCHAR(1000),
    signature VARCHAR(128),
    next_attempt_at TIMESTAMP,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_webhook_delivery_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'DEAD'))
);

CREATE INDEX idx_webhook_delivery_endpoint ON webhook_deliveries(endpoint_id);
CREATE INDEX idx_webhook_delivery_status ON webhook_deliveries(status, next_attempt_at);
CREATE INDEX idx_webhook_delivery_event ON webhook_deliveries(event_id);
