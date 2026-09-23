package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantAccessValidator;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.task.service.TaskOrchestrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * `/ws/tasks/{taskId}/logs` için bilet tabanlı handshake doğrulaması. Asıl (uzun ömürlü)
 * access token'ın `?token=<jwt>` query param'ında taşınması; tarayıcı geçmişi, network
 * log'u ya da ters proxy access log'u gibi yerlerde tam yetkili bir kimlik bilgisinin
 * sızmasına açık bir yüzey olurdu. Bunun yerine
 * `POST /api/v1/tasks/{taskId}/logs/ws-ticket` (normal Bearer + X-Organization-Id ile
 * doğrulanan bir HTTP isteği) TEK KULLANIMLIK, 60 saniye geçerli bir bilet üretir —
 * bkz. {@link WsTicketService} — ve handshake'te yalnızca o bilet `?ticket=<bilet>` ile
 * taşınır.
 *
 * Yetki modeli, mevcut `GET /api/v1/tasks/{taskId}/logs` HTTP uç noktasıyla birebir aynı —
 * o org'un aktif üyesi olmak yeterli, task'a özel ek bir rol kontrolü şu an hiçbir yerde yok:
 * 1) organizasyon üyeliği ({@link TenantAccessValidator}), 2) task'ın gerçekten o org'a ait
 * olduğu (RLS ile, {@link TaskOrchestrationService#getTask}) — bilet zaten ticket-issue anında
 * bunları doğrulamıştı, burada TEKRAR doğrulanıyor (savunma derinliği — bilet arada organizasyon
 * üyeliği iptal edilmiş birine ait olabilir).
 */
@Slf4j
@Component
public class TaskLogStreamAuthInterceptor implements HandshakeInterceptor {

    private final TenantAccessValidator tenantAccessValidator;
    private final TaskOrchestrationService taskOrchestrationService;
    private final WsTicketService wsTicketService;

    public TaskLogStreamAuthInterceptor(TenantAccessValidator tenantAccessValidator,
                                         TaskOrchestrationService taskOrchestrationService,
                                         WsTicketService wsTicketService) {
        this.tenantAccessValidator = tenantAccessValidator;
        this.taskOrchestrationService = taskOrchestrationService;
        this.wsTicketService = wsTicketService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        UUID taskId = extractTaskId(request.getURI());
        if (taskId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            log.warn("Task log stream handshake reddedildi: URI'den taskId çözülemedi, path={}", request.getURI().getPath());
            return false;
        }

        Map<String, java.util.List<String>> params = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams();
        String ticketParam = firstValue(params.get("ticket"));

        if (ticketParam == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            log.warn("Task log stream handshake reddedildi: ticket query param eksik, taskId={}", taskId);
            return false;
        }

        Optional<WsTicketService.TaskLogTicket> resolved = wsTicketService.consume(ticketParam);
        if (resolved.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn("Task log stream handshake reddedildi: geçersiz/süresi dolmuş/zaten kullanılmış bilet, taskId={}", taskId);
            return false;
        }

        WsTicketService.TaskLogTicket ticket = resolved.get();
        if (!ticket.taskId().equals(taskId)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            log.warn("Task log stream handshake reddedildi: bilet başka bir task için üretilmiş, biletTaskId={}, istenenTaskId={}",
                    ticket.taskId(), taskId);
            return false;
        }

        UUID organizationId = ticket.organizationId();
        UUID userId = ticket.userId();

        if (!tenantAccessValidator.hasActiveAccess(organizationId, userId)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            log.warn("Task log stream handshake reddedildi: organizasyon üyeliği yok, userId={}, organizationId={}",
                    userId, organizationId);
            return false;
        }

        TenantContext.set(organizationId);
        try {
            // Yalnızca varlık + tenant doğrulaması için — RLS, taskId başka bir org'a aitse
            // burada NotFoundException fırlatılmasını garanti eder (organizationId'nin
            // kendisi doğru olsa bile, task o org'da değilse görülemez).
            taskOrchestrationService.getTask(taskId);
        } catch (NotFoundException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            log.warn("Task log stream handshake reddedildi: task bulunamadı/bu org'a ait değil, taskId={}, organizationId={}",
                    taskId, organizationId);
            return false;
        } finally {
            TenantContext.clear();
        }

        attributes.put("taskId", taskId);
        attributes.put("organizationId", organizationId);
        attributes.put("userId", userId);
        log.info("Task log stream handshake başarılı: taskId={}, organizationId={}, userId={}", taskId, organizationId, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private UUID extractTaskId(URI uri) {
        // /ws/tasks/{taskId}/logs -> ["", "ws", "tasks", "{taskId}", "logs"]
        String[] segments = uri.getPath().split("/");
        if (segments.length < 5) {
            return null;
        }
        try {
            return UUID.fromString(segments[3]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * `UriComponentsBuilder...getQueryParams()` ham (yüzde-kodlu) değerleri döndürüyor —
     * manuel decode etmezsek `@` gibi karakterler `%40` olarak kalıp email'i başka bir
     * kullanıcıya (yanlışlıkla auto-provision edilen boş bir kullanıcıya) çözdürüyordu.
     */
    private String firstValue(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return URLDecoder.decode(values.get(0), StandardCharsets.UTF_8);
    }
}
