package com.AgentSaasAplication.task.domain;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.domain.UsedAgent;
import com.AgentSaasAplication.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tasks")
public class Task extends TenantScopedEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "agent_connection_id")
    private UUID agentConnectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false)
    private String title;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Version
    private Long version;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    /** Görevi fiilen çalıştıran kullanıcı runner'ı. */
    @Column(name = "runner_connection_id")
    private UUID runnerConnectionId;

    /** Kullanıcının bitirirken bildirdiği araç. Opsiyonel — bildirilmezse null kalır. */
    @Enumerated(EnumType.STRING)
    @Column(name = "used_agent")
    private UsedAgent usedAgent;

    /** Modele/araca gönderilecek prompt metni — başlıktan (kısa etiket) bilinçli olarak ayrı,
     * kalıcı ve düzenlenebilir. Boşsa çağıran taraf (frontend) başlığı prompt yerine kullanır. */
    @Column(name = "prompt")
    private String prompt;

    private Task(UUID organizationId, UUID actorUserId, UUID projectId, TaskType type, String title, UUID assignedUserId) {
        super(organizationId);
        this.projectId = projectId;
        this.type = type;
        this.title = title;
        this.status = TaskStatus.QUEUED;
        this.retryCount = 0;
        this.assignedUserId = assignedUserId;
        this.assignCreatedBy(actorUserId);
    }

    public static Task create(UUID organizationId, UUID actorUserId, UUID projectId, TaskType type, String title, UUID assignedUserId) {
        return new Task(organizationId, actorUserId, projectId, type, title, assignedUserId);
    }

    public void transitionTo(TaskStatus newStatus) {
        if (!TaskStateMachine.canTransition(this.status, newStatus)) {
            throw new IllegalStateException(
                    "Geçersiz task durum geçişi: " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
    }

    public void assignAgent(UUID agentConnectionId) {
        this.agentConnectionId = agentConnectionId;
    }

    public void assignRunner(UUID runnerConnectionId) {
        this.runnerConnectionId = runnerConnectionId;
    }

    public void recordUsedAgent(UsedAgent usedAgent) {
        this.usedAgent = usedAgent;
    }

    public void updatePrompt(String prompt) {
        this.prompt = prompt;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean isTerminal() {
        return TaskStateMachine.isTerminal(this.status);
    }
}