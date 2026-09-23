package com.AgentSaasAplication.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * `ApiKeyCipher`'ın Vault'a taşınmadan ÖNCEki, tek başına yeterli davranışı —
 * `app.vault.enabled=false` (varsayılan) iken aktif. Anahtar (`API_KEY_SECRET`) uygulamanın kendi
 * ortam değişkeninde — bu, `VaultApiKeyCipher`'ın tam olarak çözdüğü zayıflık (uygulama süreci
 * çökse/sızsa anahtar da sızar). Küçük ölçekli/yerel geliştirme için hâlâ yeterli ve Vault
 * çalıştırmayı gerektirmiyor.
 */
@Component
@ConditionalOnProperty(prefix = "app.vault", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalAesApiKeyCipher implements ApiKeyCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec keySpec;

    public LocalAesApiKeyCipher(@Value("${app.security.api-key-secret}") String base64Secret) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("API key şifrelenemedi", e);
        }
    }

    @Override
    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API key çözülemedi", e);
        }
    }
}
