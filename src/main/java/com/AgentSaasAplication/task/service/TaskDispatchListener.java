package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.agent.connector.AgentConnector;
import com.AgentSaasAplication.agent.connector.ConnectorRegistry;
import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.service.AgentConnectionService;
import com.AgentSaasAplication.agent.service.AiAssistService;
import com.AgentSaasAplication.common.bridge.BridgeMessage;
import com.AgentSaasAplication.common.bridge.BridgeMessageSender;
import com.AgentSaasAplication.common.domain.ModelTier;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.service.RunnerConnectionService;
import com.AgentSaasAplication.runner.service.RunnerLookupService;
import com.AgentSaasAplication.project.domain.Project;
import com.AgentSaasAplication.project.service.ProjectService;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.event.TaskCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Görevi iki yoldan biriyle çalıştırır:
 * <ul>
 *   <li><b>Runner yolu (varsayılan):</b> görev kullanıcının makinesine düşer, kullanıcı hangi
 *       araçla çözeceğine kendi terminalinde karar verir. Araç tipi backend'de seçilmez.</li>
 *   <li><b>Bulut modeli yolu:</b> istekte agentConnectionId verilmişse Claude/ChatGPT/Gemini
 *       görevi tool-use döngüsüyle çözer; araç çağrıları yine bir runner'a iner, o yüzden
 *       bu yolda da göreve bir runner atanır.</li>
 * </ul>
 */
@Slf4j
@Component
public class TaskDispatchListener {

	private final TaskStateService taskStateService;
	private final TaskOrchestrationService taskOrchestrationService;
	private final AgentConnectionService agentConnectionService;
	private final RunnerConnectionService runnerConnectionService;
	private final RunnerLookupService runnerLookupService;
	private final ConnectorRegistry connectorRegistry;
	private final BridgeMessageSender bridgeMessageSender;
	private final ObjectMapper objectMapper;
	private final ProjectService projectService;
	private final AiAssistService aiAssistService;

	public TaskDispatchListener(TaskStateService taskStateService,
			TaskOrchestrationService taskOrchestrationService,
			AgentConnectionService agentConnectionService,
			RunnerConnectionService runnerConnectionService,
			RunnerLookupService runnerLookupService,
			ConnectorRegistry connectorRegistry,
			BridgeMessageSender bridgeMessageSender,
			ObjectMapper objectMapper,
			ProjectService projectService,
			AiAssistService aiAssistService) {
		this.taskStateService = taskStateService;
		this.taskOrchestrationService = taskOrchestrationService;
		this.agentConnectionService = agentConnectionService;
		this.runnerConnectionService = runnerConnectionService;
		this.runnerLookupService = runnerLookupService;
		this.connectorRegistry = connectorRegistry;
		this.bridgeMessageSender = bridgeMessageSender;
		this.objectMapper = objectMapper;
		this.projectService = projectService;
		this.aiAssistService = aiAssistService;
	}

	/**
	 * Artık in-process ApplicationEventPublisher yerine gerçek bir Kafka tüketicisi —
	 * TaskCreatedEventKafkaBridge tarafından `task.created` topic'ine (AFTER_COMMIT'te)
	 * yayınlanan olayı tüketiyor. Kafka'nın kendi consumer thread'i zaten ayrı bir thread'de
	 * çalıştığı için @Async'e gerek yok. JSON decode başarısız olursa (bozuk mesaj) hatayı
	 * loglayıp yutuyoruz — consumer'ın poll döngüsünü çökertmemeli.
	 */
	@KafkaListener(topics = TaskCreatedEventKafkaBridge.TOPIC, groupId = "${spring.kafka.consumer.group-id}")
	public void onTaskCreatedFromKafka(ConsumerRecord<String, String> record) {
		TaskCreatedEvent event;
		try {
			event = objectMapper.readValue(record.value(), TaskCreatedEvent.class);
		} catch (Exception e) {
			log.error("Kafka'dan gelen TaskCreatedEvent çözülemedi: payload={}", record.value(), e);
			return;
		}

		log.info("Task dispatch başlatılıyor (Kafka): taskId={}", event.taskId());

		TenantContext.set(event.organizationId());
		try {
			Task task = taskOrchestrationService.getTask(event.taskId());

			if (event.preferredAgentConnectionId() != null) {
				dispatchToCloudAgent(event, task);
			} else {
				dispatchToRunner(event, task);
			}
		} catch (Exception e) {
			log.error("Task dispatch başarısız: taskId={}", event.taskId(), e);
		} finally {
			TenantContext.clear();
		}
	}

	/** Görev kullanıcının kendi makinesinde çözülür — araç seçimi runner tarafında. */
	private void dispatchToRunner(TaskCreatedEvent event, Task task) {
		RunnerConnection runner;

		if (event.preferredRunnerConnectionId() != null) {
			runner = runnerConnectionService.getRunnerConnection(event.preferredRunnerConnectionId());
			Optional<String> rejection = rejectionReason(runner, task);
			if (rejection.isPresent()) {
				fail(event.taskId(), rejection.get());
				return;
			}
		} else {
			List<RunnerConnection> eligible = runnerLookupService
					.findEligibleRunners(task.getAssignedUserId(), task.getProjectId()).stream()
					.filter(r -> runnerConnectionService.hasCapability(r.getId(), task.getType()))
					.toList();

			Optional<RunnerConnection> matched = eligible.stream().min(Comparator.comparingLong(
					r -> taskOrchestrationService.countActiveTasksForRunner(r.getId())));

			if (matched.isEmpty()) {
				fail(event.taskId(), noRunnerReason(task));
				return;
			}
			runner = matched.get();
		}

		taskStateService.assignRunner(task.getId(), runner.getId());
		taskStateService.transition(event.taskId(), TaskStatus.DISPATCHED,
				"Runner'a gönderildi: " + runnerLabel(runner), null);

		Project project = projectService.getProject(task.getProjectId());
		String prompt = triagedPrompt(task, project);

		boolean sent = bridgeMessageSender.send(runner.getId(),
				BridgeMessage.dispatch(task.getId(), task.getType().name(), task.getTitle(), prompt,
						project.getId(), project.getRepoUrl(), project.getDefaultBranch()));

		if (!sent) {
			log.warn("Task dispatch mesajı gönderilemedi: runnerId={}, taskId={}", runner.getId(), task.getId());
			fail(event.taskId(), "Runner'a dispatch mesajı iletilemedi");
		}
	}

	/**
	 * Görev triyajı — Görevin promptu boşsa, organizasyonda kayıtlı bir API ajanı varsa
	 * ucuz bir modele kısa bir çalışma planı yazdırıp promptu onunla dolduruyoruz. Böylece
	 * masaüstündeki kişiye boş bir başlık yerine üzerinde düşünülmüş bir plan düşüyor.
	 *
	 * Dispatch'ten ÖNCE ve senkron çalışıyor: TASK_DISPATCH mesajı prompt'u taşıdığı için,
	 * sonradan yazılan bir plan runner'a hiç ulaşmazdı. API ajanı yoksa ya da çağrı başarısız
	 * olursa mevcut prompt aynen kullanılır — akış hiçbir şekilde durmaz.
	 */
	private String triagedPrompt(Task task, Project project) {
		String existing = task.getPrompt();
		if (existing != null && !existing.isBlank()) {
			return existing;
		}
		String system = "Sen bir yazilim ekibinin teknik lideri gibi davranan bir asistansin. "
				+ "Sana verilen gorev icin KISA bir calisma plani yaz: en fazla 6 madde, her madde "
				+ "tek satir, somut ve uygulanabilir. Giris cumlesi, ozet ya da kapanis yazma; "
				+ "yalnizca maddeler. Yanitini TURKCE ver.";
		String user = "Gorev tipi: " + task.getType()
				+ "\nBaslik: " + task.getTitle()
				+ (project.getRepoUrl() != null ? "\nDepo: " + project.getRepoUrl() : "")
				+ (project.getDefaultBranch() != null ? "\nVarsayilan dal: " + project.getDefaultBranch() : "");

		Optional<String> plan = aiAssistService.ask(ModelTier.BUDGET, system, user);
		if (plan.isEmpty()) {
			return existing;
		}
		// Kalici hale getiriyoruz ki web'deki Prompt paneli de ayni metni gostersin.
		taskOrchestrationService.updatePrompt(null, task.getId(), plan.get());
		taskStateService.appendLog(task.getId(), "TRIAGE", "Gorev plani otomatik olusturuldu:\n" + plan.get());
		return plan.get();
	}

	/** Görev bulut modeline devredilir; araç çağrıları için ayrıca bir runner atanır. */
	private void dispatchToCloudAgent(TaskCreatedEvent event, Task task) {
		AgentConnection agent = agentConnectionService.getAgentConnection(event.preferredAgentConnectionId());
		if (!agent.isOnline()) {
			fail(event.taskId(), "Seçilen agent şu an online değil: " + agent.getAgentType());
			return;
		}

		taskStateService.assignAgent(task.getId(), agent.getId());

		// Runner bulunamazsa görev yine de başlatılıyor: model araç çağırmadan da cevap
		// üretebilir. Araç çağırmaya kalkarsa o noktada açık bir hata mesajı alır.
		UUID runnerId = runnerLookupService.findEligibleRunners(task.getAssignedUserId(), task.getProjectId())
				.stream()
				.min(Comparator.comparingLong(r -> taskOrchestrationService.countActiveTasksForRunner(r.getId())))
				.map(RunnerConnection::getId)
				.orElse(null);

		if (runnerId != null) {
			taskStateService.assignRunner(task.getId(), runnerId);
		} else {
			log.warn("Bulut modeline dispatch edilen görev için uygun runner yok, araç çağrıları başarısız olacak: taskId={}",
					task.getId());
		}

		taskStateService.transition(event.taskId(), TaskStatus.DISPATCHED,
				"Bulut modeline gönderildi: " + agent.getAgentType(), null);

		// Atamalar ayrı transaction'larda yazıldığı için elimizdeki nesne bayat —
		// connector'ın runnerConnectionId'yi görebilmesi için yeniden okunuyor.
		Task refreshed = taskOrchestrationService.getTask(event.taskId());

		AgentConnector connector = connectorRegistry.get(agent.getAgentType());
		connector.executeTask(agent, refreshed, event.preferredModelTier());
	}

	/**
	 * Kullanıcı belirli bir runner zorladığında, atama ve proje sınırlarının override ile
	 * sessizce delinmemesi için aynı kurallar burada da uygulanıyor.
	 */
	private Optional<String> rejectionReason(RunnerConnection runner, Task task) {
		if (!runner.isOnline()) {
			return Optional.of("Seçilen runner şu an online değil");
		}
		if (task.getAssignedUserId() != null && !task.getAssignedUserId().equals(runner.getOwnerUserId())) {
			return Optional.of("Seçilen runner, görevin atandığı kullanıcıya ait değil");
		}
		if (!runner.servesProject(task.getProjectId())) {
			return Optional.of("Seçilen runner başka bir projeye bağlı");
		}
		if (!runnerConnectionService.hasCapability(runner.getId(), task.getType())) {
			return Optional.of("Seçilen runner bu görev tipini desteklemiyor: " + task.getType());
		}
		return Optional.empty();
	}

	/** Eşleşme yokken sebebi söylemek, teşhisi "hiçbir runner yok" belirsizliğinden kurtarıyor. */
	private String noRunnerReason(Task task) {
		if (task.getAssignedUserId() != null) {
			return "Görevin atandığı kullanıcıya ait, bu projeye ve görev tipine uygun online bir runner bulunamadı";
		}
		return "Bu projeye ve görev tipine uygun online bir runner bulunamadı";
	}

	private String runnerLabel(RunnerConnection runner) {
		return runner.getLabel() != null ? runner.getLabel() : runner.getId().toString();
	}

	private void fail(UUID taskId, String reason) {
		taskStateService.transition(taskId, TaskStatus.FAILED, reason, null);
	}
}
