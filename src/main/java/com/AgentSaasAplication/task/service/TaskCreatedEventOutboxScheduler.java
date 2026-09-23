package com.AgentSaasAplication.task.service;

import com.AgentSaasAplication.common.cluster.SchedulerLock;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.OrganizationSweepService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * StaleDispatchScheduler/RunnerHealthScheduler ile aynı desen: her organizasyon için
 * TenantContext ayarlanıp TaskCreatedEventOutboxService'e devrediliyor. Bu, Kafka broker'ının
 * geçici olarak erişilemez olduğu (TaskCreatedEventKafkaBridge'in AFTER_COMMIT hızlı yolunun
 * başarısız olduğu) durumda "en-az-bir-kez teslim" garantisinin gerçek kaynağı — broker geri
 * gelene kadar satır her taramada tekrar denenir, hiçbir görev sessizce kaybolmaz.
 *
 * {@link SchedulerLock} olmadan, 2+ instance'lı bir
 * dağıtımda iki instance aynı `publishedAt IS NULL` satırını okuyup ikisi de Kafka'ya
 * yayınlayabilirdi (bu entity'de `@Version` yok) — aynı event'in iki kez teslim edilmesi riski.
 */
@Slf4j
@Component
public class TaskCreatedEventOutboxScheduler {

    private static final Duration LOCK_TTL = Duration.ofSeconds(25);

    private final OrganizationSweepService organizationSweepService;
    private final TaskCreatedEventOutboxService outboxService;
    private final SchedulerLock schedulerLock;

    public TaskCreatedEventOutboxScheduler(OrganizationSweepService organizationSweepService,
                                            TaskCreatedEventOutboxService outboxService,
                                            SchedulerLock schedulerLock) {
        this.organizationSweepService = organizationSweepService;
        this.outboxService = outboxService;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.task.outbox-recheck-interval-ms:30000}")
    public void retryUnpublished() {
        if (!schedulerLock.tryAcquire("outbox-retry", LOCK_TTL)) {
            return;
        }
        for (UUID orgId : organizationSweepService.allOrganizationIds()) {
            TenantContext.set(orgId);
            try {
                List<UUID> unpublishedIds = outboxService.findUnpublishedIds();
                for (UUID outboxId : unpublishedIds) {
                    outboxService.attemptPublish(outboxId);
                }
            } catch (Exception e) {
                log.error("Organizasyon için outbox taraması başarısız: orgId={}", orgId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
