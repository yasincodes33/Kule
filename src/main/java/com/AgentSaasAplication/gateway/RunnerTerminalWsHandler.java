package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.bridge.BridgeMessage;
import com.AgentSaasAplication.common.bridge.BridgeMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * `/ws/runners/{runnerId}/terminal` — tarayıcı ile runner arasındaki canlı shell akışının
 * tarayıcı ucu. Kimlik/yetki tamamen {@link RunnerTerminalAuthInterceptor}'da yapılıyor; bu sınıf
 * yalnızca handshake sırasında oraya konan attribute'ları okuyup RunnerTerminalSessionRegistry'ye
 * kaydediyor/çıkarıyor ve tarayıcı<->runner arasında TERMINAL_INPUT/TERMINAL_RESIZE/TERMINAL_CLOSE
 * mesajlarını BridgeMessageSender üzerinden köprülüyor. Runner'dan gelen yön (TERMINAL_OUTPUT/
 * TERMINAL_CLOSED) AgentBridgeHandler'dan RunnerTerminalSessionRegistry.deliverToBrowser()
 * üzerinden geliyor — bu sınıf o yönü hiç bilmiyor.
 */
@Slf4j
@Component
public class RunnerTerminalWsHandler extends TextWebSocketHandler {

    private static final int DEFAULT_COLS = 80;
    private static final int DEFAULT_ROWS = 24;

    private final RunnerTerminalSessionRegistry sessionRegistry;
    private final BridgeMessageSender bridgeMessageSender;
    private final ObjectMapper objectMapper;

    public RunnerTerminalWsHandler(RunnerTerminalSessionRegistry sessionRegistry,
                                    BridgeMessageSender bridgeMessageSender,
                                    ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.bridgeMessageSender = bridgeMessageSender;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID terminalSessionId = terminalSessionId(session);
        UUID runnerConnectionId = runnerConnectionId(session);

        sessionRegistry.register(terminalSessionId, session);

        UUID taskId = taskId(session);
        if (taskId != null) {
            sessionRegistry.linkTask(terminalSessionId, taskId);
        }

        if (!bridgeMessageSender.isConnected(runnerConnectionId)) {
            log.warn("Terminal oturumu açılamadı: runner artık bağlı değil, runnerId={}", runnerConnectionId);
            closeQuietly(session, CloseStatus.SERVER_ERROR.withReason("Runner bağlı değil"));
            return;
        }

        bridgeMessageSender.send(runnerConnectionId, BridgeMessage.terminalOpen(terminalSessionId, DEFAULT_COLS, DEFAULT_ROWS));
        log.info("Terminal oturumu açıldı: terminalSessionId={}, runnerId={}", terminalSessionId, runnerConnectionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID terminalSessionId = terminalSessionId(session);
        UUID runnerConnectionId = runnerConnectionId(session);

        try {
            BrowserTerminalMessage parsed = objectMapper.readValue(message.getPayload(), BrowserTerminalMessage.class);
            if ("input".equals(parsed.type())) {
                bridgeMessageSender.send(runnerConnectionId,
                        BridgeMessage.terminalInput(terminalSessionId, parsed.data() != null ? parsed.data() : ""));
            } else if ("resize".equals(parsed.type())) {
                int cols = parsed.cols() != null ? parsed.cols() : DEFAULT_COLS;
                int rows = parsed.rows() != null ? parsed.rows() : DEFAULT_ROWS;
                bridgeMessageSender.send(runnerConnectionId, BridgeMessage.terminalResize(terminalSessionId, cols, rows));
            } else {
                log.warn("Tarayıcıdan bilinmeyen terminal mesaj tipi: type={}, terminalSessionId={}", parsed.type(), terminalSessionId);
            }
        } catch (Exception e) {
            log.warn("Tarayıcı terminal mesajı işlenemedi: terminalSessionId={}, hata={}", terminalSessionId, e.getMessage());
        }
    }

    private record BrowserTerminalMessage(String type, String data, Integer cols, Integer rows) {}

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID terminalSessionId = terminalSessionId(session);
        UUID runnerConnectionId = runnerConnectionId(session);

        sessionRegistry.remove(terminalSessionId);
        bridgeMessageSender.send(runnerConnectionId, BridgeMessage.terminalClose(terminalSessionId));
        log.info("Terminal oturumu kapandı: terminalSessionId={}, runnerId={}, status={}", terminalSessionId, runnerConnectionId, status);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // bağlantı zaten kopmuş olabilir
        }
    }

    private UUID terminalSessionId(WebSocketSession session) {
        return (UUID) session.getAttributes().get("terminalSessionId");
    }

    private UUID runnerConnectionId(WebSocketSession session) {
        return (UUID) session.getAttributes().get("runnerConnectionId");
    }

    private UUID taskId(WebSocketSession session) {
        return (UUID) session.getAttributes().get("taskId");
    }
}
