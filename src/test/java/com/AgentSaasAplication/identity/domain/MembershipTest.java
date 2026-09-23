package com.AgentSaasAplication.identity.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Membership, tüm yetki kontrollerinin (MembershipAuthorizationService) dayandığı
 * temel — davet/kabul/red akışındaki invariant'lar (yalnızca PENDING'ten geçiş, email eşleşmesi,
 * OWNER'ın davetle atanamaması) burada bir açık olursa organizasyon sınırları delinebilir.
 */
class MembershipTest {

    private final UUID organizationId = UUID.randomUUID();

    @Test
    void owner_rolu_davetle_olusturulamaz() {
        assertThatThrownBy(() -> Membership.invite(organizationId, "x@test.com", Role.OWNER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OWNER rolü davetle atanamaz");
    }

    @Test
    void createOwner_dogrudan_active_ve_owner_olusturur() {
        UUID userId = UUID.randomUUID();
        Membership owner = Membership.createOwner(organizationId, userId);

        assertThat(owner.isOwner()).isTrue();
        assertThat(owner.isActive()).isTrue();
        assertThat(owner.getUserId()).isEqualTo(userId);
    }

    @Test
    void davet_dogru_email_ile_kabul_edilince_active_olur_ve_user_id_atanir() {
        Membership membership = Membership.invite(organizationId, "dev@test.com", Role.DEVELOPER);
        UUID acceptingUserId = UUID.randomUUID();

        membership.accept(acceptingUserId, "dev@test.com");

        assertThat(membership.isActive()).isTrue();
        assertThat(membership.getUserId()).isEqualTo(acceptingUserId);
    }

    @Test
    void davet_kabul_email_buyuk_kucuk_harf_duyarsiz_eslesir() {
        Membership membership = Membership.invite(organizationId, "Dev@Test.com", Role.DEVELOPER);

        membership.accept(UUID.randomUUID(), "dev@test.com");

        assertThat(membership.isActive()).isTrue();
    }

    @Test
    void davet_yanlis_email_ile_kabul_edilemez() {
        Membership membership = Membership.invite(organizationId, "dev@test.com", Role.DEVELOPER);

        assertThatThrownBy(() -> membership.accept(UUID.randomUUID(), "baskasi@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bu kullanıcıya ait değil");
    }

    @Test
    void zaten_active_olan_davet_tekrar_kabul_edilemez() {
        Membership membership = Membership.invite(organizationId, "dev@test.com", Role.DEVELOPER);
        membership.accept(UUID.randomUUID(), "dev@test.com");

        assertThatThrownBy(() -> membership.accept(UUID.randomUUID(), "dev@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bekliyor durumunda değil");
    }

    @Test
    void davet_dogru_email_ile_reddedilince_revoked_olur() {
        Membership membership = Membership.invite(organizationId, "dev@test.com", Role.VIEWER);

        membership.reject("dev@test.com");

        assertThat(membership.isActive()).isFalse();
    }

    @Test
    void revoke_herhangi_bir_durumdan_calisir() {
        Membership membership = Membership.createOwner(organizationId, UUID.randomUUID());

        membership.revoke();

        assertThat(membership.isActive()).isFalse();
    }

    @Test
    void changeRole_null_ile_cagrilirsa_npe_atar() {
        Membership membership = Membership.createOwner(organizationId, UUID.randomUUID());

        assertThatThrownBy(() -> membership.changeRole(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void isAdminOrOwner_yalnizca_admin_ve_owner_icin_true() {
        assertThat(activeWithRole(Role.ADMIN).isAdminOrOwner()).isTrue();
        assertThat(Membership.createOwner(organizationId, UUID.randomUUID()).isAdminOrOwner()).isTrue();
        assertThat(activeWithRole(Role.DEVELOPER).isAdminOrOwner()).isFalse();
        assertThat(activeWithRole(Role.APPROVER).isAdminOrOwner()).isFalse();
        assertThat(activeWithRole(Role.VIEWER).isAdminOrOwner()).isFalse();
    }

    @Test
    void hasApprovalRights_approver_admin_owner_icin_true_developer_viewer_icin_false() {
        assertThat(activeWithRole(Role.APPROVER).hasApprovalRights()).isTrue();
        assertThat(activeWithRole(Role.ADMIN).hasApprovalRights()).isTrue();
        assertThat(activeWithRole(Role.DEVELOPER).hasApprovalRights()).isFalse();
        assertThat(activeWithRole(Role.VIEWER).hasApprovalRights()).isFalse();
    }

    private Membership activeWithRole(Role role) {
        Membership membership = Membership.invite(organizationId, "x@test.com", role);
        membership.accept(UUID.randomUUID(), "x@test.com");
        return membership;
    }
}
