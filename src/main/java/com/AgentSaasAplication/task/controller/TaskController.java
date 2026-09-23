	package com.AgentSaasAplication.task.controller;

import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.gateway.WsTicketService;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskLog;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.dto.CompleteTaskRequest;
import com.AgentSaasAplication.task.dto.CreateTaskRequest;
import com.AgentSaasAplication.task.dto.UpdateTaskPromptRequest;
import com.AgentSaasAplication.task.dto.TaskLogResponse;
import com.AgentSaasAplication.task.dto.TaskResponse;
import com.AgentSaasAplication.task.dto.WsTicketResponse;
import com.AgentSaasAplication.task.service.TaskOrchestrationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskOrchestrationService taskOrchestrationService;
    private final CurrentUserResolver currentUserResolver;
    private final WsTicketService wsTicketService;

    public TaskController(TaskOrchestrationService taskOrchestrationService,
                           CurrentUserResolver currentUserResolver,
                           WsTicketService wsTicketService) {
        this.taskOrchestrationService = taskOrchestrationService;
        this.currentUserResolver = currentUserResolver;
        this.wsTicketService = wsTicketService;
    }
    @PostMapping("/tasks/{taskId}/retry")
	public ResponseEntity<TaskResponse> retryTask(@PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt) {
		UUID actorUserId = currentUserResolver.resolve(jwt);
		Task task = taskOrchestrationService.retryTask(actorUserId, taskId);
		return ResponseEntity.ok(TaskResponse.from(task));
	}
    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID actorUserId = currentUserResolver.resolve(jwt);
        Task task = taskOrchestrationService.createTask(
                actorUserId, projectId, request.type(), request.title(),
                request.agentConnectionId(), request.runnerConnectionId(),
                request.preferredModelTier(), request.assignedUserId(), request.prompt());

        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(task));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID taskId) {
        Task task = taskOrchestrationService.getTask(taskId);
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ResponseEntity<Page<TaskResponse>> listProjectTasks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            Pageable pageable) {

        Page<Task> tasks = taskOrchestrationService.listProjectTasks(projectId, status, pageable);
        return ResponseEntity.ok(tasks.map(TaskResponse::from));
    }

    /**
     * Görevin runner'ın yerel terminaline hiç gitmeden web'den tamamlanması — bkz.
     * TaskOrchestrationService.completeTask() Javadoc'u (bridge'den gelen TASK_RESULT ile aynı
     * kod yolunu paylaşıyor). Ekstra bir rol kontrolü yok: createTask/cancelTask/retryTask gibi,
     * bu org'un aktif üyesi olmak (TenantFilter zaten garanti ediyor) yeterli kabul ediliyor.
     */
    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<TaskResponse> completeTask(@PathVariable UUID taskId,
                                                       @Valid @RequestBody CompleteTaskRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        UUID actorUserId = currentUserResolver.resolve(jwt);
        Task task = taskOrchestrationService.completeTask(
                actorUserId, taskId, request.status(), request.message(), request.usedAgent());
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    /** bkz. TaskOrchestrationService.updatePrompt — görevin modele/araca gönderilecek prompt
     * metnini oluşturulduktan sonra da düzenleyebilmek için. */
    @PutMapping("/tasks/{taskId}/prompt")
    public ResponseEntity<TaskResponse> updateTaskPrompt(@PathVariable UUID taskId,
                                                           @RequestBody UpdateTaskPromptRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) {
        UUID actorUserId = currentUserResolver.resolve(jwt);
        Task task = taskOrchestrationService.updatePrompt(actorUserId, taskId, request.prompt());
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseEntity<TaskResponse> cancelTask(@PathVariable UUID taskId,
                                                     @AuthenticationPrincipal Jwt jwt) {
        UUID actorUserId = currentUserResolver.resolve(jwt);
        Task task = taskOrchestrationService.cancelTask(actorUserId, taskId);
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @GetMapping("/tasks/{taskId}/logs")
    public ResponseEntity<List<TaskLogResponse>> getTaskLogs(@PathVariable UUID taskId) {
        List<TaskLog> logs = taskOrchestrationService.getTaskLogs(taskId);
        List<TaskLogResponse> response = logs.stream().map(TaskLogResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    /** `/ws/tasks/{taskId}/logs` WebSocket handshake'i için tek
     * kullanımlık, 60 saniye geçerli bir bilet üretir — bkz. WsTicketService. Bu uç normal
     * Bearer+X-Organization-Id ile doğrulanan bir HTTP isteği, bu yüzden asıl access token hiç
     * WS URL'sine taşınmıyor. Task'ın gerçekten bu org'a ait olduğu burada da (RLS ile)
     * doğrulanıyor — bilet, var olmayan/başka bir org'un task'ı için üretilemez. */
    @PostMapping("/tasks/{taskId}/logs/ws-ticket")
    public ResponseEntity<WsTicketResponse> issueLogsWsTicket(@PathVariable UUID taskId, @AuthenticationPrincipal Jwt jwt) {
        taskOrchestrationService.getTask(taskId);
        UUID userId = currentUserResolver.resolve(jwt);
        UUID organizationId = TenantContext.get();
        String ticket = wsTicketService.issueTicket(userId, organizationId, taskId);
        return ResponseEntity.ok(new WsTicketResponse(ticket));
    }
}