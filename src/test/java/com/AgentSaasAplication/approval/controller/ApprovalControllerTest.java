package com.AgentSaasAplication.approval.controller;

import com.AgentSaasAplication.approval.domain.ApprovalRequest;
import com.AgentSaasAplication.approval.service.ApprovalService;
import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
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

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ApprovalController — reject()'in gövdesiz (sebep verilmemiş) çağrıya karşı null-güvenli
 * davranışı ve ApprovalService'in fırlattığı istisnaların (AccessDenied/NotFound/IllegalState)
 * doğru HTTP durumlarına eşlendiği bkz. TaskControllerTest'teki standaloneSetup deseni.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    @Mock private ApprovalService approvalService;
    @Mock private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApprovalController controller = new ApprovalController(approvalService, currentUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private ApprovalRequest fakeRequest() {
        return ApprovalRequest.requestFor(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600));
    }

    @Test
    void onay_istegi_olusturma_201_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(approvalService.requestApproval(any(), any())).thenReturn(fakeRequest());

        mockMvc.perform(post("/api/v1/tasks/{taskId}/approvals", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isCreated());
    }

    @Test
    void zaten_bekleyen_istek_varsa_409_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(approvalService.requestApproval(any(), any()))
                .thenThrow(new IllegalStateException("Bu task için zaten bekleyen bir onay isteği var"));

        mockMvc.perform(post("/api/v1/tasks/{taskId}/approvals", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isConflict());
    }

    @Test
    void yetkisiz_kullanici_onaylarsa_403_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(approvalService.approve(any(), any())).thenThrow(new AccessDeniedException("yetkisiz"));

        mockMvc.perform(post("/api/v1/tasks/{taskId}/approvals/{approvalId}/approve",
                        UUID.randomUUID(), UUID.randomUUID()).with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void var_olmayan_onay_istegi_404_doner() throws Exception {
        when(approvalService.getApproval(any())).thenThrow(new NotFoundException("bulunamadı"));

        mockMvc.perform(get("/api/v1/tasks/{taskId}/approvals/{approvalId}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void govdesiz_reddetme_istegi_null_sebeple_servise_iletilir() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        UUID approvalId = UUID.randomUUID();
        when(approvalService.reject(eq(approvalId), any(), isNull())).thenReturn(fakeRequest());

        mockMvc.perform(post("/api/v1/tasks/{taskId}/approvals/{approvalId}/reject",
                        UUID.randomUUID(), approvalId).with(jwt()))
                .andExpect(status().isOk());

        verify(approvalService).reject(eq(approvalId), any(), isNull());
    }

    @Test
    void govdeli_reddetme_istegi_sebep_ile_servise_iletilir() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        UUID approvalId = UUID.randomUUID();
        when(approvalService.reject(eq(approvalId), any(), eq("riskli komut"))).thenReturn(fakeRequest());

        mockMvc.perform(post("/api/v1/tasks/{taskId}/approvals/{approvalId}/reject",
                        UUID.randomUUID(), approvalId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"riskli komut\"}"))
                .andExpect(status().isOk());

        verify(approvalService).reject(eq(approvalId), any(), eq("riskli komut"));
    }
}
