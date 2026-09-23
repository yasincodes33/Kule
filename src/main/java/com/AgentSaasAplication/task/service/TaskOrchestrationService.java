package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.service.AgentConnectionService;
import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.domain.ModelTier;
import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.MembershipAuthorizationService;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskCreatedEventOutbox;
import com.AgentSaasAplication.task.domain.TaskLog;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.event.TaskCreatedEvent;
import com.AgentSaasAplication.task.repository.TaskCreatedEventOutboxRepository;
import com.AgentSaasAplication.task.repository.TaskLogRepository;
import com.AgentSaasAplication.task.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class TaskOrchestrationService {

    private final TaskRepository taskRepository;
    private final TaskLogRepository taskLogRepository;
    private final TaskCreatedEventOutboxRepository taskCreatedEventOutboxRepository;
    private final TaskStateService taskStateService;
    private final AgentConnectionService agentConnectionService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.AgentSaasAplication.project.service.ProjectService projectService;
    private final MembershipAuthorizationService membershipAuthorizationService;
    private final com.AgentSaasAplication.runner.service.RunnerConnectionService runnerConnectionService;

    @Value("${app.task.max-retries:3}")
    private int maxRetries;

    public TaskOrchestrationService(TaskRepository taskRepository,
                                    TaskLogRepository taskLogRepository,
                                    TaskCreatedEventOutboxRepository taskCreatedEventOutboxRepository,
                                    TaskStateService taskStateService,
                                    AgentConnectionService agentConnectionService,
                                    ApplicationEventPublisher eventPublisher,
                                    com.AgentSaasAplication.project.service.ProjectService projectService,
                                    MembershipAuthorizationService membershipAuthorizationService,
                                    com.AgentSaasAplication.runner.service.RunnerConnectionService runnerConnectionService) {
        this.taskRepository = taskRepository;
        this.taskLogRepository = taskLogRepository;
        this.taskCreatedEventOutboxRepository = taskCreatedEventOutboxRepository;
        this.taskStateService = taskStateService;
        this.agentConnectionService = agentConnectionService;
        this.eventPublisher = eventPublisher;
        this.projectService = projectService;
        this.membershipAuthorizationService = membershipAuthorizationService;
        this.runnerConnectionService = runnerConnectionService;
    }

    @Transactional
    public Task createTask(UUID actorUserId, UUID projectId, TaskType type, String title,
                            UUID preferredAgentConnectionId, UUID preferredRunnerConnectionId,
                            ModelTier preferredModelTier, UUID assignedUserId) {
        return createTask(actorUserId, projectId, type, title, preferredAgentConnectionId,
                preferredRunnerConnectionId, preferredModelTier, assignedUserId, null);
    }

    @Transactional
    public Task createTask(UUID actorUserId, UUID projectId, TaskType type, String title,
                            UUID preferredAgentConnectionId, UUID preferredRunnerConnectionId,
                            ModelTier preferredModelTier, UUID assignedUserId, String prompt) {
        UUID organizationId = TenantContext.get();

        projectService.ensureProjectExists(projectId);

        // projectId için yapılan tenant doğrulamasının aynısı assignedUserId için de gerekli:
        // users tablosu global (RLS yok), doğrulanmazsa başka org'un kullanıcı ID'si
        // yazılabilir ve görev hiçbir zaman eşleşmeyen bir sahibe kilitlenir.
        if (assignedUserId != null) {
            membershipAuthorizationService.requireActiveMembership(
                    organizationId, assignedUserId, "Görevin atandığı kullanıcı");
        }

        if (preferredAgentConnectionId != null && preferredRunnerConnectionId != null) {
            throw new IllegalArgumentException(
                    "agentConnectionId ve runnerConnectionId aynı anda verilemez — görev ya bulut modeline ya runner'a gider");
        }

        if (preferredAgentConnectionId != null) {
            AgentConnection preferred = agentConnectionService.getAgentConnection(preferredAgentConnectionId);
            if (!agentConnectionService.hasCapability(preferred.getId(), type)) {
                throw new IllegalArgumentException(
                        "Seçilen agent (" + preferred.getAgentType() + ") bu görev tipini (" + type + ") desteklemiyor");
            }
        }

        // Runner override'ı fail-fast doğrulanıyor; online olup olmadığı ise dispatch anında
        // bakılacak gerçek zamanlı bir durum.
        if (preferredRunnerConnectionId != null) {
            runnerConnectionService.getRunnerConnection(preferredRunnerConnectionId);
        }

        Task task = Task.create(organizationId, actorUserId, projectId, type, title, assignedUserId);
        if (prompt != null && !prompt.isBlank()) {
            task.updatePrompt(prompt);
        }
        task = taskRepository.save(task);

        // Task ile AYNI transaction'da kalıcı bir outbox satırı — Kafka'ya yayın
        // (AFTER_COMMIT'te, bkz. TaskCreatedEventKafkaBridge) başarısız olsa bile bu satır
        // DB'de kalır, TaskCreatedEventOutboxScheduler broker'a tekrar erişilebilir olana kadar
        // periyodik olarak yeniden dener — en-az-bir-kez teslim garantisi.
        taskCreatedEventOutboxRepository.save(TaskCreatedEventOutbox.create(
                organizationId, task.getId(), preferredAgentConnectionId, preferredRunnerConnectionId, preferredModelTier));

        eventPublisher.publishEvent(new TaskCreatedEvent(
                task.getId(), organizationId, preferredAgentConnectionId, preferredRunnerConnectionId, preferredModelTier));
        eventPublisher.publishEvent(new AuditEvent(
                organizationId, actorUserId, "TASK_CREATED", "Task", task.getId(),
                Map.of("type", type.name(), "title", title)));

        log.info("Task oluşturuldu: taskId={}, projectId={}, type={}, status=QUEUED, preferredAgent={}, preferredTier={}",
                task.getId(), projectId, type, preferredAgentConnectionId, preferredModelTier);
        return task;
    }

    @Transactional
    public Task cancelTask(UUID actorUserId, UUID taskId) {
        return taskStateService.transition(
                taskId, TaskStatus.CANCELLED, "Kullanıcı tarafından iptal edildi", actorUserId);
    }

    /** Kullanıcının orijinal agent/model tercihi (varsa) retry'da hatırlanmıyor — bilinçli basitleştirme. */
    @Transactional
    public Task retryTask(UUID actorUserId, UUID taskId) {
        Task task = getTask(taskId);

        if (task.getStatus() != TaskStatus.FAILED) {
            throw new IllegalStateException(
                    "Sadece FAILED durumundaki task'lar yeniden denenebilir, mevcut durum: " + task.getStatus());
        }
        if (task.getRetryCount() >= maxRetries) {
            throw new IllegalStateException(
                    "Maksimum yeniden deneme sayısına ulaşıldı (" + maxRetries + ")");
        }

        task.incrementRetryCount();
        Task updated = taskStateService.transition(taskId, TaskStatus.QUEUED,
                "Yeniden deneniyor (" + task.getRetryCount() + "/" + maxRetries + ")", actorUserId);

        UUID organizationId = TenantContext.get();
        // createTask() ile aynı gerekçe — retry de kendi outbox satırını yazıyor.
        // Kullanıcının orijinal agent/model tercihi retry'da hatırlanmadığı için (bkz. yukarıdaki
        // metot yorumu) üçü de null.
        taskCreatedEventOutboxRepository.save(TaskCreatedEventOutbox.create(organizationId, taskId, null, null, null));
        eventPublisher.publishEvent(new TaskCreatedEvent(taskId, organizationId, null, null, null));
        eventPublisher.publishEvent(new AuditEvent(
                organizationId, actorUserId, "TASK_RETRIED", "Task", taskId,
                Map.of("retryCount", task.getRetryCount())));

        log.info("Task yeniden deneniyor: taskId={}, retryCount={}/{}", taskId, task.getRetryCount(), maxRetries);
        return updated;
    }

    public Page<Task> listTasks(Pageable pageable) {
        return taskRepository.findByTenantId(TenantContext.get(), pageable);
    }

    public Page<Task> listProjectTasks(UUID projectId, TaskStatus status, Pageable pageable) {
        if (status != null) {
            return taskRepository.findByProjectIdAndStatus(projectId, status, pageable);
        }
        return taskRepository.findByProjectId(projectId, pageable);
    }

    public Task getTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new com.AgentSaasAplication.common.exceptions.NotFoundException("Task bulunamadı: " + taskId));
    }

    public List<TaskLog> getTaskLogs(UUID taskId) {
        getTask(taskId);
        return taskLogRepository.findByTaskIdOrderByTimestampAsc(taskId);
    }

    public long countActiveTasksForAgent(UUID agentConnectionId) {
        return taskRepository.countByAgentConnectionIdAndStatusIn(
                agentConnectionId, List.of(TaskStatus.DISPATCHED, TaskStatus.RUNNING));
    }

    /** Ekstra bir rol kontrolü yok — completeTask ile aynı gerekçe, org'un aktif üyesi olmak
     * yeterli (TenantFilter zaten garanti ediyor). */
    @Transactional
    public Task updatePrompt(UUID actorUserId, UUID taskId, String prompt) {
        Task task = getTask(taskId);
        task.updatePrompt(prompt);

        eventPublisher.publishEvent(new AuditEvent(
                task.getTenantId(), actorUserId, "TASK_PROMPT_UPDATED", "Task", taskId, null));

        log.info("Task prompt'u güncellendi: taskId={}, actorUserId={}", taskId, actorUserId);
        return task;
    }

    public long countActiveTasksForRunner(UUID runnerConnectionId) {
        return taskRepository.countByRunnerConnectionIdAndStatusIn(
                runnerConnectionId, List.of(TaskStatus.DISPATCHED, TaskStatus.RUNNING));
    }

    /**
     * Bir görevi COMPLETED/FAILED olarak kapatır — hem AgentBridgeHandler'ın runner'dan gelen
     * TASK_RESULT'u işlerken hem de TaskController'ın web'den gelen "görevi tamamla" isteğini
     * işlerken çağırdığı TEK ortak yer (aynı mantığın iki yerde kopyalanmasını önlemek için
     * buraya taşındı). actorUserId runner-kaynaklı çağrılarda null olabilir (mevcut davranış).
     *
     * DISPATCHED'ten COMPLETED/FAILED'e doğrudan bir geçiş TaskStateMachine'de tanımlı değil —
     * bu yüzden hâlâ DISPATCHED ise önce RUNNING'e alınıyor (runner'ın işi fiilen kabul ettiği an).
     * AWAITING_APPROVAL'dan çağrılırsa TaskStateMachine zaten reddeder (IllegalStateException) —
     * onay kapısını bu yoldan bypass etmek mümkün değil.
     */
    @Transactional
    public Task completeTask(UUID actorUserId, UUID taskId, TaskStatus resultStatus, String message, String usedAgentName) {
        if (resultStatus != TaskStatus.COMPLETED && resultStatus != TaskStatus.FAILED) {
            throw new IllegalArgumentException("Görev yalnızca COMPLETED veya FAILED olarak tamamlanabilir: " + resultStatus);
        }

        taskStateService.recordUsedAgent(taskId, usedAgentName);

        if (taskStateService.getCurrentStatus(taskId) == TaskStatus.DISPATCHED) {
            taskStateService.transition(taskId, TaskStatus.RUNNING, "İşe başlandı", actorUserId);
        }

        return taskStateService.transition(taskId, resultStatus,
                message != null && !message.isBlank() ? message : ("Sonuç: " + resultStatus), actorUserId);
    }
}