package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.domain.Role;
import com.AgentSaasAplication.identity.repository.MembershipRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Bu servis, yetki kontrolünün TEK noktası — requireAdminOrOwner/requireApprovalPermission
 * yanlışlıkla gevşek davranırsa (ör. rol kontrolünü atlarsa), yetkisiz bir kullanıcı onay
 * verebilir/runner silebilir. requireActiveMembership ise IDOR benzeri bir açığın (Faz 4/5
 * denetiminde bulunan) kalıcı düzeltmesi — üçüncü bir kullanıcının org üyeliğini doğrular.
 */
@ExtendWith(MockitoExtension.class)
class MembershipAuthorizationServiceTest {

    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    private MembershipAuthorizationService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Membership activeMembership(Role role) {
        Membership membership = Membership.invite(organizationId, "user@test.com", role);
        membership.accept(userId, "user@test.com");
        return membership;
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new MembershipAuthorizationService(membershipRepository, entityManager);
        // requireAdminOrOwner/requireApprovalPermission RLS oturum değişkenini (app.current_user_id)
        // elle ayarlıyor — requireActiveMembership'i test eden iki metotta kullanılmıyor, o yüzden lenient.
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    @Test
    void uye_olmayan_kullanici_admin_or_owner_kontrolunde_reddedilir() {
        when(membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId, userId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireAdminOrOwner(organizationId, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("üyeliğiniz yok");
    }

    @Test
    void developer_rolu_admin_or_owner_kontrolunde_reddedilir() {
        when(membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId, userId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(Role.DEVELOPER)));

        assertThatThrownBy(() -> service.requireAdminOrOwner(organizationId, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ADMIN veya OWNER");
    }

    @Test
    void admin_rolu_admin_or_owner_kontrolunu_gecer() {
        when(membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId, userId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(Role.ADMIN)));

        assertThatCode(() -> service.requireAdminOrOwner(organizationId, userId)).doesNotThrowAnyException();
    }

    @Test
    void owner_rolu_admin_or_owner_kontrolunu_gecer() {
        when(membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId, userId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(Membership.createOwner(organizationId, userId)));

        assertThatCode(() -> service.requireAdminOrOwner(organizationId, userId)).doesNotThrowAnyException();
    }

    @Test
    void viewer_rolu_onay_yetkisi_kontrolunde_reddedilir() {
        when(membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId, userId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(Role.VIEWER)));

        assertThatThrownBy(() -> service.requireApprovalPermission(organizationId, userId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("APPROVER, ADMIN veya OWNER");
    }

    @Test
    void approver_rolu_onay_yetkisi_kontrolunu_gecer() {
        when(membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId, userId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(Role.APPROVER)));

        assertThatCode(() -> service.requireApprovalPermission(organizationId, userId)).doesNotThrowAnyException();
    }

    @Test
    void aktif_uye_olmayan_referans_kullanici_icin_illegal_argument_firlatir() {
        UUID referencedUserId = UUID.randomUUID();
        when(membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId, referencedUserId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveMembership(organizationId, referencedUserId, "Görevin atandığı kullanıcı"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Görevin atandığı kullanıcı")
                .hasMessageContaining("aktif üyesi değil");
    }

    @Test
    void aktif_uye_olan_referans_kullanici_icin_exception_atmaz() {
        UUID referencedUserId = UUID.randomUUID();
        when(membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId, referencedUserId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(Role.DEVELOPER)));

        assertThatCode(() -> service.requireActiveMembership(organizationId, referencedUserId, "Runner sahibi"))
                .doesNotThrowAnyException();
    }
}
