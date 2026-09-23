package com.AgentSaasAplication.agent.dto;

import com.AgentSaasAplication.common.domain.TaskType;
import jakarta.validation.constraints.NotNull;

public record AddCapabilityRequest(
        @NotNull(message = "Görev tipi boş olamaz") TaskType taskType) {
}