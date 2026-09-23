package com.AgentSaasAplication.task.repository;

import com.AgentSaasAplication.task.domain.TaskLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskLogRepository extends JpaRepository<TaskLog, UUID> {

    List<TaskLog> findByTaskIdOrderByTimestampAsc(UUID taskId);
}