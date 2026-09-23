package com.AgentSaasAplication.gateway;

import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * WebSocket handshake'leri için tek kullanımlık, kısa ömürlü (60 sn) bilet üretir. Asıl
 * (uzun ömürlü) access token'ın `?token=...` query param'ında taşınması; tarayıcı
 * geçmişi, network log'u ya da ters proxy access log'u gibi yerlerde tam yetkili bir
 * kimlik bilgisinin sızmasına açık bir yüzey olurdu. Bilet, normal
 * (Bearer + X-Organization-Id ile doğrulanmış) bir HTTP isteğiyle alınır, hemen ardından
 * WS handshake'inde tüketilip silinir; sızsa bile ya süresi geçmiş ya da zaten tüketilmiş
 * olur.
 *
 * Redis kullanılıyor (bellek-içi bir registry değil) çünkü bilet HTTP isteğini karşılayan
 * instance ile WS handshake'ini karşılayan instance FARKLI olabilir (bkz. rate limiter/registry'
 * lerin aynı gerekçesi).
 */
@Component
public class WsTicketService {

    private static final String KEY_PREFIX = "ws-ticket:";
    // Runner terminal biletleri ayrı bir Redis anahtar önekinde tutuluyor — bu, bir task-log
    // biletinin (yanlışlıkla veya kötü niyetle) bir terminal handshake'inde tüketilmesini,
    // "purpose" alanını kontrol etmeye gerek kalmadan, anahtar uzayı seviyesinde imkansız kılıyor.
    private static final String TERMINAL_KEY_PREFIX = "ws-ticket:terminal:";
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String DELIMITER = "|";

    // Atomik get-and-delete için Lua script (EVAL) kullanılıyor: GETDEL yalnızca Redis
    // 6.2+ ile geliyor, GET + DEL ise atomik değil (iki isteğin aynı bileti tüketmesi
    // mümkün kalır). Bu script Redis 2.6+ ile çalışır.
    private static final String GET_AND_DELETE_SCRIPT =
            "local v = redis.call('GET', KEYS[1]); if v then redis.call('DEL', KEYS[1]) end; return v";

    private final StringRedisTemplate redisTemplate;

    public WsTicketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String issueTicket(UUID userId, UUID organizationId, UUID taskId) {
        String ticket = UUID.randomUUID().toString();
        String value = userId + DELIMITER + organizationId + DELIMITER + taskId;
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, value, TTL);
        return ticket;
    }

    /** Bileti TÜKETİR (bulunduysa hemen siler) — aynı bilet iki kez kullanılamaz. */
    public Optional<TaskLogTicket> consume(String ticket) {
        String value = getAndDelete(KEY_PREFIX + ticket);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\" + DELIMITER);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TaskLogTicket(UUID.fromString(parts[0]), UUID.fromString(parts[1]), UUID.fromString(parts[2])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record TaskLogTicket(UUID userId, UUID organizationId, UUID taskId) {
    }

    /** issueTicket/consume'in runner-terminal WS handshake'i için aynı atomik tek-kullanımlık
     * bilet mekanizmasını kullanan karşılığı — bkz. RunnerTerminalSessionController/
     * RunnerTerminalAuthInterceptor. Ayrı anahtar önekiyle (TERMINAL_KEY_PREFIX) tutulur.
     * `taskId` nullable — yalnızca görev detay sayfasından "bu araçla başlat" ile açılan
     * oturumlarda dolu (bkz. RunnerTerminalSessionRegistry.linkTask — çıktının hangi görevin
     * log akışına yazılacağını belirlemek için), RunnersPage'deki genel amaçlı terminal
     * erişiminde her zaman null. */
    public String issueTerminalTicket(UUID userId, UUID organizationId, UUID runnerConnectionId, UUID taskId) {
        String ticket = UUID.randomUUID().toString();
        String value = userId + DELIMITER + organizationId + DELIMITER + runnerConnectionId + DELIMITER + taskId;
        redisTemplate.opsForValue().set(TERMINAL_KEY_PREFIX + ticket, value, TTL);
        return ticket;
    }

    public Optional<TerminalTicket> consumeTerminalTicket(String ticket) {
        String value = getAndDelete(TERMINAL_KEY_PREFIX + ticket);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\\" + DELIMITER, -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            UUID taskId = "null".equals(parts[3]) ? null : UUID.fromString(parts[3]);
            return Optional.of(new TerminalTicket(UUID.fromString(parts[0]), UUID.fromString(parts[1]), UUID.fromString(parts[2]), taskId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record TerminalTicket(UUID userId, UUID organizationId, UUID runnerConnectionId, UUID taskId) {
    }

    private String getAndDelete(String key) {
        return redisTemplate.execute((RedisCallback<String>) connection -> {
            byte[] result = connection.scriptingCommands().eval(
                    GET_AND_DELETE_SCRIPT.getBytes(StandardCharsets.UTF_8), ReturnType.VALUE, 1, key.getBytes(StandardCharsets.UTF_8));
            return result == null ? null : new String(result, StandardCharsets.UTF_8);
        });
    }
}
