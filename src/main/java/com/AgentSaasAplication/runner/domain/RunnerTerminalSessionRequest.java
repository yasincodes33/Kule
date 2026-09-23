package com.AgentSaasAplication.runner.domain;

import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import com.AgentSaasAplication.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * approval_requests'in (bkz. ApprovalRequest) aynı şekli — task_id yerine runner_connection_id.
 * Ayrı tutuldu çünkü ApprovalRequest.approve()/reject() doğrudan taskStateService'e
 * (task durum geçişine) bağlı; terminal oturumunun bir task'ı yok. ApprovalStatus enum'ı
 * (zaten task'tan bağımsız, jenerik) REUSE ediliyor.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "runner_terminal_session_requests")
public class RunnerTerminalSessionRequest extends TenantScopedEntity {

    @Column(name = "runner_connection_id", nullable = false)
    private UUID runnerConnectionId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(name = "approved_by")
    private UUID decidedBy;

    @Column(name = "responded_at")
    private Instant decidedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private RunnerTerminalSessionRequest(UUID organizationId, UUID runnerConnectionId, UUID requestedBy, Instant expiresAt) {
        super(organizationId);
        this.runnerConnectionId = runnerConnectionId;
        this.requestedBy = requestedBy;
        this.expiresAt = expiresAt;
        this.status = ApprovalStatus.PENDING;
    }

    public static RunnerTerminalSessionRequest requestFor(UUID organizationId, UUID runnerConnectionId,
                                                            UUID requestedBy, Instant expiresAt) {
        return new RunnerTerminalSessionRequest(organizationId, runnerConnectionId, requestedBy, expiresAt);
    }

    public void approve(UUID approverId) {
        requirePending();
        this.status = ApprovalStatus.APPROVED;
        this.decidedBy = approverId;
        this.decidedAt = Instant.now();
    }

    public void reject(UUID approverId) {
        requirePending();
        this.status = ApprovalStatus.REJECTED;
        this.decidedBy = approverId;
        this.decidedAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    private void requirePending() {
        if (this.status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Bu terminal oturum isteği zaten karara bağlanmış: " + this.status);
        }
    }
}
