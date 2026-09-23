-- Transactional outbox: TaskCreatedEvent, Kafka'ya yayın başarısız olduğunda (broker
-- geçici olarak erişilemezse) kaybolmamalı. Bu tablo, task oluşturma/retry ile AYNI
-- transaction'da yazılan bir outbox satırı tutar: yayın başarılı olunca published_at
-- set edilir, başarısız olursa satır published_at=NULL kalır ve
-- TaskCreatedEventOutboxScheduler tarafından Kafka tekrar erişilebilir olana kadar
-- periyodik olarak yeniden denenir (en-az-bir-kez teslim garantisi).

CREATE TABLE task_created_event_outbox (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id                 UUID NOT NULL,
    task_id                         UUID NOT NULL REFERENCES tasks(id),
    preferred_agent_connection_id   UUID,
    preferred_runner_connection_id  UUID,
    preferred_model_tier            TEXT CHECK (preferred_model_tier IN ('BUDGET','DEFAULT','REASONING')),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                      UUID,
    published_at                    TIMESTAMPTZ,
    attempt_count                   INT NOT NULL DEFAULT 0
);

-- Scheduler'ın taradığı sorgu her zaman "yayınlanmamış" satırları çekiyor — kısmi index
-- tablo büyüdükçe (çoğu satır zaten yayınlanmış olacağı için) taramayı ucuz tutuyor.
CREATE INDEX idx_task_created_outbox_unpublished ON task_created_event_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_task_created_outbox_task_id ON task_created_event_outbox (task_id);

ALTER TABLE task_created_event_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_created_event_outbox FORCE ROW LEVEL SECURITY;

-- V2/V3'te bulunan derste öğrenilen desen doğrudan uygulanıyor: session değişkeni boş/unset
-- iken NULLIF olmadan doğrudan ::uuid cast'i "invalid input syntax for type uuid" ile patlar.
CREATE POLICY task_created_event_outbox_tenant_isolation ON task_created_event_outbox
USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
