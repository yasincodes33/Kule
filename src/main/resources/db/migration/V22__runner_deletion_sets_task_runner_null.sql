-- V22__runner_deletion_sets_task_runner_null.sql
--
-- RunnerConnectionService.removeRunner() bir runner'ı sildiğinde, o runner'a atanmış (COMPLETED/
-- FAILED gibi TERMİNAL durumdaki eskiler dahil) HER görev tasks.runner_connection_id FK'sini
-- (V15'te varsayılan ON DELETE NO ACTION ile eklenmişti) ihlal ediyor ve ham bir 409 "veri
-- bütünlüğü kısıtı ihlali" ile siliniyor — canlı kullanımda bulundu: bir runner en az bir görev
-- çalıştırdıktan sonra KALICI olarak silinemez hale geliyordu.
--
-- Görev geçmişi (log, sonuç, used_agent) runner silinince de anlamlı kalmalı; yalnızca "hangi
-- runner çalıştırdı" referansı anlamını yitiriyor. Bu yüzden CASCADE değil, SET NULL — TaskResponse/
-- TaskDetailPage zaten runner_connection_id null olan görevleri "—" göstererek ele alıyor.

ALTER TABLE tasks DROP CONSTRAINT tasks_runner_connection_id_fkey;

ALTER TABLE tasks ADD CONSTRAINT tasks_runner_connection_id_fkey
    FOREIGN KEY (runner_connection_id) REFERENCES runner_connections(id) ON DELETE SET NULL;

-- Aynı sorun runner_terminal_session_requests için de geçerli (V20) — canlı terminal isteği
-- geçmişi görevler kadar kalıcı değerli değil (yalnızca bir onay iş akışı kaydı), bu yüzden
-- burada SET NULL yerine CASCADE: runner silinince onun terminal istek geçmişi de silinir.
ALTER TABLE runner_terminal_session_requests DROP CONSTRAINT runner_terminal_session_requests_runner_connection_id_fkey;

ALTER TABLE runner_terminal_session_requests ADD CONSTRAINT runner_terminal_session_requests_runner_connection_id_fkey
    FOREIGN KEY (runner_connection_id) REFERENCES runner_connections(id) ON DELETE CASCADE;
