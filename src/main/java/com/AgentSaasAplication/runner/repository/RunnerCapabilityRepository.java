package com.AgentSaasAplication.runner.repository;

import com.AgentSaasAplication.runner.domain.RunnerCapability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RunnerCapabilityRepository extends JpaRepository<RunnerCapability, UUID> {

    List<RunnerCapability> findByRunnerConnectionId(UUID runnerConnectionId);

    /** Runner listeleme sayfasındaki HER satır için ayrı bir
     * capability sorgusu atmak yerine, sayfadaki tüm runnerConnectionId'ler için TEK sorguda
     * toplu çekmek için — bkz. RunnerConnectionService.listRunnersWithCapabilities(). */
    List<RunnerCapability> findByRunnerConnectionIdIn(List<UUID> runnerConnectionIds);

    boolean existsByRunnerConnectionIdAndTaskType(UUID runnerConnectionId, com.AgentSaasAplication.common.domain.TaskType taskType);

    void deleteByRunnerConnectionId(UUID runnerConnectionId);
}
