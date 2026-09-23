-- "Şifremi unuttum" akışı için — users tablosu gibi organization_id taşımıyor
-- (kullanıcı henüz hangi org bağlamında olduğunu bilmiyor olabilir, bir org'a bile ait olmayabilir),
-- bu yüzden RLS politikası yok (users tablosuyla aynı gerekçe). Token DÜZ METİN saklanmıyor —
-- yalnızca SHA-256 hash'i (bkz. common.security.BridgeTokenGenerator, agent_connections'daki
-- bridge_token_hash ile aynı desen). Süresi dolmuş/kullanılmış token'lar silinmiyor, denetim
-- izi olarak kalıyor — used_at/expires_at ile geçerlilik kontrol ediliyor.
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    token_hash  TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID
);

CREATE UNIQUE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens (token_hash);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
