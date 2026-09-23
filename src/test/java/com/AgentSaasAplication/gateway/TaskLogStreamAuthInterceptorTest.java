package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantAccessValidator;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.service.TaskOrchestrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task log WS handshake'i artık `?token=<jwt>` yerine
 * `?ticket=<tek-kullanımlık-bilet>` bekliyor — bu testler ticket eksikse/geçersizse/başka bir
 * task için üretilmişse handshake'in reddedildiğini, ve geçerli bir biletle her zamanki
 * organizasyon-üyeliği + task-varlığı kontrollerinin hâlâ uygulandığını kanıtlıyor.
 */
@ExtendWith(MockitoExtension.class)
class TaskLogStreamAuthInterceptorTest {

    @Mock private TenantAccessValidator tenantAccessValidator;
    @Mock private TaskOrchestrationService taskOrchestrationService;
    @Mock private WsTicketService wsTicketService;

    private TaskLogStreamAuthInterceptor interceptor;

    private final UUID taskId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private ServletServerHttpRequest requestFor(String queryString) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ws/tasks/" + taskId + "/logs");
        req.setQueryString(queryString);
        return new ServletServerHttpRequest(req);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        interceptor = new TaskLogStreamAuthInterceptor(tenantAccessValidator, taskOrchestrationService, wsTicketService);
    }

    private Task sampleTask() {
        return Task.create(organizationId, UUID.randomUUID(), UUID.randomUUID(), TaskType.DEV, "başlık", null);
    }

    @Test
    void ticket_query_param_eksikse_400_ile_reddedilir() {
        ServletServerHttpRequest request = requestFor("");
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        assertThat(response.getServletResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void gecersiz_veya_suresi_dolmus_bilet_401_ile_reddedilir() {
        when(wsTicketService.consume("kotu-bilet")).thenReturn(Optional.empty());
        ServletServerHttpRequest request = requestFor("ticket=kotu-bilet");
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        assertThat(response.getServletResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void baska_bir_task_icin_uretilmis_bilet_403_ile_reddedilir() {
        UUID otherTaskId = UUID.randomUUID();
        when(wsTicketService.consume("bilet"))
                .thenReturn(Optional.of(new WsTicketService.TaskLogTicket(userId, organizationId, otherTaskId)));
        ServletServerHttpRequest request = requestFor("ticket=bilet");
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        assertThat(response.getServletResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void organizasyon_uyeligi_yoksa_403_ile_reddedilir() {
        when(wsTicketService.consume("bilet"))
                .thenReturn(Optional.of(new WsTicketService.TaskLogTicket(userId, organizationId, taskId)));
        when(tenantAccessValidator.hasActiveAccess(organizationId, userId)).thenReturn(false);
        ServletServerHttpRequest request = requestFor("ticket=bilet");
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        assertThat(response.getServletResponse().getStatus()).isEqualTo(403);
        verify(taskOrchestrationService, never()).getTask(any());
    }

    @Test
    void task_baska_bir_orga_aitse_404_ile_reddedilir() {
        when(wsTicketService.consume("bilet"))
                .thenReturn(Optional.of(new WsTicketService.TaskLogTicket(userId, organizationId, taskId)));
        when(tenantAccessValidator.hasActiveAccess(organizationId, userId)).thenReturn(true);
        when(taskOrchestrationService.getTask(taskId)).thenThrow(new NotFoundException("Task bulunamadı"));
        ServletServerHttpRequest request = requestFor("ticket=bilet");
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        boolean result = interceptor.beforeHandshake(request, response, null, new HashMap<>());

        assertThat(result).isFalse();
        assertThat(response.getServletResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void gecerli_bilet_ve_yetkiyle_handshake_kabul_edilir_ve_attribute_lar_set_edilir() {
        when(wsTicketService.consume("bilet"))
                .thenReturn(Optional.of(new WsTicketService.TaskLogTicket(userId, organizationId, taskId)));
        when(tenantAccessValidator.hasActiveAccess(organizationId, userId)).thenReturn(true);
        when(taskOrchestrationService.getTask(taskId)).thenReturn(sampleTask());
        ServletServerHttpRequest request = requestFor("ticket=bilet");
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("taskId")).isEqualTo(taskId);
        assertThat(attributes.get("organizationId")).isEqualTo(organizationId);
        assertThat(attributes.get("userId")).isEqualTo(userId);
    }

    @Test
    void ayni_bilet_iki_kez_kullanilirsa_ikinci_denemede_bulunamaz_ve_401_doner() {
        // consume() gerçek WsTicketService'te atomik getAndDelete kullanıyor — burada mock'u ilk
        // çağrıda dolu, ikincide boş dönecek şekilde kurup interceptor'ın bunu doğru işlediğini
        // (401) doğruluyoruz; asıl atomiklik garantisi WsTicketServiceTest'te.
        when(wsTicketService.consume("tek-kullanimlik"))
                .thenReturn(Optional.of(new WsTicketService.TaskLogTicket(userId, organizationId, taskId)))
                .thenReturn(Optional.empty());
        lenient().when(tenantAccessValidator.hasActiveAccess(organizationId, userId)).thenReturn(true);
        lenient().when(taskOrchestrationService.getTask(taskId)).thenReturn(sampleTask());

        ServletServerHttpRequest request1 = requestFor("ticket=tek-kullanimlik");
        assertThat(interceptor.beforeHandshake(request1, new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>()))
                .isTrue();

        ServletServerHttpRequest request2 = requestFor("ticket=tek-kullanimlik");
        ServletServerHttpResponse response2 = new ServletServerHttpResponse(new MockHttpServletResponse());
        assertThat(interceptor.beforeHandshake(request2, response2, null, new HashMap<>())).isFalse();
        assertThat(response2.getServletResponse().getStatus()).isEqualTo(401);
    }
}
