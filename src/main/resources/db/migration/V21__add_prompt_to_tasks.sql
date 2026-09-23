-- Kullanıcının modele/araca göndereceği prompt metni — görev başlığından (kısa etiket) bilinçli
-- olarak ayrı: kalıcı, düzenlenebilir bir alan (bkz. TaskOrchestrationService.updatePrompt).
-- Serbest metin, CHECK kısıtına gerek yok (V15'teki used_agent eklemesiyle aynı desen).
ALTER TABLE tasks ADD COLUMN prompt TEXT;
