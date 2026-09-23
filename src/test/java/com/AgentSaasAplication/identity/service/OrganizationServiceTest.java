package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.domain.Organization;
import com.AgentSaasAplication.identity.repository.MembershipRepository;
import com.AgentSaasAplication.identity.repository.OrganizationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slug üretimi (küçük harf, tire normalizasyonu, çakışmada sayısal sonek) ve kurucunun
 * otomatik OWNER olması. İkisi de sessizce bozulduğunda fark edilmesi zor regresyonlardır.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query query;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, membershipRepository, entityManager);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        // Gerçek save() @GeneratedValue ile bir id atar — mock'ta bunu simüle etmezsek
        // organization.getId() null kalır ve ownerMembership'in doğru org'a bağlandığı
        // anlamlı biçimde doğrulanamaz.
        lenient().when(organizationRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
    }

    private Organization withId(Organization organization) {
        ReflectionTestUtils.setField(organization, "id", UUID.randomUUID());
        return organization;
    }

    @Test
    void createOrganization_ismi_kucuk_harfe_cevirip_tirelere_donusturerek_slug_uretir() {
        when(organizationRepository.findBySlug(anyString())).thenReturn(Optional.empty());

        Organization result = service.createOrganization("Acme Corp!", UUID.randomUUID());

        assertThat(result.getSlug()).isEqualTo("acme-corp");
    }

    @Test
    void createOrganization_buyuk_I_harfi_iceren_isimde_slug_dogru_uretilir() {
        // Türkçe sistem locale'inde toLowerCase()
        // (Locale.ROOT olmadan) "I" harfini "ı" (noktasız) yapardı — bu, [a-z0-9] regex'iyle
        // eşleşmediği için "I" sessizce bir tireye dönüşüp slug'ı bozardı ("-bm-turkey" gibi).
        when(organizationRepository.findBySlug(anyString())).thenReturn(Optional.empty());

        Organization result = service.createOrganization("IBM Turkey", UUID.randomUUID());

        assertThat(result.getSlug()).isEqualTo("ibm-turkey");
    }

    @Test
    void createOrganization_slug_zaten_varsa_sayisal_sonek_eklenir() {
        when(organizationRepository.findBySlug("acme")).thenReturn(Optional.of(withId(Organization.create("Acme", "acme"))));
        when(organizationRepository.findBySlug("acme-1")).thenReturn(Optional.empty());

        Organization result = service.createOrganization("Acme", UUID.randomUUID());

        assertThat(result.getSlug()).isEqualTo("acme-1");
    }

    @Test
    void createOrganization_kurucu_otomatik_owner_uyeligi_alir() {
        when(organizationRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        UUID creatorId = UUID.randomUUID();
        ArgumentCaptor<Membership> membershipCaptor = ArgumentCaptor.forClass(Membership.class);

        Organization result = service.createOrganization("Acme", creatorId);

        verify(membershipRepository).save(membershipCaptor.capture());
        Membership ownerMembership = membershipCaptor.getValue();
        assertThat(ownerMembership.isOwner()).isTrue();
        assertThat(ownerMembership.isActive()).isTrue();
        assertThat(ownerMembership.getUserId()).isEqualTo(creatorId);
        assertThat(ownerMembership.getTenantId()).isEqualTo(result.getId());
    }

    @Test
    void listMyOrganizations_yalnizca_aktif_uyeliklerin_organizasyonlarini_dondurur() {
        UUID userId = UUID.randomUUID();
        Organization orgA = withId(Organization.create("Org A", "org-a"));
        Membership activeMembership = Membership.createOwner(orgA.getId(), userId);
        when(membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(activeMembership));
        when(organizationRepository.findAllById(List.of(orgA.getId()))).thenReturn(List.of(orgA));

        List<Organization> result = service.listMyOrganizations(userId);

        assertThat(result).containsExactly(orgA);
    }

    @Test
    void listMyOrganizations_uyeligi_olmayan_kullanici_icin_bos_liste_doner() {
        UUID userId = UUID.randomUUID();
        when(membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE)).thenReturn(List.of());
        when(organizationRepository.findAllById(List.of())).thenReturn(List.of());

        List<Organization> result = service.listMyOrganizations(userId);

        assertThat(result).isEmpty();
    }
}
