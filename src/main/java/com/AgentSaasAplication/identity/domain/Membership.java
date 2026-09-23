package com.AgentSaasAplication.identity.domain;

import com.AgentSaasAplication.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;
import java.util.UUID;
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "memberships")
public class Membership extends TenantScopedEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "invited_email")
    private String invitedEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    private Membership(UUID organizationId, UUID userId, String invitedEmail, Role role, MembershipStatus status) {
        super(organizationId);
        this.userId = userId;
        this.invitedEmail = invitedEmail;
        this.role = role;
        this.status = status;
    }

    public static Membership invite(UUID organizationId, String invitedEmail, Role role) {
        if (role == Role.OWNER) {
            throw new IllegalArgumentException("OWNER rolü davetle atanamaz, sahiplik devri ayrı bir akıştır");
        }
        return new Membership(organizationId, null, invitedEmail, role, MembershipStatus.PENDING);
    }

    public static Membership createOwner(UUID organizationId, UUID userId) {
        return new Membership(organizationId, userId, null, Role.OWNER, MembershipStatus.ACTIVE);
    }

    public void accept(UUID acceptingUserId, String acceptingEmail) {
        requirePending();
        requireEmailMatch(acceptingEmail);
        this.userId = acceptingUserId;
        this.status = MembershipStatus.ACTIVE;
    }

    public void reject(String rejectingEmail) {
        requirePending();
        requireEmailMatch(rejectingEmail);
        this.status = MembershipStatus.REVOKED;
    }

    public void revoke() {
        this.status = MembershipStatus.REVOKED;
    }

    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole, "role null olamaz");
    }

    public boolean isOwner() {
        return this.role == Role.OWNER;
    }

    public boolean isAdminOrOwner() {
        return this.role == Role.ADMIN || this.role == Role.OWNER;
    }

    public boolean isActive() {
        return this.status == MembershipStatus.ACTIVE;
    }

    private void requirePending() {
        if (this.status != MembershipStatus.PENDING) {
            throw new IllegalStateException("Bu davet artık bekliyor durumunda değil");
        }
    }

    private void requireEmailMatch(String email) {
        if (invitedEmail == null || !invitedEmail.equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("Bu davet bu kullanıcıya ait değil");
        }
    }
    public boolean hasApprovalRights() {
        return this.role == Role.APPROVER || this.role == Role.ADMIN || this.role == Role.OWNER;
    }
}