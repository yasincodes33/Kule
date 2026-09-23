package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.identity.domain.Organization;
import com.AgentSaasAplication.identity.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Her org için periyodik tarama yapan dört scheduler
 * (ApprovalExpirationScheduler/RunnerHealthScheduler/StaleDispatchScheduler/
 * TaskCreatedEventOutboxScheduler) kendi paketlerinden (approval/runner/task)
 * {@code identiy.repository.OrganizationRepository}'i DOĞRUDAN enjekte ediyordu — identity
 * modülünün aggregate sınırını dört ayrı yerden delen bir bağımlılıktı. Artık yalnızca bu
 * servis o repository'ye bağımlı; diğer modüller org listesine yalnızca bu dar kapsamlı arayüz
 * üzerinden erişiyor.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationSweepService {

    private final OrganizationRepository organizationRepository;

    public OrganizationSweepService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public List<UUID> allOrganizationIds() {
        return organizationRepository.findAll().stream().map(Organization::getId).toList();
    }
}
