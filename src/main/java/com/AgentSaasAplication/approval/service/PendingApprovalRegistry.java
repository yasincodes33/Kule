package com.AgentSaasAplication.approval.service;

import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.cluster.RedisRelay;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gateway.PendingToolCallRegistry (callId -> CompletableFuture<BridgeMessage>) ile aynı desen —
 * burada approvalId -> CompletableFuture<ApprovalStatus>. ToolCallApprovalGate bir riskli araç
 * çağrısı için onay isteği oluşturduğunda burada bir future kaydediyor ve onu bekliyor.
 *
 * Çoklu-instance: onayı veren istek (POST .../approve) HANGİ instance'a düşerse
 * `onAuditEvent` orada yerel olarak tetiklenir (bu, DB'ye hiç dokunmayan saf bir olay —
 * NotificationEventListener'daki RLS sorunuyla karıştırılmamalı, o farklı bir sınıf).
 * Ama bekleyen future BAŞKA bir instance'ta olabilir — bu yüzden karar burada doğrudan
 * tamamlanmıyor, Redis'e (`approval:decision` kanalı) yayınlanıyor; TÜM instance'lar dinliyor,
 * yalnızca o approvalId için yerel bekleyeni olan tamamlıyor.
 */
@Component
public class PendingApprovalRegistry {

    private static final String CHANNEL = "approval:decision";

    private final Map<UUID, CompletableFuture<ApprovalStatus>> pending = new ConcurrentHashMap<>();
    private final RedisRelay redisRelay;

    public PendingApprovalRegistry(RedisRelay redisRelay) {
        this.redisRelay = redisRelay;
    }

    @PostConstruct
    void subscribe() {
        redisRelay.subscribe(CHANNEL, ApprovalDecisionMessage.class, this::completeLocally);
    }

    public CompletableFuture<ApprovalStatus> register(UUID approvalId) {
        CompletableFuture<ApprovalStatus> future = new CompletableFuture<>();
        pending.put(approvalId, future);
        return future;
    }

    public void remove(UUID approvalId) {
        pending.remove(approvalId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(AuditEvent event) {
        if (!"ApprovalRequest".equals(event.entityType())) {
            return;
        }
        ApprovalStatus status = switch (event.action()) {
            case "APPROVAL_APPROVED" -> ApprovalStatus.APPROVED;
            case "APPROVAL_REJECTED" -> ApprovalStatus.REJECTED;
            case "APPROVAL_EXPIRED" -> ApprovalStatus.EXPIRED;
            default -> null;
        };
        if (status == null) {
            return;
        }
        redisRelay.publish(CHANNEL, new ApprovalDecisionMessage(event.entityId(), status));
    }

    private void completeLocally(ApprovalDecisionMessage decision) {
        CompletableFuture<ApprovalStatus> future = pending.remove(decision.approvalId());
        if (future != null) {
            future.complete(decision.status());
        }
    }

    public record ApprovalDecisionMessage(UUID approvalId, ApprovalStatus status) {}
}
