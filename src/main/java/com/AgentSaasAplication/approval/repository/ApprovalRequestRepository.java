package com.AgentSaasAplication.approval.repository;

import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    List<ApprovalRequest> findByTaskId(UUID taskId);

    Optional<ApprovalRequest> findByTaskIdAndStatus(UUID taskId, ApprovalStatus status);

    boolean existsByTaskIdAndStatus(UUID taskId, ApprovalStatus status);

    Page<ApprovalRequest> findByTenantIdAndStatus(UUID tenantId, ApprovalStatus status, Pageable pageable);

    // İleride zamanlanmış expire job'ı için hazır (henüz job yazılmadı)
    List<ApprovalRequest> findByStatusAndExpiresAtBefore(ApprovalStatus status, Instant threshold);
}