package com.AgentSaasAplication.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `clientIp()` davranışı: güvenilmeyen bir kaynaktan gelen X-Forwarded-For yok sayılır,
 * yalnızca `trustedProxies` listesindeki bir peer'dan geldiğinde dikkate alınır. Aksi
 * halde bir saldırgan her istekte farklı bir değer göndererek aynı IP'den hız sınırını
 * sınırsızca atlayabilirdi.
 */
@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuthRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitFilter(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(filter, "loginMaxAttempts", 5);
        ReflectionTestUtils.setField(filter, "defaultMaxRequests", 20);
        ReflectionTestUtils.setField(filter, "trustedProxies", List.of("10.0.0.1"));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        chain = mock(FilterChain.class);
    }

    private MockHttpServletRequest loginRequest(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    @Test
    void guvenilmeyen_bir_peer_dan_gelen_X_Forwarded_For_yok_sayilir() throws Exception {
        // Aynı sahte X-Forwarded-For ile 6 istek — ama gerçek bağlantı GÜVENİLMEYEN bir peer'dan
        // (ör. doğrudan internetten) geliyor, bu yüzden hepsi FARKLI saymalı (spoofing engellenmeli)
        // — burada sadece key'in remoteAddr'a göre kurulduğunu doğruluyoruz.
        when(valueOperations.increment("ratelimit:auth:203.0.113.9:login")).thenReturn(1L);

        MockHttpServletRequest request = loginRequest("203.0.113.9", "1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);

        verify(valueOperations).increment("ratelimit:auth:203.0.113.9:login");
        verify(chain).doFilter(request, response);
    }

    @Test
    void guvenilir_bir_proxy_den_gelen_X_Forwarded_For_dikkate_alinir() throws Exception {
        when(valueOperations.increment("ratelimit:auth:9.9.9.9:login")).thenReturn(1L);

        MockHttpServletRequest request = loginRequest("10.0.0.1", "9.9.9.9, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);

        verify(valueOperations).increment("ratelimit:auth:9.9.9.9:login");
    }

    @Test
    void guvenilmeyen_peer_farkli_X_Forwarded_For_degerleriyle_limiti_atlayamaz() throws Exception {
        // Saldırganın gerçek bağlantı IP'si SABİT (203.0.113.9) — her istekte X-Forwarded-For'u
        // değiştirse bile (spoofing denemesi), rate limit key'i hep AYNI remoteAddr'a kurulmalı.
        when(valueOperations.increment("ratelimit:auth:203.0.113.9:login")).thenReturn(6L);

        MockHttpServletRequest request = loginRequest("203.0.113.9", "random-spoofed-value-" + System.nanoTime());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verify(chain, never()).doFilter(request, response);
    }
}
