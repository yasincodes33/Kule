package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.runner.service.RunnerConnectionService;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.repository.TaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gerçek bir uçtan uca testte bulundu: bir runner, kendisine DISPATCHED edilmiş bir
 * görevi hiç yanıtlamadan bağlantısını koparırsa (çöker, ağ kesilir) — AgentBridgeHandler
 * yalnızca runner'ı OFFLINE işaretliyor, task'a hiç dokunmuyor. Task sonsuza dek
 * DISPATCHED/RUNNING'de asılı kalıyor; POST /tasks/{id}/retry da yalnızca FAILED'ten çalıştığı
 * için hiçbir kurtarma yolu yok. Bu servis + StaleDispatchScheduler o boşluğu kapatıyor.
 *
 * Yalnızca "runner'ı offline" olan task'lar başarısız sayılıyor — runner hâlâ online ama görev
 * gerçekten uzun sürüyorsa (gerçek bir CLI ajanı dakikalarca çalışabilir) yanlış pozitif
 * üretilmemesi için dokunulmuyor.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class StaleDispatchService {

    private final TaskRepository taskRepository;
    private final TaskStateService taskStateService;
    private final RunnerConnectionService runnerConnectionService;

    public StaleDispatchService(TaskRepository taskRepository, TaskStateService taskStateService,
                                 RunnerConnectionService runnerConnectionService) {
        this.taskRepository = taskRepository;
        this.taskStateService = taskStateService;
        this.runnerConnectionService = runnerConnectionService;
    }

    public List<UUID> findStaleDispatchIds(Instant updatedBefore) {
        return taskRepository
                .findByStatusInAndUpdatedAtBefore(List.of(TaskStatus.DISPATCHED, TaskStatus.RUNNING), updatedBefore)
                .stream()
                .filter(this::runnerIsGone)
                .map(Task::getId)
                .toList();
    }

    private boolean runnerIsGone(Task task) {
        if (task.getRunnerConnectionId() == null) {
            return false;
        }
        try {
            return !runnerConnectionService.getRunnerConnection(task.getRunnerConnectionId()).isOnline();
        } catch (NotFoundException e) {
            return true; // runner kaydı tamamen silinmiş — kesinlikle asılı kalmış sayılır
        }
    }

    @Transactional
    public void failStaleDispatch(UUID taskId) {
        taskStateService.transition(taskId, TaskStatus.FAILED,
                "Runner bağlantısı koptu ve görev çok uzun süredir ilerlemedi (zaman aşımı) — "
                        + "/tasks/{id}/retry ile yeniden denenebilir", null);
    }
}
