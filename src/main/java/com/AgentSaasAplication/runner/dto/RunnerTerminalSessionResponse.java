package com.AgentSaasAplication.runner.dto;

import com.AgentSaasAplication.approval.domain.ApprovalStatus;
import com.AgentSaasAplication.runner.domain.RunnerTerminalSessionRequest;

import java.time.Instant;
import java.util.UUID;

public record RunnerTerminalSessionResponse(
        UUID id,
        UUID runnerConnectionId,
        UUID requestedBy,
        ApprovalStatus status,
        UUID decidedBy,
        Instant decidedAt,
        Instant expiresAt,
        Instant createdAt
) {
    public static RunnerTerminalSessionResponse from(RunnerTerminalSessionRequest request) {
        return new RunnerTerminalSessionResponse(
                request.getId(),
                request.getRunnerConnectionId(),
                request.getRequestedBy(),
                request.getStatus(),
                request.getDecidedBy(),
                request.getDecidedAt(),
                request.getExpiresAt(),
                request.getCreatedAt()
        );
    }
}
