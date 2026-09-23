package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.tenant.RlsSessionVariables;
import com.AgentSaasAplication.identity.domain.Membership;
import com.AgentSaasAplication.identity.domain.MembershipStatus;
import com.AgentSaasAplication.identity.repository.MembershipRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class MembershipAuthorizationService {

    private final MembershipRepository membershipRepository;
    private final EntityManager entityManager;

    public MembershipAuthorizationService(MembershipRepository membershipRepository, EntityManager entityManager) {
        this.membershipRepository = membershipRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void requireAdminOrOwner(UUID organizationId, UUID actorUserId) {
        RlsSessionVariables.setUserId(entityManager, actorUserId);

        Membership actorMembership = membershipRepository
                .findByTenantIdAndUserIdAndStatus(organizationId, actorUserId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Bu organizasyonda üyeliğiniz yok"));

        if (!actorMembership.isAdminOrOwner()) {
            throw new AccessDeniedException("Bu işlem için ADMIN veya OWNER rolü gerekir");
        }
    }
    /**
     * Verilen kullanıcının bu organizasyonda AKTİF üye olduğunu doğrular.
     * requireAdminOrOwner'dan farkı: RLS oturum değişkenine dokunmuyor — çünkü burada
     * doğrulanan kişi isteği yapan kişi değil, referans verilen üçüncü bir kullanıcı
     * (görevin atandığı çalışan, runner'ın sahibi). Membership tablosu RLS altında
     * olduğu için başka org'un üyeliği zaten görünmez, sorgu boş döner.
     */
    @Transactional(readOnly = true)
    public void requireActiveMembership(UUID organizationId, UUID userId, String subjectLabel) {
        membershipRepository
                .findByTenantIdAndUserIdAndStatus(organizationId, userId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        subjectLabel + " bu organizasyonun aktif üyesi değil: " + userId));
    }

    @Transactional
    public void requireApprovalPermission(UUID organizationId, UUID actorUserId) {
        RlsSessionVariables.setUserId(entityManager, actorUserId);
        Membership actorMembership = membershipRepository
                .findByTenantIdAndUserIdAndStatus(organizationId, actorUserId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Bu organizasyonda üyeliğiniz yok"));
        if (!actorMembership.hasApprovalRights()) {
            throw new AccessDeniedException("Bu işlem için APPROVER, ADMIN veya OWNER rolü gerekir");
        }
    }
}