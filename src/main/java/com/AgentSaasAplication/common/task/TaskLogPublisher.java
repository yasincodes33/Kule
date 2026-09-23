package com.AgentSaasAplication.common.task;

import java.time.Instant;
import java.util.UUID;

/**
 * task modülünden gateway'e bağımlılık olmadan canlı log push'u için (Dependency Inversion —
 * BridgeMessageSender/TaskStatusUpdater ile aynı desen). Gerçek uygulaması gateway modülünde,
 * o taskId'ye abone WebSocket oturumlarına anlık gönderim yapıyor. `newStatus` yalnızca bu
 * log bir durum geçişinin yan etkisiyse doldurulur (bkz. TaskStateService.transition), aksi
 * halde null.
 */
public interface TaskLogPublisher {
    void publish(UUID logId, UUID taskId, String level, String message, Instant timestamp, String newStatus);
}
