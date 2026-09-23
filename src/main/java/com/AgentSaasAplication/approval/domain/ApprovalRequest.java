package com.AgentSaasAplication.approval.domain;

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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends TenantScopedEntity {

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    // DB kolonu "approved_by" ama hem approve hem reject kararını taşıyor — Java tarafında
    // daha doğru anlamı yansıtan "decidedBy" adı kullanılıyor.
    @Column(name = "approved_by")
    private UUID decidedBy;

    @Column(name = "responded_at")
    private Instant decidedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Onaya sebep olan araç çağrısının adı ve argümanları. Onay ekranı bunlar olmadan
     * "neyi onayladığını" gösteremiyordu (bkz. V23 migration). Web arayüzünden elle
     * istenen onaylarda bir araç çağrısı olmadığı için ikisi de NULL olabilir.
     */
    @Column(name = "tool_name")
    private String toolName;

    @Column(name = "tool_arguments")
    private String toolArguments;

    private ApprovalRequest(UUID organizationId, UUID taskId, UUID requestedBy, Instant expiresAt,
                            String toolName, String toolArguments) {
        super(organizationId);
        this.taskId = taskId;
        this.requestedBy = requestedBy;
        this.expiresAt = expiresAt;
        this.toolName = toolName;
        this.toolArguments = toolArguments;
        this.status = ApprovalStatus.PENDING;
    }

    /** Araç bağlamı olmayan onaylar için (web arayüzünden elle istenen onay). */
    public static ApprovalRequest requestFor(UUID organizationId, UUID taskId, UUID requestedBy, Instant expiresAt) {
        return new ApprovalRequest(organizationId, taskId, requestedBy, expiresAt, null, null);
    }

    /** Riskli bir araç çağrısından doğan onaylar için — onay ekranı bu bilgiyi gösterir. */
    public static ApprovalRequest requestFor(UUID organizationId, UUID taskId, UUID requestedBy, Instant expiresAt,
                                             String toolName, String toolArguments) {
        return new ApprovalRequest(organizationId, taskId, requestedBy, expiresAt, toolName, toolArguments);
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

    /** İleride zamanlanmış bir job tarafından çağrılacak (mimari plan Bölüm 8: "Onay süresi doldu"). */
    public void markExpired() {
        requirePending();
        this.status = ApprovalStatus.EXPIRED;
        this.decidedAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    private void requirePending() {
        if (this.status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Bu onay isteği zaten karara bağlanmış: " + this.status);
        }
    }
}