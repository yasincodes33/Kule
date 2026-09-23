package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.bridge.BridgeMessage;
import com.AgentSaasAplication.common.bridge.BridgeMessageSender;
import com.AgentSaasAplication.common.cluster.InstanceId;
import com.AgentSaasAplication.common.cluster.RedisRelay;
import jakarta.annotation.PostConstruct;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class BridgeMessageSenderImpl implements BridgeMessageSender {

    private static final String RELAY_CHANNEL_PREFIX = "bridge:relay:";

    private final BridgeSessionRegistry sessionRegistry;
    private final PendingToolCallRegistry pendingToolCallRegistry;
    private final ObjectMapper objectMapper;
    private final RedisRelay redisRelay;
    private final InstanceId instanceId;

    public BridgeMessageSenderImpl(BridgeSessionRegistry sessionRegistry,
                                    PendingToolCallRegistry pendingToolCallRegistry,
                                    ObjectMapper objectMapper,
                                    RedisRelay redisRelay,
                                    InstanceId instanceId) {
        this.sessionRegistry = sessionRegistry;
        this.pendingToolCallRegistry = pendingToolCallRegistry;
        this.objectMapper = objectMapper;
        this.redisRelay = redisRelay;
        this.instanceId = instanceId;
    }

    /**
     * Bu instance, KENDİ instanceId'sine özel relay kanalını dinliyor — başka bir
     * instance, burada bağlı olan bir runner'a mesaj göndermek istediğinde (kendi yerelinde
     * bulamayınca BridgeSessionRegistry'nin yönlendirme tablosuna bakıp) buraya yayınlıyor.
     */
    @PostConstruct
    void subscribe() {
        redisRelay.subscribe(RELAY_CHANNEL_PREFIX + instanceId.value(), RelayMessage.class,
                relay -> sessionRegistry.get(relay.runnerConnectionId())
                        .ifPresentOrElse(session -> trySend(session, relay.message()),
                                () -> log.warn("Relay mesajı geldi ama runner artık burada bağlı değil: runnerId={}",
                                        relay.runnerConnectionId())));
    }

    @Override
    public boolean send(UUID runnerConnectionId, BridgeMessage message) {
        Optional<WebSocketSession> local = sessionRegistry.get(runnerConnectionId);
        if (local.isPresent()) {
            return trySend(local.get(), message);
        }

        // Yerelde yok — başka bir instance'a bağlı olabilir, yönlendirme tablosuna bak.
        Optional<String> targetInstance = sessionRegistry.locateInstance(runnerConnectionId);
        if (targetInstance.isEmpty()) {
            return false; // hiçbir instance'da bağlı değil
        }
        redisRelay.publish(RELAY_CHANNEL_PREFIX + targetInstance.get(),
                new RelayMessage(runnerConnectionId, message));
        // İyimser dönüş: hedef instance'ın gerçekten teslim ettiğinin senkron onayı yok
        // (bunun için ayrı bir ack round-trip'i gerekirdi) — sendToolCallAndWait zaten
        // TOOL_RESULT'un kendisini (PendingToolCallRegistry üzerinden, o da çoklu-instance)
        // bekleyerek asıl doğrulamayı yapıyor.
        return true;
    }

    @Override
    public boolean isConnected(UUID runnerConnectionId) {
        return sessionRegistry.isConnected(runnerConnectionId);
    }

    @Override
    public Optional<BridgeMessage> sendToolCallAndWait(UUID runnerConnectionId, BridgeMessage toolCallMessage, Duration timeout) {
        UUID callId = toolCallMessage.callId();
        if (callId == null) {
            throw new IllegalArgumentException("Araç çağrısı mesajında callId zorunlu");
        }

        CompletableFuture<BridgeMessage> future = pendingToolCallRegistry.register(callId);
        boolean sent = send(runnerConnectionId, toolCallMessage);
        if (!sent) {
            pendingToolCallRegistry.remove(callId);
            log.warn("Araç çağrısı gönderilemedi: callId={}, runnerConnectionId={}", callId, runnerConnectionId);
            return Optional.empty();
        }

        try {
            BridgeMessage result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return Optional.of(result);
        } catch (TimeoutException e) {
            log.warn("Araç çağrısı zaman aşımına uğradı: callId={}, timeout={}", callId, timeout);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException e) {
            log.error("Araç çağrısı sonucu işlenirken hata: callId={}", callId, e);
            return Optional.empty();
        } finally {
            pendingToolCallRegistry.remove(callId);
        }
    }

    private boolean trySend(WebSocketSession session, BridgeMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (Exception e) {
            log.error("Bridge mesajı gönderilemedi: sessionId={}", session.getId(), e);
            return false;
        }
    }

    public record RelayMessage(UUID runnerConnectionId, BridgeMessage message) {}
}