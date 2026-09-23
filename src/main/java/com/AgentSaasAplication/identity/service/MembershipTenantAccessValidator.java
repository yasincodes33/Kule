package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.tenant.RlsSessionVariables;
import com.AgentSaasAplication.common.tenant.TenantAccessValidator;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.repository.MembershipRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class MembershipTenantAccessValidator implements TenantAccessValidator {

    private final MembershipRepository membershipRepository;
    private final EntityManager entityManager;

    public MembershipTenantAccessValidator(MembershipRepository membershipRepository, EntityManager entityManager) {
        this.membershipRepository = membershipRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public boolean hasActiveAccess(UUID organizationId, UUID userId) {
        RlsSessionVariables.setUserId(entityManager, userId);
        return membershipRepository
                .findByTenantIdAndUserIdAndStatus(organizationId, userId, MembershipStatus.ACTIVE)
                .isPresent();
    }
}