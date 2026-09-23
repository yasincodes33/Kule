package com.AgentSaasAplication.common.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Dağıtık scheduler kilidinin çekirdek davranışı: yalnızca BİR
 * çağıran kilidi alabilir (Redis `SET NX`), Redis erişilemezse fail-open (sweep'lerin tek-instance
 * dağıtımda hiç çalışmaması, bir koordinasyon aracının kendisi yüzünden olmamalı).
 */
@ExtendWith(MockitoExtension.class)
class SchedulerLockTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private SchedulerLock lock;

    @BeforeEach
    void setUp() {
        lock = new SchedulerLock(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void kilit_bosken_alinir() {
        when(valueOperations.setIfAbsent(eq("scheduler-lock:job-a"), any(), eq(Duration.ofSeconds(30)))).thenReturn(true);

        assertThat(lock.tryAcquire("job-a", Duration.ofSeconds(30))).isTrue();
    }

    @Test
    void kilit_baskasindaysa_alinamaz() {
        when(valueOperations.setIfAbsent(eq("scheduler-lock:job-a"), any(), any(Duration.class))).thenReturn(false);

        assertThat(lock.tryAcquire("job-a", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void redis_erisilemezse_fail_open_true_doner() {
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenThrow(new RuntimeException("redis down"));

        assertThat(lock.tryAcquire("job-a", Duration.ofSeconds(30))).isTrue();
    }
}
