package com.AgentSaasAplication.audit.dto;

import com.AgentSaasAplication.audit.domain.AuditLogEntry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(UUID id, UUID actorUserId, String action, String entityType, UUID entityId,
		Map<String, Object> metadata, Instant createdAt) {
	public static AuditLogResponse from(AuditLogEntry entry) {
		return new AuditLogResponse(entry.getId(), entry.getActorUserId(), entry.getAction(), entry.getEntityType(),
				entry.getEntityId(), entry.getMetadata(), entry.getCreatedAt());
	}
}