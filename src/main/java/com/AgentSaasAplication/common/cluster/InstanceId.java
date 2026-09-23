package com.AgentSaasAplication.common.cluster;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bu JVM'in çoklu-instance ortamında kendini tanımlamak için kullandığı, süreç boyunca
 * sabit rastgele kimlik. BridgeSessionRegistry'nin Redis yönlendirme tablosunda ("bu runner şu an
 * hangi instance'a bağlı") ve per-instance relay kanallarında (`bridge:relay:{instanceId}`)
 * kullanılıyor.
 */
@Component
public class InstanceId {

    private final String value = UUID.randomUUID().toString();

    public String value() {
        return value;
    }
}
