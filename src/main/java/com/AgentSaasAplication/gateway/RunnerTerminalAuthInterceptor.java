package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantAccessValidator;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.runner.service.RunnerConnectionService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * TaskLogStreamAuthInterceptor'ın aynı şekli — `/ws/runners/{runnerId}/terminal` için bilet
 * tabanlı handshake doğrulaması. Ek olarak: bilet yalnızca (önceden onaylanmış bir
 * RunnerTerminalSessionRequest karşılığında, bkz. RunnerTerminalSessionController) o runner
 * için üretilmiş olmalı — başka bir runner'ın bileti burada işe yaramaz.
 */
@Slf4j
@Component
public class RunnerTerminalAuthInterceptor implements HandshakeInterceptor {

    private final TenantAccessValidator tenantAccessValidator;
    private final RunnerConnectionService runnerConnectionService;
    private final WsTicketService wsTicketService;

    public RunnerTerminalAuthInterceptor(TenantAccessValidator tenantAccessValidator,
                                          RunnerConnectionService runnerConnectionService,
                                          WsTicketService wsTicketService) {
        this.tenantAccessValidator = tenantAccessValidator;
        this.runnerConnectionService = runnerConnectionService;
        this.wsTicketService = wsTicketService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        UUID runnerId = extractRunnerId(request.getURI());
        if (runnerId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            log.warn("Runner terminal handshake reddedildi: URI'den runnerId çözülemedi, path={}", request.getURI().getPath());
            return false;
        }

        String ticketParam = firstValue(UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().get("ticket"));
        if (ticketParam == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            log.warn("Runner terminal handshake reddedildi: ticket query param eksik, runnerId={}", runnerId);
            return false;
        }

        Optional<WsTicketService.TerminalTicket> resolved = wsTicketService.consumeTerminalTicket(ticketParam);
        if (resolved.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn("Runner terminal handshake reddedildi: geçersiz/süresi dolmuş/zaten kullanılmış bilet, runnerId={}", runnerId);
            return false;
        }

        WsTicketService.TerminalTicket ticket = resolved.get();
        if (!ticket.runnerConnectionId().equals(runnerId)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            log.warn("Runner terminal handshake reddedildi: bilet başka bir runner için üretilmiş, biletRunnerId={}, istenenRunnerId={}",
                    ticket.runnerConnectionId(), runnerId);
            return false;
        }

        UUID organizationId = ticket.organizationId();
        UUID userId = ticket.userId();

        if (!tenantAccessValidator.hasActiveAccess(organizationId, userId)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            log.warn("Runner terminal handshake reddedildi: organizasyon üyeliği yok, userId={}, organizationId={}", userId, organizationId);
            return false;
        }

        TenantContext.set(organizationId);
        try {
            runnerConnectionService.getRunnerConnection(runnerId);
        } catch (NotFoundException e) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            log.warn("Runner terminal handshake reddedildi: runner bulunamadı/bu org'a ait değil, runnerId={}, organizationId={}",
                    runnerId, organizationId);
            return false;
        } finally {
            TenantContext.clear();
        }

        attributes.put("runnerConnectionId", runnerId);
        attributes.put("organizationId", organizationId);
        attributes.put("userId", userId);
        attributes.put("terminalSessionId", UUID.randomUUID());
        // Yalnızca görev sayfasından "bu araçla başlat" ile açılan oturumlarda dolu — bkz.
        // RunnerTerminalSessionRegistry.linkTask. null olabilir, RunnerTerminalWsHandler kontrol ediyor.
        attributes.put("taskId", ticket.taskId());
        log.info("Runner terminal handshake başarılı: runnerId={}, organizationId={}, userId={}", runnerId, organizationId, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private UUID extractRunnerId(URI uri) {
        // /ws/runners/{runnerId}/terminal -> ["", "ws", "runners", "{runnerId}", "terminal"]
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

    private String firstValue(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return URLDecoder.decode(values.get(0), StandardCharsets.UTF_8);
    }
}
