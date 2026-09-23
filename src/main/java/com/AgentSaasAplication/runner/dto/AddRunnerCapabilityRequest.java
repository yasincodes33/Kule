package com.AgentSaasAplication.runner.dto;

import com.AgentSaasAplication.common.domain.TaskType;
import jakarta.validation.constraints.NotNull;

public record AddRunnerCapabilityRequest(
        @NotNull(message = "Görev tipi boş olamaz") TaskType taskType) {
}
