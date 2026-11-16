-- Incident management enhancements
ALTER TABLE security_incidents ADD COLUMN incident_number VARCHAR(20) UNIQUE;
ALTER TABLE security_incidents ADD COLUMN severity VARCHAR(20);
ALTER TABLE security_incidents ADD COLUMN affected_secrets TEXT;
ALTER TABLE security_incidents ADD COLUMN affected_versions TEXT;
ALTER TABLE security_incidents ADD COLUMN quarantine_actions TEXT;
ALTER TABLE security_incidents ADD COLUMN post_mortem TEXT;
ALTER TABLE security_incidents ADD COLUMN timeline JSONB;
