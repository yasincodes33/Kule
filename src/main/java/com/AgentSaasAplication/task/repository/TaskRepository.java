package com.AgentSaasAplication.task.repository;

import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Page<Task> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Task> findByProjectId(UUID projectId, Pageable pageable);

    List<Task> findByProjectIdAndStatus(UUID projectId, TaskStatus status);

    Page<Task> findByProjectIdAndStatus(UUID projectId, TaskStatus status, Pageable pageable);

    List<Task> findByAgentConnectionId(UUID agentConnectionId);

    List<Task> findByRunnerConnectionId(UUID runnerConnectionId);

	long countByAgentConnectionIdAndStatusIn(UUID agentConnectionId, List<TaskStatus> of);

	long countByRunnerConnectionIdAndStatusIn(UUID runnerConnectionId, List<TaskStatus> statuses);

	// Runner koparsa/yanıt vermezse DISPATCHED/RUNNING'de sonsuza dek asılı kalan
	// task'ları bulmak için — bkz. StaleDispatchService.
	List<Task> findByStatusInAndUpdatedAtBefore(List<TaskStatus> statuses, Instant threshold);
}