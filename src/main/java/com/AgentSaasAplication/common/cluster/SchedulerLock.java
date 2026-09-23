package com.AgentSaasAplication.common.cluster;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Dört `@Scheduled` sweep'in (StaleDispatchScheduler,
 * ApprovalExpirationScheduler, RunnerHealthScheduler, TaskCreatedEventOutboxScheduler) hiçbiri
 * dağıtık bir kilit kullanmıyordu — uygulama 2+ instance ile çalıştırılırsa HER instance HER
 * tick'te aynı sweep'i eşzamanlı çalıştırır. En somut risk: TaskCreatedEventOutboxScheduler'da
 * iki instance aynı `publishedAt IS NULL` satırını okuyup ikisi de Kafka'ya yayınlayabilir
 * (outbox entity'sinde `@Version`/optimistic locking yok) — aynı TaskCreatedEvent'in iki kez
 * teslim edilmesi, ApprovalExpirationService'te de aynı onayın iki kez expire edilmeye
 * çalışılması (fazladan audit satırı).
 *
 * Basit bir "bu tick'i kim yürütecek" kilidi: Redis `SET NX PX` (setIfAbsent+TTL) atomik olduğu
 * için tam olarak bir instance kilidi alır, diğerleri o tick'i sessizce atlar. TTL, zamanlanmış
 * aralıktan kısa tutulmalı ki bir instance çökse bile kilit doğal olarak süresi dolup bir
 * sonraki tick'te (hangi instance olursa olsun) devralınabilsin — yani "kilidi serbest bırakmayı
 * unutan çökmüş bir instance" sistemi kalıcı olarak kilitlemiyor.
 */
@Component
public class SchedulerLock {

    private static final String KEY_PREFIX = "scheduler-lock:";

    private final StringRedisTemplate redisTemplate;

    public SchedulerLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Bu tick için kilidi almaya çalışır. Redis erişilemezse fail-OPEN döner (true) — rate
     * limiter'la aynı gerekçe: bir koordinasyon mekanizmasının kendisi, tek-instance'lık en yaygın
     * dağıtım şeklinde sweep'lerin hiç çalışmamasına neden olan bir tek-nokta-arıza olmamalı.
     */
    public boolean tryAcquire(String lockName, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + lockName, "1", ttl));
        } catch (Exception e) {
            return true;
        }
    }
}
