package com.AgentSaasAplication.common.security;

import com.AgentSaasAplication.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Self-issued JWT altyapısının çekirdeği. Gerçek bir NimbusJwtEncoder ile çalışır
 * (Spring context olmadan, JwtConfig'in bean üretim metodu doğrudan çağrılarak):
 * mock'lanmış bir encoder, imzalama ve JWK seçimindeki gerçek hataları yakalayamazdı.
 */
class JwtServiceTest {

    private static final String SECRET = "u0jPk8Yj54/bZdstnsXwYA/HSwg07yaSPdXGOh3dURg=";

    private JwtService jwtService;
    private final UUID userId = UUID.randomUUID();
    private final String email = "test@example.com";

    @BeforeEach
    void setUp() {
        JwtEncoder encoder = new JwtConfig(SECRET).jwtEncoder();
        jwtService = new JwtService(encoder, SECRET, 60, 30);
    }

    @Test
    void access_token_dogru_claimlerle_uretilir_ve_decode_edilebilir() {
        String token = jwtService.issueAccessToken(userId, email);

        Jwt decoded = jwtService.decodeForRefresh(token);

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("email")).isEqualTo(email);
        assertThat(decoded.getClaimAsString(JwtConfig.CLAIM_TOKEN_USE)).isEqualTo(JwtConfig.TOKEN_USE_ACCESS);
        assertThat(jwtService.isRefreshToken(decoded)).isFalse();
    }

    @Test
    void refresh_token_dogru_claimlerle_uretilir_ve_isRefreshToken_true_doner() {
        String token = jwtService.issueRefreshToken(userId, email, "jti-1");

        Jwt decoded = jwtService.decodeForRefresh(token);

        assertThat(decoded.getClaimAsString(JwtConfig.CLAIM_TOKEN_USE)).isEqualTo(JwtConfig.TOKEN_USE_REFRESH);
        assertThat(jwtService.isRefreshToken(decoded)).isTrue();
        assertThat(decoded.getId()).isEqualTo("jti-1");
    }

    @Test
    void access_token_jti_tasimaz() {
        String token = jwtService.issueAccessToken(userId, email);

        Jwt decoded = jwtService.decodeForRefresh(token);

        assertThat(decoded.getId()).isNull();
    }

    @Test
    void access_ve_refresh_token_farkli_degerler_uretir() {
        String access = jwtService.issueAccessToken(userId, email);
        String refresh = jwtService.issueRefreshToken(userId, email, "jti-2");

        assertThat(access).isNotEqualTo(refresh);
    }

    @Test
    void baska_bir_sirla_imzalanmis_token_reddedilir() {
        JwtEncoder otherEncoder = new JwtConfig("dv6QNddKyGwsmeribhNGDTgrVP+jMHtXpI2n7XsbAf0=").jwtEncoder();
        JwtService otherService = new JwtService(otherEncoder, "dv6QNddKyGwsmeribhNGDTgrVP+jMHtXpI2n7XsbAf0=", 60, 30);
        String tokenFromOtherSecret = otherService.issueAccessToken(userId, email);

        assertThatThrownBy(() -> jwtService.decodeForRefresh(tokenFromOtherSecret))
                .isInstanceOf(JwtException.class);
    }

    // Süresi dolmuş bir token'ın reddedilmesi Spring'in kendi JwtValidators.createDefault()'unun
    // (framework davranışı) sorumluluğunda — burada test edilmiyor, bu servisin kendi kodunu
    // (claim üretimi, token_use ayrımı, imza doğrulaması) test etmeye odaklanıyoruz.

    @Test
    void accessTokenTtlSeconds_dakikayi_saniyeye_cevirir() {
        assertThat(jwtService.accessTokenTtlSeconds()).isEqualTo(60 * 60L);
    }
}
