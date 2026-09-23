package com.AgentSaasAplication.config;

import com.AgentSaasAplication.common.exceptions.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Uygulamanın tek güvenlik yapılandırması: durum tutmayan (stateless) oturum yönetimi,
 * kendi imzaladığımız JWT'lerin doğrulanması, BCrypt parola kodlaması ve REST
 * istemcileri için JSON gövdeli 401/403 cevapları.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    // Frontend ayrı bir origin'den Authorization ve X-Organization-Id header'larıyla
    // istek attığı için CORS açıkça yapılandırılıyor. Varsayılan liste yaygın yerel
    // geliştirme portlarını kapsar; dağıtımda CORS_ALLOWED_ORIGINS ile gerçek origin
    // verilmelidir.
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:4200}")
    private List<String> allowedOrigins;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Spring Boot, bir controller exception fırlattığında dahili olarak
                // /error'a forward eder ve bu forward AYNI filtre zincirinden ikinci kez
                // geçer. /error permitAll'da değilse, başka herhangi bir isteğin
                // (permitAll olsa bile) içindeki gerçek hata (400/404/500...) 401'e
                // dönüşür ve asıl durumu maskeler.
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/public").permitAll()
                .requestMatchers("/ws/agent-bridge").permitAll()
                .requestMatchers("/ws/tasks/**").permitAll()
                // /ws/tasks/** ile aynı gerekçe: WS handshake'i Bearer JWT taşımıyor, kimlik
                // doğrulaması tamamen RunnerTerminalAuthInterceptor'daki tek-kullanımlık bilet ile
                // yapılıyor (bkz. issueTerminalWsTicket — o uç nokta normal Bearer auth altında).
                .requestMatchers("/ws/runners/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(this::writeUnauthorized)
                .accessDeniedHandler((request, response, e) -> writeJsonError(response, 403, "Bu işlem için yetkiniz yok"))
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Organization-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeUnauthorized(jakarta.servlet.http.HttpServletRequest request,
                                    jakarta.servlet.http.HttpServletResponse response,
                                    org.springframework.security.core.AuthenticationException e) throws IOException {
        writeJsonError(response, 401, "Kimlik doğrulama gerekli — geçerli bir Bearer access token gönderin");
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(status, message)));
    }
}
