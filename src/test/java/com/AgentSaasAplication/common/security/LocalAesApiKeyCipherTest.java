package com.AgentSaasAplication.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `ApiKeyCipher`'ın yerel AES/GCM uygulaması. Gerçek şifreleme ile round-trip yapar (mock
 * değil) ve her çağrının farklı bir IV kullandığını, yani aynı düz metnin iki kez
 * şifrelendiğinde farklı ciphertext ürettiğini doğrular.
 */
class LocalAesApiKeyCipherTest {

    private LocalAesApiKeyCipher cipher;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        cipher = new LocalAesApiKeyCipher(Base64.getEncoder().encodeToString(key));
    }

    @Test
    void sifreleyip_cozmek_orijinal_metni_geri_verir() {
        String plaintext = "sk-ant-gizli-api-anahtari-12345";

        String encrypted = cipher.encrypt(plaintext);
        String decrypted = cipher.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void ayni_metin_iki_kez_sifrelenince_farkli_ciphertext_uretir() {
        // Her çağrı rastgele bir IV kullanmalı — aksi halde aynı plaintext hep aynı ciphertext'i
        // üretir, bu da (AES/GCM için) hem bir güvenlik zafiyeti hem "rastgele IV" garantisinin
        // bozulduğunun işareti olurdu.
        String plaintext = "sk-ant-gizli-api-anahtari-12345";

        String first = cipher.encrypt(plaintext);
        String second = cipher.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(plaintext);
        assertThat(cipher.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void bozuk_ciphertext_cozulmeye_calisilinca_istisna_firlatir() {
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString("bozuk-veri-16by".getBytes())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void yanlis_anahtarla_cozulmeye_calisilinca_istisna_firlatir() {
        String encrypted = cipher.encrypt("gizli-deger");

        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        LocalAesApiKeyCipher otherCipher = new LocalAesApiKeyCipher(Base64.getEncoder().encodeToString(otherKey));

        assertThatThrownBy(() -> otherCipher.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }
}
