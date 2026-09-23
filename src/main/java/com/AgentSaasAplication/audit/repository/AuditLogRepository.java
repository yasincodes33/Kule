package com.AgentSaasAplication.audit.repository;

import com.AgentSaasAplication.audit.domain.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    Page<AuditLogEntry> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<AuditLogEntry> findByOrganizationIdAndCreatedAtBetween(
            UUID organizationId, Instant from, Instant to, Pageable pageable);

    List<AuditLogEntry> findByEntityTypeAndEntityId(String entityType, UUID entityId);
}