-- V14__index_user_assignment_columns.sql
--
-- V13, owner_user_id / assigned_user_id kolonlarını ekledi ama indekslemedi.
-- Dispatch yolu bu kolonlara göre filtreliyor (şu an Java tarafında, ileride
-- sorguya inecek) ve "bu kullanıcının runner'ları" / "bana atanan görevler"
-- listeleri ürünün doğal ekranları. Kolonlar nullable olduğu için partial index
-- yeterli — atanmamış görevler indekste yer kaplamıyor.

CREATE INDEX IF NOT EXISTS idx_agent_connections_owner_user_id
    ON agent_connections (owner_user_id)
    WHERE owner_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tasks_assigned_user_id
    ON tasks (assigned_user_id)
    WHERE assigned_user_id IS NOT NULL;
