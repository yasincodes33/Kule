package com.AgentSaasAplication.runner.service;

import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.runner.domain.RunnerConnection;
import com.AgentSaasAplication.runner.domain.RunnerStatus;
import com.AgentSaasAplication.runner.repository.RunnerConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * RunnerConnectionService'ten BİLİNÇLİ olarak ayrı, dar kapsamlı bir servis — yalnızca
 * "bu görev için uygun online runner'ı bul" sorumluluğunu taşıyor. Ayrılmasının sebebi
 * bağımlılık yönü: bulut connector'ları (ChatGptConnector vb.) araç çağrılarını bir
 * runner'a iletmek zorunda, ama RunnerConnectionService identity/project modüllerine
 * bağımlı — connector'lar oraya bağlansaydı gereksiz geniş bir bağımlılık ağı oluşurdu.
 */
@Service
@Transactional(readOnly = true)
public class RunnerLookupService {

    private final RunnerConnectionRepository runnerConnectionRepository;

    public RunnerLookupService(RunnerConnectionRepository runnerConnectionRepository) {
        this.runnerConnectionRepository = runnerConnectionRepository;
    }

    /**
     * Göreve uygun online runner'lar. İki filtre de sessiz bir yetki/doğruluk sınırı:
     * <ul>
     *   <li>Görev bir çalışana atanmışsa yalnızca onun makinesi kabul edilir — başkasının
     *       diskinde dosya değiştirmek atamanın anlamını yok ederdi.</li>
     *   <li>Runner bir projeye bağlıysa yalnızca o projenin görevlerini alır — aksi halde
     *       X projesinin görevi, diskinde Y reposu duran bir makinede çalışırdı.</li>
     * </ul>
     */
    public List<RunnerConnection> findEligibleRunners(UUID assignedUserId, UUID projectId) {
        return runnerConnectionRepository
                .findByTenantIdAndStatus(TenantContext.get(), RunnerStatus.ONLINE)
                .stream()
                .filter(r -> assignedUserId == null || assignedUserId.equals(r.getOwnerUserId()))
                .filter(r -> projectId == null || r.servesProject(projectId))
                .toList();
    }

    public UUID findOnlineRunnerId(UUID assignedUserId, UUID projectId) {
        return findEligibleRunners(assignedUserId, projectId).stream()
                .map(RunnerConnection::getId)
                .findFirst()
                .orElse(null);
    }
}
