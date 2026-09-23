package com.AgentSaasAplication.agent.controller;

import com.AgentSaasAplication.agent.domain.AgentConnection;
import com.AgentSaasAplication.agent.domain.AgentType;
import com.AgentSaasAplication.agent.service.AgentConnectionService;
import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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
 * AgentConnectionController — apiKey/agentType/capabilities validasyonları ve
 * kritik model doğrulaması başarısız olduğunda servisin attığı IllegalArgumentException'ın
 * 400'e doğru eşlendiği.
 */
@ExtendWith(MockitoExtension.class)
class AgentConnectionControllerTest {

    @Mock private AgentConnectionService agentConnectionService;
    @Mock private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AgentConnectionController controller = new AgentConnectionController(agentConnectionService, currentUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void apiKey_bossa_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/agents")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentType\":\"CLAUDE\",\"capabilities\":[\"DEV\"],\"apiKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capabilities_bossa_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/agents")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentType\":\"CLAUDE\",\"capabilities\":[],\"apiKey\":\"key\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gecerli_istek_201_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(agentConnectionService.registerAgent(any(), any(), any(), any()))
                .thenReturn(AgentConnection.register(UUID.randomUUID(), AgentType.CLAUDE, "encrypted"));

        mockMvc.perform(post("/api/v1/agents")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentType\":\"CLAUDE\",\"capabilities\":[\"DEV\"],\"apiKey\":\"key\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void kritik_model_dogrulanamazsa_400_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(agentConnectionService.registerAgent(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("API key doğrulanamadı — temel model erişilebilir değil"));

        mockMvc.perform(post("/api/v1/agents")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentType\":\"CLAUDE\",\"capabilities\":[\"DEV\"],\"apiKey\":\"yanlış\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ayni_tipte_ikinci_agent_409_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(agentConnectionService.registerAgent(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Bu organizasyonda zaten bir CLAUDE agent'ı kayıtlı"));

        mockMvc.perform(post("/api/v1/agents")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentType\":\"CLAUDE\",\"capabilities\":[\"DEV\"],\"apiKey\":\"key\"}"))
                .andExpect(status().isConflict());
    }
}
