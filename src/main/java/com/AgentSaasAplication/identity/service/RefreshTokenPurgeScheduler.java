package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.cluster.SchedulerLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Günde bir kez, revoke edilmiş/süresi dolmuş
 * refresh_token_records satırlarını (varsayılan 7 günden eski olanları) temizler — bkz.
 * RefreshTokenMaintenanceService. RLS'siz global bir tablo olduğu için organizasyon bazlı
 * taramaya gerek yok; SchedulerLock yine de çoklu-instance'ta gereksiz eşzamanlı DELETE
 * taramalarını önlemek için kullanılıyor. */
@Slf4j
@Component
public class RefreshTokenPurgeScheduler {

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final RefreshTokenMaintenanceService refreshTokenMaintenanceService;
    private final SchedulerLock schedulerLock;

    @Value("${app.auth.refresh-token-retention-days:7}")
    private long retentionDays;

    public RefreshTokenPurgeScheduler(RefreshTokenMaintenanceService refreshTokenMaintenanceService,
                                       SchedulerLock schedulerLock) {
        this.refreshTokenMaintenanceService = refreshTokenMaintenanceService;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(fixedDelayString = "${app.auth.refresh-token-purge-interval-ms:86400000}")
    public void purgeStaleRecords() {
        if (!schedulerLock.tryAcquire("refresh-token-purge", LOCK_TTL)) {
            return;
        }
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
            refreshTokenMaintenanceService.purgeStaleRecords(cutoff);
        } catch (Exception e) {
            log.error("Refresh token temizliği başarısız", e);
        }
    }
}
