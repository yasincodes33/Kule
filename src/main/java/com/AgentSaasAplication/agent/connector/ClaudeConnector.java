package com.AgentSaasAplication.agent.connector;

import com.AgentSaasAplication.agent.domain.AgentType;
import com.AgentSaasAplication.agent.service.AgentConnectionService;
import com.AgentSaasAplication.approval.service.RiskyToolPolicy;
import com.AgentSaasAplication.approval.service.ToolCallApprovalGate;
import com.AgentSaasAplication.common.bridge.BridgeMessageSender;
import com.AgentSaasAplication.common.domain.ModelTier;
import com.AgentSaasAplication.common.domain.StandardTools;
import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.domain.ToolDefinition;
import com.AgentSaasAplication.common.security.ApiKeyCipher;
import com.AgentSaasAplication.common.task.TaskStatusUpdater;
import com.AgentSaasAplication.task.domain.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.AgentSaasAplication.runner.service.RunnerLookupService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ClaudeConnector extends AbstractApiAgentConnector {

	private static final String API_URL = "https://api.anthropic.com/v1/messages";
	private static final String ANTHROPIC_VERSION = "2023-06-01";

	private final RestClient restClient;

	@Value("${app.claude.default-model}")
	private String defaultModel;
	@Value("${app.claude.budget-model}")
	private String budgetModel;
	@Value("${app.claude.reasoning-model}")
	private String reasoningModel;

	public ClaudeConnector(RestClient.Builder restClientBuilder, ApiKeyCipher apiKeyCipher,
			TaskStatusUpdater taskStatusUpdater, RunnerLookupService runnerLookupService,
			BridgeMessageSender bridgeMessageSender, RiskyToolPolicy riskyToolPolicy,
			ToolCallApprovalGate toolCallApprovalGate) {
		super(apiKeyCipher, taskStatusUpdater, runnerLookupService, bridgeMessageSender,
				riskyToolPolicy, toolCallApprovalGate);
		this.restClient = withTimeouts(restClientBuilder).build();
	}

	@Override
	public AgentType getAgentType() {
		return AgentType.CLAUDE;
	}

	@Override
	protected List<String> getModelChain(TaskType taskType) {
		return switch (taskType) {
		case DEV -> List.of(budgetModel, defaultModel);
		case DEPLOY -> List.of(reasoningModel, defaultModel, budgetModel);
		case ANALYSIS -> List.of(defaultModel, budgetModel);
		};
	}

	@Override
	protected String modelForTier(ModelTier tier) {
		return switch (tier) {
		case BUDGET -> budgetModel;
		case DEFAULT -> defaultModel;
		case REASONING -> reasoningModel;
		};
	}

	@Override
	protected List<String> allConfiguredModels() {
		return List.of(defaultModel, budgetModel, reasoningModel);
	}

	@Override
	protected String pingModel(String apiKey, String model) {
		try {
			restClient.post().uri(API_URL).header("x-api-key", apiKey).header("anthropic-version", ANTHROPIC_VERSION)
					.contentType(MediaType.APPLICATION_JSON).body(Map.of("model", model, "max_tokens", 1, "messages",
							List.of(Map.of("role", "user", "content", "ping"))))
					.retrieve().toBodilessEntity();
			return null;
		} catch (RestClientResponseException e) {
			log.warn("Claude model doğrulaması başarısız: model={}, status={}", model, e.getStatusCode());
			return "HTTP " + e.getStatusCode();
		} catch (Exception e) {
			log.error("Claude model doğrulaması sırasında beklenmeyen hata: model={}", model, e);
			return e.getClass().getSimpleName() + ": " + e.getMessage();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	protected String completeText(String apiKey, String model, String systemPrompt, String userPrompt) {
		Map<String, Object> body = new java.util.HashMap<>(Map.of("model", model, "max_tokens", 2048,
				"messages", List.of(Map.of("role", "user", "content", userPrompt))));
		if (systemPrompt != null) {
			body.put("system", systemPrompt);
		}
		Map<String, Object> response = restClient.post().uri(API_URL).header("x-api-key", apiKey)
				.header("anthropic-version", ANTHROPIC_VERSION).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().body(Map.class);
		if (response == null) {
			throw new IllegalStateException("Boş yanıt");
		}
		StringBuilder text = new StringBuilder();
		for (Map<String, Object> block : (List<Map<String, Object>>) response.get("content")) {
			if ("text".equals(block.get("type"))) {
				text.append((String) block.get("text"));
			}
		}
		return text.toString();
	}

	@Override
	protected List<Map<String, Object>> buildInitialMessages(Task task) {
		List<Map<String, Object>> messages = new ArrayList<>();
		messages.add(Map.of("role", "user", "content", task.getTitle()));
		return messages;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected TurnOutcome sendTurn(String apiKey, String model, List<Map<String, Object>> messages, Task task) {
		List<Map<String, Object>> toolSchemas = StandardTools.ALL.stream().map(this::toClaudeToolSchema).toList();

		Map<String, Object> response = restClient.post().uri(API_URL).header("x-api-key", apiKey)
				.header("anthropic-version", ANTHROPIC_VERSION).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("model", model, "max_tokens", 4096, "tools", toolSchemas, "messages", messages)).retrieve()
				.body(Map.class);

		if (response == null)
			throw new IllegalStateException("Boş yanıt");

		List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
		messages.add(Map.of("role", "assistant", "content", content));

		List<RequestedToolCall> toolCalls = new ArrayList<>();
		StringBuilder textParts = new StringBuilder();

		for (Map<String, Object> block : content) {
			String type = (String) block.get("type");
			if ("text".equals(type)) {
				textParts.append((String) block.get("text"));
			} else if ("tool_use".equals(type)) {
				toolCalls.add(new RequestedToolCall((String) block.get("id"), (String) block.get("name"),
						(Map<String, Object>) block.get("input")));
			}
		}

		if (!toolCalls.isEmpty()) {
			return TurnOutcome.toolCalls(toolCalls);
		}
		return TurnOutcome.finalAnswer(textParts.toString());
	}

	@Override
	protected void appendToolResult(List<Map<String, Object>> messages, RequestedToolCall call,
			ToolExecutionResult result) {
		messages.add(Map.of("role", "user", "content", List.of(Map.of("type", "tool_result", "tool_use_id",
				call.callId(), "content", result.output(), "is_error", !result.success()))));
	}

	private Map<String, Object> toClaudeToolSchema(ToolDefinition tool) {
		Map<String, Object> properties = new java.util.HashMap<>();
		tool.parameters().forEach((name, schema) -> properties.put(name,
				Map.of("type", schema.type(), "description", schema.description())));

		return Map.of("name", tool.name(), "description", tool.description(), "input_schema",
				Map.of("type", "object", "properties", properties, "required", tool.required()));
	}
}