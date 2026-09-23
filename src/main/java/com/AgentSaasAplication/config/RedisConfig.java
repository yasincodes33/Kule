package com.AgentSaasAplication.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Çoklu-instance koordinasyonu için Redis Pub/Sub altyapısı. `StringRedisTemplate`/
 * `RedisConnectionFactory` Spring Boot'un kendi autoconfig'inden geliyor (spring-boot-starter-
 * data-redis + application.yml'deki spring.data.redis.host/port) — burada yalnızca dinleme
 * (subscribe) tarafı için gereken, otomatik yapılandırılmayan tek parça: RedisMessageListenerContainer.
 *
 * Kullanım deseni (dört registry'de de aynı — bkz. common.cluster.RedisRelay): bir instance
 * WebSocketSession/CompletableFuture gibi JVM-yerel bir nesneyi Redis'e KOYAMAZ (serileştirilemez,
 * anlamsız olur) — bunun yerine ilgili olay (TOOL_RESULT geldi, onay karara bağlandı, yeni bir
 * TaskLog yazıldı) TÜM instance'lara Redis Pub/Sub ile yayınlanır, her instance SADECE kendi
 * yerel kaydında (bu id için bekleyen biri varsa) tamamlar/iletir — diğerleri sessizce yok sayar.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
