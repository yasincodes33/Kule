package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.cluster.RedisRelay;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TaskLogSessionRegistry (tarayıcı WS oturumlarını tutma) + BridgeSessionRegistry'nin (çoklu-
 * instance relay) birleşimi: bir terminal oturumunu izleyen tarayıcı WebSocket'i JVM-yerel
 * bellekte tutuluyor, ama runner'dan gelen TERMINAL_OUTPUT/TERMINAL_CLOSED HANGİ instance'a
 * düşerse düşsün (runner'ın bridge bağlantısı farklı bir instance'ta olabilir) doğru tarayıcıya
 * ulaşsın diye RedisRelay üzerinden TÜM instance'lara yayınlanıyor — yalnızca ilgili
 * terminalSessionId'yi yerelinde tutan instance gerçekten iletiyor (PendingToolCallRegistry'yle
 * birebir aynı desen).
 */
@Slf4j
@Component
public class RunnerTerminalSessionRegistry {

    private static final String CHANNEL = "terminal:output";
    // Faz B: terminalSessionId -> taskId eşlemesi. Bağlantıyı kuran instance ile runner'ın
    // TERMINAL_OUTPUT'unu işleyen instance FARKLI olabileceği için (tıpkı BridgeSessionRegistry'nin
    // runner routing tablosu gibi) bu Redis'te tutuluyor, yerel bellekte DEĞİL.
    private static final String TASK_LINK_KEY_PREFIX = "terminal:task-link:";
    // Bir terminal oturumunun makul azami ömrü — bağlantı bundan uzun sürerse link'in süresi
    // dolar ve TERMINAL_CLOSED'da biriken çıktı hiçbir task'a yazılmaz (sessizce, veri kaybı
    // olmadan — yalnızca kalıcı loglama atlanır). Oturumun approve TTL'inden (15dk) kasıtlı
    // olarak daha uzun.
    private static final Duration TASK_LINK_TTL = Duration.ofHours(6);

    private final Map<UUID, WebSocketSession> browserSessions = new ConcurrentHashMap<>();
    private final RedisRelay redisRelay;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public RunnerTerminalSessionRegistry(RedisRelay redisRelay, ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.redisRelay = redisRelay;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void subscribe() {
        redisRelay.subscribe(CHANNEL, RelayEvent.class, this::deliverLocally);
    }

    // Bir shell oturumundan saniyede onlarca TERMINAL_OUTPUT parçası gelebilir; her biri
    // RedisRelay'in dinleyici thread'lerinden (birden fazla eşzamanlı olabilir, bkz. sınıf
    // Javadoc'u) deliverLocally()'ye düşüyor. Ham WebSocketSession.sendMessage() eşzamanlı
    // çağrıya karşı GÜVENLİ DEĞİL (Spring Javadoc'u da bunu açıkça söylüyor) — iki thread aynı
    // anda yazarsa Tomcat'in durum makinesi "TEXT_PARTIAL_WRITING" IllegalStateException'ı
    // fırlatıyor ve o mesaj tarayıcıya hiç ulaşmıyor (canlı çıktı runner'da görünüp webde
    // görünmeme raporunun kök nedeni buydu). Decorator eşzamanlı gönderimleri sıraya koyup
    // tek tek yolluyor.
    private static final int SEND_TIME_LIMIT_MS = 15_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    public void register(UUID terminalSessionId, WebSocketSession browserSession) {
        browserSessions.put(terminalSessionId,
                new ConcurrentWebSocketSessionDecorator(browserSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES));
    }

    public void remove(UUID terminalSessionId) {
        browserSessions.remove(terminalSessionId);
    }

    /** RunnerTerminalWsHandler'ın bağlantı kurulunca (taskId doluysa) çağırdığı yer — bkz. Faz B. */
    public void linkTask(UUID terminalSessionId, UUID taskId) {
        redisTemplate.opsForValue().set(TASK_LINK_KEY_PREFIX + terminalSessionId, taskId.toString(), TASK_LINK_TTL);
    }

    /** AgentBridgeHandler'ın TERMINAL_CLOSED'da "bu oturumun çıktısı bir göreve mi ait" diye
     * sorduğu yer. Bulunamazsa (link hiç kurulmadıysa ya da TTL doldıysa) boş döner — biriken
     * çıktı sessizce atılır, hata fırlatılmaz. */
    public Optional<UUID> getLinkedTaskId(UUID terminalSessionId) {
        String value = redisTemplate.opsForValue().get(TASK_LINK_KEY_PREFIX + terminalSessionId);
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    /** Oturum kapanınca link'in kendisine artık gerek yok — temizlik. */
    public void unlinkTask(UUID terminalSessionId) {
        redisTemplate.delete(TASK_LINK_KEY_PREFIX + terminalSessionId);
    }

    /** AgentBridgeHandler'ın runner'dan gelen TERMINAL_OUTPUT/TERMINAL_CLOSED'ı ilgili tarayıcı
     * WS bağlantısına iletmek için çağırdığı yer. */
    public void deliverToBrowser(UUID terminalSessionId, String type, String data) {
        redisRelay.publish(CHANNEL, new RelayEvent(terminalSessionId, type, data));
    }

    private void deliverLocally(RelayEvent event) {
        WebSocketSession session = browserSessions.get(event.terminalSessionId());
        if (session == null || !session.isOpen()) {
            return; // bu terminal oturumu bu instance'ta değil — sessizce yok say
        }
        try {
            String json = objectMapper.writeValueAsString(
                    Map.of("type", event.type(), "data", event.data() == null ? "" : event.data()));
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Terminal çıktısı tarayıcıya iletilemedi: terminalSessionId={}", event.terminalSessionId(), e);
        }
    }

    public record RelayEvent(UUID terminalSessionId, String type, String data) {}
}
