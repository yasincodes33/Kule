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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiConnector extends AbstractApiAgentConnector {

    private final RestClient restClient;

    @Value("${app.gemini.default-model}")
    private String defaultModel;
    @Value("${app.gemini.free-tier-model}")
    private String freeTierModel;
    @Value("${app.gemini.pro-model}")
    private String proModel;

    public GeminiConnector(RestClient.Builder restClientBuilder, ApiKeyCipher apiKeyCipher,
                            TaskStatusUpdater taskStatusUpdater, RunnerLookupService runnerLookupService,
                            BridgeMessageSender bridgeMessageSender, RiskyToolPolicy riskyToolPolicy,
                            ToolCallApprovalGate toolCallApprovalGate) {
        super(apiKeyCipher, taskStatusUpdater, runnerLookupService, bridgeMessageSender,
                riskyToolPolicy, toolCallApprovalGate);
        this.restClient = withTimeouts(restClientBuilder).build();
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.GEMINI;
    }

    @Override
    protected List<String> getModelChain(TaskType taskType) {
        return switch (taskType) {
            case DEV -> List.of(freeTierModel, defaultModel);
            case DEPLOY -> List.of(proModel, defaultModel, freeTierModel);
            case ANALYSIS -> List.of(defaultModel, freeTierModel);
        };
    }

    @Override
    protected String modelForTier(ModelTier tier) {
        return switch (tier) {
            case BUDGET -> freeTierModel;
            case DEFAULT -> defaultModel;
            case REASONING -> proModel;
        };
    }

    @Override
    protected List<String> allConfiguredModels() {
        return List.of(defaultModel, freeTierModel, proModel);
    }

    private String endpoint(String apiKey, String model) {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
    }

    @Override
    protected String pingModel(String apiKey, String model) {
        try {
            restClient.post().uri(endpoint(apiKey, model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", "ping"))))))
                    .retrieve().toBodilessEntity();
            return null;
        } catch (RestClientResponseException e) {
            log.warn("Gemini model doğrulaması başarısız: model={}, status={}", model, e.getStatusCode());
            return "HTTP " + e.getStatusCode();
        } catch (Exception e) {
            log.error("Gemini model doğrulaması sırasında beklenmeyen hata: model={}", model, e);
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String completeText(String apiKey, String model, String systemPrompt, String userPrompt) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))));
        if (systemPrompt != null) {
            body.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }
        Map<String, Object> response = restClient.post().uri(endpoint(apiKey, model))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
        if (response == null) {
            throw new IllegalStateException("Boş yanıt");
        }
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> part : (List<Map<String, Object>>) content.get("parts")) {
            if (part.containsKey("text")) {
                text.append((String) part.get("text"));
            }
        }
        return text.toString();
    }

    @Override
    protected List<Map<String, Object>> buildInitialMessages(Task task) {
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", task.getTitle()))));
        return contents;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected TurnOutcome sendTurn(String apiKey, String model, List<Map<String, Object>> messages, Task task) {
        Map<String, Object> functionDeclarations = Map.of("functionDeclarations",
                StandardTools.ALL.stream().map(this::toGeminiFunctionDeclaration).toList());

        Map<String, Object> response = restClient.post().uri(endpoint(apiKey, model))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("contents", messages, "tools", List.of(functionDeclarations)))
                .retrieve().body(Map.class);

        if (response == null) throw new IllegalStateException("Boş yanıt");

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

        // Modelin cevabını (role: model) konuşma geçmişine ekliyoruz — Gemini'nin beklediği yapı.
        messages.add(Map.of("role", "model", "parts", parts));

        List<RequestedToolCall> toolCalls = new ArrayList<>();
        StringBuilder textParts = new StringBuilder();

        for (Map<String, Object> part : parts) {
            if (part.containsKey("text")) {
                textParts.append((String) part.get("text"));
            } else if (part.containsKey("functionCall")) {
                Map<String, Object> fc = (Map<String, Object>) part.get("functionCall");
                // Gemini fonksiyon çağrılarında ayrı bir "id" döndürmüyor — kendimiz üretiyoruz,
                // sonucu geri eşlerken sadece fonksiyon adını kullanacağız.
                toolCalls.add(new RequestedToolCall(
                        java.util.UUID.randomUUID().toString(), (String) fc.get("name"),
                        (Map<String, Object>) fc.get("args")));
            }
        }

        if (!toolCalls.isEmpty()) return TurnOutcome.toolCalls(toolCalls);
        return TurnOutcome.finalAnswer(textParts.toString());
    }
    
    @Override
    protected void appendToolResult(List<Map<String, Object>> messages, RequestedToolCall call, ToolExecutionResult result) {
        messages.add(Map.of("role", "user", "parts", List.of(Map.of(
                "functionResponse", Map.of("name", call.toolName(), "response", Map.of("result", result.output()))
        ))));
    }

    private Map<String, Object> toGeminiFunctionDeclaration(ToolDefinition tool) {
        Map<String, Object> declaration = new HashMap<>();
        declaration.put("name", tool.name());
        declaration.put("description", tool.description());

        // Gemini, OBJECT tipli bir şemada BOŞ "properties" kabul etmiyor —
        // "should be non-empty for OBJECT type" diye 400 INVALID_ARGUMENT dönüyor ve
        // tek bozuk bildirim TÜM isteği düşürüyor. git_status/git_branch_list gibi
        // parametresiz araçlarda "parameters" alanı hiç gönderilmemeli.
        if (!tool.parameters().isEmpty()) {
            Map<String, Object> properties = new HashMap<>();
            tool.parameters().forEach((name, schema) ->
                    properties.put(name, Map.of("type", schema.type().toUpperCase(java.util.Locale.ROOT), "description", schema.description())));

            declaration.put("parameters",
                    Map.of("type", "OBJECT", "properties", properties, "required", tool.required()));
        }

        return declaration;
    }
}