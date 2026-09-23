package com.AgentSaasAplication.agent.connector;

import com.AgentSaasAplication.agent.domain.AgentType;
import com.AgentSaasAplication.agent.service.AgentConnectionService;
import com.AgentSaasAplication.approval.service.RiskyToolPolicy;
import com.AgentSaasAplication.approval.service.ToolCallApprovalGate;
import com.AgentSaasAplication.runner.service.RunnerLookupService;
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
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ChatGptConnector extends AbstractApiAgentConnector {

	private static final String API_URL = "https://api.openai.com/v1/chat/completions";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	@Value("${app.chatgpt.default-model}")
	private String defaultModel;
	@Value("${app.chatgpt.budget-model}")
	private String budgetModel;
	@Value("${app.chatgpt.reasoning-model}")
	private String reasoningModel;

	public ChatGptConnector(RestClient.Builder restClientBuilder, ApiKeyCipher apiKeyCipher,
			TaskStatusUpdater taskStatusUpdater, RunnerLookupService runnerLookupService,
			BridgeMessageSender bridgeMessageSender, RiskyToolPolicy riskyToolPolicy,
			ToolCallApprovalGate toolCallApprovalGate) {
		super(apiKeyCipher, taskStatusUpdater, runnerLookupService, bridgeMessageSender,
				riskyToolPolicy, toolCallApprovalGate);
		this.restClient = withTimeouts(restClientBuilder).build();
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public AgentType getAgentType() {
		return AgentType.CHATGPT;
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
			restClient.post().uri(API_URL).header("Authorization", "Bearer " + apiKey)
					.contentType(MediaType.APPLICATION_JSON).body(Map.of("model", model, "max_tokens", 1, "messages",
							List.of(Map.of("role", "user", "content", "ping"))))
					.retrieve().toBodilessEntity();
			return null;
		} catch (RestClientResponseException e) {
			log.warn("ChatGPT model doğrulaması başarısız: model={}, status={}", model, e.getStatusCode());
			return "HTTP " + e.getStatusCode();
		} catch (Exception e) {
			log.error("ChatGPT model doğrulaması sırasında beklenmeyen hata: model={}", model, e);
			return e.getClass().getSimpleName() + ": " + e.getMessage();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	protected String completeText(String apiKey, String model, String systemPrompt, String userPrompt) {
		List<Map<String, Object>> messages = new ArrayList<>();
		if (systemPrompt != null) {
			messages.add(Map.of("role", "system", "content", systemPrompt));
		}
		messages.add(Map.of("role", "user", "content", userPrompt));
		Map<String, Object> response = restClient.post().uri(API_URL).header("Authorization", "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("model", model, "max_tokens", 2048, "messages", messages))
				.retrieve().body(Map.class);
		if (response == null) {
			throw new IllegalStateException("Boş yanıt");
		}
		List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
		Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
		Object content = message.get("content");
		return content == null ? "" : content.toString();
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
		List<Map<String, Object>> toolSchemas = StandardTools.ALL.stream().map(this::toOpenAiToolSchema).toList();

		Map<String, Object> response = restClient.post().uri(API_URL).header("Authorization", "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("model", model, "tools", toolSchemas, "messages", messages)).retrieve().body(Map.class);

		if (response == null)
			throw new IllegalStateException("Boş yanıt");

		List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
		Map<String, Object> assistantMessage = (Map<String, Object>) choices.get(0).get("message");
		messages.add(assistantMessage);

		List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) assistantMessage.get("tool_calls");
		if (rawToolCalls != null && !rawToolCalls.isEmpty()) {
			List<RequestedToolCall> toolCalls = new ArrayList<>();
			for (Map<String, Object> raw : rawToolCalls) {
				Map<String, Object> function = (Map<String, Object>) raw.get("function");
				String argumentsJson = (String) function.get("arguments");
				Map<String, Object> arguments = parseArguments(argumentsJson);
				toolCalls.add(new RequestedToolCall((String) raw.get("id"), (String) function.get("name"), arguments));
			}
			return TurnOutcome.toolCalls(toolCalls);
		}

		return TurnOutcome.finalAnswer((String) assistantMessage.get("content"));
	}

	@Override
	protected void appendToolResult(List<Map<String, Object>> messages, RequestedToolCall call,
			ToolExecutionResult result) {
		messages.add(Map.of("role", "tool", "tool_call_id", call.callId(), "content", result.output()));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseArguments(String argumentsJson) {
		try {
			return objectMapper.readValue(argumentsJson, Map.class);
		} catch (Exception e) {
			log.warn("Araç argümanları ayrıştırılamadı: {}", argumentsJson, e);
			return Map.of();
		}
	}

	private Map<String, Object> toOpenAiToolSchema(ToolDefinition tool) {
		Map<String, Object> properties = new HashMap<>();
		tool.parameters().forEach((name, schema) -> properties.put(name,
				Map.of("type", schema.type(), "description", schema.description())));

		return Map.of("type", "function", "function", Map.of("name", tool.name(), "description", tool.description(),
				"parameters", Map.of("type", "object", "properties", properties, "required", tool.required())));
	}
}