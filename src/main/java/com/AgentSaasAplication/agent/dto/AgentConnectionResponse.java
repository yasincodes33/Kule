package com.AgentSaasAplication.agent.dto;

import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.domain.AgentStatus;
import com.AgentSaasAplication.agent.domain.AgentType;
import com.AgentSaasAplication.common.domain.TaskType;

import java.util.List;
import java.util.UUID;

public record AgentConnectionResponse(
        UUID id,
        AgentType agentType,
        AgentStatus status,
        List<TaskType> capabilities
) {
    public static AgentConnectionResponse from(AgentConnection connection, List<TaskType> capabilities) {
        return new AgentConnectionResponse(
                connection.getId(),
                connection.getAgentType(),
                connection.getStatus(),
                capabilities
        );
    }
}
