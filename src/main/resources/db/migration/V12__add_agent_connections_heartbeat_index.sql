CREATE INDEX idx_agent_connections_status_heartbeat
    ON agent_connections (connection_mode, status, last_heartbeat_at);