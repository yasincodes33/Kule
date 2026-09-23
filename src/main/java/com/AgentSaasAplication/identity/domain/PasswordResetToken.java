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
 * "Şifremi unuttum" akışı — bkz. V18__add_password_reset_tokens.sql. Düz metin
 * token yalnızca üretildiği anda (BridgeTokenGenerator.generate()) var olur, burada SADECE hash'i
 * tutulur. Kullanıldıktan veya süresi dolduktan sonra satır silinmiyor, denetim izi olarak kalıyor.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    private PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static PasswordResetToken issue(UUID userId, String tokenHash, Instant expiresAt) {
        return new PasswordResetToken(userId, tokenHash, expiresAt);
    }

    public boolean isValid() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void markUsed() {
        if (usedAt != null) {
            throw new IllegalStateException("Bu sıfırlama bağlantısı zaten kullanılmış");
        }
        this.usedAt = Instant.now();
    }
}
