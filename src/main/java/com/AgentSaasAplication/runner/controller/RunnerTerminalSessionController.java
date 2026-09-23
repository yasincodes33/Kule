package com.AgentSaasAplication.runner.controller;

import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.gateway.WsTicketService;
import com.AgentSaasAplication.runner.domain.RunnerTerminalSessionRequest;
import com.AgentSaasAplication.runner.dto.IssueTerminalTicketRequest;
import com.AgentSaasAplication.runner.dto.RunnerTerminalSessionResponse;
import com.AgentSaasAplication.runner.service.RunnerTerminalSessionService;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.dto.WsTicketResponse;
import com.AgentSaasAplication.task.service.TaskOrchestrationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ApprovalController ile aynı şekil — bkz. RunnerTerminalSessionRequest/Service Javadoc'ları.
 * ws-ticket uç noktası TaskController.issueLogsWsTicket'in aynısı: isteğin APPROVED + bu
 * kullanıcıya ait + süresi geçmemiş olduğu doğrulanır, ardından tek kullanımlık bir WS bileti
 * üretilir (asıl access token hiçbir zaman WS URL'sine taşınmaz).
 */
@RestController
@RequestMapping("/api/v1")
public class RunnerTerminalSessionController {

    private final RunnerTerminalSessionService terminalSessionService;
    private final WsTicketService wsTicketService;
    private final CurrentUserResolver currentUserResolver;
    private final TaskOrchestrationService taskOrchestrationService;

    public RunnerTerminalSessionController(RunnerTerminalSessionService terminalSessionService,
                                            WsTicketService wsTicketService,
                                            CurrentUserResolver currentUserResolver,
                                            TaskOrchestrationService taskOrchestrationService) {
        this.terminalSessionService = terminalSessionService;
        this.wsTicketService = wsTicketService;
        this.currentUserResolver = currentUserResolver;
        this.taskOrchestrationService = taskOrchestrationService;
    }

    @PostMapping("/runners/{runnerId}/terminal-sessions")
    public ResponseEntity<RunnerTerminalSessionResponse> requestSession(@PathVariable UUID runnerId,
                                                                          @AuthenticationPrincipal Jwt jwt) {
        UUID requestedBy = currentUserResolver.resolve(jwt);
        RunnerTerminalSessionRequest request = terminalSessionService.requestSession(runnerId, requestedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(RunnerTerminalSessionResponse.from(request));
    }

    @GetMapping("/terminal-sessions")
    public ResponseEntity<Page<RunnerTerminalSessionResponse>> listPendingSessions(Pageable pageable) {
        Page<RunnerTerminalSessionResponse> page = terminalSessionService.listPendingSessions(pageable).map(RunnerTerminalSessionResponse::from);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/terminal-sessions/{requestId}")
    public ResponseEntity<RunnerTerminalSessionResponse> getSession(@PathVariable UUID requestId) {
        RunnerTerminalSessionRequest request = terminalSessionService.getSession(requestId);
        return ResponseEntity.ok(RunnerTerminalSessionResponse.from(request));
    }

    @PostMapping("/terminal-sessions/{requestId}/approve")
    public ResponseEntity<RunnerTerminalSessionResponse> approve(@PathVariable UUID requestId,
                                                                   @AuthenticationPrincipal Jwt jwt) {
        UUID approverUserId = currentUserResolver.resolve(jwt);
        RunnerTerminalSessionRequest request = terminalSessionService.approve(requestId, approverUserId);
        return ResponseEntity.ok(RunnerTerminalSessionResponse.from(request));
    }

    @PostMapping("/terminal-sessions/{requestId}/reject")
    public ResponseEntity<RunnerTerminalSessionResponse> reject(@PathVariable UUID requestId,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        UUID approverUserId = currentUserResolver.resolve(jwt);
        RunnerTerminalSessionRequest request = terminalSessionService.reject(requestId, approverUserId);
        return ResponseEntity.ok(RunnerTerminalSessionResponse.from(request));
    }

    @PostMapping("/runners/{runnerId}/terminal-sessions/{requestId}/ws-ticket")
    public ResponseEntity<WsTicketResponse> issueTerminalWsTicket(@PathVariable UUID runnerId,
                                                                    @PathVariable UUID requestId,
                                                                    @RequestBody(required = false) IssueTerminalTicketRequest body,
                                                                    @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        terminalSessionService.requireUsableApprovedSession(requestId, userId);

        UUID taskId = body != null ? body.taskId() : null;
        if (taskId != null) {
            Task task = taskOrchestrationService.getTask(taskId);
            if (!runnerId.equals(task.getRunnerConnectionId())) {
                throw new IllegalArgumentException("Bu görev bu runner'a atanmamış: taskId=" + taskId + ", runnerId=" + runnerId);
            }
        }

        UUID organizationId = TenantContext.get();
        String ticket = wsTicketService.issueTerminalTicket(userId, organizationId, runnerId, taskId);
        return ResponseEntity.ok(new WsTicketResponse(ticket));
    }
}
