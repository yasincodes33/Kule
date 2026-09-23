package com.AgentSaasAplication.common.security;

import com.AgentSaasAplication.common.exceptions.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * `/api/v1/auth/**` için IP başına, Redis'te INCR+EXPIRE ile tutulan basit bir sabit
 * pencere sayacı. `login`, diğer iki uç noktadan (register/refresh) daha sıkı sınırlanır;
 * asıl hedef brute-force parola denemesidir.
 *
 * Redis geçici olarak erişilemezse İSTEK REDDEDİLMİYOR (fail-open) — bir rate limiter'ın kendisi
 * tüm auth akışını kilitleyecek bir tek-nokta-arıza haline gelmemeli; yalnızca loglanıyor.
 *
 * `response.sendError()` bilinçli olarak KULLANILMIYOR: bu çağrı Spring MVC/Security
 * exception handling'ini atlayıp düz bir Spring Boot Whitelabel sayfasına düşürür.
 * Bunun yerine SecurityConfig'teki
 * writeJsonError() ile AYNI desenle, projenin kendi ErrorResponse şemasıyla doğrudan yazılıyor.
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    // forgot-password de login kadar numaralandırma/kötüye kullanıma açık
    // (bir saldırgan hangi e-postaların kayıtlı olduğunu denemek için spam'leyebilir) — genel
    // 20/dk kovasına bırakmak yerine login'le AYNI sıkı tier'ı alıyor, ama AYRI bir sayaçla
    // (login kilitlenmesi şifre sıfırlamayı da kilitlemesin diye).
    private static final String FORGOT_PASSWORD_PATH = "/api/v1/auth/forgot-password";
    // register de aynı numaralandırma riskini taşıyor: bu proje
    // henüz gerçek bir e-posta doğrulama akışı taşımadığından (bilinçli, belgelenmiş bir kapsam
    // sınırı — bkz. requestPasswordReset), "e-posta zaten kayıtlı" hatası TAM olarak
    // gizlenemiyor; en azından deneme HIZINI login kadar sıkı tutmak brute-force numaralandırmayı
    // pratikte anlamsız hale getiriyor.
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.login-max-attempts:5}")
    private int loginMaxAttempts;

    @Value("${app.rate-limit.auth-max-requests:20}")
    private int defaultMaxRequests;

    // X-Forwarded-For istemciden gelen, sahtelenebilir bir header. Ona koşulsuz güvenilirse
    // bir saldırgan her istekte farklı bir değer göndererek login/forgot-password limitini
    // sınırsızca atlayabilir. Varsayılan (boş liste) HİÇBİR
    // proxy'ye güvenmez — bu durumda header tamamen yok sayılır, yalnızca gerçek TCP bağlantısının
    // IP'si (request.getRemoteAddr()) kullanılır (bu proje henüz bir reverse proxy arkasında
    // çalışmıyor). Gerçek bir dağıtımda önündeki proxy'nin/LB'nin IP'si buraya eklenmeli — YALNIZCA
    // O ZAMAN header'a güvenilir, aksi halde spoofing mümkün kalır.
    @Value("${app.rate-limit.trusted-proxies:}")
    private List<String> trustedProxies;

    public AuthRateLimitFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isStrictTier = LOGIN_PATH.equals(path) || FORGOT_PASSWORD_PATH.equals(path) || REGISTER_PATH.equals(path);
        int limit = isStrictTier ? loginMaxAttempts : defaultMaxRequests;
        Duration window = isStrictTier ? LOGIN_WINDOW : DEFAULT_WINDOW;
        String bucket = LOGIN_PATH.equals(path) ? "login"
                : FORGOT_PASSWORD_PATH.equals(path) ? "forgot-password"
                : REGISTER_PATH.equals(path) ? "register"
                : "other";
        String key = "ratelimit:auth:" + clientIp(request) + ":" + bucket;

        if (isOverLimit(key, limit, window)) {
            log.warn("Rate limit aşıldı: path={}, key={}", request.getRequestURI(), key);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ErrorResponse.of(429, "Çok fazla istek — lütfen biraz sonra tekrar deneyin")));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOverLimit(String key, int limit, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
            return count != null && count > limit;
        } catch (Exception e) {
            log.error("Rate limit sayacı okunamadı (Redis erişilemez olabilir) — istek fail-open geçiriliyor: key={}", key, e);
            return false;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustedProxies.contains(remoteAddr)) {
            // İstek doğrudan bize (veya güvenilir olmayan bir atlamadan) geldi — X-Forwarded-For
            // istemcinin kendisi tarafından serbestçe set edilebilir, güvenilmez.
            return remoteAddr;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }
}
