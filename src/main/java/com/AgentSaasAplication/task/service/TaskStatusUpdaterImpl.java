package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.task.TaskLogPublisher;
import com.AgentSaasAplication.common.task.TaskStatusUpdater;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.task.domain.TaskLog;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.repository.TaskLogRepository;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TaskStatusUpdaterImpl implements TaskStatusUpdater {

    private final TaskStateService taskStateService;
    private final TaskLogRepository taskLogRepository;
    private final TaskLogPublisher taskLogPublisher;

    public TaskStatusUpdaterImpl(TaskStateService taskStateService, TaskLogRepository taskLogRepository,
                                  TaskLogPublisher taskLogPublisher) {
        this.taskStateService = taskStateService;
        this.taskLogRepository = taskLogRepository;
        this.taskLogPublisher = taskLogPublisher;
    }

    @Override
    public void markRunning(UUID taskId, String message) {
        taskStateService.transition(taskId, TaskStatus.RUNNING, message, null);
    }

    @Override
    public void markCompleted(UUID taskId, String message) {
        taskStateService.transition(taskId, TaskStatus.COMPLETED, message, null);
    }

    @Override
    public void markFailed(UUID taskId, String message) {
        taskStateService.transition(taskId, TaskStatus.FAILED, message, null);
    }
    @Transactional
    @Override
    public void logInfo(UUID taskId, String message) {
        TaskLog savedLog = taskLogRepository.save(TaskLog.of(TenantContext.get(), taskId, "INFO", message));
        taskLogPublisher.publish(savedLog.getId(), taskId, savedLog.getLevel(), savedLog.getMessage(),
                savedLog.getTimestamp(), null);
    }
}