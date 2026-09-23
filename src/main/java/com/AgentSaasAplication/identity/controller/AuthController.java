package com.AgentSaasAplication.identity.controller;

import com.AgentSaasAplication.identity.dto.AuthResponse;
import com.AgentSaasAplication.identity.dto.AuthTokenResponse;
import com.AgentSaasAplication.identity.dto.ForgotPasswordRequest;
import com.AgentSaasAplication.identity.dto.LoginRequest;
import com.AgentSaasAplication.identity.dto.RegisterRequest;
import com.AgentSaasAplication.identity.dto.ResetPasswordRequest;
import com.AgentSaasAplication.identity.service.AuthenticationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Kimlik doğrulama uç noktaları — hepsi SecurityConfig'te permitAll.
 *
 * Refresh token httpOnly bir cookie'de taşınır: JS'e görünmediği için bir XSS açığı
 * token'ı çalamaz. Cookie yalnızca /api/v1/auth/* altına scope'ludur (Path),
 * SameSite=Lax ve üretimde Secure'dur. Access token JSON gövdesinde döner ve
 * Authorization header'ı ile bellekte tutulur; kısa ömürlü olduğu için kalıcı
 * depolamaya ihtiyaç yoktur.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthenticationService authenticationService;
    private final boolean cookieSecure;
    private final long refreshTokenTtlDays;

    public AuthController(AuthenticationService authenticationService,
                           @Value("${app.cookie.secure:true}") boolean cookieSecure,
                           @Value("${app.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.authenticationService = authenticationService;
        this.cookieSecure = cookieSecure;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthResponse tokens = authenticationService.register(request.email(), request.password(), request.displayName());
        setRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthTokenResponse.from(tokens));
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse tokens = authenticationService.login(request.email(), request.password());
        setRefreshCookie(response, tokens.refreshToken());
        return AuthTokenResponse.from(tokens);
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                      HttpServletResponse response) {
        if (refreshToken == null) {
            throw new BadCredentialsException("Geçersiz veya süresi dolmuş refresh token");
        }
        AuthResponse tokens = authenticationService.refresh(refreshToken);
        setRefreshCookie(response, tokens.refreshToken());
        return AuthTokenResponse.from(tokens);
    }

    /** Refresh token'ı sunucu tarafında da iptal eder (artık DB'de izleniyor —
     * bkz. RefreshTokenRecord). Cookie yoksa/geçersizse de aynı 200'ü döner, idempotent. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
                                        HttpServletResponse response) {
        if (refreshToken != null) {
            authenticationService.logout(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok().build();
    }

    /** Kullanıcı var olsa da olmasa da aynı 200 yanıtını döner — bkz. AuthenticationService. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.requestPasswordReset(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(refreshTokenTtlDays))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
