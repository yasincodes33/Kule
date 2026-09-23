package com.AgentSaasAplication.task.dto;

import com.AgentSaasAplication.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Web üzerinden bir görevi tamamlama isteği — runner'ın bridge üzerinden gönderdiği TASK_RESULT
 * mesajının web karşılığı (bkz. TaskOrchestrationService.completeTask, iki yol da aynı metodu
 * paylaşıyor). `usedAgent` bilinçli olarak serbest bir string — recordUsedAgent() zaten
 * tanınmayan değerleri sessizce yok sayıyor, DTO'da sıkı bir enum validasyonu bridge yoluyla
 * gelenle tutarsız bir davranışa yol açardı.
 */
public record CompleteTaskRequest(
        @NotNull TaskStatus status,
        String message,
        String usedAgent
) {
}
