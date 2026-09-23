package com.AgentSaasAplication.agent.controller;

import com.AgentSaasAplication.agent.domain.AgentCapability;
import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.dto.AddCapabilityRequest;
import com.AgentSaasAplication.agent.dto.AgentConnectionResponse;
import com.AgentSaasAplication.agent.dto.RegisterAgentRequest;
import com.AgentSaasAplication.agent.service.AgentConnectionService;
import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentConnectionController {

	private final AgentConnectionService agentConnectionService;
	private final CurrentUserResolver currentUserResolver;

	public AgentConnectionController(AgentConnectionService agentConnectionService,
			CurrentUserResolver currentUserResolver) {
		this.agentConnectionService = agentConnectionService;
		this.currentUserResolver = currentUserResolver;
	}

	@PostMapping
	public ResponseEntity<AgentConnectionResponse> registerAgent(@Valid @RequestBody RegisterAgentRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		UUID actorUserId = currentUserResolver.resolve(jwt);
		AgentConnection connection = agentConnectionService.registerAgent(actorUserId, request.agentType(),
				request.capabilities(), request.apiKey());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(AgentConnectionResponse.from(connection, request.capabilities()));
	}

	@GetMapping
	public Page<AgentConnectionResponse> listAgents(Pageable pageable) {
		return agentConnectionService.listAgentConnectionsWithCapabilities(pageable);
	}

	@GetMapping("/{connectionId}")
	public AgentConnectionResponse getAgent(@PathVariable UUID connectionId) {
		AgentConnection connection = agentConnectionService.getAgentConnection(connectionId);
		List<TaskType> capabilities = agentConnectionService.getCapabilities(connectionId).stream()
				.map(AgentCapability::getTaskType).toList();
		return AgentConnectionResponse.from(connection, capabilities);
	}

	@PostMapping("/{connectionId}/capabilities")
	public ResponseEntity<Void> addCapability(@PathVariable UUID connectionId,
			@Valid @RequestBody AddCapabilityRequest request) {
		agentConnectionService.addCapability(connectionId, request.taskType());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@DeleteMapping("/{connectionId}")
	public ResponseEntity<Void> removeAgent(@PathVariable UUID connectionId, @AuthenticationPrincipal Jwt jwt) {
		UUID actorUserId = currentUserResolver.resolve(jwt);
		agentConnectionService.removeAgent(actorUserId, connectionId);
		return ResponseEntity.noContent().build();
	}
}