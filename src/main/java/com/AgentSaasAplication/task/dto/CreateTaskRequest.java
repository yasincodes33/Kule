package com.AgentSaasAplication.task.dto;

import com.AgentSaasAplication.common.domain.ModelTier;
import com.AgentSaasAplication.common.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTaskRequest(
        @NotNull(message = "Görev tipi boş olamaz")
        TaskType type,

        @NotBlank(message = "Görev başlığı boş olamaz")
        String title,

        UUID agentConnectionId,        // opsiyonel — bulut modeline (Claude/ChatGPT/Gemini) zorlar

        UUID runnerConnectionId,       // opsiyonel — belirli bir kullanıcı runner'ına zorlar

        ModelTier preferredModelTier,   // opsiyonel — API-tabanlı agent'larda model tavanını belirler

        UUID assignedUserId,           // opsiyonel - belirli bir kullanıcıya/çalışana atar

        String prompt                  // opsiyonel — boşsa çağıran taraf başlığı prompt yerine kullanır
) {}