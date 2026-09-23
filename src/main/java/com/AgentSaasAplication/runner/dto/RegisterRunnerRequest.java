package com.AgentSaasAplication.runner.dto;

import com.AgentSaasAplication.common.domain.TaskType;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * @param ownerUserId runner'ı çalıştıracak çalışan. Boş bırakılırsa isteği yapan kişi.
 * @param projectId   runner'ın disk üzerindeki çalışma kopyasının ait olduğu proje.
 *                    Boş bırakılırsa runner organizasyondaki her projeye açık olur.
 * @param label       kullanıcının kendi makinelerini ayırt etmesi için serbest metin.
 */
public record RegisterRunnerRequest(
        UUID ownerUserId,
        UUID projectId,
        String label,
        @NotEmpty(message = "En az bir yetenek belirtilmeli") List<TaskType> capabilities
) {}
