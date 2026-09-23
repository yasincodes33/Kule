package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.identity.dto.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `AuthenticationService.login()`'in transaction sınırlarını gerçek bir veritabanına karşı
 * doğrular: `issueTokens()` içindeki `refreshTokenRecordRepository.save()` salt okunur bir
 * transaction'da çalışırsa login 200 döner ama RefreshTokenRecord yazılmaz ve bir sonraki
 * `/auth/refresh` her zaman 401 verir.
 *
 * Mockito tabanlı bir unit test `@Transactional`'ın gerçekten uygulandığını kanıtlayamaz
 * (AOP, Spring container'ı gerektirir); bu yüzden gerçek transaction sınırlarıyla çalışan
 * bir entegrasyon testi gerekiyor.
 */
@SpringBootTest
@ActiveProfiles("test")  // testler docker-compose veritabanina baglanir (bkz. application-test.properties)
@TestPropertySource(properties = {
        "app.security.api-key-secret=Nob4gLqy5BdHw4+JVQLwPOn/pZPAwRHAbsNLrhT8vrw=",
        "app.jwt.secret=u0jPk8Yj54/bZdstnsXwYA/HSwg07yaSPdXGOh3dURg="
})
class AuthenticationIntegrationTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Test
    void giris_sonrasinda_uretilen_refresh_token_gercekten_kullanilabilir() {
        String email = "auth-integration-" + UUID.randomUUID() + "@test.local";
        String password = "correcthorsebatterystaple";
        authenticationService.register(email, password, null);

        AuthResponse loginResponse = authenticationService.login(email, password);

        // Asıl regresyon kontrolü: login()'in ürettiği refresh token, register()'ınki gibi,
        // gerçekten DB'de izleniyor ve /auth/refresh ile başarıyla kullanılabiliyor olmalı —
        // BadCredentialsException fırlarsa refreshTokenRecordRepository.save() hiç kalıcı olmamış
        // demektir (yani @Transactional yine eksik/bozuk).
        AuthResponse refreshResponse = authenticationService.refresh(loginResponse.refreshToken());

        assertThat(refreshResponse.accessToken()).isNotBlank();
        assertThat(refreshResponse.refreshToken()).isNotBlank();
        assertThat(refreshResponse.refreshToken()).isNotEqualTo(loginResponse.refreshToken());
    }
}
