package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.email.EmailService;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.security.BridgeTokenGenerator;
import com.AgentSaasAplication.common.security.JwtService;
import com.AgentSaasAplication.identity.domain.PasswordResetToken;
import com.AgentSaasAplication.identity.domain.RefreshTokenRecord;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.dto.AuthResponse;
import com.AgentSaasAplication.identity.repository.PasswordResetTokenRepository;
import com.AgentSaasAplication.identity.repository.RefreshTokenRecordRepository;
import com.AgentSaasAplication.identity.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Self-issued JWT ile kimlik doğrulama: register / login / refresh. Doğrulama
 * hataları (yanlış şifre, geçersiz/süresi dolmuş refresh token) hep aynı
 * {@link BadCredentialsException}'ı fırlatıyor — hangisinin yanlış olduğunu (email mi şifre mi)
 * istemciye sızdırmamak için (kullanıcı numaralandırma saldırısını zorlaştırır).
 *
 * Refresh token'lar veritabanında izleniyor ve HER refresh çağrısında rotate ediliyor
 * (bkz. RefreshTokenRecord). Zaten rotate edilmiş (revoked) bir token tekrar sunulursa bu
 * olası bir hırsızlık belirtisi sayılır ve kullanıcının TÜM aktif refresh token'ları iptal
 * edilir, yani her yerden çıkış zorlanır (bkz. handleReuse()).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AuthenticationService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRecordRepository refreshTokenRecordRepository;
    private final EmailService emailService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                  JwtService jwtService, PasswordResetTokenRepository passwordResetTokenRepository,
                                  RefreshTokenRecordRepository refreshTokenRecordRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRecordRepository = refreshTokenRecordRepository;
        this.emailService = emailService;
    }

    @Transactional
    public AuthResponse register(String email, String rawPassword, String displayName) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Bu e-posta zaten kayıtlı: " + email);
        }

        User user = userRepository.save(User.register(email, passwordEncoder.encode(rawPassword), displayName));
        log.info("Yeni kullanıcı kaydoldu: userId={}, email={}", user.getId(), email);
        return issueTokens(user);
    }

    /** `@Transactional` eksikti — sınıf düzeyindeki
     * `@Transactional(readOnly = true)`'ı miras alıyordu, bu yüzden issueTokens() içindeki
     * refreshTokenRecordRepository.save() bir salt-okunur transaction'da çalışıyordu ve DB'ye
     * hiç yazılmıyordu: login 200 dönüyordu (JWT üretimi bellek içi, transaction'dan bağımsız)
     * ama üretilen refresh token için hiçbir RefreshTokenRecord satırı yoktu — bir sonraki
     * /auth/refresh çağrısı (sayfa yenileme, sessiz yenileme) HER ZAMAN 401 veriyordu. register()
     * kendi @Transactional'ını taşıdığı için bu hatadan etkilenmiyordu, o yüzden yalnızca "kayıt
     * ol → hemen dene" akışıyla test edildiğinde fark edilmemişti. */
    @Transactional
    public AuthResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("E-posta veya şifre hatalı"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("E-posta veya şifre hatalı");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        Jwt jwt;
        try {
            jwt = jwtService.decodeForRefresh(refreshToken);
        } catch (JwtException e) {
            throw new BadCredentialsException("Geçersiz veya süresi dolmuş refresh token");
        }

        if (!jwtService.isRefreshToken(jwt)) {
            throw new BadCredentialsException("Bu bir refresh token değil");
        }

        String jti = jwt.getId();
        RefreshTokenRecord record = refreshTokenRecordRepository.findByJti(jti)
                .orElseThrow(() -> new BadCredentialsException("Geçersiz veya süresi dolmuş refresh token"));

        if (record.getRevokedAt() != null) {
            handleReuse(record);
            throw new BadCredentialsException("Bu refresh token artık geçersiz — lütfen tekrar giriş yapın");
        }
        if (!record.isActive()) {
            throw new BadCredentialsException("Geçersiz veya süresi dolmuş refresh token");
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Kullanıcı artık mevcut değil"));

        String newAccessToken = jwtService.issueAccessToken(user.getId(), user.getEmail());
        String newJti = UUID.randomUUID().toString();
        String newRefreshToken = jwtService.issueRefreshToken(user.getId(), user.getEmail(), newJti);

        record.rotateTo(newJti);
        refreshTokenRecordRepository.save(RefreshTokenRecord.issue(userId, newJti, jwtService.refreshTokenExpiry()));

        return new AuthResponse(newAccessToken, newRefreshToken, jwtService.accessTokenTtlSeconds());
    }

    /** Bir refresh token'ın sunucu tarafında da iptal edilmesi — çıkış yapınca çağrılır.
     * Geçersiz/süresi dolmuş bir token için sessizce hiçbir şey yapmaz (idempotent, hata sızdırmaz). */
    @Transactional
    public void logout(String refreshToken) {
        try {
            Jwt jwt = jwtService.decodeForRefresh(refreshToken);
            refreshTokenRecordRepository.findByJti(jwt.getId()).ifPresent(RefreshTokenRecord::revoke);
        } catch (JwtException e) {
            log.debug("logout() geçersiz bir refresh token ile çağrıldı — yok sayılıyor");
        }
    }

    /**
     * Kullanıcı var olmasa bile aynı şekilde (istisnasız, aynı yanıtla) döner —
     * aksi halde "bu e-posta kayıtlı mı" sorusuna cevap sızdırılmış olur (kullanıcı numaralandırma).
     *
     * Bağlantı e-postayla gönderiliyor (bkz. EmailService). Token'ın kendisi INFO
     * seviyesinde loglanmıyor (yalnızca DEBUG'da, o da yalnızca `local` profilinde açık —
     * bkz. application-local.properties): bir bearer credential'ı üretim log'larına düz
     * metin yazmak, alıcısı e-posta olsa bile gereksiz bir risktir.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            BridgeTokenGenerator.BridgeToken token = BridgeTokenGenerator.generate();
            Instant expiresAt = Instant.now().plus(RESET_TOKEN_TTL);
            passwordResetTokenRepository.save(PasswordResetToken.issue(user.getId(), token.hash(), expiresAt));

            String resetLink = frontendBaseUrl + "/reset-password?token=" + token.plaintext();
            log.debug("Şifre sıfırlama bağlantısı üretildi: userId={}, email={}, resetLink={}", user.getId(), email, resetLink);
            emailService.sendPasswordResetEmail(email, resetLink);
        }, () -> log.info(
                "Şifre sıfırlama istendi ama böyle bir kullanıcı yok (numaralandırmayı önlemek için sessizce yok sayıldı): email={}",
                email));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = BridgeTokenGenerator.hash(rawToken);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .filter(PasswordResetToken::isValid)
                .orElseThrow(() -> new IllegalArgumentException("Sıfırlama bağlantısı geçersiz veya süresi dolmuş"));

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));

        user.changePassword(passwordEncoder.encode(newPassword));
        resetToken.markUsed();
        revokeAllRefreshTokens(user.getId());

        log.info("Şifre sıfırlandı: userId={}", user.getId());
    }

    /** Oturum açıkken (mevcut şifreyi bilerek) şifre değiştirme — token tabanlı resetPassword()'den
     * FARKLI bir akış: kimlik zaten JWT ile kanıtlanmış, ek olarak mevcut şifre de doğrulanıyor. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Mevcut şifre hatalı");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        revokeAllRefreshTokens(userId);
        log.info("Şifre kullanıcı tarafından değiştirildi: userId={}", userId);
    }

    /** Zaten rotate edilmiş (kullanılmış) bir refresh token'ın TEKRAR sunulması —
     * ya kullanıcı eski bir token'ı (ör. iki sekme) tekrar kullandı ya da token çalınmış olabilir,
     * ikisini ayırt edemeyiz. Temkinli davranıp o kullanıcının TÜM aktif refresh token'larını
     * iptal ediyoruz — her cihazda yeniden giriş zorunlu hale gelir. */
    private void handleReuse(RefreshTokenRecord record) {
        log.warn("Zaten kullanılmış (rotate edilmiş) bir refresh token tekrar sunuldu — olası hırsızlık, "
                + "kullanıcının tüm oturumları iptal ediliyor: userId={}, jti={}", record.getUserId(), record.getJti());
        revokeAllRefreshTokens(record.getUserId());
    }

    private void revokeAllRefreshTokens(UUID userId) {
        refreshTokenRecordRepository.findByUserIdAndRevokedAtIsNull(userId)
                .forEach(RefreshTokenRecord::revoke);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail());
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtService.issueRefreshToken(user.getId(), user.getEmail(), jti);
        refreshTokenRecordRepository.save(RefreshTokenRecord.issue(user.getId(), jti, jwtService.refreshTokenExpiry()));
        return new AuthResponse(accessToken, refreshToken, jwtService.accessTokenTtlSeconds());
    }
}
