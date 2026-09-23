package com.AgentSaasAplication.approval.dto;

import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.domain.ApprovalStatus;

import java.time.Instant;
import java.util.UUID;

public record ApprovalResponse(
        UUID id,
        UUID taskId,
        UUID requestedBy,
        ApprovalStatus status,
        UUID decidedBy,
        Instant decidedAt,
        Instant expiresAt,
        Instant createdAt,
        /** Onaya sebep olan araç çağrısı; elle istenen onaylarda null (bkz. V23 migration). */
        String toolName,
        String toolArguments
) {
    public static ApprovalResponse from(ApprovalRequest approvalRequest) {
        return new ApprovalResponse(
                approvalRequest.getId(),
                approvalRequest.getTaskId(),
                approvalRequest.getRequestedBy(),
                approvalRequest.getStatus(),
                approvalRequest.getDecidedBy(),
                approvalRequest.getDecidedAt(),
                approvalRequest.getExpiresAt(),
                approvalRequest.getCreatedAt(),
                approvalRequest.getToolName(),
                approvalRequest.getToolArguments()
        );
    }
}