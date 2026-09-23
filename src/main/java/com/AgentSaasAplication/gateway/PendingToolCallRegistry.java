package com.AgentSaasAplication.gateway;

import com.AgentSaasAplication.common.bridge.BridgeMessage;
import com.AgentSaasAplication.common.cluster.RedisRelay;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Çoklu-instance: bir araç çağrısını başlatan (ve `CompletableFuture`'ı burada
 * bekleten) instance, o çağrıyı yürüten runner'ın TOOL_RESULT'unu ALAN instance'la AYNI
 * olmayabilir — runner'ın canlı WebSocket bağlantısı hangi instance'a düşerse TOOL_RESULT
 * oraya gelir. Bu yüzden `complete()` sonucu yerel map'e değil, Redis'e (`bridge:tool-result`
 * kanalı) yayınlıyor; TÜM instance'lar bunu dinliyor, YALNIZCA o callId için kendi yerel
 * bekleyeni olan instance future'ı tamamlıyor, diğerleri sessizce yok sayıyor.
 * `BridgeMessage` zaten mevcut bridge protokolünün Jackson-serileştirilebilir tipi — yeni bir
 * DTO icat etmeye gerek yok.
 */
@Component
public class PendingToolCallRegistry {

    private static final String CHANNEL = "bridge:tool-result";

    private final Map<UUID, CompletableFuture<BridgeMessage>> pending = new ConcurrentHashMap<>();
    private final RedisRelay redisRelay;

    public PendingToolCallRegistry(RedisRelay redisRelay) {
        this.redisRelay = redisRelay;
    }

    @PostConstruct
    void subscribe() {
        redisRelay.subscribe(CHANNEL, BridgeMessage.class, this::completeLocally);
    }

    public CompletableFuture<BridgeMessage> register(UUID callId) {
        CompletableFuture<BridgeMessage> future = new CompletableFuture<>();
        pending.put(callId, future);
        return future;
    }

    /** Sonucu tüm instance'lara yayınlar — hangi instance'ın beklediği önemli değil. */
    public void complete(UUID callId, BridgeMessage result) {
        redisRelay.publish(CHANNEL, result);
    }

    public void remove(UUID callId) {
        pending.remove(callId);
    }

    private void completeLocally(BridgeMessage result) {
        CompletableFuture<BridgeMessage> future = pending.remove(result.callId());
        if (future != null) {
            future.complete(result);
        }
    }
}
