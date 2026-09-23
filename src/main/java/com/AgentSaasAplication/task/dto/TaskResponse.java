package com.AgentSaasAplication.task.dto;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.domain.UsedAgent;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID projectId,
        UUID agentConnectionId,
        TaskType type,
        TaskStatus status,
        String title,
        int retryCount,
        UUID assignedUserId,
        UUID runnerConnectionId,
        UsedAgent usedAgent,
        String prompt,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getAgentConnectionId(),
                task.getType(),
                task.getStatus(),
                task.getTitle(),
                task.getRetryCount(),
                task.getAssignedUserId(),
                task.getRunnerConnectionId(),
                task.getUsedAgent(),
                task.getPrompt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}