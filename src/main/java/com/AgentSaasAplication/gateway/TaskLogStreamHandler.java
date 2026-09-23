package com.AgentSaasAplication.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

/**
 * `/ws/tasks/{taskId}/logs` — yalnızca sunucudan istemciye tek yönlü push kanalı (frontend'in
 * o task'ın canlı log akışını izlemesi için). Kimlik/yetki doğrulaması tamamen
 * {@link TaskLogStreamAuthInterceptor}'da yapılıyor; bu sınıf yalnızca handshake sırasında
 * oraya konan `taskId` attribute'unu okuyup oturumu {@link TaskLogSessionRegistry}'ye
 * kaydediyor/çıkarıyor. Gerçek push, TaskLog yazan servislerin (TaskStateService,
 * TaskStatusUpdaterImpl) doğrudan çağırdığı {@code TaskLogPublisherImpl} üzerinden olur —
 * bu sınıf DB/tenant context'e hiç dokunmuyor.
 */
@Slf4j
@Component
public class TaskLogStreamHandler extends TextWebSocketHandler {

    private final TaskLogSessionRegistry sessionRegistry;

    public TaskLogStreamHandler(TaskLogSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID taskId = taskId(session);
        sessionRegistry.register(taskId, session);
        log.info("Task log stream bağlandı: taskId={}, sessionId={}", taskId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID taskId = taskId(session);
        sessionRegistry.remove(taskId, session);
        log.info("Task log stream kapandı: taskId={}, sessionId={}, status={}", taskId, session.getId(), status);
    }

    private UUID taskId(WebSocketSession session) {
        return (UUID) session.getAttributes().get("taskId");
    }
}
