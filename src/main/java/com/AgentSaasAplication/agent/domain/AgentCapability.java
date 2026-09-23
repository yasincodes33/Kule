package com.AgentSaasAplication.agent.domain;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "agent_capabilities")
public class AgentCapability extends TenantScopedEntity {

    @Column(name = "agent_connection_id", nullable = false)
    private UUID agentConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    private AgentCapability(UUID organizationId, UUID agentConnectionId, TaskType taskType) {
        super(organizationId);
        this.agentConnectionId = agentConnectionId;
        this.taskType = taskType;
    }

    public static AgentCapability create(UUID organizationId, UUID agentConnectionId, TaskType taskType) {
        return new AgentCapability(organizationId, agentConnectionId, taskType);
    }
}