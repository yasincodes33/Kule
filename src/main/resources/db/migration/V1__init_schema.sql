-- V1__init_schema.sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- IDENTITY: organizations, users, memberships (tenant kökü)
-- ============================================================

CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    slug        TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID
);

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth0_subject TEXT NOT NULL UNIQUE,
    email         TEXT NOT NULL UNIQUE,
    display_name  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID
);

CREATE TABLE memberships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id         UUID REFERENCES users(id),
    invited_email   TEXT,
    role            TEXT NOT NULL CHECK (role IN ('OWNER','ADMIN','APPROVER','DEVELOPER','VIEWER')),
    status          TEXT NOT NULL CHECK (status IN ('PENDING','ACTIVE','REVOKED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    CONSTRAINT chk_membership_identity CHECK (user_id IS NOT NULL OR invited_email IS NOT NULL)
);

-- Bir kullanıcı aynı org'da birden fazla ACTIVE üyeliğe sahip olamaz
CREATE UNIQUE INDEX uq_membership_active_user
    ON memberships (organization_id, user_id)
    WHERE user_id IS NOT NULL AND status <> 'REVOKED';

CREATE INDEX idx_membership_invited_email ON memberships (invited_email, status);
CREATE INDEX idx_membership_user_status ON memberships (user_id, status);

ALTER TABLE memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE memberships FORCE ROW LEVEL SECURITY;

CREATE POLICY memberships_tenant_isolation ON memberships
USING (
    organization_id = current_setting('app.current_tenant_id', true)::uuid
    OR invited_email = current_setting('app.current_user_email', true)
);

-- ============================================================
-- PROJECT
-- ============================================================

CREATE TABLE projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name            TEXT NOT NULL,
    repo_url        TEXT NOT NULL,
    default_branch  TEXT NOT NULL DEFAULT 'main',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID
);
CREATE INDEX idx_projects_organization_id ON projects (organization_id);
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects FORCE ROW LEVEL SECURITY;

CREATE POLICY projects_tenant_isolation ON projects
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);

-- ============================================================
-- AGENT
-- ============================================================

CREATE TABLE agent_connections (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    agent_type          TEXT NOT NULL CHECK (agent_type IN ('HERMES','OPENCLAW','OMNIROUTE')),
    status              TEXT NOT NULL CHECK (status IN ('ONLINE','OFFLINE')) DEFAULT 'OFFLINE',
    last_heartbeat_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID
);
CREATE INDEX idx_agent_connections_organization_id ON agent_connections (organization_id);
ALTER TABLE agent_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_connections FORCE ROW LEVEL SECURITY;

CREATE POLICY agent_connections_tenant_isolation ON agent_connections
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE TABLE agent_capabilities (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    agent_connection_id UUID NOT NULL REFERENCES agent_connections(id),
    task_type           TEXT NOT NULL CHECK (task_type IN ('DEV','ANALYSIS','DEPLOY')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID
);
CREATE INDEX idx_agent_capabilities_organization_id ON agent_capabilities (organization_id);
CREATE INDEX idx_agent_capabilities_agent_connection_id ON agent_capabilities (agent_connection_id);
ALTER TABLE agent_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_capabilities FORCE ROW LEVEL SECURITY;

CREATE POLICY agent_capabilities_tenant_isolation ON agent_capabilities
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);

-- ============================================================
-- TASK
-- ============================================================

CREATE TABLE tasks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    project_id          UUID NOT NULL REFERENCES projects(id),
    agent_connection_id UUID REFERENCES agent_connections(id),
    type                TEXT NOT NULL CHECK (type IN ('DEV','ANALYSIS','DEPLOY')),
    status              TEXT NOT NULL CHECK (status IN
                            ('QUEUED','DISPATCHED','RUNNING','AWAITING_APPROVAL',
                             'COMPLETED','FAILED','REJECTED','CANCELLED')),
    title               TEXT NOT NULL,
    retry_count         INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID
);
CREATE INDEX idx_tasks_organization_id ON tasks (organization_id);
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks FORCE ROW LEVEL SECURITY;

CREATE POLICY tasks_tenant_isolation ON tasks
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);

-- Bölüm 8'deki geçiş tablosunun DB seviyesindeki ikinci savunma hattı
CREATE OR REPLACE FUNCTION enforce_task_status_transition()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = NEW.status THEN
        RETURN NEW;
    END IF;

    IF NOT (
        (OLD.status = 'QUEUED'            AND NEW.status IN ('DISPATCHED','CANCELLED','FAILED')) OR
        (OLD.status = 'DISPATCHED'        AND NEW.status IN ('RUNNING','FAILED','CANCELLED')) OR
        (OLD.status = 'RUNNING'           AND NEW.status IN ('AWAITING_APPROVAL','COMPLETED','FAILED','CANCELLED')) OR
        (OLD.status = 'AWAITING_APPROVAL' AND NEW.status IN ('RUNNING','REJECTED')) OR
        (OLD.status = 'FAILED'            AND NEW.status = 'QUEUED')
    ) THEN
        RAISE EXCEPTION 'Geçersiz task durum geçişi: % -> %', OLD.status, NEW.status;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_enforce_task_status_transition
    BEFORE UPDATE ON tasks
    FOR EACH ROW
    EXECUTE FUNCTION enforce_task_status_transition();

CREATE INDEX idx_task_project_status ON tasks (project_id, status);
CREATE INDEX idx_task_agent_connection ON tasks (agent_connection_id);

CREATE TABLE task_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,  -- denormalize edildi, bkz. Bölüm 9
    task_id         UUID NOT NULL REFERENCES tasks(id),
    "timestamp"     TIMESTAMPTZ NOT NULL DEFAULT now(),
    level           TEXT NOT NULL DEFAULT 'INFO',
    message         TEXT NOT NULL
);

CREATE INDEX idx_task_logs_task_timestamp ON task_logs (task_id, "timestamp");

ALTER TABLE task_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_logs FORCE ROW LEVEL SECURITY;

CREATE POLICY task_logs_tenant_isolation ON task_logs
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);

-- ============================================================
-- APPROVAL
-- ============================================================

CREATE TABLE approval_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    task_id         UUID NOT NULL REFERENCES tasks(id),
    requested_by    UUID REFERENCES users(id),
    status          TEXT NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED')) DEFAULT 'PENDING',
    approved_by     UUID REFERENCES users(id),
    responded_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID
);
CREATE INDEX idx_approval_requests_organization_id ON approval_requests (organization_id);
CREATE INDEX idx_approval_task_status ON approval_requests (task_id, status);

ALTER TABLE approval_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE approval_requests FORCE ROW LEVEL SECURITY;

CREATE POLICY approval_requests_tenant_isolation ON approval_requests
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);

-- ============================================================
-- AUDIT
-- ============================================================

CREATE TABLE audit_log_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    actor_user_id   UUID REFERENCES users(id),
    action          TEXT NOT NULL,
    entity_type     TEXT NOT NULL,
    entity_id       UUID,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_org_created ON audit_log_entries (organization_id, created_at);

ALTER TABLE audit_log_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log_entries FORCE ROW LEVEL SECURITY;

CREATE POLICY audit_log_entries_tenant_isolation ON audit_log_entries
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);

-- ============================================================
-- NOTIFICATION
-- ============================================================

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    type            TEXT NOT NULL,
    payload         JSONB,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_organization_id ON notifications (organization_id);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications FORCE ROW LEVEL SECURITY;

CREATE POLICY notifications_tenant_isolation ON notifications
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);

-- ============================================================
-- BILLING
-- ============================================================

CREATE TABLE usage_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    period          TEXT NOT NULL,  -- 'YYYY-MM'
    task_count      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, period)
);

ALTER TABLE usage_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_records FORCE ROW LEVEL SECURITY;

CREATE POLICY usage_records_tenant_isolation ON usage_records
USING (organization_id = current_setting('app.current_tenant_id', true)::uuid);
DROP POLICY IF EXISTS memberships_tenant_isolation ON memberships;

CREATE POLICY memberships_tenant_isolation ON memberships
USING (
    organization_id = current_setting('app.current_tenant_id', true)::uuid
    OR invited_email = current_setting('app.current_user_email', true)
    OR user_id = current_setting('app.current_user_id', true)::uuid
);