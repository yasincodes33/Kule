ALTER TABLE agent_connections
    ADD COLUMN bridge_token_hash TEXT,
    ADD COLUMN bridge_token_issued_at TIMESTAMPTZ;

CREATE UNIQUE INDEX idx_agent_connections_bridge_token_hash
    ON agent_connections (bridge_token_hash)
    WHERE bridge_token_hash IS NOT NULL;