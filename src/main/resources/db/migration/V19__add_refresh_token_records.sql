-- Refresh token rotasyonu + çalıntı-token tespiti için. Token'ın kendisi hâlâ
-- bir JWT (jwtEncoder/jwtDecoder değişmedi) — burada yalnızca onun "jti" (JWT ID) claim'i
-- izleniyor, düz metin token asla saklanmıyor. Her /auth/refresh çağrısı: (a) sunulan jti'yi
-- revoked_at=NULL olarak bulur, (b) onu revoke edip replaced_by_jti ile yeni jti'ye bağlar,
-- (c) yeni bir satır ekler. ZATEN revoked_at DOLU bir jti tekrar sunulursa bu "reuse" sayılır
-- (token muhtemelen çalınmış) — o kullanıcının TÜM aktif kayıtları iptal edilir.
CREATE TABLE refresh_token_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    jti             TEXT NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_jti TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID
);

CREATE UNIQUE INDEX idx_refresh_token_records_jti ON refresh_token_records (jti);
CREATE INDEX idx_refresh_token_records_user_active ON refresh_token_records (user_id) WHERE revoked_at IS NULL;
