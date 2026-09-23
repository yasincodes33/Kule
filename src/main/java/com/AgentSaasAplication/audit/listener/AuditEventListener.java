package com.AgentSaasAplication.audit.listener;

import com.AgentSaasAplication.audit.service.AuditService;
import com.AgentSaasAplication.common.audit.AuditEvent;
import com.AgentSaasAplication.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class AuditEventListener {

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(AuditEvent event) {
        TenantContext.set(event.organizationId());   // ← yeni thread'de tenant bilgisini elle kur
        try {
            auditService.record(event);
        } catch (Exception e) {
            log.error("Audit kaydı oluşturulamadı: action={}, entityType={}, entityId={}",
                    event.action(), event.entityType(), event.entityId(), e);
        } finally {
            TenantContext.clear();
        }
    }
}	