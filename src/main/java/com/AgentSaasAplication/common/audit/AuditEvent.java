package com.AgentSaasAplication.common.audit;

import java.util.Map;
import java.util.UUID;

public record AuditEvent(UUID organizationId, UUID actorUserId, // null olabilir — sistem tetikli aksiyonlarda (örn.
																// otomatik dispatch)
		String action, String entityType, UUID entityId, Map<String, Object> metadata // null olabilir
) {
}