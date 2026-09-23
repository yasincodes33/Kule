package com.AgentSaasAplication.project.controller;

import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.project.domain.Project;
import com.AgentSaasAplication.project.service.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProjectController.assertPathMatchesTenant() — URL'deki organizationId ile
 * X-Organization-Id header'ından türeyen TenantContext'in eşleşmesini zorluyor. TenantFilter
 * normalde TenantContext.set()'i header'dan yapıyor; bu slice testte o adım manuel simüle
 * ediliyor (bkz. TaskControllerTest'teki standaloneSetup deseni — burada aynı sebeple filtre
 * bean'lerine hiç ihtiyaç yok, TenantContext saf bir ThreadLocal).
 */
@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock private ProjectService projectService;

    private MockMvc mockMvc;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ProjectController controller = new ProjectController(projectService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void url_deki_organizationId_header_dan_gelen_tenant_ile_eslesmezse_403_doner() throws Exception {
        TenantContext.set(UUID.randomUUID()); // farklı org

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/projects", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"proje\",\"repoUrl\":\"https://x.git\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void url_deki_organizationId_tenant_ile_eslesirse_201_doner() throws Exception {
        TenantContext.set(organizationId);
        when(projectService.createProject(any(), any(), any()))
                .thenReturn(Project.create(organizationId, "proje", "https://x.git"));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/projects", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"proje\",\"repoUrl\":\"https://x.git\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void repoUrl_eksikse_400_doner() throws Exception {
        TenantContext.set(organizationId);

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/projects", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"proje\"}"))
                .andExpect(status().isBadRequest());
    }
}
