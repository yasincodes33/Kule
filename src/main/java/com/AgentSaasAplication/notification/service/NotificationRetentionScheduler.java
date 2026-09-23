package com.AgentSaasAplication.notification.service;

import com.AgentSaasAplication.common.cluster.SchedulerLock;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.service.OrganizationSweepService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Günde bir kez, okunmuş ve belirli bir süreden
 * eski bildirimleri temizler — bkz. NotificationService.purgeOldRead. `notifications` RLS'li
 * olduğu için (bkz. V1 migration) diğer sweep'lerle AYNI desen: her org için TenantContext
 * ayarlanıp temizleniyor. audit_log_entries KASITLI OLARAK buraya dahil edilmedi — bir denetim
 * kaydının bütün amacı kalıcı bir hesap verebilirlik izi olması, otomatik silinmesi ayrı, açık
 * bir retention-politikası kararı gerektirir (bu proje kapsamında henüz verilmedi). */
@Slf4j
@Component
public class NotificationRetentionScheduler {

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final OrganizationSweepService organizationSweepService;
    private final NotificationService notificationService;
    private final SchedulerLock schedulerLock;

    @Value("${app.notification.retention-days:90}")
    private long retentionDays;

    public NotificationRetentionScheduler(OrganizationSweepService organizationSweepService,
                                           NotificationService notificationService,
                                           SchedulerLock schedulerLock) {
        this.organizationSweepService = organizationSweepService;
        this.notificationService = notificationService;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.notification.retention-check-interval-ms:86400000}")
    public void purgeOldReadNotifications() {
        if (!schedulerLock.tryAcquire("notification-retention", LOCK_TTL)) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));

        for (UUID orgId : organizationSweepService.allOrganizationIds()) {
            TenantContext.set(orgId);
            try {
                notificationService.purgeOldRead(cutoff);
            } catch (Exception e) {
                log.error("Organizasyon için bildirim temizliği başarısız: orgId={}", orgId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
