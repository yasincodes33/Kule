package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.email.EmailService;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.domain.Role;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.domain.Organization;
import com.AgentSaasAplication.identity.dto.PendingInvitationResponse;
import com.AgentSaasAplication.identity.repository.MembershipRepository;
import com.AgentSaasAplication.identity.repository.OrganizationRepository;
import com.AgentSaasAplication.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MembershipService — davet/kabul/sahiplik devri akışlarındaki gerçek iş kuralları:
 * çift davet engeli, zaten aktif üyeyi tekrar davet etmeme, kendine sahiplik devrini reddetme,
 * son OWNER'ı kaldırmayı engelleme. Bunlardan herhangi biri bozulursa ya kullanıcı deneyimi
 * bozulur (sessiz çift davet) ya da organizasyon sahipsiz kalabilir (son OWNER kaldırılabilir).
 */
@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock private MembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query query;
    @Mock private MembershipAuthorizationService membershipAuthorizationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EmailService emailService;

    private MembershipService service;

    private final UUID organizationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MembershipService(
                membershipRepository, userRepository, organizationRepository, entityManager,
                membershipAuthorizationService, eventPublisher, emailService);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(organizationRepository.findById(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private User userWithEmail(String email) {
        User user = User.register(email, "$2a$12$test", null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private Organization organizationWithId(UUID id, String name) {
        Organization organization = Organization.create(name, name.toLowerCase(java.util.Locale.ROOT));
        ReflectionTestUtils.setField(organization, "id", id);
        return organization;
    }

    @Test
    void ayni_epostaya_zaten_bekleyen_davet_varsa_ikinci_davet_reddedilir() {
        when(membershipRepository.existsByTenantIdAndInvitedEmailAndStatus(
                organizationId, "x@test.com", MembershipStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.inviteMember(organizationId, UUID.randomUUID(), "x@test.com", Role.DEVELOPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten bekleyen bir davet");
    }

    @Test
    void zaten_aktif_uye_olan_kullanici_tekrar_davet_edilemez() {
        User existing = userWithEmail("x@test.com");
        when(membershipRepository.existsByTenantIdAndInvitedEmailAndStatus(any(), any(), any())).thenReturn(false);
        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.of(existing));
        when(membershipRepository.findByTenantIdAndUserIdAndStatus(organizationId, existing.getId(), MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(userActiveMembership(existing)));

        assertThatThrownBy(() -> service.inviteMember(organizationId, UUID.randomUUID(), "x@test.com", Role.DEVELOPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("zaten organizasyonun üyesi");
    }

    @Test
    void henuz_hesabi_olmayan_kullanici_sorunsuz_davet_edilir() {
        when(membershipRepository.existsByTenantIdAndInvitedEmailAndStatus(any(), any(), any())).thenReturn(false);
        when(userRepository.findByEmail("yeni@test.com")).thenReturn(Optional.empty());
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Membership result = service.inviteMember(organizationId, UUID.randomUUID(), "yeni@test.com", Role.VIEWER);

        assertThat(result.getInvitedEmail()).isEqualTo("yeni@test.com");
        assertThat(result.getStatus()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    void davet_gonderilince_organizasyon_adiyla_bir_davet_e_postasi_gonderilir() {
        // Davet gerçekten e-postayla
        // gönderiliyor, bkz. EmailService.
        when(membershipRepository.existsByTenantIdAndInvitedEmailAndStatus(any(), any(), any())).thenReturn(false);
        when(userRepository.findByEmail("yeni@test.com")).thenReturn(Optional.empty());
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organizationWithId(organizationId, "Acme")));

        service.inviteMember(organizationId, UUID.randomUUID(), "yeni@test.com", Role.VIEWER);

        verify(emailService).sendInvitationEmail("yeni@test.com", "Acme", Role.VIEWER);
    }

    @Test
    void kendine_sahiplik_devri_reddedilir() {
        UUID ownerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.transferOwnership(organizationId, ownerId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kendinize devredilemez");
    }

    @Test
    void owner_olmayan_kullanici_sahiplik_devredemez() {
        UUID currentUserId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        Membership nonOwnerMembership = activeMembership(currentUserId, Role.ADMIN);
        when(membershipRepository.lockByTenantIdAndUserIdAndStatus(organizationId, currentUserId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(nonOwnerMembership));

        assertThatThrownBy(() -> service.transferOwnership(organizationId, currentUserId, newOwnerId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Sadece mevcut OWNER");
    }

    @Test
    void hedef_kullanici_aktif_uye_degilse_devir_reddedilir() {
        UUID currentUserId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        Membership ownerMembership = activeMembership(currentUserId, Role.OWNER);
        when(membershipRepository.lockByTenantIdAndUserIdAndStatus(organizationId, currentUserId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.lockByTenantIdAndUserIdAndStatus(organizationId, newOwnerId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferOwnership(organizationId, currentUserId, newOwnerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aktif üyesi değil");
    }

    @Test
    void gecerli_devir_rolleri_dogru_sekilde_degistiriyor() {
        UUID currentUserId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        Membership ownerMembership = activeMembership(currentUserId, Role.OWNER);
        Membership targetMembership = activeMembership(newOwnerId, Role.DEVELOPER);
        when(membershipRepository.lockByTenantIdAndUserIdAndStatus(organizationId, currentUserId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.lockByTenantIdAndUserIdAndStatus(organizationId, newOwnerId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(targetMembership));

        service.transferOwnership(organizationId, currentUserId, newOwnerId);

        assertThat(ownerMembership.getRole()).isEqualTo(Role.ADMIN);
        assertThat(targetMembership.getRole()).isEqualTo(Role.OWNER);
    }

    @Test
    void son_owner_kaldirma_denemesi_engellenir() {
        UUID membershipId = UUID.randomUUID();
        Membership ownerMembership = activeMembership(UUID.randomUUID(), Role.OWNER);
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.countByTenantIdAndRoleAndStatus(organizationId, Role.OWNER, MembershipStatus.ACTIVE))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.revokeMembership(organizationId, UUID.randomUUID(), membershipId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("son OWNER'ı kaldırılamaz");
    }

    @Test
    void birden_fazla_owner_varken_biri_kaldirilabilir() {
        UUID membershipId = UUID.randomUUID();
        Membership ownerMembership = activeMembership(UUID.randomUUID(), Role.OWNER);
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.countByTenantIdAndRoleAndStatus(organizationId, Role.OWNER, MembershipStatus.ACTIVE))
                .thenReturn(2L);

        service.revokeMembership(organizationId, UUID.randomUUID(), membershipId);

        assertThat(ownerMembership.isActive()).isFalse();
    }

    @Test
    void owner_olmayan_uye_serbestce_kaldirilabilir() {
        UUID membershipId = UUID.randomUUID();
        Membership devMembership = activeMembership(UUID.randomUUID(), Role.DEVELOPER);
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(devMembership));

        service.revokeMembership(organizationId, UUID.randomUUID(), membershipId);

        assertThat(devMembership.isActive()).isFalse();
        org.mockito.Mockito.verify(membershipRepository, never())
                .countByTenantIdAndRoleAndStatus(any(), any(), any());
    }

    @Test
    void listOrganizationMembers_tenantcontextteki_organizasyonun_uyelerini_dondurur() {
        TenantContext.set(organizationId);
        Membership pending = Membership.invite(organizationId, "davetli@test.com", Role.VIEWER);
        Page<Membership> page = new PageImpl<>(List.of(pending), PageRequest.of(0, 20), 1);
        when(membershipRepository.findByTenantId(eq(organizationId), any())).thenReturn(page);

        Page<Membership> result = service.listOrganizationMembers(PageRequest.of(0, 20));

        assertThat(result.getContent()).containsExactly(pending);
    }

    @Test
    void listMyPendingInvitations_kullanici_yoksa_notfound_firlatir() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMyPendingInvitations(userId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listMyPendingInvitations_kullanicinin_emailine_gelen_bekleyen_davetleri_organizasyon_adiyla_dondurur() {
        User user = userWithEmail("davetli@test.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        Membership pending = Membership.invite(organizationId, "davetli@test.com", Role.DEVELOPER);
        when(membershipRepository.findByInvitedEmailAndStatus("davetli@test.com", MembershipStatus.PENDING))
                .thenReturn(List.of(pending));
        when(organizationRepository.findAllById(List.of(organizationId)))
                .thenReturn(List.of(organizationWithId(organizationId, "Acme")));

        List<PendingInvitationResponse> result = service.listMyPendingInvitations(user.getId());

        assertThat(result).containsExactly(
                new PendingInvitationResponse(pending.getId(), organizationId, "Acme", Role.DEVELOPER));
    }

    @Test
    void acceptInvitation_kullanici_yoksa_notfound_firlatir() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvitation(UUID.randomUUID(), userId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void acceptInvitation_davet_yoksa_notfound_firlatir() {
        User user = userWithEmail("davetli@test.com");
        UUID membershipId = UUID.randomUUID();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptInvitation(membershipId, user.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void acceptInvitation_gecerli_davet_kabul_edilir_ve_denetim_kaydi_yayinlanir() {
        User user = userWithEmail("davetli@test.com");
        UUID membershipId = UUID.randomUUID();
        Membership pending = Membership.invite(organizationId, "davetli@test.com", Role.DEVELOPER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(pending));

        Membership result = service.acceptInvitation(membershipId, user.getId());

        assertThat(result.isActive()).isTrue();
        assertThat(result.getUserId()).isEqualTo(user.getId());
        verify(eventPublisher).publishEvent(any(com.AgentSaasAplication.common.audit.AuditEvent.class));
    }

    @Test
    void rejectInvitation_davet_yoksa_notfound_firlatir() {
        User user = userWithEmail("davetli@test.com");
        UUID membershipId = UUID.randomUUID();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rejectInvitation(membershipId, user.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectInvitation_gecerli_red_uyeligi_revoked_yapar_ve_denetim_kaydi_yayinlanir() {
        User user = userWithEmail("davetli@test.com");
        UUID membershipId = UUID.randomUUID();
        Membership pending = Membership.invite(organizationId, "davetli@test.com", Role.VIEWER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(pending));

        service.rejectInvitation(membershipId, user.getId());

        assertThat(pending.isActive()).isFalse();
        verify(eventPublisher).publishEvent(any(com.AgentSaasAplication.common.audit.AuditEvent.class));
    }

    private Membership activeMembership(UUID userId, Role role) {
        if (role == Role.OWNER) {
            return Membership.createOwner(organizationId, userId);
        }
        Membership membership = Membership.invite(organizationId, "x@test.com", role);
        membership.accept(userId, "x@test.com");
        return membership;
    }

    private Membership userActiveMembership(User user) {
        return activeMembership(user.getId(), Role.DEVELOPER);
    }
}
