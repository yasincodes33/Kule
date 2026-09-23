package com.AgentSaasAplication.runner.controller;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.dto.AddRunnerCapabilityRequest;
import com.AgentSaasAplication.runner.dto.RegisterRunnerRequest;
import com.AgentSaasAplication.runner.dto.RunnerConnectionResponse;
import com.AgentSaasAplication.runner.dto.RunnerTokenResponse;
import com.AgentSaasAplication.runner.service.RunnerConnectionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Kullanıcı runner'larının yönetimi. Bulut sağlayıcı kayıtları (Claude/ChatGPT/Gemini)
 * ayrı bir kaynak: /api/v1/agents.
 */
@RestController
@RequestMapping("/api/v1/runners")
public class RunnerConnectionController {

    private final RunnerConnectionService runnerConnectionService;
    private final CurrentUserResolver currentUserResolver;

    public RunnerConnectionController(RunnerConnectionService runnerConnectionService,
                                       CurrentUserResolver currentUserResolver) {
        this.runnerConnectionService = runnerConnectionService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    public ResponseEntity<RunnerConnectionResponse> registerRunner(@Valid @RequestBody RegisterRunnerRequest request,
                                                                    @AuthenticationPrincipal Jwt jwt) {
        UUID actorUserId = currentUserResolver.resolve(jwt);
        RunnerConnection connection = runnerConnectionService.registerRunner(
                actorUserId, request.ownerUserId(), request.projectId(), request.label(), request.capabilities());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RunnerConnectionResponse.from(connection, request.capabilities()));
    }

    @GetMapping
    public Page<RunnerConnectionResponse> listRunners(Pageable pageable) {
        return runnerConnectionService.listRunnersWithCapabilities(pageable);
    }

    @GetMapping("/{runnerId}")
    public RunnerConnectionResponse getRunner(@PathVariable UUID runnerId) {
        RunnerConnection connection = runnerConnectionService.getRunnerConnection(runnerId);
        return RunnerConnectionResponse.from(connection, runnerConnectionService.getCapabilities(runnerId));
    }

    /**
     * Bridge token üretir. Dönen "token" alanı SADECE bu yanıtta görünür, bir daha
     * geri alınamaz — runner'ın config.json'ına yazılması gerekir. ADMIN/OWNER yetkisi ister.
     */
    @PostMapping("/{runnerId}/bridge-token")
    public ResponseEntity<RunnerTokenResponse> issueBridgeToken(@PathVariable UUID runnerId,
                                                                 @AuthenticationPrincipal Jwt jwt) {
        UUID actorUserId = currentUserResolver.resolve(jwt);
        String token = runnerConnectionService.issueBridgeToken(actorUserId, runnerId);
        return ResponseEntity.ok(new RunnerTokenResponse(runnerId, token));
    }

    @PostMapping("/{runnerId}/capabilities")
    public ResponseEntity<Void> addCapability(@PathVariable UUID runnerId,
                                               @Valid @RequestBody AddRunnerCapabilityRequest request) {
        runnerConnectionService.addCapability(runnerId, request.taskType());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{runnerId}/capabilities")
    public List<TaskType> getCapabilities(@PathVariable UUID runnerId) {
        return runnerConnectionService.getCapabilities(runnerId);
    }

    @DeleteMapping("/{runnerId}")
    public ResponseEntity<Void> removeRunner(@PathVariable UUID runnerId, @AuthenticationPrincipal Jwt jwt) {
        UUID actorUserId = currentUserResolver.resolve(jwt);
        runnerConnectionService.removeRunner(actorUserId, runnerId);
        return ResponseEntity.noContent().build();
    }
}
