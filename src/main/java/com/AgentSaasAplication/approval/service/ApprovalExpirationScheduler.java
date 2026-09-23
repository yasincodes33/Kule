package com.AgentSaasAplication.approval.service;

import com.AgentSaasAplication.common.cluster.SchedulerLock;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.OrganizationSweepService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@link SchedulerLock} ile korunuyor — kilit olmadan iki instance
 * aynı süresi geçmiş onayı eşzamanlı expire etmeye çalışıp fazladan audit satırı üretebilirdi. */
@Slf4j
@Component
public class ApprovalExpirationScheduler {

    private static final Duration LOCK_TTL = Duration.ofSeconds(50);

    private final OrganizationSweepService organizationSweepService;
    private final ApprovalExpirationService approvalExpirationService;
    private final SchedulerLock schedulerLock;

    public ApprovalExpirationScheduler(OrganizationSweepService organizationSweepService,
                                        ApprovalExpirationService approvalExpirationService,
                                        SchedulerLock schedulerLock) {
        this.organizationSweepService = organizationSweepService;
        this.approvalExpirationService = approvalExpirationService;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.approval.expiration-check-interval-ms:60000}")
    public void sweepExpiredApprovals() {
        if (!schedulerLock.tryAcquire("approval-expiration-sweep", LOCK_TTL)) {
            return;
        }
        Instant now = Instant.now();

        for (UUID orgId : organizationSweepService.allOrganizationIds()) {
            TenantContext.set(orgId);
            try {
                List<UUID> overdueIds = approvalExpirationService.findOverdueApprovalIds(now);
                for (UUID approvalId : overdueIds) {
                    try {
                        approvalExpirationService.expireApproval(approvalId);
                    } catch (Exception e) {
                        log.error("Onay süresi dolumu işlenemedi: approvalId={}", approvalId, e);
                    }
                }
            } catch (Exception e) {
                log.error("Organizasyon için onay süre taraması başarısız: orgId={}", orgId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}