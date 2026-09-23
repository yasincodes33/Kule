package com.AgentSaasAplication.identity.domain;

import com.AgentSaasAplication.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token rotasyonu + çalıntı-token tespiti — bkz.
 * V19__add_refresh_token_records.sql. Token'ın kendisi hâlâ bir JWT (imza/süre JwtService'te
 * doğrulanıyor); burada yalnızca onun "jti" claim'i izleniyor, düz metin token hiç saklanmıyor.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_token_records")
public class RefreshTokenRecord extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_jti")
    private String replacedByJti;

    private RefreshTokenRecord(UUID userId, String jti, Instant expiresAt) {
        this.userId = userId;
        this.jti = jti;
        this.expiresAt = expiresAt;
    }

    public static RefreshTokenRecord issue(UUID userId, String jti, Instant expiresAt) {
        return new RefreshTokenRecord(userId, jti, expiresAt);
    }

    public boolean isActive() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void revoke() {
        if (revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    /** Rotasyon — bu kaydı iptal eder ve hangi yeni jti ile değiştirildiğini işaretler. */
    public void rotateTo(String newJti) {
        this.revokedAt = Instant.now();
        this.replacedByJti = newJti;
    }
}
