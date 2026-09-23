package com.AgentSaasAplication.identity.controller;

import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.identity.dto.AuthResponse;
import com.AgentSaasAplication.identity.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kimlik doğrulama uç noktaları. Refresh token JSON gövdesinde değil httpOnly bir
 * cookie'de taşınır (bkz. AuthController); bu testler cookie'nin set/clear edildiğini ve
 * gövdede asla görünmediğini doğrular.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final boolean COOKIE_SECURE = false;
    private static final long REFRESH_TTL_DAYS = 30;

    @Mock private AuthenticationService authenticationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authenticationService, COOKIE_SECURE, REFRESH_TTL_DAYS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void kayit_gecerli_istekle_201_ve_cookie_ile_access_token_doner() throws Exception {
        when(authenticationService.register(any(), any(), any()))
                .thenReturn(new AuthResponse("access-token", "refresh-token", 3600L));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"correcthorsebatterystaple\",\"displayName\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value("refresh_token", "refresh-token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"));
    }

    @Test
    void kayit_gecersiz_email_ile_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gecersiz\",\"password\":\"correcthorsebatterystaple\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void kayit_kisa_sifreyle_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"kisa\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void giris_gecerli_istekle_200_ve_cookie_ile_doner() throws Exception {
        when(authenticationService.login(any(), any()))
                .thenReturn(new AuthResponse("access-token", "refresh-token", 3600L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"correcthorsebatterystaple\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value("refresh_token", "refresh-token"));
    }

    @Test
    void giris_yanlis_sifreyle_401_doner() throws Exception {
        when(authenticationService.login(any(), any()))
                .thenThrow(new BadCredentialsException("E-posta veya şifre hatalı"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"yanlisSifre\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_gecerli_cookie_ile_200_ve_rotate_edilmis_yeni_cookie_doner() throws Exception {
        when(authenticationService.refresh(eq("some-refresh-token")))
                .thenReturn(new AuthResponse("new-access-token", "new-refresh-token", 3600L));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "some-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value("refresh_token", "new-refresh-token"));
    }

    @Test
    void refresh_gecersiz_token_ile_401_doner() throws Exception {
        when(authenticationService.refresh(any()))
                .thenThrow(new BadCredentialsException("Geçersiz veya süresi dolmuş refresh token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "gecersiz")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_cookie_yoksa_401_doner() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_gecerli_cookie_ile_200_doner_ve_cookie_temizlenir() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", "some-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(authenticationService).logout("some-refresh-token");
    }

    @Test
    void logout_cookie_yoksa_yine_200_doner_ve_servis_cagrilmaz() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());

        verify(authenticationService, never()).logout(any());
    }

    @Test
    void sifremi_unuttum_gecerli_istekle_200_doner() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void sifremi_unuttum_gecersiz_email_ile_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gecersiz\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sifre_sifirla_gecerli_istekle_200_doner() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"gecerli-token\",\"newPassword\":\"yeniSifre123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void sifre_sifirla_kisa_sifreyle_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"gecerli-token\",\"newPassword\":\"kisa\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sifre_sifirla_gecersiz_token_ile_400_doner() throws Exception {
        doThrow(new IllegalArgumentException("Sıfırlama bağlantısı geçersiz veya süresi dolmuş"))
                .when(authenticationService).resetPassword(any(), any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"gecersiz-token\",\"newPassword\":\"yeniSifre123\"}"))
                .andExpect(status().isBadRequest());
    }
}
