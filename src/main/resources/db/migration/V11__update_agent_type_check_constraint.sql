ALTER TABLE agent_connections DROP CONSTRAINT IF EXISTS agent_connections_agent_type_check;

ALTER TABLE agent_connections
    ADD CONSTRAINT agent_connections_agent_type_check
    CHECK (agent_type IN ('HERMES', 'OPENCLAW', 'OMNIROUTE', 'CLAUDE', 'CHATGPT', 'GEMINI'));