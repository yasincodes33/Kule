-- V15__split_runner_connections_from_agent_connections.sql
--
-- agent_connections iki farklı şeyi aynı tabloda tutuyordu:
--   1) organizasyon seviyesindeki bulut sağlayıcıları (CLAUDE/CHATGPT/GEMINI + API anahtarı)
--   2) kullanıcıların kendi makinelerindeki yerel runner'lar (HERMES/OPENCLAW/OMNIROUTE)
-- Sahiplik ve yaşam döngüleri farklı olduğu için owner_user_id kayıtların yarısında,
-- api_key_encrypted diğer yarısında anlamsız kalıyordu.
--
-- Bu migration ikisini ayırıyor: agent_connections yalnızca (1)'e kalıyor, runner'lar
-- yeni runner_connections tablosuna taşınıyor. Runner'da araç tipi kavramı tamamen
-- kalkıyor — kullanıcı görevi hangi araçla çözeceğine kendi terminalinde karar veriyor
-- ve sonucu bildirirken tasks.used_agent alanıyla opsiyonel olarak raporluyor.
--
-- RLS NOTU: Bu, projedeki ilk VERİ TAŞIYAN migration. Tüm tablolarda FORCE ROW LEVEL
-- SECURITY açık ve app.current_tenant_id migration sırasında set edilmiş değil; policy'ler
-- hiçbir satırı görünür kılmaz ve taşıma sessizce 0 satır işlerdi. Bu yüzden kaynak
-- tablolarda RLS taşıma süresince kapatılıp sonunda aynen geri açılıyor, yeni tablolarda
-- ise RLS taşımadan SONRA açılıyor.

-- ============================================================
-- 1. Yeni tablolar (RLS henüz açılmıyor — bkz. yukarıdaki not)
-- ============================================================

CREATE TABLE runner_connections (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id        UUID NOT NULL REFERENCES organizations(id),
    owner_user_id          UUID NOT NULL REFERENCES users(id),
    project_id             UUID REFERENCES projects(id),
    label                  TEXT,
    status                 TEXT NOT NULL CHECK (status IN ('ONLINE','OFFLINE')) DEFAULT 'OFFLINE',
    last_heartbeat_at      TIMESTAMPTZ,
    bridge_token_hash      TEXT,
    bridge_token_issued_at TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             UUID
);

CREATE INDEX idx_runner_connections_organization_id ON runner_connections (organization_id);
CREATE INDEX idx_runner_connections_owner_user_id   ON runner_connections (owner_user_id);
CREATE INDEX idx_runner_connections_project_id      ON runner_connections (project_id)
    WHERE project_id IS NOT NULL;
CREATE INDEX idx_runner_connections_heartbeat       ON runner_connections (status, last_heartbeat_at);

-- Token hash'i tekil; NULL'lar çakışmaz (birden fazla runner token ihraç edilmemiş bekleyebilir).
CREATE UNIQUE INDEX idx_runner_connections_bridge_token_hash
    ON runner_connections (bridge_token_hash)
    WHERE bridge_token_hash IS NOT NULL;

CREATE TABLE runner_capabilities (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id      UUID NOT NULL REFERENCES organizations(id),
    runner_connection_id UUID NOT NULL REFERENCES runner_connections(id),
    task_type            TEXT NOT NULL CHECK (task_type IN ('DEV','ANALYSIS','DEPLOY')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID
);

CREATE INDEX idx_runner_capabilities_organization_id ON runner_capabilities (organization_id);
CREATE INDEX idx_runner_capabilities_runner_id       ON runner_capabilities (runner_connection_id);
CREATE UNIQUE INDEX uq_runner_capabilities_runner_task
    ON runner_capabilities (runner_connection_id, task_type);

-- ============================================================
-- 2. tasks: hangi runner çalıştırdı + hangi araçla yapıldı
-- ============================================================

ALTER TABLE tasks ADD COLUMN runner_connection_id UUID REFERENCES runner_connections(id);
ALTER TABLE tasks ADD COLUMN used_agent TEXT;

-- Enum genişlerse bu kısıt da güncellenmeli (V11'de agent_type ile birebir aynı hata yaşandı).
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_used_agent
    CHECK (used_agent IS NULL OR used_agent IN
        ('CLAUDE_CODE','ANTIGRAVITY','HERMES','OPENCLAW','OMNIROUTE','MANUAL'));

CREATE INDEX idx_tasks_runner_connection_id ON tasks (runner_connection_id)
    WHERE runner_connection_id IS NOT NULL;

-- ============================================================
-- 3. Mevcut bridge kayıtlarını taşı (RLS geçici olarak kapalı)
-- ============================================================

ALTER TABLE agent_connections  DISABLE ROW LEVEL SECURITY;
ALTER TABLE agent_capabilities DISABLE ROW LEVEL SECURITY;
ALTER TABLE tasks              DISABLE ROW LEVEL SECURITY;

-- owner_user_id'si olan bridge kayıtları runner'a dönüşüyor. Sahipsizler (V13 öncesinde
-- kaydedilmiş, hiç token ihraç edilmemiş kayıtlar) NOT NULL kısıtını karşılamıyor ve
-- zaten hiçbir atamalı göreve eşleşemiyorlardı; aşağıda siliniyorlar.
-- Eski agent_type değeri (HERMES/OPENCLAW/...) label olarak saklanıyor: artık bir davranış
-- belirlemiyor ama kullanıcının hangi kaydın hangisi olduğunu tanıması için bilgi olarak duruyor.
INSERT INTO runner_connections (
    id, organization_id, owner_user_id, project_id, label, status,
    last_heartbeat_at, bridge_token_hash, bridge_token_issued_at,
    created_at, updated_at, created_by)
SELECT
    ac.id, ac.organization_id, ac.owner_user_id, NULL, ac.agent_type, ac.status,
    ac.last_heartbeat_at, ac.bridge_token_hash, ac.bridge_token_issued_at,
    ac.created_at, ac.updated_at, ac.created_by
FROM agent_connections ac
WHERE ac.connection_mode = 'BRIDGE' AND ac.owner_user_id IS NOT NULL;

INSERT INTO runner_capabilities (organization_id, runner_connection_id, task_type, created_at, updated_at)
SELECT cap.organization_id, cap.agent_connection_id, cap.task_type, cap.created_at, cap.updated_at
FROM agent_capabilities cap
JOIN runner_connections rc ON rc.id = cap.agent_connection_id;

-- Taşınan runner'lara işaret eden görevleri yeni kolona bağla, eski kolonu boşalt.
UPDATE tasks t
SET runner_connection_id = t.agent_connection_id
FROM runner_connections rc
WHERE rc.id = t.agent_connection_id;

UPDATE tasks SET agent_connection_id = NULL
WHERE agent_connection_id IN (SELECT id FROM runner_connections);

DELETE FROM agent_capabilities WHERE agent_connection_id IN (SELECT id FROM runner_connections);
DELETE FROM agent_connections  WHERE id IN (SELECT id FROM runner_connections);

-- Taşınamayan (sahipsiz) bridge kayıtları da artık geçersiz: runner'lar bu tabloda durmuyor.
UPDATE tasks SET agent_connection_id = NULL WHERE agent_connection_id IN
    (SELECT id FROM agent_connections WHERE connection_mode = 'BRIDGE');
DELETE FROM agent_capabilities WHERE agent_connection_id IN
    (SELECT id FROM agent_connections WHERE connection_mode = 'BRIDGE');
DELETE FROM agent_connections WHERE connection_mode = 'BRIDGE';

-- API-tabanlı olup API anahtarı olmayan kayıt kalırsa aşağıdaki NOT NULL kısıtı patlar;
-- böyle bir kayıt zaten kullanılamaz durumda olduğu için temizleniyor.
DELETE FROM agent_capabilities WHERE agent_connection_id IN
    (SELECT id FROM agent_connections WHERE api_key_encrypted IS NULL);
UPDATE tasks SET agent_connection_id = NULL WHERE agent_connection_id IN
    (SELECT id FROM agent_connections WHERE api_key_encrypted IS NULL);
DELETE FROM agent_connections WHERE api_key_encrypted IS NULL;

ALTER TABLE agent_connections  ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_connections  FORCE ROW LEVEL SECURITY;
ALTER TABLE agent_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_capabilities FORCE ROW LEVEL SECURITY;
ALTER TABLE tasks              ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks              FORCE ROW LEVEL SECURITY;

-- ============================================================
-- 4. Yeni tablolarda RLS
-- ============================================================

ALTER TABLE runner_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE runner_connections FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS runner_connections_tenant_isolation ON runner_connections;
CREATE POLICY runner_connections_tenant_isolation ON runner_connections
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

ALTER TABLE runner_capabilities ENABLE ROW LEVEL SECURITY;
ALTER TABLE runner_capabilities FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS runner_capabilities_tenant_isolation ON runner_capabilities;
CREATE POLICY runner_capabilities_tenant_isolation ON runner_capabilities
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- ============================================================
-- 5. agent_connections sadeleşiyor
-- ============================================================

-- Artık her kayıt API-tabanlı: connection_mode ve owner_user_id anlamsız.
ALTER TABLE agent_connections DROP COLUMN connection_mode;
ALTER TABLE agent_connections DROP COLUMN owner_user_id;

-- Bridge tipleri AgentType enum'undan kalktığı için CHECK kısıtı daraltılıyor.
ALTER TABLE agent_connections DROP CONSTRAINT IF EXISTS agent_connections_agent_type_check;
ALTER TABLE agent_connections ADD CONSTRAINT agent_connections_agent_type_check
    CHECK (agent_type IN ('CLAUDE','CHATGPT','GEMINI'));

-- API anahtarı artık her kayıtta zorunlu.
ALTER TABLE agent_connections ALTER COLUMN api_key_encrypted SET NOT NULL;
