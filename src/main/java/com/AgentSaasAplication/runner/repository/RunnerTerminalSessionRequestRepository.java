package com.AgentSaasAplication.runner.repository;

import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import com.AgentSaasAplication.runner.domain.RunnerTerminalSessionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RunnerTerminalSessionRequestRepository extends JpaRepository<RunnerTerminalSessionRequest, UUID> {

    boolean existsByRunnerConnectionIdAndStatus(UUID runnerConnectionId, ApprovalStatus status);

    Page<RunnerTerminalSessionRequest> findByTenantIdAndStatus(UUID tenantId, ApprovalStatus status, Pageable pageable);
}
