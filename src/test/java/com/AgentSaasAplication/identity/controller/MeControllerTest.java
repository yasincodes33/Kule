package com.AgentSaasAplication.identity.controller;

import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.service.AuthenticationService;
import com.AgentSaasAplication.identity.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private UserService userService;
    @Mock private AuthenticationService authenticationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MeController controller = new MeController(currentUserResolver, userService, authenticationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    // Map.of(...) null value'ya izin vermiyor — User.getId() @GeneratedValue olduğu için hiç
    // save edilmemiş bir entity'de null döner, bu yüzden reflection'la set ediliyor (aksi halde
    // controller'ın KENDİ bug'ı değil, testin unsaved-entity limitasyonu 500 üretir).
    private User savedUser(String email, String displayName) {
        User user = User.register(email, "$2a$12$test", displayName);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void mevcut_kullanici_bilgileri_donuyor() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(userService.getById(any())).thenReturn(savedUser("dev@test.com", "Dev"));

        mockMvc.perform(get("/api/v1/me").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dev@test.com"));
    }

    @Test
    void displayName_null_ise_bos_string_donuyor_npe_atmaz() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(userService.getById(any())).thenReturn(savedUser("dev@test.com", null));

        mockMvc.perform(get("/api/v1/me").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(""));
    }

    @Test
    void jwt_artik_var_olmayan_bir_kullaniciyi_isaret_ediyorsa_404_doner() throws Exception {
        // Controller UserRepository'ye doğrudan erişmez;
        // Optional.orElseThrow() (mesajsız NoSuchElementException) çağırıyordu — GlobalExceptionHandler
        // bunu 404'e ÇEVİREMEZDİ (hiçbir @ExceptionHandler NoSuchElementException'ı yakalamıyor),
        // muhtemelen 500 üretirdi. Artık UserService.getById() üzerinden gidiyor, o da
        // GlobalExceptionHandler'ın 404'e çevirdiği NotFoundException fırlatıyor.
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(userService.getById(any())).thenThrow(new NotFoundException("Kullanıcı bulunamadı"));

        mockMvc.perform(get("/api/v1/me").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void profil_guncelleme_200_ve_yeni_displayname_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(userService.updateProfile(any(), any())).thenReturn(savedUser("dev@test.com", "Yeni İsim"));

        mockMvc.perform(put("/api/v1/me")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Yeni İsim\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Yeni İsim"));
    }

    @Test
    void sifre_degistirme_gecerli_istekle_200_doner() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"eskiSifre123\",\"newPassword\":\"yeniSifre456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void sifre_degistirme_kisa_yeni_sifreyle_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/me/change-password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"eskiSifre123\",\"newPassword\":\"kisa\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sifre_degistirme_yanlis_mevcut_sifreyle_401_doner() throws Exception {
        doThrow(new BadCredentialsException("Mevcut şifre hatalı"))
                .when(authenticationService).changePassword(any(), any(), any());

        mockMvc.perform(post("/api/v1/me/change-password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"yanlisSifre\",\"newPassword\":\"yeniSifre456\"}"))
                .andExpect(status().isUnauthorized());
    }
}
