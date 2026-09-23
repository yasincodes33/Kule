package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.cluster.SchedulerLock;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.OrganizationSweepService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RunnerHealthScheduler/ApprovalExpirationScheduler ile aynı desen: her organizasyon
 * için TenantContext ayarlanıp StaleDispatchService'e devrediliyor.
 *
 * {@link SchedulerLock} ile korunuyor — bkz. o sınıfın Javadoc'u.
 */
@Slf4j
@Component
public class StaleDispatchScheduler {

    private static final Duration LOCK_TTL = Duration.ofSeconds(50);

    private final OrganizationSweepService organizationSweepService;
    private final StaleDispatchService staleDispatchService;
    private final SchedulerLock schedulerLock;

    @Value("${app.task.stale-dispatch-threshold-seconds:120}")
    private long staleThresholdSeconds;

    public StaleDispatchScheduler(OrganizationSweepService organizationSweepService,
                                   StaleDispatchService staleDispatchService,
                                   SchedulerLock schedulerLock) {
        this.organizationSweepService = organizationSweepService;
        this.staleDispatchService = staleDispatchService;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.task.stale-dispatch-check-interval-ms:60000}")
    public void sweepStaleDispatches() {
        if (!schedulerLock.tryAcquire("stale-dispatch-sweep", LOCK_TTL)) {
            return;
        }
        Instant updatedBefore = Instant.now().minusSeconds(staleThresholdSeconds);

        for (UUID orgId : organizationSweepService.allOrganizationIds()) {
            TenantContext.set(orgId);
            try {
                List<UUID> staleIds = staleDispatchService.findStaleDispatchIds(updatedBefore);
                for (UUID taskId : staleIds) {
                    try {
                        staleDispatchService.failStaleDispatch(taskId);
                        log.warn("Asılı kalmış görev FAILED'e çekildi (runner offline): taskId={}", taskId);
                    } catch (Exception e) {
                        log.error("Asılı görev başarısız işaretlenemedi: taskId={}", taskId, e);
                    }
                }
            } catch (Exception e) {
                log.error("Organizasyon için asılı görev taraması başarısız: orgId={}", orgId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
