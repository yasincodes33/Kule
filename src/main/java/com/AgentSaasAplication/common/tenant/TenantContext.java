package com.AgentSaasAplication.common.tenant;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID organizationId) {
        CURRENT_TENANT.set(organizationId);
    }

    public static UUID get() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context henüz kurulmadı");
        }
        return tenantId;
    }

    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}