package com.AgentSaasAplication.audit.domain;

import com.AgentSaasAplication.common.audit.AuditEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "audit_log_entries")
public class AuditLogEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(name = "actor_user_id")
	private UUID actorUserId;

	@Column(nullable = false)
	private String action;

	@Column(name = "entity_type", nullable = false)
	private String entityType;

	@Column(name = "entity_id")
	private UUID entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, Object> metadata;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	private AuditLogEntry(UUID organizationId, UUID actorUserId, String action, String entityType, UUID entityId,
			Map<String, Object> metadata) {
		this.organizationId = organizationId;
		this.actorUserId = actorUserId;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.metadata = metadata;
		this.createdAt = Instant.now();
	}

	public static AuditLogEntry from(AuditEvent event) {
		return new AuditLogEntry(event.organizationId(), event.actorUserId(), event.action(), event.entityType(),
				event.entityId(), event.metadata());
	}
}