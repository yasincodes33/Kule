-- ============================================================
-- RUNNER TERMINAL SESSION REQUESTS
-- ============================================================
-- approval_requests ile aynı şekil (bkz. V1__init_schema.sql), task_id yerine
-- runner_connection_id: canlı terminal oturumu AÇMA isteği için ayrı, küçük bir onay tablosu.

CREATE TABLE runner_terminal_session_requests (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID NOT NULL REFERENCES organizations(id),
    runner_connection_id  UUID NOT NULL REFERENCES runner_connections(id),
    requested_by          UUID REFERENCES users(id),
    status                TEXT NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')) DEFAULT 'PENDING',
    approved_by           UUID REFERENCES users(id),
    responded_at          TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            UUID
);
CREATE INDEX idx_runner_terminal_session_requests_organization_id ON runner_terminal_session_requests (organization_id);
CREATE INDEX idx_runner_terminal_session_requests_runner_status ON runner_terminal_session_requests (runner_connection_id, status);

ALTER TABLE runner_terminal_session_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE runner_terminal_session_requests FORCE ROW LEVEL SECURITY;

CREATE POLICY runner_terminal_session_requests_tenant_isolation ON runner_terminal_session_requests
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);
