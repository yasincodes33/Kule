package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.cluster.RedisRelay;
import com.AgentSaasAplication.common.task.TaskLogPublisher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * TaskLog yazan her yer (TaskStateService.transition/appendLog, TaskStatusUpdaterImpl.logInfo)
 * kaydı DB'ye yazdıktan hemen sonra bunu çağırıyor.
 *
 * Çoklu-instance: bir task'ı izleyen tarayıcı, DB yazımını tetikleyen HTTP/WS
 * isteğinden FARKLI bir backend instance'ına bağlı olabilir. Bu yüzden `publish()` artık
 * doğrudan yerel oturumlara yazmıyor — olayı Redis'e (`tasklog:push` kanalı) yayınlıyor;
 * TÜM instance'lar (yayınlayan dahil) bunu dinliyor ve YALNIZCA kendi yerel
 * `TaskLogSessionRegistry`'sinde o taskId'ye abone biri varsa iletiyor, yoksa sessizce
 * yok sayıyor. Redis yoksa/erişilemezse `RedisRelay.publish()` hatayı loglayıp yutar —
 * canlı log push'u devre dışı kalır ama backend'in geri kalanı etkilenmez.
 */
@Slf4j
@Component
public class TaskLogPublisherImpl implements TaskLogPublisher {

    private static final String CHANNEL = "tasklog:push";

    private final TaskLogSessionRegistry sessionRegistry;
    private final RedisRelay redisRelay;
    private final ObjectMapper objectMapper;

    public TaskLogPublisherImpl(TaskLogSessionRegistry sessionRegistry, RedisRelay redisRelay,
                                 ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.redisRelay = redisRelay;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void subscribe() {
        redisRelay.subscribe(CHANNEL, TaskLogPushMessage.class, this::deliverLocally);
    }

    @Override
    public void publish(UUID logId, UUID taskId, String level, String message, Instant timestamp, String newStatus) {
        redisRelay.publish(CHANNEL, new TaskLogPushMessage(logId, taskId, level, message, timestamp, newStatus));
    }

    private void deliverLocally(TaskLogPushMessage push) {
        Collection<WebSocketSession> sessions = sessionRegistry.get(push.taskId());
        if (sessions.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(push);
        } catch (Exception e) {
            log.error("Task log push mesajı serileştirilemedi: taskId={}", push.taskId(), e);
            return;
        }

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                log.warn("Task log push gönderilemedi: taskId={}, sessionId={}, hata={}",
                        push.taskId(), session.getId(), e.getMessage());
            }
        }
    }

    public record TaskLogPushMessage(UUID id, UUID taskId, String level, String message,
                                      Instant timestamp, String status) {}
}
