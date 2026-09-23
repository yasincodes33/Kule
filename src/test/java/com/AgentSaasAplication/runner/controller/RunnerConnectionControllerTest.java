package com.AgentSaasAplication.runner.controller;

import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.service.RunnerConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RunnerConnectionController — capabilities @NotEmpty ve taskType @NotNull
 * validasyonlarının gerçekten 400'e düştüğü, ADMIN/OWNER olmayan bir kullanıcının bridge token
 * isteyememesinin (AccessDeniedException → 403) doğrulanması.
 */
@ExtendWith(MockitoExtension.class)
class RunnerConnectionControllerTest {

    @Mock private RunnerConnectionService runnerConnectionService;
    @Mock private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RunnerConnectionController controller = new RunnerConnectionController(runnerConnectionService, currentUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void capabilities_bossa_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/runners")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"ev\",\"capabilities\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gecerli_istekle_kayit_201_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(runnerConnectionService.registerRunner(any(), any(), any(), any(), any()))
                .thenReturn(RunnerConnection.register(UUID.randomUUID(), UUID.randomUUID(), null, "ev"));

        mockMvc.perform(post("/api/v1/runners")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"ev\",\"capabilities\":[\"DEV\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void yetkisiz_kullanici_bridge_token_isteyemez_403_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(runnerConnectionService.issueBridgeToken(any(), any()))
                .thenThrow(new AccessDeniedException("Bu işlem için ADMIN veya OWNER rolü gerekir"));

        mockMvc.perform(post("/api/v1/runners/{runnerId}/bridge-token", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void taskType_eksikse_yetenek_ekleme_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/runners/{runnerId}/capabilities", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ayni_yetenek_ikinci_kez_eklenirse_409_doner() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Bu runner zaten DEV yeteneğine sahip"))
                .when(runnerConnectionService).addCapability(any(), any());

        mockMvc.perform(post("/api/v1/runners/{runnerId}/capabilities", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"DEV\"}"))
                .andExpect(status().isConflict());
    }
}
