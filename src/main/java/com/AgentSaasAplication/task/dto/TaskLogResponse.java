package com.AgentSaasAplication.task.dto;

import com.AgentSaasAplication.task.domain.TaskLog;

import java.time.Instant;
import java.util.UUID;

public record TaskLogResponse(
        UUID id,
        UUID taskId,
        String level,
        String message,
        Instant timestamp
) {
    public static TaskLogResponse from(TaskLog log) {
        return new TaskLogResponse(
                log.getId(),
                log.getTaskId(),
                log.getLevel(),
                log.getMessage(),
                log.getTimestamp()
        );
    }
}