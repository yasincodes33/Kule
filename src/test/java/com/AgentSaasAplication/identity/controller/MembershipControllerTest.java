package com.AgentSaasAplication.identity.controller;

import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.Role;
import com.AgentSaasAplication.identity.service.MembershipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MembershipController — assertPathMatchesTenant() koruması burada İKİ farklı uç noktada
 * (davet, sahiplik devri, üyelik iptali) tekrarlanıyor; bu testler URL/header tutarsızlığının
 * hepsinde aynı şekilde 403 ürettiğini doğruluyor (ProjectControllerTest ile aynı desen).
 */
@ExtendWith(MockitoExtension.class)
class MembershipControllerTest {

    @Mock private MembershipService membershipService;
    @Mock private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MembershipController controller = new MembershipController(membershipService, currentUserResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                // Pageable parametreli listMemberships() için — standaloneSetup Pageable'ı
                // otomatik çözmüyor (bkz. AuditControllerTest'teki aynı not).
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver(), new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void davet_url_organizationId_header_ile_eslesmezse_403_doner() throws Exception {
        TenantContext.set(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships/invite", organizationId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@test.com\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void gecerli_davet_201_doner() throws Exception {
        TenantContext.set(organizationId);
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        Membership invited = Membership.invite(organizationId, "x@test.com", Role.DEVELOPER);
        when(membershipService.inviteMember(any(), any(), any(), any())).thenReturn(invited);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships/invite", organizationId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@test.com\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void gecersiz_email_ile_davet_400_doner() throws Exception {
        TenantContext.set(organizationId);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships/invite", organizationId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gecersiz-email\",\"role\":\"DEVELOPER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rol_eksikse_davet_400_doner() throws Exception {
        TenantContext.set(organizationId);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships/invite", organizationId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@test.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newOwnerUserId_eksikse_sahiplik_devri_400_doner() throws Exception {
        TenantContext.set(organizationId);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships/transfer-ownership", organizationId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sahiplik_devri_url_organizationId_header_ile_eslesmezse_403_doner() throws Exception {
        TenantContext.set(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships/transfer-ownership", organizationId)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOwnerUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void uyelik_iptali_url_organizationId_header_ile_eslesmezse_403_doner() throws Exception {
        TenantContext.set(UUID.randomUUID());

        mockMvc.perform(delete("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizationId, UUID.randomUUID()).with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void uyelik_iptali_eslesirse_204_doner() throws Exception {
        TenantContext.set(organizationId);
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());

        mockMvc.perform(delete("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizationId, UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void yanlis_email_ile_davet_kabulu_400_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(membershipService.acceptInvitation(any(), any()))
                .thenThrow(new IllegalArgumentException("Bu davet bu kullanıcıya ait değil"));

        mockMvc.perform(post("/api/v1/invitations/{membershipId}/accept", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void davet_reddi_204_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/invitations/{membershipId}/reject", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void uye_listesi_url_organizationId_header_ile_eslesmezse_403_doner() throws Exception {
        TenantContext.set(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/memberships", organizationId).with(jwt()))
                .andExpect(status().isForbidden());
    }

    // Not: "eşleşirse 200 döner + içerik doğru" senaryosu burada YOK — aynı AuditControllerTest'teki
    // sınırlama: standaloneSetup'ta Page'in Jackson 3 serileştirmesi, gerçek uygulamada Spring
    // Boot'un otomatik yapılandırdığı Page Jackson modülü olmadan çöküyor (saf bir standalone test
    // altyapısı sınırı, gerçek bir uygulama hatası değil). listOrganizationMembers()'ın davranışı
    // (PENDING+ACTIVE üyelerin ikisini de döndürmesi) MembershipServiceTest'te zaten doğrulanıyor;
    // buradaki test yalnızca bu sınıfın asıl amacı olan tenant doğrulamasını kanıtlıyor.
}
