package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.identity.repository.RefreshTokenRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * refresh_token_records'ı hiç temizleyen bir
 * mekanizma yoktu — bu, yeni eklenen purge'ün doğru cutoff'u geçtiğini ve silinen sayıyı
 * döndürdüğünü kanıtlıyor.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenMaintenanceServiceTest {

    @Mock private RefreshTokenRecordRepository refreshTokenRecordRepository;

    @Test
    void purgeStaleRecords_ayni_cutoff_i_revoked_ve_expires_icin_kullanir_ve_silinen_sayiyi_dondurur() {
        RefreshTokenMaintenanceService service = new RefreshTokenMaintenanceService(refreshTokenRecordRepository);
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        when(refreshTokenRecordRepository.deleteByRevokedAtBeforeOrExpiresAtBefore(cutoff, cutoff)).thenReturn(42L);

        long deleted = service.purgeStaleRecords(cutoff);

        assertThat(deleted).isEqualTo(42L);
    }
}
