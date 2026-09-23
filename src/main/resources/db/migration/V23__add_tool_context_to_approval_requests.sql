-- V23__add_tool_context_to_approval_requests.sql
--
-- Onay kuyruğu ekranı, onaylayan kişiye NEYİ onayladığını göstermiyordu: yalnızca görev
-- başlığı, talep edenin kimliği ve süre bilgisi vardı. Hangi aracın (git_push, run_command…)
-- hangi argümanlarla çalıştırılmak istendiği hiçbir yerde saklanmıyordu — ToolCallApprovalGate
-- `toolName`'i alıyor ama yalnızca log satırına yazıyordu.
--
-- Bu, "insan kapısı" özelliğinin amacını zayıflatıyordu: onaylayan kişi `git status` ile
-- `rm -rf` arasındaki farkı göremeden karar veriyordu. İki kolon ekleniyor.
--
-- Her ikisi de NULL olabilir: (1) bu migration'dan önceki kayıtlarda değer yok, (2) onay
-- web arayüzünden elle de istenebiliyor (ApprovalController) ve o yolda bir araç çağrısı yok.

ALTER TABLE approval_requests ADD COLUMN tool_name TEXT;
ALTER TABLE approval_requests ADD COLUMN tool_arguments TEXT;

-- Bekleyen onaylar ekranda araç adına göre de süzülebilsin diye.
CREATE INDEX idx_approval_requests_tool_name ON approval_requests (tool_name)
    WHERE tool_name IS NOT NULL;
