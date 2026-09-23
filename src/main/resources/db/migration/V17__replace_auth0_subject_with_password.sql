-- users.auth0_subject kaldırılıyor: kimlik doğrulama backend'in kendi imzaladığı
-- JWT'lerle yapılıyor (bkz. config/JwtConfig.java, identity/service/AuthenticationService
-- .java). users tablosu artık bir password_hash (BCrypt) tutuyor ve JWT'nin "sub" claim'i
-- doğrudan users.id (UUID) oluyor.
--
-- Mevcut satırlar password_hash='' ile kalıyor: boş bir BCrypt hash hiçbir düz metin
-- parolayla eşleşmez, dolayısıyla bu satırlar login ile erişilemez hale gelir ama veri
-- kaybolmaz. Kullanıcı /api/v1/auth/register ile yeniden kayıt olabilir (email UNIQUE
-- olduğu için önce eski satırın silinmesi gerekir).

ALTER TABLE users DROP COLUMN auth0_subject;
ALTER TABLE users ADD COLUMN password_hash TEXT NOT NULL DEFAULT '';
ALTER TABLE users ALTER COLUMN password_hash DROP DEFAULT;
