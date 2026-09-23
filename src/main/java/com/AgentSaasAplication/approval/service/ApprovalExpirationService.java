package com.AgentSaasAplication.approval.service;

import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import com.AgentSaasAplication.approval.repository.ApprovalRequestRepository;
import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.service.TaskStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ApprovalExpirationService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final TaskStateService taskStateService;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovalExpirationService(ApprovalRequestRepository approvalRequestRepository,
                                      TaskStateService taskStateService,
                                      ApplicationEventPublisher eventPublisher) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.taskStateService = taskStateService;
        this.eventPublisher = eventPublisher;
    }

    public List<UUID> findOverdueApprovalIds(Instant now) {
        return approvalRequestRepository.findByStatusAndExpiresAtBefore(ApprovalStatus.PENDING, now)
                .stream().map(ApprovalRequest::getId).toList();
    }

    @Transactional
    public void expireApproval(UUID approvalId) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("Onay isteği bulunamadı: " + approvalId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            return; 
        }

        approval.markExpired();
        TaskStatus currentTaskStatus = taskStateService.getCurrentStatus(approval.getTaskId());
        if (currentTaskStatus == TaskStatus.AWAITING_APPROVAL) {
            taskStateService.transition(approval.getTaskId(), TaskStatus.REJECTED, "Onay süresi doldu", null);
        } else {
            log.warn("Onay süresi doldu ama task artık AWAITING_APPROVAL değil ({}), task'a dokunulmadı: taskId={}",
                    currentTaskStatus, approval.getTaskId());
        }

        eventPublisher.publishEvent(new AuditEvent(
                approval.getTenantId(), null, "APPROVAL_EXPIRED", "ApprovalRequest", approval.getId(),
                Map.of("taskId", approval.getTaskId().toString())));

        log.info("Onay isteği süresi doldu: approvalId={}, taskId={}", approvalId, approval.getTaskId());
    }
}