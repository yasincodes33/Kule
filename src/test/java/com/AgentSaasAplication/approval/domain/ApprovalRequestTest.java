package com.AgentSaasAplication.approval.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ApprovalRequest'in requirePending() koruması, ApprovalService'in üstünde durduğu
 * tek savunma katmanı — zaten karara bağlanmış bir isteğin tekrar onaylanıp task'ı ikinci kez
 * RUNNING'e geçirmesini engelliyor.
 */
class ApprovalRequestTest {

    private ApprovalRequest pending() {
        return ApprovalRequest.requestFor(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600));
    }

    @Test
    void yeni_istek_pending_durumunda_baslar() {
        assertThat(pending().getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void approve_durumu_approved_yapar_ve_karar_bilgisini_kaydeder() {
        ApprovalRequest request = pending();
        UUID approverId = UUID.randomUUID();

        request.approve(approverId);

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(request.getDecidedBy()).isEqualTo(approverId);
        assertThat(request.getDecidedAt()).isNotNull();
    }

    @Test
    void reject_durumu_rejected_yapar_ve_karar_bilgisini_kaydeder() {
        ApprovalRequest request = pending();
        UUID approverId = UUID.randomUUID();

        request.reject(approverId);

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(request.getDecidedBy()).isEqualTo(approverId);
    }

    @Test
    void markExpired_durumu_expired_yapar() {
        ApprovalRequest request = pending();

        request.markExpired();

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.EXPIRED);
    }

    @Test
    void onaylanmis_istek_tekrar_onaylanamaz() {
        ApprovalRequest request = pending();
        request.approve(UUID.randomUUID());

        assertThatThrownBy(() -> request.approve(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten karara bağlanmış");
    }

    @Test
    void reddedilmis_istek_onaylanamaz() {
        ApprovalRequest request = pending();
        request.reject(UUID.randomUUID());

        assertThatThrownBy(() -> request.approve(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suresi_gecmis_istek_expired_sayilir() {
        ApprovalRequest request = ApprovalRequest.requestFor(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now().minusSeconds(1));

        assertThat(request.isExpired()).isTrue();
    }

    @Test
    void suresi_dolmamis_istek_expired_sayilmaz() {
        assertThat(pending().isExpired()).isFalse();
    }
}
