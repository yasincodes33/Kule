package com.AgentSaasAplication.task.domain;

import com.AgentSaasAplication.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "task_logs")
public class TaskLog extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    private TaskLog(UUID organizationId, UUID taskId, String level, String message) {
        this.organizationId = organizationId;
        this.taskId = taskId;
        this.level = level;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public static TaskLog of(UUID organizationId, UUID taskId, String level, String message) {
        return new TaskLog(organizationId, taskId, level, message);
    }
}