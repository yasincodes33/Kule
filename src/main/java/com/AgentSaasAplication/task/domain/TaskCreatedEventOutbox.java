package com.AgentSaasAplication.task.domain;

import com.AgentSaasAplication.common.domain.ModelTier;
import com.AgentSaasAplication.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox — TaskCreatedEvent'in Kafka'ya en-az-bir-kez teslimini
 * garanti eder. Task.create()/retryTask() ile AYNI transaction'da yazılır; Kafka'ya yayın
 * başarısız olsa bile bu satır kalıcıdır, TaskCreatedEventOutboxScheduler broker tekrar
 * erişilebilir olana kadar periyodik olarak yeniden dener.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "task_created_event_outbox")
public class TaskCreatedEventOutbox extends TenantScopedEntity {

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "preferred_agent_connection_id")
    private UUID preferredAgentConnectionId;

    @Column(name = "preferred_runner_connection_id")
    private UUID preferredRunnerConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_model_tier")
    private ModelTier preferredModelTier;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    private TaskCreatedEventOutbox(UUID organizationId, UUID taskId, UUID preferredAgentConnectionId,
                                    UUID preferredRunnerConnectionId, ModelTier preferredModelTier) {
        super(organizationId);
        this.taskId = taskId;
        this.preferredAgentConnectionId = preferredAgentConnectionId;
        this.preferredRunnerConnectionId = preferredRunnerConnectionId;
        this.preferredModelTier = preferredModelTier;
        this.attemptCount = 0;
    }

    public static TaskCreatedEventOutbox create(UUID organizationId, UUID taskId, UUID preferredAgentConnectionId,
                                                  UUID preferredRunnerConnectionId, ModelTier preferredModelTier) {
        return new TaskCreatedEventOutbox(
                organizationId, taskId, preferredAgentConnectionId, preferredRunnerConnectionId, preferredModelTier);
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void recordAttempt() {
        this.attemptCount++;
    }
}
