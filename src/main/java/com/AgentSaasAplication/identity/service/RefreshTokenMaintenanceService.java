package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.identity.repository.RefreshTokenRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * `refresh_token_records` (bkz. V19 migration)
 * için hiçbir temizlik mekanizması yoktu — `password_reset_tokens`'ın aksine (orada kalıcı
 * tutulması BİLİNÇLİ bir denetim-izi kararıydı, bkz. V18 migration yorumu), burada satırların
 * sonsuza dek birikmesi için hiçbir gerekçe yoktu; her aktif kullanıcının her refresh döngüsü
 * (varsayılan erişim token TTL'si 60dk) yeni bir satır ekliyordu. Bu tablo `organization_id`
 * taşımıyor (kullanıcıya, org'a değil bağlı) — RLS'siz, bu yüzden basit, global bir DELETE yeterli.
 */
@Slf4j
@Service
public class RefreshTokenMaintenanceService {

    private final RefreshTokenRecordRepository refreshTokenRecordRepository;

    public RefreshTokenMaintenanceService(RefreshTokenRecordRepository refreshTokenRecordRepository) {
        this.refreshTokenRecordRepository = refreshTokenRecordRepository;
    }

    /** revoked_at YA DA expires_at cutoff'tan eskiyse satır artık işe yaramaz — silinir. */
    @Transactional
    public long purgeStaleRecords(Instant cutoff) {
        long deleted = refreshTokenRecordRepository.deleteByRevokedAtBeforeOrExpiresAtBefore(cutoff, cutoff);
        if (deleted > 0) {
            log.info("Eski refresh_token_records satırları temizlendi: silinenSayısı={}, cutoff={}", deleted, cutoff);
        }
        return deleted;
    }
}
