package com.AgentSaasAplication.agent.repository;

import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.domain.AgentStatus;
import com.AgentSaasAplication.agent.domain.AgentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentConnectionRepository extends JpaRepository<AgentConnection, UUID> {

    Page<AgentConnection> findByTenantId(UUID tenantId, Pageable pageable);

    List<AgentConnection> findByTenantIdAndStatus(UUID tenantId, AgentStatus status);

    boolean existsByTenantIdAndAgentType(UUID tenantId, AgentType agentType);
}