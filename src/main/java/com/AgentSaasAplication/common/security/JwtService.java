package com.AgentSaasAplication.common.security;

import com.AgentSaasAplication.config.JwtConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Backend'in kendi JWT'lerini üretip doğruladığı servis. Bilinçli olarak
 * identity.domain.User entity'sine değil yalnızca (userId, email) çiftine bağımlı:
 * `common.security` paketi hiçbir özellik paketine bağımlı olmamalı invariantı bu
 * sayede korunuyor.
 *
 * Refresh token'lar için AYRI bir {@code JwtDecoder} bean'i TANIMLANMADI — Spring'in
 * {@code oauth2ResourceServer().jwt()} zinciri tam olarak BİR {@code JwtDecoder} bean'i
 * bekliyor (JwtConfig'teki, yalnızca access token kabul eden), ikinci bir bean eklemek
 * ambiguous-bean hatası verirdi. Bunun yerine burada, Spring'in bean grafiğinin DIŞINDA, aynı
 * sır materyaliyle ayrı bir decoder örneği tutuluyor — yalnızca imza+süre doğruluyor,
 * token_use kısıtlaması YOK (çağıran taraf — AuthenticationService — bunu ayrıca kontrol eder).
 */
@Component
public class JwtService {

    private static final String ISSUER = "agentsaas";

    private final JwtEncoder jwtEncoder;
    private final NimbusJwtDecoder refreshTokenDecoder;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(JwtEncoder jwtEncoder,
                       @Value("${app.jwt.secret}") String base64Secret,
                       @Value("${app.jwt.access-token-ttl-minutes:60}") long accessTokenTtlMinutes,
                       @Value("${app.jwt.refresh-token-ttl-days:30}") long refreshTokenTtlDays) {
        this.jwtEncoder = jwtEncoder;
        SecretKeySpec secretKey = new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256");
        this.refreshTokenDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        this.refreshTokenDecoder.setJwtValidator(JwtValidators.createDefault());
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    public String issueAccessToken(UUID userId, String email) {
        return issue(userId, email, JwtConfig.TOKEN_USE_ACCESS, accessTokenTtl, null);
    }

    /**
     * jti (JWT ID) artık ZORUNLU — refresh token rotasyonu/iptali bu claim
     * üzerinden izleniyor (bkz. identity.domain.RefreshTokenRecord). Çağıran taraf
     * (AuthenticationService) jti'yi kendisi üretip hem token'a hem DB kaydına aynı değeri veriyor.
     */
    public String issueRefreshToken(UUID userId, String email, String jti) {
        return issue(userId, email, JwtConfig.TOKEN_USE_REFRESH, refreshTokenTtl, jti);
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(refreshTokenTtl);
    }

    /**
     * Yalnızca /auth/refresh için. İmza/süre geçerliliğini doğrular; dönen {@link Jwt}'nin
     * {@code token_use} claim'inin gerçekten "refresh" olduğunu çağıran taraf AYRICA kontrol
     * etmeli — bu metot bilerek onu zorunlu kılmıyor (bir access token'ın burada reddedilmesi
     * gerekmiyor, yalnızca AuthenticationService.refresh()'in iş kuralı).
     */
    public Jwt decodeForRefresh(String token) {
        return refreshTokenDecoder.decode(token);
    }

    public boolean isRefreshToken(Jwt jwt) {
        return JwtConfig.TOKEN_USE_REFRESH.equals(jwt.getClaimAsString(JwtConfig.CLAIM_TOKEN_USE));
    }

    private String issue(UUID userId, String email, String tokenUse, Duration ttl, String jti) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim("email", email)
                .claim(JwtConfig.CLAIM_TOKEN_USE, tokenUse)
                .issuedAt(now)
                .expiresAt(now.plus(ttl));
        if (jti != null) {
            claimsBuilder.id(jti);
        }
        JwtClaimsSet claims = claimsBuilder.build();
        // Algoritma JwsHeader'da AÇIKÇA HS256 olarak belirtiliyor:
        // JwtEncoderParameters.from(claims) kısayolu varsayılan olarak RSA/EC imzalama
        // bekler ve HMAC (OctetSequenceKey) anahtarıyla eşleşmediği için NimbusJwtEncoder
        // "Failed to select a JWK signing key" hatası verir.
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
