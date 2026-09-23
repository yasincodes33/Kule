package com.AgentSaasAplication.runner.domain;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "runner_capabilities")
public class RunnerCapability extends TenantScopedEntity {

    @Column(name = "runner_connection_id", nullable = false)
    private UUID runnerConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    private RunnerCapability(UUID organizationId, UUID runnerConnectionId, TaskType taskType) {
        super(organizationId);
        this.runnerConnectionId = runnerConnectionId;
        this.taskType = taskType;
    }

    public static RunnerCapability create(UUID organizationId, UUID runnerConnectionId, TaskType taskType) {
        return new RunnerCapability(organizationId, runnerConnectionId, taskType);
    }
}
