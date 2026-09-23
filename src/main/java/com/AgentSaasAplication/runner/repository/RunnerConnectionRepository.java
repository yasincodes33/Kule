package com.AgentSaasAplication.runner.repository;

import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.domain.RunnerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RunnerConnectionRepository extends JpaRepository<RunnerConnection, UUID> {

    Page<RunnerConnection> findByTenantId(UUID tenantId, Pageable pageable);

    List<RunnerConnection> findByTenantIdAndStatus(UUID tenantId, RunnerStatus status);

    List<RunnerConnection> findByTenantIdAndOwnerUserId(UUID tenantId, UUID ownerUserId);

    Optional<RunnerConnection> findByTenantIdAndBridgeTokenHash(UUID tenantId, String bridgeTokenHash);

    List<RunnerConnection> findByStatusAndLastHeartbeatAtBefore(RunnerStatus status, Instant threshold);
}
