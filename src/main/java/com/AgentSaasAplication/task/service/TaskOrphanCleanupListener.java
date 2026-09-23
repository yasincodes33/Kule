package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
public class TaskOrphanCleanupListener {

    private final TaskRepository taskRepository;
    private final TaskStateService taskStateService;

    public TaskOrphanCleanupListener(TaskRepository taskRepository, TaskStateService taskStateService) {
        this.taskRepository = taskRepository;
        this.taskStateService = taskStateService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(AuditEvent event) {
        if (!"RUNNER_MARKED_STALE".equals(event.action())) {
            return;
        }

        TenantContext.set(event.organizationId());
        try {
            List<Task> orphaned = taskRepository.findByRunnerConnectionId(event.entityId()).stream()
                    .filter(t -> t.getStatus() == TaskStatus.DISPATCHED || t.getStatus() == TaskStatus.RUNNING)
                    .toList();

            for (Task task : orphaned) {
                taskStateService.transition(task.getId(), TaskStatus.FAILED,
                        "Runner bağlantısı kayboldu (heartbeat zaman aşımı)", null);
            }

            if (!orphaned.isEmpty()) {
                log.info("Stale runner nedeniyle {} task FAILED'e çekildi: runnerId={}",
                        orphaned.size(), event.entityId());
            }
        } catch (Exception e) {
            log.error("Orphan task temizliği başarısız: runnerId={}", event.entityId(), e);
        } finally {
            TenantContext.clear();
        }
    }
}