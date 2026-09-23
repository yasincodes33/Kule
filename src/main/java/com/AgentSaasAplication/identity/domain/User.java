package com.AgentSaasAplication.identity.domain;

import com.AgentSaasAplication.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash — asla düz metin, asla API yanıtlarında dönmez. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String displayName;

    private User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public static User register(String email, String passwordHash, String displayName) {
        return new User(email, passwordHash, displayName);
    }

    /** Şifre sıfırlama akışı — parametre zaten BCrypt hash'i, düz metin asla buraya girmemeli. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
