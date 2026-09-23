package com.AgentSaasAplication.approval.controller;

import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.dto.ApprovalResponse;
import com.AgentSaasAplication.approval.dto.RejectApprovalRequest;
import com.AgentSaasAplication.approval.service.ApprovalService;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final CurrentUserResolver currentUserResolver;

    public ApprovalController(ApprovalService approvalService,
                               CurrentUserResolver currentUserResolver) {
        this.approvalService = approvalService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/tasks/{taskId}/approvals")
    public ResponseEntity<ApprovalResponse> requestApproval(@PathVariable UUID taskId,
                                                              @AuthenticationPrincipal Jwt jwt) {
        UUID requestedBy = currentUserResolver.resolve(jwt);
        ApprovalRequest approvalRequest = approvalService.requestApproval(taskId, requestedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApprovalResponse.from(approvalRequest));
    }

    @GetMapping("/tasks/{taskId}/approvals/{approvalId}")
    public ResponseEntity<ApprovalResponse> getApproval(@PathVariable UUID taskId,
                                                          @PathVariable UUID approvalId) {
        ApprovalRequest approvalRequest = approvalService.getApproval(approvalId);
        return ResponseEntity.ok(ApprovalResponse.from(approvalRequest));
    }

    @PostMapping("/tasks/{taskId}/approvals/{approvalId}/approve")
    public ResponseEntity<ApprovalResponse> approve(@PathVariable UUID taskId,
                                                      @PathVariable UUID approvalId,
                                                      @AuthenticationPrincipal Jwt jwt) {
        UUID approverUserId = currentUserResolver.resolve(jwt);
        ApprovalRequest approvalRequest = approvalService.approve(approvalId, approverUserId);
        return ResponseEntity.ok(ApprovalResponse.from(approvalRequest));
    }

    @PostMapping("/tasks/{taskId}/approvals/{approvalId}/reject")
    public ResponseEntity<ApprovalResponse> reject(@PathVariable UUID taskId,
                                                     @PathVariable UUID approvalId,
                                                     @RequestBody(required = false) RejectApprovalRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        UUID approverUserId = currentUserResolver.resolve(jwt);
        String reason = request != null ? request.reason() : null;
        ApprovalRequest approvalRequest = approvalService.reject(approvalId, approverUserId, reason);
        return ResponseEntity.ok(ApprovalResponse.from(approvalRequest));
    }

    @GetMapping("/approvals")
    public ResponseEntity<Page<ApprovalResponse>> listPendingApprovals(Pageable pageable) {
        Page<ApprovalResponse> page = approvalService.listPendingApprovals(pageable).map(ApprovalResponse::from);
        return ResponseEntity.ok(page);
    }
}