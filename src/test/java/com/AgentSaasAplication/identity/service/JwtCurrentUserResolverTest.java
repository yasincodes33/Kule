package com.AgentSaasAplication.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Çözümleyici veritabanına hiç gitmez; "sub" claim'i doğrudan users.id'dir. Bu testler
 * sub'ın gerçekten UUID.fromString ile çözüldüğünü ve geçersiz bir sub'ın sessizce yanlış
 * bir kullanıcıya çözülmek yerine hata verdiğini doğrular.
 */
class JwtCurrentUserResolverTest {

    private final JwtCurrentUserResolver resolver = new JwtCurrentUserResolver();

    @Test
    void jwt_subject_dogrudan_uuid_olarak_cozulur() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = fakeJwt(userId.toString());

        assertThat(resolver.resolve(jwt)).isEqualTo(userId);
    }

    @Test
    void gecersiz_bir_subject_sessizce_yanlis_bir_kullaniciya_degil_hataya_duser() {
        Jwt jwt = fakeJwt("not-a-uuid-subject");

        assertThatThrownBy(() -> resolver.resolve(jwt)).isInstanceOf(IllegalArgumentException.class);
    }

    private Jwt fakeJwt(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
