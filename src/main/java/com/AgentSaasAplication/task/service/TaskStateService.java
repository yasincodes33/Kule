package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.domain.UsedAgent;
import com.AgentSaasAplication.common.task.TaskLogPublisher;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskLog;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.repository.TaskLogRepository;
import com.AgentSaasAplication.task.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class TaskStateService {

    private final TaskRepository taskRepository;
    private final TaskLogRepository taskLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskLogPublisher taskLogPublisher;

    public TaskStateService(TaskRepository taskRepository, TaskLogRepository taskLogRepository,
                             ApplicationEventPublisher eventPublisher, TaskLogPublisher taskLogPublisher) {
        this.taskRepository = taskRepository;
        this.taskLogRepository = taskLogRepository;
        this.eventPublisher = eventPublisher;
        this.taskLogPublisher = taskLogPublisher;
    }

    @Transactional
    public Task transition(UUID taskId, TaskStatus newStatus, String logMessage, UUID actorUserId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task bulunamadı: " + taskId));
        TaskStatus previous = task.getStatus();
        task.transitionTo(newStatus);
        TaskLog savedLog = taskLogRepository.save(TaskLog.of(task.getTenantId(), taskId, "INFO",
                logMessage != null ? logMessage : ("Durum: " + previous + " -> " + newStatus)));
        taskLogPublisher.publish(savedLog.getId(), taskId, savedLog.getLevel(), savedLog.getMessage(),
                savedLog.getTimestamp(), newStatus.name());

        eventPublisher.publishEvent(new AuditEvent(
                task.getTenantId(), actorUserId, "TASK_STATUS_CHANGED", "Task", taskId,
                Map.of("from", previous.name(), "to", newStatus.name())));

        log.info("Task durum geçişi: taskId={}, {} -> {}", taskId, previous, newStatus);
        return task;
    }
    public TaskStatus getCurrentStatus(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task bulunamadı: " + taskId))
                .getStatus();
    }
    @Transactional
    public void assignAgent(UUID taskId, UUID agentConnectionId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task bulunamadı: " + taskId));
        task.assignAgent(agentConnectionId);
    }

    @Transactional
    public void assignRunner(UUID taskId, UUID runnerConnectionId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task bulunamadı: " + taskId));
        task.assignRunner(runnerConnectionId);
    }

    /**
     * Runner'ın bildirdiği araç adını kaydeder. Değer kullanıcı tarafından üretildiği için
     * tanınmayan bir isim gelebilir — bu durumda görevin sonucu düşürülmüyor, yalnızca
     * araç bilgisi boş bırakılıp uyarı loglanıyor.
     */
    @Transactional
    public void recordUsedAgent(UUID taskId, String usedAgentName) {
        if (usedAgentName == null || usedAgentName.isBlank()) {
            return;
        }
        UsedAgent parsed;
        try {
            parsed = UsedAgent.valueOf(usedAgentName.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Tanınmayan araç adı bildirildi, yok sayılıyor: taskId={}, usedAgent={}", taskId, usedAgentName);
            return;
        }
        taskRepository.findById(taskId).ifPresent(task -> task.recordUsedAgent(parsed));
    }

    /**
     * Bridge'den (runner'ın kendi terminal mesajları, "Kullanıcı X seçti" gibi) gelen tekil
     * LOG mesajlarını kalıcı hale getirir. Bu metodun @Transactional olması zorunlu —
     * TenantConnectionAspect RLS için gereken "SET LOCAL app.current_tenant_id" komutunu
     * yalnızca @Transactional işaretli bir dış çağrıya girerken tetikliyor.
     */
    @Transactional
    public void appendLog(UUID taskId, String level, String message) {
        TaskLog savedLog = taskLogRepository.save(TaskLog.of(TenantContext.get(), taskId,
                level != null ? level : "INFO", message));
        taskLogPublisher.publish(savedLog.getId(), taskId, savedLog.getLevel(), savedLog.getMessage(),
                savedLog.getTimestamp(), null);
    }
}