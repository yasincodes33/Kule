package com.AgentSaasAplication.runner.service;

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
 * Heartbeat'i kesilen runner'ları offline'a çeker.
 *
 * {@link SchedulerLock} ile korunuyor — bkz. o sınıfın Javadoc'u.
 */
@Slf4j
@Component
public class RunnerHealthScheduler {

    private static final Duration LOCK_TTL = Duration.ofSeconds(50);

    private final OrganizationSweepService organizationSweepService;
    private final RunnerConnectionService runnerConnectionService;
    private final SchedulerLock schedulerLock;

    @Value("${app.agent.stale-threshold-seconds:300}")
    private long staleThresholdSeconds;

    public RunnerHealthScheduler(OrganizationSweepService organizationSweepService,
                                  RunnerConnectionService runnerConnectionService,
                                  SchedulerLock schedulerLock) {
        this.organizationSweepService = organizationSweepService;
        this.runnerConnectionService = runnerConnectionService;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.agent.health-check-interval-ms:60000}")
    public void sweepStaleRunners() {
        if (!schedulerLock.tryAcquire("runner-health-sweep", LOCK_TTL)) {
            return;
        }
        Instant staleBefore = Instant.now().minusSeconds(staleThresholdSeconds);

        for (UUID orgId : organizationSweepService.allOrganizationIds()) {
            TenantContext.set(orgId);
            try {
                List<UUID> staleIds = runnerConnectionService.findStaleOnlineRunnerIds(staleBefore);
                for (UUID runnerId : staleIds) {
                    try {
                        runnerConnectionService.markOfflineDueToStaleness(runnerId);
                    } catch (Exception e) {
                        log.error("Runner stale işaretleme başarısız: runnerId={}", runnerId, e);
                    }
                }
            } catch (Exception e) {
                log.error("Organizasyon için runner sağlık taraması başarısız: orgId={}", orgId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
