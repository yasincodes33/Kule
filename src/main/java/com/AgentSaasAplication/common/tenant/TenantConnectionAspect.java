package com.AgentSaasAplication.common.tenant;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TenantConnectionAspect {

    private final EntityManager entityManager;

    public TenantConnectionAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) "
            + "|| @within(org.springframework.transaction.annotation.Transactional)")
    public void applyTenantToConnection() {
        if (!TenantContext.isSet()) {
            return;
        }
        RlsSessionVariables.setTenantId(entityManager, TenantContext.get());
    }
}