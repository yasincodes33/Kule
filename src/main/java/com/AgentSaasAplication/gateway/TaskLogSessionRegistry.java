package com.AgentSaasAplication.gateway;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bir taskId'yi canlı izleyen (birden fazla olabilir — aynı görevi birden fazla sekme/kullanıcı
 * açabilir) aktif WebSocket oturumlarını bellekte tutar. Tek-instance deployment varsayımıyla
 * yazıldı (bkz. BridgeSessionRegistry).
 *
 * Oturumlar {@link ConcurrentWebSocketSessionDecorator} ile SARILARAK tutuluyor — aynı task'ı
 * izleyen bir sekmeye art arda gelen log push'ları RedisRelay'in (birden fazla olabilen)
 * dinleyici thread'lerinden eşzamanlı deliverLocally() çağrısına yol açabilir; ham
 * WebSocketSession.sendMessage() eşzamanlı çağrıya karşı güvenli değil (bkz.
 * RunnerTerminalSessionRegistry'deki aynı sınıftan kök neden). Session kimliği (getId()),
 * sarmalamadan ETKİLENMEYEN sabit bir değer olduğu için map anahtarı olarak KULLANILIYOR —
 * register/remove farklı WebSocketSession referanslarıyla (ham vs. sarmalanmış) çağrılsa bile
 * doğru kaydı bulur.
 */
@Component
public class TaskLogSessionRegistry {

    private static final int SEND_TIME_LIMIT_MS = 15_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final Map<UUID, Map<String, WebSocketSession>> sessionsByTask = new ConcurrentHashMap<>();

    public void register(UUID taskId, WebSocketSession session) {
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        sessionsByTask.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>()).put(session.getId(), decorated);
    }

    public void remove(UUID taskId, WebSocketSession session) {
        sessionsByTask.computeIfPresent(taskId, (id, sessions) -> {
            sessions.remove(session.getId());
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public Collection<WebSocketSession> get(UUID taskId) {
        return sessionsByTask.getOrDefault(taskId, Map.of()).values();
    }
}
