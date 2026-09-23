package com.AgentSaasAplication.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetTokenTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void yeni_uretilen_token_kullanilmamis_ve_gecerlidir() {
        PasswordResetToken token = PasswordResetToken.issue(userId, "hash", Instant.now().plusSeconds(600));

        assertThat(token.isValid()).isTrue();
        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void suresi_dolmus_token_gecersizdir() {
        PasswordResetToken token = PasswordResetToken.issue(userId, "hash", Instant.now().minusSeconds(1));

        assertThat(token.isValid()).isFalse();
    }

    @Test
    void markUsed_sonrasi_token_gecersizdir() {
        PasswordResetToken token = PasswordResetToken.issue(userId, "hash", Instant.now().plusSeconds(600));

        token.markUsed();

        assertThat(token.isValid()).isFalse();
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void zaten_kullanilmis_token_tekrar_markUsed_edilemez() {
        PasswordResetToken token = PasswordResetToken.issue(userId, "hash", Instant.now().plusSeconds(600));
        token.markUsed();

        assertThatThrownBy(token::markUsed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten kullanılmış");
    }
}
