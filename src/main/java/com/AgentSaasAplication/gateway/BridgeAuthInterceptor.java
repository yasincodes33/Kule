package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.service.RunnerConnectionService;
import com.AgentSaasAplication.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class BridgeAuthInterceptor implements HandshakeInterceptor {

    private static final String ORG_HEADER = "X-Organization-Id";
    private static final String TOKEN_HEADER = "X-Bridge-Token";

    private final RunnerConnectionService runnerConnectionService;

    public BridgeAuthInterceptor(RunnerConnectionService runnerConnectionService) {
        this.runnerConnectionService = runnerConnectionService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        List<String> orgValues = request.getHeaders().get(ORG_HEADER);
        List<String> tokenValues = request.getHeaders().get(TOKEN_HEADER);

        if (isEmpty(orgValues) || isEmpty(tokenValues)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            log.warn("Bridge handshake reddedildi: {} veya {} header'ı eksik", ORG_HEADER, TOKEN_HEADER);
            return false;
        }

        UUID organizationId;
        try {
            organizationId = UUID.fromString(orgValues.get(0));
        } catch (IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        TenantContext.set(organizationId);
        try {
            RunnerConnection connection = runnerConnectionService.authenticateBridge(tokenValues.get(0));
            attributes.put("organizationId", organizationId);
            attributes.put("runnerConnectionId", connection.getId());
            attributes.put("ownerUserId", connection.getOwnerUserId());
            log.info("Bridge handshake başarılı: runnerId={}, organizationId={}, ownerUserId={}",
                    connection.getId(), organizationId, connection.getOwnerUserId());
            return true;
        } catch (AccessDeniedException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn("Bridge handshake reddedildi: geçersiz token, organizationId={}", organizationId);
            return false;
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }
}