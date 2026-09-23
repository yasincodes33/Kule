package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.cluster.InstanceId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aktif runner bridge oturumlarını bellekte tutar (tek-instance varsayımıyla yazılmıştı).
 *
 * Çoklu-instance: gerçek `WebSocketSession` nesnesi doğası gereği yalnızca bağlantının
 * fiziksel olarak kurulduğu instance'ta yaşayabilir — Redis'e "konamaz". Bunun yerine burada
 * bir YÖNLENDİRME TABLOSU tutuluyor: `bridge:location:{runnerConnectionId} -> instanceId`
 * (TTL'li, heartbeat'le tazeleniyor — bkz. AgentBridgeHandler'ın HEARTBEAT işleyicisi). Bir
 * başka instance bu runner'a mesaj göndermek istediğinde (BridgeMessageSenderImpl), önce yerel
 * belleğe bakıyor, yoksa bu tabloya bakıp doğru instance'ın relay kanalına yönlendiriyor.
 * TTL süresi StaleDispatchScheduler'ın eşiğiyle (120sn) bilinçli olarak tutarlı tutuldu — bir
 * instance çökerse kayıt kendiliğinden düşer, sonsuza dek yanlış yönlendirme olmaz.
 */
@Component
public class BridgeSessionRegistry {

    private static final Duration ROUTING_TTL = Duration.ofSeconds(120);
    private static final String KEY_PREFIX = "bridge:location:";

    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final InstanceId instanceId;

    public BridgeSessionRegistry(StringRedisTemplate redisTemplate, InstanceId instanceId) {
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceId;
    }

    public void register(UUID runnerConnectionId, WebSocketSession session) {
        sessions.put(runnerConnectionId, session);
        redisTemplate.opsForValue().set(routingKey(runnerConnectionId), instanceId.value(), ROUTING_TTL);
    }

    public void remove(UUID runnerConnectionId) {
        sessions.remove(runnerConnectionId);
        // Yalnızca hâlâ BU instance'ı gösteriyorsa sil — aksi halde, runner aynı anda başka bir
        // instance'a yeniden bağlanmışsa (reconnect yarışı) onun az önce yazdığı kaydı silme riski var.
        String key = routingKey(runnerConnectionId);
        String current = redisTemplate.opsForValue().get(key);
        if (instanceId.value().equals(current)) {
            redisTemplate.delete(key);
        }
    }

    public Optional<WebSocketSession> get(UUID runnerConnectionId) {
        return Optional.ofNullable(sessions.get(runnerConnectionId));
    }

    public boolean isConnected(UUID runnerConnectionId) {
        WebSocketSession session = sessions.get(runnerConnectionId);
        if (session != null && session.isOpen()) {
            return true;
        }
        // Yerelde yoksa başka bir instance'a bağlı olabilir — yönlendirme tablosuna bak.
        return Boolean.TRUE.equals(redisTemplate.hasKey(routingKey(runnerConnectionId)));
    }

    /** Bu runner şu an hangi instance'a bağlı (varsa) — BridgeMessageSenderImpl'ın çapraz-instance yönlendirmesi için. */
    public Optional<String> locateInstance(UUID runnerConnectionId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(routingKey(runnerConnectionId)));
    }

    /** Heartbeat geldiğinde TTL'i tazeler — bkz. AgentBridgeHandler HEARTBEAT case'i. */
    public void refreshRouting(UUID runnerConnectionId) {
        if (sessions.containsKey(runnerConnectionId)) {
            redisTemplate.expire(routingKey(runnerConnectionId), ROUTING_TTL);
        }
    }

    private String routingKey(UUID runnerConnectionId) {
        return KEY_PREFIX + runnerConnectionId;
    }
}
