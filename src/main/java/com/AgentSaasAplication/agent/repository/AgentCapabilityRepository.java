package com.AgentSaasAplication.agent.repository;

import com.AgentSaasAplication.agent.domain.AgentCapability;
import com.AgentSaasAplication.common.domain.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentCapabilityRepository extends JpaRepository<AgentCapability, UUID> {

    List<AgentCapability> findByAgentConnectionId(UUID agentConnectionId);

    /** Agent listeleme sayfasındaki HER satır için ayrı bir
     * capability sorgusu yerine, sayfadaki tüm agentConnectionId'ler için TEK sorguda toplu
     * çekmek için — bkz. AgentConnectionService.listAgentConnectionsWithCapabilities(). */
    List<AgentCapability> findByAgentConnectionIdIn(List<UUID> agentConnectionIds);

    boolean existsByAgentConnectionIdAndTaskType(UUID agentConnectionId, TaskType taskType);

    void deleteByAgentConnectionId(UUID agentConnectionId);
}