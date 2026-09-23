package com.AgentSaasAplication.task.controller;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.exceptions.GlobalExceptionHandler;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.gateway.WsTicketService;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.domain.TaskStatus;
import com.AgentSaasAplication.task.service.TaskOrchestrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TaskController slice testleri — HTTP katmanının (validasyon, status kodları,
 * GlobalExceptionHandler eşlemesi) servis mantığından bağımsız doğrulanması.
 *
 * TUZAK: @WebMvcTest denendi ama bu projede AgentSaasAplicationApplication'ın kendi
 * @EnableJpaRepositories'i (auto-config'e bırakılmak yerine bilinçli olarak elle eklenmiş)
 * @WebMvcTest'in web-dışı bean'leri filtrelemesini atlatıp gerçek EntityManagerFactory/DataSource
 * kurulumunu tetikliyordu — slice test tam bir Spring context'e ihtiyaç duyar hale geliyordu.
 * Bunun yerine MockMvcBuilders.standaloneSetup() kullanılıyor: Spring context'i HİÇ açmadan
 * controller'ı doğrudan (mock bağımlılıklarla) MockMvc'ye bağlıyor. .apply(springSecurity())
 * SecurityMockMvcRequestPostProcessors.jwt() doğrudan SecurityContextHolder'a yazıyor —
 * springSecurityFilterChain bean'ine (yalnızca tam context'te var olur) ihtiyaç duymuyor,
 * bu yüzden .apply(springSecurity()) BİLİNÇLİ olarak eklenmedi (filter chain'in kendisini
 * test etmek bu slice'ın kapsamı dışında — auth zaten olmuş varsayımıyla yalnızca controller
 * mantığı test ediliyor, tıpkı @WebMvcTest(addFilters=false)'ün amaçladığı gibi).
 */
@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock private TaskOrchestrationService taskOrchestrationService;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private WsTicketService wsTicketService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TaskController controller = new TaskController(taskOrchestrationService, currentUserResolver, wsTicketService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Task sampleTask(TaskStatus status) {
        Task task = Task.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TaskType.DEV, "başlık", null);
        task.transitionTo(TaskStatus.DISPATCHED);
        if (status == TaskStatus.RUNNING) {
            task.transitionTo(TaskStatus.RUNNING);
        }
        return task;
    }

    @Test
    void gecerli_istekle_task_olusturma_201_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(taskOrchestrationService.createTask(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(sampleTask(TaskStatus.DISPATCHED));

        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", UUID.randomUUID())
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DEV\",\"title\":\"yeni görev\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
    }

    @Test
    void title_eksikse_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", UUID.randomUUID())
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DEV\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void type_eksikse_400_doner() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", UUID.randomUUID())
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"başlık\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agent_ve_runner_ayni_anda_verilirse_servisin_attigi_400_e_ceviriliyor() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(taskOrchestrationService.createTask(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("agentConnectionId ve runnerConnectionId aynı anda verilemez"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", UUID.randomUUID())
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DEV\",\"title\":\"x\",\"agentConnectionId\":\""
                                + UUID.randomUUID() + "\",\"runnerConnectionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("aynı anda verilemez")));
    }

    @Test
    void var_olmayan_task_getirilirse_404_doner() throws Exception {
        when(taskOrchestrationService.getTask(any())).thenThrow(new NotFoundException("Task bulunamadı"));

        mockMvc.perform(get("/api/v1/tasks/{taskId}", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void mevcut_task_getirilirse_200_ve_dogru_govde_doner() throws Exception {
        Task task = sampleTask(TaskStatus.DISPATCHED);
        when(taskOrchestrationService.getTask(any())).thenReturn(task);

        MvcResult result = mockMvc.perform(get("/api/v1/tasks/{taskId}", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"DISPATCHED\"");
    }

    @Test
    void FAILED_olmayan_task_retry_edilirse_409_doner() throws Exception {
        when(currentUserResolver.resolve(any())).thenReturn(UUID.randomUUID());
        when(taskOrchestrationService.retryTask(any(), any()))
                .thenThrow(new IllegalStateException("Sadece FAILED durumundaki task'lar yeniden denenebilir"));

        mockMvc.perform(post("/api/v1/tasks/{taskId}/retry", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isConflict());
    }

    @Test
    void gecerli_task_icin_ws_ticket_uretilir() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantContext.set(organizationId);
        when(taskOrchestrationService.getTask(taskId)).thenReturn(sampleTask(TaskStatus.RUNNING));
        when(currentUserResolver.resolve(any())).thenReturn(userId);
        when(wsTicketService.issueTicket(userId, organizationId, taskId)).thenReturn("bilet-123");

        mockMvc.perform(post("/api/v1/tasks/{taskId}/logs/ws-ticket", taskId).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").value("bilet-123"));
    }

    @Test
    void var_olmayan_task_icin_ws_ticket_istenirse_404_doner() throws Exception {
        TenantContext.set(UUID.randomUUID());
        when(taskOrchestrationService.getTask(any())).thenThrow(new NotFoundException("Task bulunamadı"));

        mockMvc.perform(post("/api/v1/tasks/{taskId}/logs/ws-ticket", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNotFound());
    }
}
