package com.AgentSaasAplication.runner.dto;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.domain.RunnerStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunnerConnectionResponse(
        UUID id,
        UUID ownerUserId,
        UUID projectId,
        String label,
        RunnerStatus status,
        Instant lastHeartbeatAt,
        List<TaskType> capabilities
) {
    public static RunnerConnectionResponse from(RunnerConnection connection, List<TaskType> capabilities) {
        return new RunnerConnectionResponse(
                connection.getId(),
                connection.getOwnerUserId(),
                connection.getProjectId(),
                connection.getLabel(),
                connection.getStatus(),
                connection.getLastHeartbeatAt(),
                capabilities
        );
    }
}
