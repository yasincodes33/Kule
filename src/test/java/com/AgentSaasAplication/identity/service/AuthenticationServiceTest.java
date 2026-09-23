package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.email.EmailService;
import com.AgentSaasAplication.common.security.BridgeTokenGenerator;
import com.AgentSaasAplication.common.security.JwtService;
import com.AgentSaasAplication.identity.domain.PasswordResetToken;
import com.AgentSaasAplication.identity.domain.RefreshTokenRecord;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.dto.AuthResponse;
import com.AgentSaasAplication.identity.repository.PasswordResetTokenRepository;
import com.AgentSaasAplication.identity.repository.RefreshTokenRecordRepository;
import com.AgentSaasAplication.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kimlik doğrulama çekirdeği: register / login / refresh. login() ve refresh(), yanlış
 * parola ile geçersiz token için hep AYNI BadCredentialsException'ı fırlatır (kullanıcı
 * numaralandırma saldırısını zorlaştırmak için); bu davranış ayrıca doğrulanır. Refresh
 * token rotasyonu ve yeniden kullanım tespiti de bu dosyada kapsanır.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RefreshTokenRecordRepository refreshTokenRecordRepository;
    @Mock private EmailService emailService;

    private AuthenticationService authenticationService;

    private final String email = "test@example.com";
    private final String rawPassword = "correcthorsebatterystaple";
    private final String hashedPassword = "$2a$12$hashed";

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository, passwordEncoder, jwtService, passwordResetTokenRepository, refreshTokenRecordRepository,
                emailService);
        ReflectionTestUtils.setField(authenticationService, "frontendBaseUrl", "http://localhost:5173");
        // Çoğu testte gerçek değeri önemsiz — yalnızca RefreshTokenRecord.issue() null patlamasın diye.
        lenient().when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(3600));
    }

    private User savedUser(String email, String passwordHash) {
        User user = User.register(email, passwordHash, "Test User");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private Jwt validJwt(UUID subject, String jti) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
        if (jti != null) {
            builder.claim("jti", jti);
        }
        return builder.build();
    }

    @Test
    void kayit_yeni_email_ile_kullanici_olusturur_ve_token_cifti_doner() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(rawPassword)).thenReturn(hashedPassword);
        when(userRepository.save(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            return user;
        });
        when(jwtService.issueAccessToken(any(), eq(email))).thenReturn("access-token");
        when(jwtService.issueRefreshToken(any(), eq(email), anyString())).thenReturn("refresh-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(3600L);

        AuthResponse response = authenticationService.register(email, rawPassword, "Test User");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        verify(passwordEncoder).encode(rawPassword);
        verify(refreshTokenRecordRepository).save(any(RefreshTokenRecord.class));
    }

    @Test
    void kayit_zaten_kayitli_email_ile_illegalstate_firlatir() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(savedUser(email, hashedPassword)));

        assertThatThrownBy(() -> authenticationService.register(email, rawPassword, "Test User"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten kayıtlı");

        verify(userRepository, never()).save(any());
    }

    @Test
    void giris_dogru_sifreyle_token_cifti_doner() {
        User user = savedUser(email, hashedPassword);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(jwtService.issueAccessToken(user.getId(), email)).thenReturn("access-token");
        when(jwtService.issueRefreshToken(eq(user.getId()), eq(email), anyString())).thenReturn("refresh-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(3600L);

        AuthResponse response = authenticationService.login(email, rawPassword);

        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void giris_yanlis_sifreyle_badcredentials_firlatir() {
        User user = savedUser(email, hashedPassword);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("yanlisSifre", hashedPassword)).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login(email, "yanlisSifre"))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).issueAccessToken(any(), anyString());
    }

    @Test
    void giris_var_olmayan_email_ile_de_ayni_badcredentials_hatasini_firlatir() {
        // Şifre yanlış senaryosuyla AYNI exception/mesaj — hangisinin yanlış
        // olduğunu (email mi şifre mi) istemciye sızdırmamak için, kullanıcı numaralandırma
        // saldırısını zorlaştırır.
        when(userRepository.findByEmail("yok@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login("yok@example.com", rawPassword))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("E-posta veya şifre hatalı");
    }

    @Test
    void refresh_gecerli_token_ile_rotate_edilir_ve_yeni_cift_doner() {
        UUID userId = UUID.randomUUID();
        User user = savedUser(email, hashedPassword);
        ReflectionTestUtils.setField(user, "id", userId);
        Jwt refreshJwt = validJwt(userId, "jti-old");
        RefreshTokenRecord record = RefreshTokenRecord.issue(userId, "jti-old", Instant.now().plusSeconds(600));

        when(jwtService.decodeForRefresh("valid-refresh")).thenReturn(refreshJwt);
        when(jwtService.isRefreshToken(refreshJwt)).thenReturn(true);
        when(refreshTokenRecordRepository.findByJti("jti-old")).thenReturn(Optional.of(record));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(userId, email)).thenReturn("new-access-token");
        when(jwtService.issueRefreshToken(eq(userId), eq(email), anyString())).thenReturn("new-refresh-token");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(3600L);

        AuthResponse response = authenticationService.refresh("valid-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        // Her refresh çağrısı token'ı rotate eder; aynı token ikinci kez geçerli olmaz.
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(record.getRevokedAt()).isNotNull();
        assertThat(record.getReplacedByJti()).isNotNull();
        verify(refreshTokenRecordRepository).save(any(RefreshTokenRecord.class));
    }

    @Test
    void refresh_bilinmeyen_jti_ile_badcredentials_firlatir() {
        UUID userId = UUID.randomUUID();
        Jwt refreshJwt = validJwt(userId, "jti-bilinmeyen");
        when(jwtService.decodeForRefresh("valid-refresh")).thenReturn(refreshJwt);
        when(jwtService.isRefreshToken(refreshJwt)).thenReturn(true);
        when(refreshTokenRecordRepository.findByJti("jti-bilinmeyen")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.refresh("valid-refresh"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_suresi_dolmus_kayit_ile_reddedilir() {
        UUID userId = UUID.randomUUID();
        Jwt refreshJwt = validJwt(userId, "jti-expired");
        RefreshTokenRecord expired = RefreshTokenRecord.issue(userId, "jti-expired", Instant.now().minusSeconds(60));

        when(jwtService.decodeForRefresh("valid-refresh")).thenReturn(refreshJwt);
        when(jwtService.isRefreshToken(refreshJwt)).thenReturn(true);
        when(refreshTokenRecordRepository.findByJti("jti-expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authenticationService.refresh("valid-refresh"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_zaten_rotate_edilmis_token_reuse_sayilir_ve_tum_aktif_tokenlar_iptal_edilir() {
        // Bu, bir refresh token'ın ÇALINIP tekrar kullanılmaya çalışıldığı senaryonun
        // simülasyonu — kayıt zaten rotateTo() ile revoke edilmiş durumda.
        UUID userId = UUID.randomUUID();
        Jwt refreshJwt = validJwt(userId, "jti-used");
        RefreshTokenRecord alreadyRotated = RefreshTokenRecord.issue(userId, "jti-used", Instant.now().plusSeconds(600));
        alreadyRotated.rotateTo("jti-newer");
        RefreshTokenRecord otherActiveSession = RefreshTokenRecord.issue(userId, "jti-other-device", Instant.now().plusSeconds(600));

        when(jwtService.decodeForRefresh("stolen-refresh")).thenReturn(refreshJwt);
        when(jwtService.isRefreshToken(refreshJwt)).thenReturn(true);
        when(refreshTokenRecordRepository.findByJti("jti-used")).thenReturn(Optional.of(alreadyRotated));
        when(refreshTokenRecordRepository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of(otherActiveSession));

        assertThatThrownBy(() -> authenticationService.refresh("stolen-refresh"))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(otherActiveSession.getRevokedAt()).isNotNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void refresh_gecersiz_imzali_token_ile_badcredentials_firlatir() {
        when(jwtService.decodeForRefresh("garbage")).thenThrow(new JwtException("bad signature"));

        assertThatThrownBy(() -> authenticationService.refresh("garbage"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_bir_access_token_ile_cagrilirsa_reddedilir() {
        // token_use=access olan bir token, refresh akışında KABUL EDİLMEMELİ.
        UUID userId = UUID.randomUUID();
        Jwt accessJwt = validJwt(userId, null);

        when(jwtService.decodeForRefresh("access-token-as-refresh")).thenReturn(accessJwt);
        when(jwtService.isRefreshToken(accessJwt)).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.refresh("access-token-as-refresh"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("refresh token değil");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void refresh_kullanici_artik_yoksa_badcredentials_firlatir() {
        UUID userId = UUID.randomUUID();
        Jwt refreshJwt = validJwt(userId, "jti-x");
        RefreshTokenRecord record = RefreshTokenRecord.issue(userId, "jti-x", Instant.now().plusSeconds(600));

        when(jwtService.decodeForRefresh("valid-refresh")).thenReturn(refreshJwt);
        when(jwtService.isRefreshToken(refreshJwt)).thenReturn(true);
        when(refreshTokenRecordRepository.findByJti("jti-x")).thenReturn(Optional.of(record));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.refresh("valid-refresh"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logout_gecerli_token_icin_eslesen_kaydi_iptal_eder() {
        UUID userId = UUID.randomUUID();
        Jwt refreshJwt = validJwt(userId, "jti-logout");
        RefreshTokenRecord record = RefreshTokenRecord.issue(userId, "jti-logout", Instant.now().plusSeconds(600));

        when(jwtService.decodeForRefresh("valid-refresh")).thenReturn(refreshJwt);
        when(refreshTokenRecordRepository.findByJti("jti-logout")).thenReturn(Optional.of(record));

        authenticationService.logout("valid-refresh");

        assertThat(record.getRevokedAt()).isNotNull();
    }

    @Test
    void logout_gecersiz_token_icin_sessizce_hicbir_sey_yapmaz() {
        when(jwtService.decodeForRefresh("garbage")).thenThrow(new JwtException("bad signature"));

        authenticationService.logout("garbage");

        verify(refreshTokenRecordRepository, never()).findByJti(any());
    }

    @Test
    void sifre_sifirlama_var_olan_kullanici_icin_token_uretir_ve_kaydeder() {
        User user = savedUser(email, hashedPassword);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        authenticationService.requestPasswordReset(email);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(captor.getValue().isValid()).isTrue();
        // Bağlantı gerçekten e-postayla
        // gönderiliyor, bkz. EmailService.
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(email), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith("http://localhost:5173/reset-password?token=");
    }

    @Test
    void sifre_sifirlama_var_olmayan_email_icin_sessizce_yok_sayilir() {
        // Kullanıcı numaralandırma saldırısını önlemek için — hiçbir exception,
        // hiçbir farklı davranış. login()'deki BadCredentialsException stratejisinin aynısı.
        when(userRepository.findByEmail("yok@example.com")).thenReturn(Optional.empty());

        authenticationService.requestPasswordReset("yok@example.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void sifre_sifirla_gecersiz_token_ile_reddedilir() {
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.resetPassword("gecersiz-token", "yeniSifre123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("geçersiz veya süresi dolmuş");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void sifre_sifirla_suresi_dolmus_token_ile_reddedilir() {
        UUID userId = UUID.randomUUID();
        PasswordResetToken expired = PasswordResetToken.issue(userId, BridgeTokenGenerator.hash("token"), Instant.now().minusSeconds(60));
        when(passwordResetTokenRepository.findByTokenHash(BridgeTokenGenerator.hash("token"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authenticationService.resetPassword("token", "yeniSifre123"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void sifre_sifirla_zaten_kullanilmis_token_ile_reddedilir() {
        UUID userId = UUID.randomUUID();
        PasswordResetToken used = PasswordResetToken.issue(userId, BridgeTokenGenerator.hash("token"), Instant.now().plusSeconds(600));
        used.markUsed();
        when(passwordResetTokenRepository.findByTokenHash(BridgeTokenGenerator.hash("token"))).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> authenticationService.resetPassword("token", "yeniSifre123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sifre_sifirla_gecerli_token_ile_sifreyi_gunceller_token_tekrar_kullanilamaz_ve_tum_oturumlar_iptal_edilir() {
        User user = savedUser(email, hashedPassword);
        PasswordResetToken resetToken = PasswordResetToken.issue(user.getId(), BridgeTokenGenerator.hash("token"), Instant.now().plusSeconds(600));
        RefreshTokenRecord activeSession = RefreshTokenRecord.issue(user.getId(), "jti-active", Instant.now().plusSeconds(600));
        when(passwordResetTokenRepository.findByTokenHash(BridgeTokenGenerator.hash("token"))).thenReturn(Optional.of(resetToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("yeniSifre123")).thenReturn("$2a$12$yeniHash");
        when(refreshTokenRecordRepository.findByUserIdAndRevokedAtIsNull(user.getId())).thenReturn(List.of(activeSession));

        authenticationService.resetPassword("token", "yeniSifre123");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$yeniHash");
        assertThat(resetToken.isValid()).isFalse();
        assertThatThrownBy(resetToken::markUsed).isInstanceOf(IllegalStateException.class);
        assertThat(activeSession.getRevokedAt()).isNotNull();
    }

    @Test
    void sifre_degistir_dogru_mevcut_sifreyle_gunceller_ve_tum_oturumlari_iptal_eder() {
        User user = savedUser(email, hashedPassword);
        RefreshTokenRecord activeSession = RefreshTokenRecord.issue(user.getId(), "jti-active", Instant.now().plusSeconds(600));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, hashedPassword)).thenReturn(true);
        when(passwordEncoder.encode("yeniSifre456")).thenReturn("$2a$12$yeniHash");
        when(refreshTokenRecordRepository.findByUserIdAndRevokedAtIsNull(user.getId())).thenReturn(List.of(activeSession));

        authenticationService.changePassword(user.getId(), rawPassword, "yeniSifre456");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$yeniHash");
        assertThat(activeSession.getRevokedAt()).isNotNull();
    }

    @Test
    void sifre_degistir_yanlis_mevcut_sifreyle_badcredentials_firlatir() {
        User user = savedUser(email, hashedPassword);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("yanlisSifre", hashedPassword)).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.changePassword(user.getId(), "yanlisSifre", "yeniSifre456"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Mevcut şifre hatalı");

        assertThat(user.getPasswordHash()).isEqualTo(hashedPassword);
    }

    @Test
    void sifre_degistir_kullanici_yoksa_notfound_firlatir() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.changePassword(userId, rawPassword, "yeniSifre456"))
                .isInstanceOf(com.AgentSaasAplication.common.exceptions.NotFoundException.class);
    }
}
