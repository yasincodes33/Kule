package com.AgentSaasAplication.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Backend kendi JWT'lerini kendi imzalar ve doğrular (HS256, tek paylaşılan sır).
 * `JwtDecoder` bean'i elle tanımlandığı için Boot'un
 * `OAuth2ResourceServerAutoConfiguration`'ı otomatik geri çekilir.
 *
 * TUZAK: Normal Bearer-token akışı (oauth2ResourceServer().jwt()) hem access hem refresh
 * token'ı KABUL EDER — imza/süre açısından ikisi de geçerli JWT. Refresh token'ın çalınıp
 * korunan endpoint'lerde Bearer olarak kullanılabilmesini engellemek için decoder'a
 * `token_use=access` zorunluluğu ekleniyor; refresh token'lar yalnızca AuthenticationService
 * içinde, /auth/refresh'in kendi (permitAll, security filter zincirinden habersiz) elle
 * decode akışında kabul ediliyor.
 *
 * `app.jwt.secret` TEK bir paylaşılan HS256
 * anahtarı — `kid`/çoklu anahtar desteği yok (bkz. tek bir {@link OctetSequenceKey}'den kurulan
 * {@link ImmutableJWKSet}). Bu bilinen bir sınırlama: kesintisiz rotasyon (eski anahtarla
 * imzalanmış token'lar süresi dolana kadar doğrulanabilirken yeni token'ların yeni anahtarla
 * imzalanması) çoklu-`kid` bir `JWKSet` ve anahtar sürümleme gerektirir. Mevcut tasarımda
 * `JWT_SECRET` rotasyonu tüm aktif access/refresh token'ları anında geçersiz kılar (toplu
 * çıkış); bu, sızıntı sonrası "tüm oturumları iptal et" senaryosu için doğru davranıştır
 * ama planlı bir rotasyon için yeterli değildir.
 *
 * `API_KEY_SECRET` için aynı sınırlama geçerli değil:
 * {@link com.AgentSaasAplication.common.security.ApiKeyCipher} `app.vault.enabled=true` iken
 * gerçek bir HashiCorp Vault Transit engine'ine taşınabiliyor (bkz.
 * {@link com.AgentSaasAplication.common.security.VaultApiKeyCipher}), bu durumda şifreleme
 * anahtarı hiç uygulama sürecinde bulunmuyor ve rotasyonu Vault tarafında, uygulamayı hiç
 * etkilemeden yapılabilir. JWT tarafındaki tek anahtar sınırlaması ise geçerliliğini
 * korur.
 */
@Configuration
public class JwtConfig {

    public static final String CLAIM_TOKEN_USE = "token_use";
    public static final String TOKEN_USE_ACCESS = "access";
    public static final String TOKEN_USE_REFRESH = "refresh";

    private final SecretKeySpec secretKey;

    public JwtConfig(@Value("${app.jwt.secret}") String base64Secret) {
        this.secretKey = new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey.getEncoded())
                .algorithm(JWSAlgorithm.HS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> accessTokenOnly = jwt -> {
            if (!TOKEN_USE_ACCESS.equals(jwt.getClaimAsString(CLAIM_TOKEN_USE))) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Bu uç noktada yalnızca access token kabul edilir", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), accessTokenOnly));
        return decoder;
    }
}
