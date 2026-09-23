package com.AgentSaasAplication.audit.controller;

import com.AgentSaasAplication.audit.dto.AuditLogResponse;
import com.AgentSaasAplication.audit.service.AuditService;
import com.AgentSaasAplication.common.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Mimari plandaki endpoint (Bölüm 6): GET /organizations/{orgId}/audit-logs */
    @GetMapping("/organizations/{organizationId}/audit-logs")
    public ResponseEntity<Page<AuditLogResponse>> listAuditLogs(
            @PathVariable UUID organizationId, Pageable pageable) {
        assertPathMatchesTenant(organizationId);
        Page<AuditLogResponse> page = auditService.listOrganizationAuditLogs(pageable)
                .map(AuditLogResponse::from);
        return ResponseEntity.ok(page);
    }

    private void assertPathMatchesTenant(UUID pathOrganizationId) {
        if (!pathOrganizationId.equals(TenantContext.get())) {
            throw new AccessDeniedException("URL'deki organizationId, X-Organization-Id header'ıyla eşleşmiyor");
        }
    }
}