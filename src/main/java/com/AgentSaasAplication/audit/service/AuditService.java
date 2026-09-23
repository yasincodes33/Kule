package com.AgentSaasAplication.audit.service;

import com.AgentSaasAplication.audit.domain.AuditLogEntry;
import com.AgentSaasAplication.audit.repository.AuditLogRepository;
import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(AuditEvent event) {
        AuditLogEntry entry = AuditLogEntry.from(event);
        auditLogRepository.save(entry);
        log.info("Audit kaydı oluşturuldu: action={}, entityType={}, entityId={}, organizationId={}",
                event.action(), event.entityType(), event.entityId(), event.organizationId());
    }

    public Page<AuditLogEntry> listOrganizationAuditLogs(Pageable pageable) {
        return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(TenantContext.get(), pageable);
    }
}