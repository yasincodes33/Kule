package com.AgentSaasAplication.notification.service;

import com.AgentSaasAplication.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * `notifications`'ı hiç temizleyen bir mekanizma
 * yoktu — okunmuş bildirimler süresiz birikiyordu. Bu test yeni purgeOldRead()'in repository'e
 * doğru cutoff'u ilettiğini kanıtlıyor (organizasyon filtresi burada değil, RLS'te — bkz.
 * NotificationRetentionScheduler'ın TenantContext kullanımı).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;

    @Test
    void purgeOldRead_cutoff_tan_eski_okunmus_bildirimleri_siler() {
        NotificationService service = new NotificationService(notificationRepository);
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        when(notificationRepository.deleteByReadAtBefore(cutoff)).thenReturn(7L);

        long deleted = service.purgeOldRead(cutoff);

        assertThat(deleted).isEqualTo(7L);
    }
}
