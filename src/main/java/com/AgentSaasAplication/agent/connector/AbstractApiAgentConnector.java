package com.AgentSaasAplication.agent.connector;

import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.approval.service.RiskyToolPolicy;
import com.AgentSaasAplication.approval.service.ToolCallApprovalGate;
import com.AgentSaasAplication.common.bridge.BridgeMessage;
import com.AgentSaasAplication.common.bridge.BridgeMessageSender;
import com.AgentSaasAplication.common.domain.ModelTier;
import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.security.ApiKeyCipher;
import com.AgentSaasAplication.common.task.TaskStatusUpdater;
import com.AgentSaasAplication.task.domain.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.AgentSaasAplication.runner.service.RunnerLookupService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public abstract class AbstractApiAgentConnector implements AgentConnector, ApiKeyVerifier {

	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int READ_TIMEOUT_MS = 60_000;
	private static final int MAX_ATTEMPTS_PER_MODEL = 3;
	private static final long INITIAL_BACKOFF_MS = 2_000;
	private static final int MAX_CONVERSATION_TURNS = 8;
	/** Tanınmayan bir araç adı gelirse kullanılan güvenli varsayılan. */
	private static final Duration FALLBACK_TOOL_CALL_TIMEOUT = Duration.ofSeconds(180);

	protected final ApiKeyCipher apiKeyCipher;
	protected final TaskStatusUpdater taskStatusUpdater;
	private final RunnerLookupService runnerLookupService;
	private final BridgeMessageSender bridgeMessageSender;
	private final RiskyToolPolicy riskyToolPolicy;
	private final ToolCallApprovalGate toolCallApprovalGate;

	protected AbstractApiAgentConnector(ApiKeyCipher apiKeyCipher, TaskStatusUpdater taskStatusUpdater,
			RunnerLookupService runnerLookupService, BridgeMessageSender bridgeMessageSender,
			RiskyToolPolicy riskyToolPolicy, ToolCallApprovalGate toolCallApprovalGate) {
		this.apiKeyCipher = apiKeyCipher;
		this.taskStatusUpdater = taskStatusUpdater;
		this.runnerLookupService = runnerLookupService;
		this.bridgeMessageSender = bridgeMessageSender;
		this.riskyToolPolicy = riskyToolPolicy;
		this.toolCallApprovalGate = toolCallApprovalGate;
	}

	/** Onay reddedildiğinde/zaman aşımına uğradığında fırlatılır — task durumu zaten
	 *  ApprovalService tarafından doğru terminal duruma (REJECTED) ya da AWAITING_APPROVAL'da
	 *  bırakıldığı için executeTask() bunu normal bir hata gibi FAILED'e ÇEVİRMEMELİ
	 *  (AWAITING_APPROVAL -> FAILED geçişi TaskStateMachine'de yok, IllegalStateException atar). */
	protected static final class ApprovalDeniedException extends RuntimeException {
		ApprovalDeniedException(String message) {
			super(message);
		}
	}

	protected static RestClient.Builder withTimeouts(RestClient.Builder builder) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
		factory.setReadTimeout(READ_TIMEOUT_MS);
		return builder.requestFactory(factory);
	}


	protected record RequestedToolCall(String callId, String toolName, Map<String, Object> arguments) {
	}

	/** Bir turun sonucu — ya nihai metin, ya da modelin istediği araç çağrıları. */
	protected static final class TurnOutcome {
		private final String finalText;
		private final List<RequestedToolCall> toolCalls;

		private TurnOutcome(String finalText, List<RequestedToolCall> toolCalls) {
			this.finalText = finalText;
			this.toolCalls = toolCalls;
		}

		static TurnOutcome finalAnswer(String text) {
			return new TurnOutcome(text, null);
		}

		static TurnOutcome toolCalls(List<RequestedToolCall> calls) {
			return new TurnOutcome(null, calls);
		}

		boolean isFinal() {
			return finalText != null;
		}
	}

	protected record ToolExecutionResult(boolean success, String output) {
	}

	protected abstract List<Map<String, Object>> buildInitialMessages(Task task);


	protected abstract TurnOutcome sendTurn(String apiKey, String model, List<Map<String, Object>> messages, Task task)
			throws Exception;

	protected abstract void appendToolResult(List<Map<String, Object>> messages, RequestedToolCall call,
			ToolExecutionResult result);

	protected abstract List<String> getModelChain(TaskType taskType);

	protected abstract List<String> allConfiguredModels();

	/**
	 * @return başarılıysa {@code null}, aksi halde başarısızlığın kısa nedeni (HTTP
	 *         durumu vb.). Neden metni, kullanıcıya "API key doğrulanamadı" dışında bir
	 *         ipucu verebilmek için taşınıyor.
	 */
	protected abstract String pingModel(String apiKey, String model);

	/**
	 * Araçsız, tek atışlık metin üretimi — görev triyajı, commit mesajı, kod incelemesi
	 * ve terminal özeti gibi "yardımcı" işler için. `sendTurn` bunlara uygun değil: araç
	 * şemalarını yüklüyor, konuşma geçmişini büyütüyor ve bir göreve bağlı çalışıyor.
	 *
	 * @return modelin düz metin cevabı
	 */
	protected abstract String completeText(String apiKey, String model, String systemPrompt, String userPrompt)
			throws Exception;

	/** Yardımcı çağrılar için model seçimi — {@link #getModelChain} ile aynı yapılandırmayı kullanır. */
	public String assistModel(ModelTier tier) {
		List<String> chain = getModelChain(TaskType.DEV);
		if (chain.isEmpty()) {
			throw new IllegalStateException("Yapılandırılmış model yok: " + getAgentType());
		}
		// BUDGET -> zincirin en ucuzu, aksi halde varsayılan (ilk) model.
		return tier == ModelTier.BUDGET ? chain.get(chain.size() - 1) : chain.get(0);
	}

	/** Şifreli anahtarı çözüp tek atışlık bir istem çalıştırır (bkz. {@link #completeText}). */
	public String assist(AgentConnection connection, ModelTier tier, String systemPrompt, String userPrompt)
			throws Exception {
		return completeText(apiKeyCipher.decrypt(connection.getApiKeyEncrypted()), assistModel(tier),
				systemPrompt, userPrompt);
	}


	@Override
	public boolean executeTask(AgentConnection connection, Task task, ModelTier preferredModelTier) {
		try {
			String apiKey = apiKeyCipher.decrypt(connection.getApiKeyEncrypted());
			taskStatusUpdater.markRunning(task.getId(), getAgentType() + " API'ye istek gönderiliyor");

			List<String> modelChain = resolveModelChain(task.getType(), preferredModelTier);
			Exception lastError = null;

			for (int i = 0; i < modelChain.size(); i++) {
				String model = modelChain.get(i);
				try {
					String result = runConversation(apiKey, model, task);
					if (i > 0) {
						taskStatusUpdater.logInfo(task.getId(),
								modelChain.get(0) + " kullanılamadı, " + model + " modeline düşüldü");
					}
					taskStatusUpdater.markCompleted(task.getId(),
							getAgentType() + " tamamladı (" + model + "): " + result);
					return true;
				} catch (ApprovalDeniedException e) {
					// Başka bir modelle tekrar denemek anlamsız — aynı riskli araç çağrısı yine
					// onay isteyecekti. Task durumu zaten ApprovalService tarafından ayarlandı.
					log.info("{} görev onay reddi/zaman aşımı nedeniyle durduruldu: taskId={}, sebep={}",
							getAgentType(), task.getId(), e.getMessage());
					return false;
				} catch (Exception e) {
					lastError = e;
					log.warn("{} model denemesi tükendi: taskId={}, model={}, hata={}", getAgentType(), task.getId(),
							model, e.getMessage());
				}
			}
			throw lastError != null ? lastError : new IllegalStateException("Hiçbir model denenemedi");
		} catch (Exception e) {
			log.error("{} task çalıştırma hatası: taskId={}", getAgentType(), task.getId(), e);
			taskStatusUpdater.markFailed(task.getId(), getAgentType() + " hatası: " + e.getMessage());
			return false;
		}
	}


	private String runConversation(String apiKey, String model, Task task) throws Exception {
		List<Map<String, Object>> messages = buildInitialMessages(task);

		for (int turn = 1; turn <= MAX_CONVERSATION_TURNS; turn++) {
			taskStatusUpdater.logInfo(task.getId(), "Seçilen model: " + model + " (tur " + turn + ")");

			TurnOutcome outcome = withRetry(() -> sendTurn(apiKey, model, messages, task));

			if (outcome.isFinal()) {
				return outcome.finalText;
			}

			for (RequestedToolCall call : outcome.toolCalls) {
				ToolExecutionResult result = executeToolCall(task, call);
				appendToolResult(messages, call, result);
				taskStatusUpdater.logInfo(task.getId(), "Araç çağrısı: " + call.toolName() + " -> "
						+ (result.success() ? "başarılı" : "hata: " + result.output()));
			}
		}
		throw new IllegalStateException("Maksimum tur sayısına (" + MAX_CONVERSATION_TURNS + ") ulaşıldı");
	}

	private ToolExecutionResult executeToolCall(Task task, RequestedToolCall call) {
		if (riskyToolPolicy.requiresApproval(call.toolName(), call.arguments())) {
			Optional<String> denial = toolCallApprovalGate.requestAndAwaitDecision(
					task.getId(), task.getAssignedUserId(), call.toolName(), call.arguments());
			if (denial.isPresent()) {
				throw new ApprovalDeniedException(denial.get());
			}
			taskStatusUpdater.logInfo(task.getId(), "Riskli araç çağrısı onaylandı, devam ediliyor: " + call.toolName());
		}

		UUID localRunnerConnectionId = findLocalRunnerConnectionId(task);
		if (localRunnerConnectionId == null) {
			return new ToolExecutionResult(false,
					"Bu görev için uygun online bir yerel runner bulunamadı (atama ve proje eşleşmesi kontrol edildi)");
		}

		UUID callId = UUID.randomUUID();
		BridgeMessage toolCallMessage = BridgeMessage.toolCall(task.getId(), callId, call.toolName(), call.arguments());

		// Zaman aşımı artık araca özel: read_file 30sn beklerken git_push 10dk bekliyor.
		Duration timeout = com.AgentSaasAplication.common.domain.StandardTools.byName(call.toolName())
				.map(com.AgentSaasAplication.common.domain.ToolDefinition::timeout)
				.orElse(FALLBACK_TOOL_CALL_TIMEOUT);

		Optional<BridgeMessage> response = bridgeMessageSender.sendToolCallAndWait(localRunnerConnectionId,
				toolCallMessage, timeout);

		if (response.isEmpty()) {
			return new ToolExecutionResult(false, "Yerel runner zaman aşımına uğradı ya da cevap vermedi");
		}

		BridgeMessage result = response.get();
		boolean success = Boolean.TRUE.equals(result.success());
		return new ToolExecutionResult(success, result.message() != null ? result.message() : "");
	}

	/**
	 * Görev bir çalışana atanmışsa (Faz 5) araç çağrıları ONUN makinesine gitmeli —
	 * aksi halde "Ahmet'e atanmış" bir görev Mehmet'in diskinde dosya değiştirir.
	 * Atama yoksa org havuzundaki herhangi bir online bridge runner'ı kullanılır.
	 */
	private UUID findLocalRunnerConnectionId(Task task) {
	    // Dispatch sırasında bir runner atandıysa araç çağrıları oraya gider; atanmamışsa
	    // (ör. dispatch anında hiç runner online değildi) yeniden aranır.
	    if (task.getRunnerConnectionId() != null) {
	        return task.getRunnerConnectionId();
	    }
	    return runnerLookupService.findOnlineRunnerId(task.getAssignedUserId(), task.getProjectId());
	}

	@FunctionalInterface
	private interface TurnCall {
		TurnOutcome run() throws Exception;
	}

	private TurnOutcome withRetry(TurnCall call) throws Exception {
		long backoffMillis = INITIAL_BACKOFF_MS;
		Exception lastError = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
			try {
				return call.run();
			} catch (RestClientResponseException e) {
				lastError = e;
				int status = e.getStatusCode().value();
				boolean transientError = status == 429 || status == 503;
				if (!transientError || attempt == MAX_ATTEMPTS_PER_MODEL)
					throw e;
				log.warn("{} geçici hata (deneme {}/{}), {} ms sonra tekrar denenecek: status={}", getAgentType(),
						attempt, MAX_ATTEMPTS_PER_MODEL, backoffMillis, status);
				Thread.sleep(backoffMillis);
				backoffMillis *= 2;
			}
		}
		throw lastError;
	}

	private List<String> resolveModelChain(TaskType taskType, ModelTier preferredModelTier) {
		List<String> baseChain = getModelChain(taskType);
		if (preferredModelTier == null)
			return baseChain;
		String preferredModel = modelForTier(preferredModelTier);
		List<String> merged = new ArrayList<>(new LinkedHashSet<>() {
			{
				add(preferredModel);
				addAll(baseChain);
			}
		});
		return merged;
	}

	protected abstract String modelForTier(ModelTier tier);

	@Override
	public ModelVerificationResult verifyModels(String plaintextApiKey) {
		List<String> models = allConfiguredModels();
		List<String> failed = new ArrayList<>();
		boolean criticalOk = true;
		String detail = null;
		for (int i = 0; i < models.size(); i++) {
			String reason = pingModel(plaintextApiKey, models.get(i));
			if (reason != null) {
				failed.add(models.get(i));
				if (detail == null)
					detail = models.get(i) + ": " + reason;
				if (i == 0)
					criticalOk = false;
			}
		}
		return new ModelVerificationResult(criticalOk, failed, detail);
	}

	@Override
	public boolean healthCheck(AgentConnection connection) {
		return connection.getApiKeyEncrypted() != null;
	}
}