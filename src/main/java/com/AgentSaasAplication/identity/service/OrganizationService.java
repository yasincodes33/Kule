package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.tenant.RlsSessionVariables;
import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.domain.Organization;
import com.AgentSaasAplication.identity.repository.MembershipRepository;
import com.AgentSaasAplication.identity.repository.OrganizationRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final EntityManager entityManager;

    public OrganizationService(OrganizationRepository organizationRepository,
                                MembershipRepository membershipRepository,
                                EntityManager entityManager) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Organization createOrganization(String name, UUID creatorUserId) {
        Organization organization = Organization.create(name, generateUniqueSlug(name));
        organization = organizationRepository.save(organization);

        RlsSessionVariables.setTenantId(entityManager, organization.getId());

        Membership ownerMembership = Membership.createOwner(organization.getId(), creatorUserId);
        membershipRepository.save(ownerMembership);

        log.info("Yeni organizasyon kuruldu: organizationId={}, slug={}, ownerId={}",
                organization.getId(), organization.getSlug(), creatorUserId);

        return organization;
    }

    public List<Organization> listMyOrganizations(UUID userId) {
        RlsSessionVariables.setUserId(entityManager, userId);

        List<UUID> organizationIds = membershipRepository
                .findByUserIdAndStatus(userId, MembershipStatus.ACTIVE)
                .stream()
                .map(Membership::getTenantId)
                .toList();

        return organizationRepository.findAllById(organizationIds);
    }

    // Locale.ROOT ŞART: Türkçe sistem locale'inde
    // toLowerCase() "I" harfini "ı" (noktasız, Unicode U+0131) yapıyor — bu karakter aşağıdaki
    // [a-z0-9] regex'iyle eşleşmediği için "IBM Turkey" gibi bir org adı "ibm-turkey" yerine
    // "-bm-turkey"e dönüşüyordu (I sessizce bir tireye dönüşüyordu). Bulundu ve doğrulandı: aynı
    // Turkish-I tuzağı RiskyToolPolicy.matches()'te de vardı (bkz. o dosyadaki yorum).
    private String generateUniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        String candidate = base;
        int suffix = 1;
        while (organizationRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}