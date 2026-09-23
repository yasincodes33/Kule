package com.AgentSaasAplication.common.tenant;

import java.util.UUID;

public interface TenantAccessValidator {
    boolean hasActiveAccess(UUID organizationId, UUID userId);
}