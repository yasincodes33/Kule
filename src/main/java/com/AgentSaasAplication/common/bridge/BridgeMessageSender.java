package com.AgentSaasAplication.common.bridge;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface BridgeMessageSender {
    boolean send(UUID runnerConnectionId, BridgeMessage message);
    boolean isConnected(UUID runnerConnectionId);

    Optional<BridgeMessage> sendToolCallAndWait(UUID runnerConnectionId, BridgeMessage toolCallMessage, Duration timeout);
}