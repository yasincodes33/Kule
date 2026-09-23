ALTER TABLE agent_connections
    ADD COLUMN connection_mode TEXT NOT NULL DEFAULT 'BRIDGE',
    ADD COLUMN api_key_encrypted TEXT;

ALTER TABLE agent_connections
    ADD CONSTRAINT chk_agent_connections_connection_mode
    CHECK (connection_mode IN ('BRIDGE', 'API_BASED'));