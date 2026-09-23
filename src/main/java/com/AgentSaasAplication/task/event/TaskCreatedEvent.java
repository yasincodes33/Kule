package com.AgentSaasAplication.task.event;

import com.AgentSaasAplication.common.domain.ModelTier;

import java.util.UUID;

public record TaskCreatedEvent(
        UUID taskId,
        UUID organizationId,
        UUID preferredAgentConnectionId,
        UUID preferredRunnerConnectionId,
        ModelTier preferredModelTier   // null ise TaskType'ın standart zinciri kullanılır
) {}