package com.AgentSaasAplication.common.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Dört bellek-içi registry'nin (BridgeSessionRegistry, PendingToolCallRegistry,
 * TaskLogSessionRegistry, PendingApprovalRegistry) ortak çoklu-instance deseni: bir instance'ta
 * olan bir olay (TOOL_RESULT geldi, onay karara bağlandı, yeni TaskLog yazıldı) TÜM instance'lara
 * yayınlanır; her instance SADECE kendi yerel belleğinde o id için bekleyen biri varsa
 * tamamlar/iletir, yoksa sessizce yok sayar. WebSocketSession/CompletableFuture gibi JVM-yerel
 * nesneler asla Redis'e konmuyor — yalnızca "böyle bir şey oldu" bildirimi.
 */
@Slf4j
@Component
public class RedisRelay {

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    public RedisRelay(StringRedisTemplate redisTemplate, RedisMessageListenerContainer listenerContainer,
                       ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
    }

    public void publish(String channel, Object payload) {
        try {
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Redis'e yayın başarısız: channel={}", channel, e);
        }
    }

    /**
     * Bu instance'ı bir kanala abone eder. `handler` HER instance'ta (yayınlayan dahil) çağrılır
     * — çağıran taraf kendi yerel kaydında ilgili id yoksa sessizce hiçbir şey yapmamalı.
     */
    public <T> void subscribe(String channel, Class<T> type, Consumer<T> handler) {
        MessageListener listener = (Message message, byte[] pattern) -> {
            try {
                String json = new String(message.getBody(), StandardCharsets.UTF_8);
                T payload = objectMapper.readValue(json, type);
                handler.accept(payload);
            } catch (Exception e) {
                log.error("Redis mesajı işlenemedi: channel={}", channel, e);
            }
        };
        listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
    }
}
