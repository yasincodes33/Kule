package com.AgentSaasAplication.audit.controller;

import com.AgentSaasAplication.audit.service.AuditService;
import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuditController — ProjectController/MembershipController'daki aynı
 * assertPathMatchesTenant() korumasının üçüncü tekrarı, aynı regresyon garantisi.
 */
@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock private AuditService auditService;

    private MockMvc mockMvc;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        AuditController controller = new AuditController(auditService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                // standaloneSetup, @WebMvcTest'in aksine Pageable'ı otomatik çözmüyor —
                // resolver eksikse Spring "no suitable resolver" IllegalStateException'ı atıyor,
                // GlobalExceptionHandler bunu (yanlışlıkla anlamlı görünen ama alakasız) 409'a çeviriyor.
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void url_deki_organizationId_header_ile_eslesmezse_403_doner() throws Exception {
        TenantContext.set(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/audit-logs", organizationId))
                .andExpect(status().isForbidden());
    }

    // Not: "eşleşirse 200 döner" senaryosu burada YOK — standaloneSetup'ta Page.empty()'nin
    // Jackson 3 serileştirmesi, gerçek uygulamada Spring Data'nın kendi otomatik yapılandırdığı
    // Page Jackson modülü olmadan UnsupportedOperationException'a düşüyor (tam context'te
    // SpringDataWebAutoConfiguration bunu çözüyor — gerçek bir uygulama hatası değil, saf bir
    // standalone test altyapısı sınırı). Bu sınıfın asıl amacı olan tenant doğrulaması yukarıdaki
    // testte zaten kanıtlanıyor.
}
