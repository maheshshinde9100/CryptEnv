-- Automation rules (§10): "ON event WHERE condition DO action" with a per-rule loop guard
-- (at most 3 fires per rolling 60-second window) to stop self-triggering event storms.
--
-- conditions / action_config are JSON objects. A run is recorded for every evaluation that
-- matched (SUCCESS / FAILED) or was suppressed by the loop guard (BLOCKED).

CREATE TABLE automation_rules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workspace_id BIGINT REFERENCES workspaces(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- ON <event_type>; '*' matches every event
    event_type VARCHAR(60) NOT NULL DEFAULT '*',
    -- WHERE <conditions> JSON object; empty/absent matches every event
    conditions TEXT,
    -- DO <action_type>, parameterised by <action_config> JSON
    action_type VARCHAR(40) NOT NULL,
    action_config TEXT,
    -- Loop guard state: fires within the current window
    fire_count INTEGER NOT NULL DEFAULT 0,
    window_started_at TIMESTAMP,
    last_fired_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_automation_rule_user ON automation_rules(user_id, enabled);
CREATE INDEX idx_automation_rule_workspace ON automation_rules(workspace_id);

CREATE TABLE automation_runs (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    event_id BIGINT REFERENCES domain_events(id) ON DELETE SET NULL,
    event_type VARCHAR(60),
    status VARCHAR(20) NOT NULL,
    detail VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_automation_run_status CHECK (status IN ('SUCCESS', 'FAILED', 'BLOCKED', 'SKIPPED'))
);

CREATE INDEX idx_automation_run_rule ON automation_runs(rule_id, created_at);
CREATE INDEX idx_automation_run_user ON automation_runs(user_id, created_at);
