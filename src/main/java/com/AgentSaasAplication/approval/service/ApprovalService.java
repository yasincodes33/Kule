package com.AgentSaasAplication.approval.service;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import com.AgentSaasAplication.approval.repository.ApprovalRequestRepository;
import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.MembershipAuthorizationService;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.service.TaskStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
@Slf4j
@Service
@Transactional(readOnly = true)
public class ApprovalService {

    private static final Duration DEFAULT_APPROVAL_TTL = Duration.ofHours(24);

    private final ApprovalRequestRepository approvalRequestRepository;
    private final TaskStateService taskStateService;
    private final MembershipAuthorizationService membershipAuthorizationService;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovalService(ApprovalRequestRepository approvalRequestRepository,
                            TaskStateService taskStateService,
                            MembershipAuthorizationService membershipAuthorizationService,
                            ApplicationEventPublisher eventPublisher) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.taskStateService = taskStateService;
        this.membershipAuthorizationService = membershipAuthorizationService;
        this.eventPublisher = eventPublisher;
    }

    /** Araç bağlamı olmayan onay isteği (web arayüzünden elle istenen onay). */
    @Transactional
    public ApprovalRequest requestApproval(UUID taskId, UUID requestedByUserId) {
        return requestApproval(taskId, requestedByUserId, null, null);
    }

    /**
     * Riskli bir araç çağrısından doğan onay isteği. `toolName`/`toolArguments` onay ekranında
     * gösterilir — onaylayan kişinin neyi onayladığını görebilmesi için (bkz. V23 migration).
     */
    @Transactional
    public ApprovalRequest requestApproval(UUID taskId, UUID requestedByUserId,
                                           String toolName, String toolArguments) {
        UUID organizationId = TenantContext.get();

        if (approvalRequestRepository.existsByTaskIdAndStatus(taskId, ApprovalStatus.PENDING)) {
            throw new IllegalStateException("Bu task için zaten bekleyen bir onay isteği var");
        }

        taskStateService.transition(taskId, TaskStatus.AWAITING_APPROVAL, "Onay bekleniyor", requestedByUserId);

        Instant expiresAt = Instant.now().plus(DEFAULT_APPROVAL_TTL);
        ApprovalRequest approvalRequest = ApprovalRequest.requestFor(organizationId, taskId, requestedByUserId,
                expiresAt, toolName, toolArguments);
        approvalRequest = approvalRequestRepository.save(approvalRequest);

        eventPublisher.publishEvent(new AuditEvent(
                organizationId, requestedByUserId, "APPROVAL_REQUESTED", "ApprovalRequest", approvalRequest.getId(),
                toolName != null ? Map.of("taskId", taskId.toString(), "tool", toolName)
                        : Map.of("taskId", taskId.toString())));

        log.info("Onay isteği oluşturuldu: approvalId={}, taskId={}, tool={}, expiresAt={}",
                approvalRequest.getId(), taskId, toolName, expiresAt);
        return approvalRequest;
    }

    @Transactional
    public ApprovalRequest approve(UUID approvalId, UUID approverUserId) {
        UUID organizationId = TenantContext.get();
        membershipAuthorizationService.requireApprovalPermission(organizationId, approverUserId);

        ApprovalRequest approvalRequest = getApproval(approvalId);
        approvalRequest.approve(approverUserId);

        taskStateService.transition(approvalRequest.getTaskId(), TaskStatus.RUNNING,
                "Onaylandı, göreve devam ediliyor", approverUserId);

        eventPublisher.publishEvent(new AuditEvent(
                organizationId, approverUserId, "APPROVAL_APPROVED", "ApprovalRequest", approvalId,
                Map.of("taskId", approvalRequest.getTaskId().toString())));

        log.info("Onay isteği onaylandı: approvalId={}, taskId={}, approverId={}",
                approvalId, approvalRequest.getTaskId(), approverUserId);
        return approvalRequest;
    }

    @Transactional
    public ApprovalRequest reject(UUID approvalId, UUID approverUserId, String reason) {
        UUID organizationId = TenantContext.get();
        membershipAuthorizationService.requireApprovalPermission(organizationId, approverUserId);

        ApprovalRequest approvalRequest = getApproval(approvalId);
        approvalRequest.reject(approverUserId);

        taskStateService.transition(approvalRequest.getTaskId(), TaskStatus.REJECTED,
                "Onay reddedildi" + (reason != null && !reason.isBlank() ? ": " + reason : ""), approverUserId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("taskId", approvalRequest.getTaskId().toString());
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        eventPublisher.publishEvent(new AuditEvent(
                organizationId, approverUserId, "APPROVAL_REJECTED", "ApprovalRequest", approvalId, metadata));

        log.info("Onay isteği reddedildi: approvalId={}, taskId={}, approverId={}",
                approvalId, approvalRequest.getTaskId(), approverUserId);
        return approvalRequest;
    }

    public ApprovalRequest getApproval(UUID approvalId) {
        return approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("Onay isteği bulunamadı: " + approvalId));
    }

    public Page<ApprovalRequest> listPendingApprovals(Pageable pageable) {
        return approvalRequestRepository.findByTenantIdAndStatus(
                TenantContext.get(), ApprovalStatus.PENDING, pageable);
    }
}