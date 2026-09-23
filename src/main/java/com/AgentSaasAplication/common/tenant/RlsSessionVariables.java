package com.AgentSaasAplication.common.tenant;

import jakarta.persistence.EntityManager;

import java.util.UUID;

public final class RlsSessionVariables {

    private RlsSessionVariables() {}

    public static void setTenantId(EntityManager entityManager, UUID organizationId) {
        entityManager.createNativeQuery(
                "SET LOCAL app.current_tenant_id = '" + organizationId + "'"
        ).executeUpdate();
    }

    public static void setUserId(EntityManager entityManager, UUID userId) {
        if (userId == null) {
            throw new IllegalStateException("setUserId çağrıldı ama userId null — CurrentUserResolver bir UUID döndürmedi");
        }
        entityManager.createNativeQuery(
                "SET LOCAL app.current_user_id = '" + userId + "'"
        ).executeUpdate();
    }

    public static void setUserEmail(EntityManager entityManager, String email) {
        String escaped = email.replace("'", "''");
        entityManager.createNativeQuery(
                "SET LOCAL app.current_user_email = '" + escaped + "'"
        ).executeUpdate();
    }
}