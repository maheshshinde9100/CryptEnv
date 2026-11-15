-- Notifications (§10): in-app notifications plus per-user channel preferences.
--
-- A domain event is mirrored into a per-user in-app notification, and — according to each
-- user's preferences — relayed to email (SMTP), Slack or Discord.
--
-- Notification bodies NEVER contain plaintext secret values: they are derived from the
-- already-redacted domain event payload.
--
-- Channel webhook URLs (Slack / Discord) embed a bearer token, so they are stored as
-- master-key ciphertext and are never returned by the API (only a "configured" flag is).

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    event_id BIGINT REFERENCES domain_events(id) ON DELETE SET NULL,
    type VARCHAR(60) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    title VARCHAR(255) NOT NULL,
    body TEXT,
    resource_type VARCHAR(40),
    resource_id VARCHAR(255),
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notification_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);

CREATE INDEX idx_notification_user ON notifications(user_id, read_at);
CREATE INDEX idx_notification_created ON notifications(created_at);

CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_address VARCHAR(255),
    slack_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- Slack incoming-webhook URL (contains a bearer token) — master-key encrypted
    slack_webhook_encrypted TEXT,
    discord_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- Discord webhook URL (contains a bearer token) — master-key encrypted
    discord_webhook_encrypted TEXT,
    -- Comma-separated event types the user wants to be notified about; '*' means all
    subscribed_types VARCHAR(1000) NOT NULL DEFAULT '*',
    min_severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_notification_pref_severity CHECK (min_severity IN ('INFO', 'WARNING', 'CRITICAL'))
);
